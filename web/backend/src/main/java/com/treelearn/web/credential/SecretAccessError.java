package com.treelearn.web.credential;

/**
 * 桌面凭据端口的失败分类（对照 Android {@code SecretStoreError}）。
 *
 * <p>每个取值都是失败关闭：任何一个都不得与一段已解密或半解密的秘密同时返回，调用方据此决定重试、重新录入或上报。
 */
public enum SecretAccessError {

    /** 调用方给的 target 不在允许的命名空间/字符/长度内，未发起任何平台调用。 */
    INVALID_TARGET,

    /** Windows 拒绝访问该凭据（ERROR_ACCESS_DENIED）。 */
    ACCESS_DENIED,

    /** 凭据存在但内容不可用（blob 缺失或为空），不得据此返回明文。 */
    MALFORMED,

    /** 其它未归类的平台失败（含未知 Win32 错误码）。 */
    UNEXPECTED
}
