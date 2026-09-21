package com.treelearn.core.domain;

/** Who authored a message at a fixed logical position inside a branch. */
public enum MessageRole {

    /** Authored by the learner. The content is immutable once created. */
    USER,

    /** Produced by the model. Only the currently displayed revision and attempt are referenced. */
    AI
}
