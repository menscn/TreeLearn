/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.GenerationCommand
 */
package com.treelearn.app.wiring;

import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.core.domain.GenerationCommand;

public interface GenerationPort {
    default public void addObserver(GenerationCoordinator.Observer observer) {
    }

    default public void removeObserver(GenerationCoordinator.Observer observer) {
    }

    public GenerationCoordinator.SubmitOutcome submit(GenerationCommand var1, GenerationCoordinator.Assembler var2);

    public GenerationCoordinator.SubmitOutcome submitFirstTopic(GenerationCommand var1, GenerationCoordinator.Assembler var2, GenerationCoordinator.AcceptedWrite var3);
}

