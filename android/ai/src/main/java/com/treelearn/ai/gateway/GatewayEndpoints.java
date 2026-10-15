package com.treelearn.ai.gateway;

/**
 * The DeepSeek endpoint facts (technical design 9.1) and the HTTPS-only rule.
 *
 * <p>The base URL is stated here once and reused by the stream and balance paths. The model ids are
 * re-verified against the provider documentation before release; the base URL and the two paths are
 * the only provider strings this package hard-codes.
 *
 * <p>Cleartext is refused mechanically rather than by convention: {@link #requireHttps} runs before
 * any connection is opened, so a fixture or a future caller cannot downgrade the transport.
 */
public final class GatewayEndpoints {

    public static final String BASE_URL = "https://api.deepseek.com";
    public static final String CHAT_PATH = "/chat/completions";
    public static final String BALANCE_PATH = "/user/balance";
    public static final String MODELS_PATH = "/models";

    private GatewayEndpoints() {
    }

    public static String chatUrl() {
        return BASE_URL + CHAT_PATH;
    }

    public static String balanceUrl() {
        return BASE_URL + BALANCE_PATH;
    }

    /**
     * @return {@code url} unchanged when it is an absolute https URL
     * @throws IllegalArgumentException otherwise; the message always names the https requirement so a
     *         refusal cannot be mistaken for a network problem
     */
    public static String requireHttps(String url) {
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException("a provider endpoint must be an absolute https URL "
                    + "(cleartext traffic is never sent), but was: <" + url + ">");
        }
        return url;
    }
}