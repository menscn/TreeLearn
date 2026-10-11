package com.treelearn.web.credential;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-013：桌面凭据端口跑在真实 Windows 凭据管理器上。
 *
 * <p>语义对照 Android {@code com.treelearn.data.secret.KeystoreSecretStore}：round-trip、覆盖写、
 * 删除、缺失→空、has 只返回布尔、失败抛类型化错误。每个用例用唯一 target（随机 UUID 后缀）并在
 * {@code @AfterEach} 里删除并复核无残留，避免污染真实凭据。跨进程用例由第二个 JVM 读取同一 target，
 * 命令与输出写入 {@code build/evidence/T-2026-10-10-013-cross-process.txt}（构建目录，避免每次跑测试
 * 都把工作树里的证据文件改脏）；提交到 {@code artifacts/evidence/} 的那份是它的副本。
 */
class WindowsCredentialSecretPortTest {

    private final WindowsCredentialSecretPort port = new WindowsCredentialSecretPort();
    private final List<String> created = new ArrayList<>();

    @AfterEach
    void cleanupEveryCredentialThisTestCreated() {
        for (String target : created) {
            port.delete(target);
        }
        for (String target : created) {
            assertFalse(port.has(target), "残留凭据未被清理：" + target);
        }
    }

    private String freshTarget() {
        String target = SecretPort.providerTarget(UUID.randomUUID().toString());
        created.add(target);
        return target;
    }

    @Test
    void saveThenHasThenReadRoundTripsNonAsciiText() {
        String target = freshTarget();
        char[] secret = "密钥-🔑-deepseek-api-key".toCharArray();

        assertFalse(port.has(target), "写入前不应存在");
        port.save(target, secret);
        assertTrue(port.has(target), "写入后 has 必须为真");
        Optional<char[]> read = port.read(target);
        assertTrue(read.isPresent());
        assertArrayEquals(secret, read.orElseThrow(), "往返必须逐字符一致（含中文与 emoji）");
    }

    @Test
    void overwritingReplacesTheStoredSecret() {
        String target = freshTarget();
        port.save(target, "first-secret".toCharArray());
        port.save(target, "second-secret".toCharArray());

        assertArrayEquals("second-secret".toCharArray(), port.read(target).orElseThrow());
    }

    @Test
    void deleteRemovesTheCredentialSoHasIsFalseAndReadIsEmpty() {
        String target = freshTarget();
        port.save(target, "to-be-deleted".toCharArray());

        port.delete(target);

        assertFalse(port.has(target));
        assertTrue(port.read(target).isEmpty());
        port.delete(target);
    }

    @Test
    void readingAndCheckingAMissingTargetIsEmptyRatherThanFailing() {
        String target = freshTarget();
        assertFalse(port.has(target));
        assertTrue(port.read(target).isEmpty());
    }

    @Test
    void illegalTargetsAreRejectedBeforeAnyPlatformCall() {
        for (String illegal : List.of(
                "not-a-treelearn-target",
                SecretPort.TARGET_PREFIX,
                SecretPort.TARGET_PREFIX + "has space",
                SecretPort.TARGET_PREFIX + "slash/inside",
                SecretPort.TARGET_PREFIX + "x".repeat(SecretPort.MAX_TARGET_LENGTH))) {
            SecretAccessException refused = assertThrows(SecretAccessException.class,
                    () -> port.save(illegal, "secret".toCharArray()));
            assertEquals(SecretAccessError.INVALID_TARGET, refused.code());
            assertThrows(SecretAccessException.class, () -> port.has(illegal));
            assertThrows(SecretAccessException.class, () -> port.read(illegal));
            assertThrows(SecretAccessException.class, () -> port.delete(illegal));
        }
        assertThrows(SecretAccessException.class, () -> port.save(null, "secret".toCharArray()));
    }

    @Test
    void hasNeverReturnsTheSecretAndFailuresDoNotLeakItThroughToString() {
        String target = freshTarget();
        port.save(target, "kept-secret".toCharArray());
        assertTrue(port.has(target), "has 只返回布尔，不返回任何秘密内容");

        String marker = "MARKER-LEAK-CANARY";
        char[] oversized = (marker + "x".repeat(3000)).toCharArray();
        SecretAccessException refused = assertThrows(SecretAccessException.class,
                () -> port.save(target, oversized));
        assertEquals(SecretAccessError.MALFORMED, refused.code());
        assertFalse(refused.getMessage().contains(marker), "异常 message 不得携带秘密");
        assertFalse(refused.toString().contains(marker), "异常 toString 不得携带秘密");
    }

    @Test
    void aSecondProcessReadsTheSameCredentialWithoutPrintingTheSecret() throws Exception {
        String target = freshTarget();
        String secret = "跨进程-secret-🔑";
        port.save(target, secret.toCharArray());

        String classpath = System.getProperty("java.class.path");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", classpath,
                CredentialProbe.class.getName(), target);
        builder.redirectErrorStream(true);
        Process child = builder.start();
        String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = child.waitFor();

        writeCrossProcessEvidence(java, classpath, target, output, exit);

        assertEquals(0, exit, "子进程应正常退出，输出：\n" + output);
        assertTrue(output.contains("has=true"), "第二个 JVM 必须能读到同一 target，输出：\n" + output);
        assertTrue(output.contains("readable=true"), "第二个 JVM 必须能读出内容，输出：\n" + output);
        assertFalse(output.contains(secret), "探针不得打印秘密，输出：\n" + output);
    }

    private static void writeCrossProcessEvidence(String java, String classpath, String target,
            String output, int exit) throws IOException {
        Path evidence = Path.of("build", "evidence",
                "T-2026-10-10-013-cross-process.txt").normalize().toAbsolutePath();
        Files.createDirectories(evidence.getParent());
        StringBuilder text = new StringBuilder();
        text.append("T-2026-10-10-013 跨进程重读证据（由测试生成）\n");
        text.append("command: ").append(java).append(" -cp ").append(classpath).append(' ')
                .append(CredentialProbe.class.getName()).append(' ').append(target).append('\n');
        text.append("note: 探针只打印 target/has/readable/length，不打印秘密。\n");
        text.append("EXIT_CODE=").append(exit).append('\n');
        text.append(output);
        if (!output.endsWith("\n")) {
            text.append('\n');
        }
        Files.writeString(evidence, text.toString(), StandardCharsets.UTF_8);
    }
}
