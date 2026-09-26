package com.treelearn.ai.gateway;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The provider's JSON read boundary (technical design 9.3): the only place a DeepSeek field name
 * appears outside the request writer, using the platform's {@code org.json}.
 *
 * <p>Unknown compatibility fields are ignored at every level — top level, choice and delta — because
 * only the two channel keys are ever looked up. A payload that is not usable is refused loudly
 * instead of being treated as empty: silently ignoring a chunk that lost its {@code choices} array,
 * or one carrying an in-band {@code error} object, would drop answer text or swallow a provider
 * failure.
 *
 * <p>A present-but-null {@code content} is treated as empty rather than as the string "null", which
 * is what a naive {@code optString} would produce.
 */
public final class ProviderJson {

    private static final String CHOICES = "choices";
    private static final String DELTA = "delta";
    private static final String REASONING_CONTENT = "reasoning_content";
    private static final String CONTENT = "content";
    private static final String ERROR = "error";
    private static final String IS_AVAILABLE = "is_available";
    private static final String DATA = "data";
    private static final String ID = "id";

    private ProviderJson() {
    }

    /** Decodes one streamed chunk into its two channels. */
    public static final class Deltas implements GatewayPorts.DeltaDecoder {

        @Override
        public GatewayPorts.Delta decode(String payload) {
            JSONObject root = parse(payload, "stream payload");
            if (root.has(ERROR)) {
                throw refuse(AiGatewayError.inBandProviderError(),
                        "the provider sent an error object inside an accepted stream");
            }
            JSONArray choices = choices(root);
            if (choices.length() == 0) {
                return GatewayPorts.Delta.EMPTY;
            }
            Object first = choices.opt(0);
            JSONObject delta = first instanceof JSONObject
                    ? ((JSONObject) first).optJSONObject(DELTA)
                    : null;
            return new GatewayPorts.Delta(text(delta, REASONING_CONTENT), text(delta, CONTENT));
        }
    }

    /** Reads the balance availability flag. */
    public static final class Balance implements GatewayPorts.BalanceDecoder {

        @Override
        public boolean isAvailable(String payload) {
            Object available = parse(payload, "balance response").opt(IS_AVAILABLE);
            if (!(available instanceof Boolean)) {
                throw refuse(AiGatewayError.parseFailed("the balance response must carry a boolean "
                        + IS_AVAILABLE), "the balance response has no boolean " + IS_AVAILABLE);
            }
            return (Boolean) available;
        }
    }

    /** Reads the model wire ids out of a {@code /models} response (T-086, the F7 mechanism). */
    public static final class ModelList implements GatewayPorts.ModelListDecoder {

        @Override
        public List<String> decode(String payload) {
            JSONObject root = parse(payload, "model list response");
            JSONArray data = dataArray(root);
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                Object entry = data.opt(i);
                Object id = entry instanceof JSONObject ? ((JSONObject) entry).opt(ID) : null;
                if (!(id instanceof String) || ((String) id).trim().isEmpty()) {
                    throw refuse(AiGatewayError.parseFailed("the model list response carries an "
                            + "entry without a string " + ID), "a model list entry has no string " + ID);
                }
                ids.add((String) id);
            }
            return ids;
        }
    }

    private static JSONArray dataArray(JSONObject root) {
        Object data = root.opt(DATA);
        if (!(data instanceof JSONArray)) {
            throw refuse(AiGatewayError.parseFailed("a model list response carries no " + DATA
                    + " array, so its contents cannot be trusted"), "the payload has no " + DATA);
        }
        return (JSONArray) data;
    }

    private static JSONObject parse(String payload, String what) {
        try {
            return new JSONObject(payload == null ? "" : payload);
        } catch (JSONException malformed) {
            // The message is fixed text: a parser message can quote the payload, which may contain
            // the question or the answer.
            String detail = "a " + what + " was not one JSON object";
            throw refuse(AiGatewayError.parseFailed(detail), detail);
        }
    }

    private static JSONArray choices(JSONObject root) {
        Object choices = root.opt(CHOICES);
        if (!(choices instanceof JSONArray)) {
            throw refuse(AiGatewayError.parseFailed("a stream payload carries no " + CHOICES
                    + " array, so its content cannot be trusted"), "the payload has no " + CHOICES);
        }
        return (JSONArray) choices;
    }

    private static String text(JSONObject delta, String name) {
        Object value = delta == null ? null : delta.opt(name);
        return value instanceof String ? (String) value : "";
    }

    private static GatewayPorts.PayloadException refuse(AiGatewayError error, String message) {
        return new GatewayPorts.PayloadException(error, message);
    }
}