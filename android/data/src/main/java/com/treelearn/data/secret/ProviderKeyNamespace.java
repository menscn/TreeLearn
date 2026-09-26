package com.treelearn.data.secret;

/**
 * The multi-provider key namespace (SPEC-2026-09-23 Keystore 命名空间): one provider, one alias,
 * {@code tl.provider.<provider-uuid>}. The alias names both the Keystore AES key and the Preferences
 * key that carries the provider's envelope, so a provider id has exactly one storage slot.
 *
 * <p>Pure derivation only: no Android types, no I/O, no key material. The builtin DeepSeek id is a
 * fixed synthetic UUID pinned by the T-083 schema seed, which is what makes the migrated single key
 * land on a stable alias.
 */
public final class ProviderKeyNamespace {

    public static final String ALIAS_PREFIX = "tl.provider.";

    private ProviderKeyNamespace() {
    }

    /** @throws IllegalArgumentException a blank id is a caller bug, not a storage state */
    public static String keystoreAlias(String providerId) {
        if (providerId == null || providerId.trim().isEmpty()) {
            throw new IllegalArgumentException("a provider id must not be blank");
        }
        return ALIAS_PREFIX + providerId;
    }
}
