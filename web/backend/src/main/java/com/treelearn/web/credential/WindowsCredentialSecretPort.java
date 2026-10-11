package com.treelearn.web.credential;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Windows 凭据管理器适配的 {@link SecretPort}（任务 T-2026-10-10-013）。
 *
 * <p>经手写 JNA 接口调用 Advapi32 的 {@code CredWriteW/CredReadW/CredDeleteW/CredFree}，写入 generic
 * credential：target 为 {@code Treelearn-Web/provider/<id>}，secret 以 UTF-8 存入 CredentialBlob，Persist 取
 * {@code CRED_PERSIST_LOCAL_MACHINE}，因此它与当前用户绑定、可被同机器上的后续进程重读。
 *
 * <p>失败关闭：任何平台失败都抛 {@link SecretAccessException}，不存在写明文文件或回退明文的路径；
 * {@link #has(String)} 只回答「存在与否」，读到的 blob 从不复制进受管内存。读取得到的本地 {@code byte[]} 用后
 * 尽力清零；JNA 内部缓冲与调用点在 JVM 上无法保证清零，如实记录，不做「已完全擦除」的过度声称。
 */
public final class WindowsCredentialSecretPort implements SecretPort {

    private static final int CRED_TYPE_GENERIC = 1;
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
    private static final int ERROR_NOT_FOUND = 1168;
    private static final int ERROR_ACCESS_DENIED = 5;

    private final Advapi32 advapi32 = Advapi32.INSTANCE;

    @Override
    public void save(String target, char[] secret) {
        SecretPort.requireValidTarget(target);
        if (secret == null || secret.length == 0) {
            throw new SecretAccessException(SecretAccessError.MALFORMED, target,
                    "a secret to store must not be empty");
        }
        byte[] blob = encode(secret);
        try {
            if (blob.length > MAX_BLOB_BYTES) {
                throw new SecretAccessException(SecretAccessError.MALFORMED, target,
                        "the secret encodes to " + blob.length + " bytes, above the "
                                + MAX_BLOB_BYTES + "-byte credential blob limit");
            }
            Memory memory = new Memory(blob.length);
            memory.write(0, blob, 0, blob.length);
            try {
                Credential credential = new Credential();
                credential.Type = CRED_TYPE_GENERIC;
                credential.TargetName = new WString(target);
                credential.CredentialBlobSize = blob.length;
                credential.CredentialBlob = memory;
                credential.Persist = CRED_PERSIST_LOCAL_MACHINE;
                if (!advapi32.CredWriteW(credential, 0)) {
                    throw classify("CredWriteW", target, Native.getLastError());
                }
            } finally {
                memory.clear();
            }
        } finally {
            Arrays.fill(blob, (byte) 0);
        }
    }

    @Override
    public boolean has(String target) {
        SecretPort.requireValidTarget(target);
        PointerByReference out = new PointerByReference();
        if (advapi32.CredReadW(new WString(target), CRED_TYPE_GENERIC, 0, out)) {
            Pointer credential = out.getValue();
            if (credential != null) {
                advapi32.CredFree(credential);
            }
            return true;
        }
        int error = Native.getLastError();
        if (error == ERROR_NOT_FOUND) {
            return false;
        }
        throw classify("CredReadW", target, error);
    }

    @Override
    public Optional<char[]> read(String target) {
        SecretPort.requireValidTarget(target);
        PointerByReference out = new PointerByReference();
        if (!advapi32.CredReadW(new WString(target), CRED_TYPE_GENERIC, 0, out)) {
            int error = Native.getLastError();
            if (error == ERROR_NOT_FOUND) {
                return Optional.empty();
            }
            throw classify("CredReadW", target, error);
        }
        Pointer raw = out.getValue();
        try {
            byte[] blob = new Credential(raw).readBlob();
            if (blob == null || blob.length == 0) {
                throw new SecretAccessException(SecretAccessError.MALFORMED, target,
                        "the stored credential carries no secret bytes");
            }
            try {
                return Optional.of(decode(blob));
            } finally {
                Arrays.fill(blob, (byte) 0);
            }
        } finally {
            if (raw != null) {
                advapi32.CredFree(raw);
            }
        }
    }

    @Override
    public void delete(String target) {
        SecretPort.requireValidTarget(target);
        if (!advapi32.CredDeleteW(new WString(target), CRED_TYPE_GENERIC, 0)) {
            int error = Native.getLastError();
            if (error == ERROR_NOT_FOUND) {
                return;
            }
            throw classify("CredDeleteW", target, error);
        }
    }

    private static SecretAccessException classify(String api, String target, int error) {
        SecretAccessError code = error == ERROR_ACCESS_DENIED
                ? SecretAccessError.ACCESS_DENIED
                : SecretAccessError.UNEXPECTED;
        return new SecretAccessException(code, target,
                api + " failed for target '" + target + "' (win32 error " + error + ")");
    }

    private static byte[] encode(char[] secret) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret));
        byte[] blob = new byte[encoded.remaining()];
        encoded.get(blob);
        return blob;
    }

    private static char[] decode(byte[] blob) {
        CharBuffer decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(blob));
        char[] secret = new char[decoded.remaining()];
        decoded.get(secret);
        return secret;
    }

    /** 手写 Advapi32 接口：只暴露本端口用到的四个函数，避免引入 jna-platform。 */
    interface Advapi32 extends StdCallLibrary {

        Advapi32 INSTANCE = Native.load("Advapi32", Advapi32.class);

        boolean CredWriteW(Credential credential, int flags);

        boolean CredReadW(WString target, int type, int flags, PointerByReference credential);

        boolean CredDeleteW(WString target, int type, int flags);

        void CredFree(Pointer buffer);
    }

    /** CREDENTIALW：仅 generic 需要的字段有值，其余按原始顺序保留以保证内存布局正确。 */
    public static class Credential extends Structure {

        public int Flags;
        public int Type;
        public WString TargetName;
        public WString Comment;
        public Filetime LastWritten;
        public int CredentialBlobSize;
        public Pointer CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public Pointer Attributes;
        public WString TargetAlias;
        public WString UserName;

        Credential() {
            super();
        }

        Credential(Pointer pointer) {
            super(pointer);
            read();
        }

        byte[] readBlob() {
            if (CredentialBlob == null || CredentialBlobSize <= 0) {
                return null;
            }
            return CredentialBlob.getByteArray(0, CredentialBlobSize);
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of("Flags", "Type", "TargetName", "Comment", "LastWritten",
                    "CredentialBlobSize", "CredentialBlob", "Persist", "AttributeCount",
                    "Attributes", "TargetAlias", "UserName");
        }
    }

    /** FILETIME：两个 DWORD。 */
    public static class Filetime extends Structure {

        public int dwLowDateTime;
        public int dwHighDateTime;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("dwLowDateTime", "dwHighDateTime");
        }
    }
}
