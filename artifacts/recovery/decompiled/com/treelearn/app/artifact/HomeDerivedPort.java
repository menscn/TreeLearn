/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.artifact;

import com.treelearn.app.artifact.ArtifactOutcome;
import com.treelearn.app.artifact.TreeOutline;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationModel;
import java.util.List;

public interface HomeDerivedPort {
    public ArtifactOutcome rootTitle(Branch var1, String var2, GenerationModel var3);

    public List<String> homeRecommendations(String var1, List<TreeOutline.Entry> var2, GenerationModel var3);
}

