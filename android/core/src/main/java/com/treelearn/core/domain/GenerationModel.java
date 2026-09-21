package com.treelearn.core.domain;

/**
 * User-selectable DeepSeek model tiers (technical design 9.1).
 *
 * <p>The wire ids are the only place a provider model name appears. They are validated against the
 * live provider documentation before release; the domain itself never builds a request body.
 */
public enum GenerationModel {

    /** Faster, lower-cost tier. */
    FLASH("deepseek-flash"),

    /** Stronger reasoning tier. */
    PRO("deepseek-v4-pro");

    public final String wireId;

    GenerationModel(String wireId) {
        this.wireId = wireId;
    }

    public static GenerationModel fromWireId(String wireId) {
        for (GenerationModel model : values()) {
            if (model.wireId.equals(wireId)) {
                return model;
            }
        }
        throw new IllegalArgumentException("unknown model wire id: " + wireId);
    }
}
