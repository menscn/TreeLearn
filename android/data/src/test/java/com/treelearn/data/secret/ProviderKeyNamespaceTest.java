package com.treelearn.data.secret;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The alias rule is the whole namespace contract (SPEC-2026-09-23 Keystore 命名空间): one provider,
 * one alias, {@code tl.provider.<provider-uuid>}. The builtin DeepSeek id is pinned by
 * T-083's schema seed, so the migrated single key must land on exactly that alias.
 */
public final class ProviderKeyNamespaceTest {

    @Test
    public void aProviderIdBecomesThePrefixedAlias() {
        assertEquals("tl.provider.00000000-0000-0000-0000-000000000001",
                ProviderKeyNamespace.keystoreAlias("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    public void distinctProvidersNeverShareAnAlias() {
        String first = ProviderKeyNamespace.keystoreAlias("uuid-a");
        String second = ProviderKeyNamespace.keystoreAlias("uuid-b");

        assertTrue(first.startsWith("tl.provider."));
        assertTrue(second.startsWith("tl.provider."));
        assertTrue(!first.equals(second));
    }

    @Test
    public void aBlankProviderIdIsACallerBug() {
        assertThrows(IllegalArgumentException.class, () -> ProviderKeyNamespace.keystoreAlias(null));
        assertThrows(IllegalArgumentException.class, () -> ProviderKeyNamespace.keystoreAlias(""));
        assertThrows(IllegalArgumentException.class, () -> ProviderKeyNamespace.keystoreAlias("   "));
    }
}
