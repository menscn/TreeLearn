package com.treelearn.web.credential;

import java.util.Optional;

/**
 * 桌面凭据端口（对照 Android {@code com.treelearn.data.secret.KeystoreSecretStore}）。
 *
 * <p>只暴露「保存 / 存在性 / 显式读取 / 删除」四件事，且只对 {@code Treelearn-Web/provider/<id>} 形态的 target
 * 生效；桌面 target 命名与 Android 的 {@code tl.provider.<uuid>} 别名语义对应，前缀不同但一个 provider 一个存储槽。
 *
 * <p>存在性查询返回布尔、读取返回 {@link Optional}：缺失是正常状态。绝不回退明文，绝不把秘密放进状态对象或日志。
 * 失败一律抛 {@link SecretAccessException}（未检查异常），由 UI 边界统一处理，不做静默降级。
 */
public interface SecretPort {

    /** 桌面 provider 凭据 target 前缀。 */
    String TARGET_PREFIX = "Treelearn-Web/provider/";

    /** Windows CRED_MAX_GENERIC_TARGET_NAME_LENGTH。 */
    int MAX_TARGET_LENGTH = 32767;

    /** Windows CRED_MAX_CREDENTIAL_BLOB_SIZE = 5 * 512。 */
    int MAX_BLOB_BYTES = 5 * 512;

    void save(String target, char[] secret);

    boolean has(String target);

    Optional<char[]> read(String target);

    void delete(String target);

    /** 由 provider id 派生合法 target；id 为空或含非法字符即抛。 */
    static String providerTarget(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new SecretAccessException(SecretAccessError.INVALID_TARGET, providerId,
                    "a provider id must not be blank");
        }
        String target = TARGET_PREFIX + providerId;
        requireValidTarget(target);
        return target;
    }

    /** 校验 target 只允许固定前缀 + 合法 id 字符，且不超 Credential Manager 长度上限。 */
    static void requireValidTarget(String target) {
        boolean valid = target != null
                && target.length() <= MAX_TARGET_LENGTH
                && target.startsWith(TARGET_PREFIX)
                && target.substring(TARGET_PREFIX.length()).matches("[A-Za-z0-9._-]+");
        if (!valid) {
            throw new SecretAccessException(SecretAccessError.INVALID_TARGET, target,
                    "a credential target must be '" + TARGET_PREFIX + "<id>' with id matching "
                            + "[A-Za-z0-9._-]+ and total length <= " + MAX_TARGET_LENGTH);
        }
    }
}
