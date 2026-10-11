package com.treelearn.web.credential;

import java.util.Arrays;
import java.util.Optional;

/**
 * 跨进程重读探针（任务 T-2026-10-10-013），由第二个 JVM 启动，读取指定 target 的存在性与可读性。
 *
 * <p>只打印 target、has、readable 与字符长度，绝不打印秘密正文，也不把秘密写入任何文件；用于证明桌面
 * 凭据落在系统凭据管理器后，同机器上的后续进程能独立重读。仅测试取证使用，不参与生产装配。
 */
public final class CredentialProbe {

    private CredentialProbe() {
    }

    public static void main(String[] args) {
        String target = args[0];
        WindowsCredentialSecretPort port = new WindowsCredentialSecretPort();
        Optional<char[]> read = port.read(target);
        boolean has = port.has(target);
        int length = read.map(chars -> chars.length).orElse(0);
        read.ifPresent(chars -> Arrays.fill(chars, '\0'));
        System.out.println("target=" + target);
        System.out.println("has=" + has);
        System.out.println("readable=" + read.isPresent());
        System.out.println("length=" + length);
    }
}
