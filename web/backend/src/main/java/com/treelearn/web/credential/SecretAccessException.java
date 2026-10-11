package com.treelearn.web.credential;

/**
 * 桌面凭据端口的失败关闭异常（对照 Android {@code SecretStoreException}）。
 *
 * <p>只携带分类码与 target，绝不携带秘密、其字节长度或系统返回的原始内容；cause 仅供堆栈排障，来自平台调用，
 * 同样不含秘密。{@link #target()} 可安全写入日志用于定位，{@link #getMessage()} 也不含秘密。
 */
public final class SecretAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final SecretAccessError code;
    private final String target;

    public SecretAccessException(SecretAccessError code, String target, String message) {
        super(message);
        this.code = code;
        this.target = target;
    }

    public SecretAccessException(SecretAccessError code, String target, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.target = target;
    }

    public SecretAccessError code() {
        return code;
    }

    public String target() {
        return target;
    }
}
