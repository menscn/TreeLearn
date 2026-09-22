package com.treelearn.app;

import java.nio.charset.StandardCharsets;

/**
 * Byte-level search shared by the T-005 instrumentation suites.
 *
 * <p>Two of the T-005 assertions are "this byte sequence is NOT there": no plaintext API key under
 * the app data directory, and no secret-bearing Preferences file in the backup rules. Both are
 * worthless if the search itself is broken, and a broken search fails silently in exactly the
 * direction that looks like success. The suites therefore pair every negative assertion with a
 * positive control that the same helper must find ({@code filesContaining} has to locate the stored
 * envelope; {@code manifestDeclares} has to find a declared attribute and miss an undeclared one).
 */
final class ByteSearch {

    private ByteSearch() {
    }

    /** Plain substring search; the inputs are small enough that a naive scan is the honest choice. */
    static boolean contains(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) {
            return false;
        }
        outer:
        for (int start = 0; start + needle.length <= haystack.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (haystack[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * AAPT2 writes the binary AndroidManifest string pool as UTF-8; the UTF-16LE form is searched
     * too so that a future toolchain change turns this into a visible failure instead of a silent
     * "attribute not found".
     */
    static boolean manifestDeclares(byte[] binaryManifest, String attributeName) {
        if (contains(binaryManifest, attributeName.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        char[] characters = attributeName.toCharArray();
        byte[] utf16 = new byte[characters.length * 2];
        for (int index = 0; index < characters.length; index++) {
            utf16[index * 2] = (byte) (characters[index] & 0xff);
            utf16[index * 2 + 1] = (byte) (characters[index] >> 8);
        }
        return contains(binaryManifest, utf16);
    }
}
