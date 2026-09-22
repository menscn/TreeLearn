package com.treelearn.app.ui;

import com.treelearn.core.domain.DomainResult;

/**
 * The one write the canvas performs: folding or unfolding a branch.
 *
 * <p>It returns rather than throws for a business outcome, the same way the conversation's commands
 * do, because a store that refuses the fold is an ordinary answer the screen renders and not a caller
 * bug. Nothing else about the tree is written from here: archiving, deleting and branching belong to
 * the slices that own them.
 */
public interface TreeCanvasCommands {

    /** @return the refusal, or {@code ok} once the flag is stored */
    DomainResult<Void> setCollapsed(String branchId, boolean collapsed, long now);
}