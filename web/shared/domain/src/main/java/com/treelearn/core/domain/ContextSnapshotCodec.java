package com.treelearn.core.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The versioned canonical payload of one {@link ContextSnapshot} (technical design 5.3).
 *
 * <p>The payload is an ordered <em>reference</em> list: each entry names the kind of row it points at
 * and that row's id, nothing else. It therefore never grows with an answer and can never resurrect
 * text the learner has since reset — which is the whole point of freezing a birth context instead of
 * copying one.
 *
 * <p><strong>The schema version has exactly one home.</strong> It is the {@code schema_version}
 * column, which {@link #decode} receives as an argument; the payload does not repeat it. Two copies of
 * one fact drift apart, and a payload disagreeing with its own column would leave no way to decide
 * which of the two the reader should have believed.
 *
 * <p><strong>The digest covers the canonical bytes.</strong> {@link #digestOf} hashes the UTF-8 bytes
 * of the text {@link #encode} produces, and {@link #encode} writes a {@link LinkedHashMap} in
 * insertion order, so the digest never depends on a hash iteration order. Re-encoding the same source
 * list therefore always yields the same digest, and any edit to the stored text is detectable.
 */
public final class ContextSnapshotCodec {

    private static final String SOURCES = "sources";
    private static final String KIND = "kind";
    private static final String REF = "ref";

    private ContextSnapshotCodec() {
    }

    /** @return the canonical JSON text of {@code sources}, in the order given */
    public static String encode(List<ContextSnapshot.Source> sources) {
        List<Object> encoded = new ArrayList<Object>(sources.size());
        for (ContextSnapshot.Source source : sources) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put(KIND, source.kind.name());
            entry.put(REF, source.refId);
            encoded.add(entry);
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put(SOURCES, encoded);
        StringBuilder out = new StringBuilder();
        JsonText.write(root, out);
        return out.toString();
    }

    /** @return the lowercase hexadecimal SHA-256 of the payload's UTF-8 bytes */
    public static String digestOf(String payload) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every JRE", impossible);
        }
        byte[] hash = sha256.digest(payload.getBytes(StandardCharsets.UTF_8));
        char[] hex = new char[hash.length * 2];
        for (int index = 0; index < hash.length; index++) {
            hex[index * 2] = Character.forDigit((hash[index] >> 4) & 0xF, 16);
            hex[index * 2 + 1] = Character.forDigit(hash[index] & 0xF, 16);
        }
        return new String(hex);
    }

    /**
     * Reads a stored payload back into its ordered reference list.
     *
     * <p>The three refusals are decided in one fixed order — schema version, structure, source shape —
     * so a payload that is wrong in several ways always reports the same first defect.
     *
     * @return the sources, or a failure with {@link DomainErrorCode#SNAPSHOT_SCHEMA_UNSUPPORTED} when
     *         this build cannot read that version or {@link DomainErrorCode#SNAPSHOT_PAYLOAD_INVALID}
     *         when the text is not exactly this schema's canonical shape
     */
    static DomainResult<List<ContextSnapshot.Source>> decode(int schemaVersion, String payload) {
        if (schemaVersion != ContextSnapshot.SCHEMA_VERSION) {
            return DomainResult.fail(DomainErrorCode.SNAPSHOT_SCHEMA_UNSUPPORTED,
                    "this build reads snapshot schema " + ContextSnapshot.SCHEMA_VERSION
                            + " only, but the stored row declares " + schemaVersion
                            + "; reading it with the wrong rules would silently reinterpret it");
        }
        try {
            return DomainResult.ok(interpret(JsonText.read(payload)));
        } catch (JsonText.Syntax | IllegalArgumentException refused) {
            return DomainResult.fail(DomainErrorCode.SNAPSHOT_PAYLOAD_INVALID,
                    "the stored snapshot payload is not this schema's canonical JSON: "
                            + refused.getMessage());
        }
    }

    private static List<ContextSnapshot.Source> interpret(Object root) {
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("the payload must be one JSON object");
        }
        Map<?, ?> fields = (Map<?, ?>) root;
        if (fields.size() != 1 || !fields.containsKey(SOURCES)) {
            throw new IllegalArgumentException("the payload must carry exactly one 'sources' field");
        }
        Object listed = fields.get(SOURCES);
        if (!(listed instanceof List)) {
            throw new IllegalArgumentException("'sources' must be a JSON array");
        }
        List<?> items = (List<?>) listed;
        if (items.isEmpty()) {
            throw new IllegalArgumentException("a snapshot names at least one source row");
        }
        List<ContextSnapshot.Source> sources = new ArrayList<ContextSnapshot.Source>(items.size());
        for (Object item : items) {
            sources.add(source(item));
        }
        return sources;
    }

    private static ContextSnapshot.Source source(Object item) {
        if (!(item instanceof Map)) {
            throw new IllegalArgumentException("every source must be one JSON object");
        }
        Map<?, ?> entry = (Map<?, ?>) item;
        if (entry.size() != 2 || !entry.containsKey(KIND) || !entry.containsKey(REF)) {
            throw new IllegalArgumentException("every source carries exactly 'kind' and 'ref'");
        }
        Object kind = entry.get(KIND);
        Object ref = entry.get(REF);
        if (!(kind instanceof String) || !(ref instanceof String)) {
            throw new IllegalArgumentException("'kind' and 'ref' must be JSON strings");
        }
        for (ContextSnapshot.SourceKind candidate : ContextSnapshot.SourceKind.values()) {
            if (candidate.name().equals(kind)) {
                return ContextSnapshot.Source.of(candidate, (String) ref);
            }
        }
        throw new IllegalArgumentException("no source kind named <" + kind + "> exists in this build");
    }
}