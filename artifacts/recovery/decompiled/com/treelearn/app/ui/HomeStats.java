/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

import java.time.LocalDate;
import java.util.List;

public final class HomeStats {
    public static final int WINDOW_DAYS = 14;
    public final boolean empty;
    public final float[] intensities;
    public final int streakDays;

    private HomeStats(boolean empty, float[] intensities, int streakDays) {
        this.empty = empty;
        this.intensities = intensities;
        this.streakDays = streakDays;
    }

    public static HomeStats empty() {
        return new HomeStats(true, new float[0], 0);
    }

    public static HomeStats of(List<LocalDate> activeDays, LocalDate today, int streakDays) {
        float[] cells = new float[14];
        for (int i = 0; i < 14; ++i) {
            cells[i] = activeDays.contains(today.minusDays(13 - i)) ? 1.0f : 0.0f;
        }
        return new HomeStats(false, cells, streakDays);
    }
}

