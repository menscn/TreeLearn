/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.voice;

public interface TtsEngine {
    public void init(Listener var1);

    public void speak(String var1);

    public void stop();

    public void release();

    public static interface Listener {
        public void onEngineReady();

        public void onEngineUnavailable();

        public void onPlaybackDone();

        public void onPlaybackError();
    }
}

