/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.data.db.dao.AiArtifactDao$Row
 */
package com.treelearn.app.artifact;

import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.data.db.dao.AiArtifactDao;
import java.util.function.Supplier;

public interface DerivedStore {
    public <T> T inTransaction(Supplier<T> var1);

    public String liveBaseline(ArtifactKind var1, String var2);

    public void write(AiArtifactDao.Row var1);
}

