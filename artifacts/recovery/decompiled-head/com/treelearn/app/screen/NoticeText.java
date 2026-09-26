/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.app.R$string
 */
package com.treelearn.app.screen;

import com.treelearn.app.R;
import com.treelearn.app.ui.HomeViewState;

final class NoticeText {
    private NoticeText() {
    }

    static int of(HomeViewState.Notice value) {
        switch (value) {
            case KEY_MISSING: {
                return R.string.notice_key_missing;
            }
            case KEY_REJECTED: {
                return R.string.notice_key_rejected;
            }
            case BALANCE_UNAVAILABLE: {
                return R.string.notice_balance_unavailable;
            }
            case REQUEST_REJECTED: {
                return R.string.notice_request_rejected;
            }
            case RATE_LIMITED: {
                return R.string.notice_rate_limited;
            }
            case PROVIDER_UNAVAILABLE: {
                return R.string.notice_provider_unavailable;
            }
            case NETWORK_INTERRUPTED: {
                return R.string.notice_network_interrupted;
            }
            case STORAGE: {
                return R.string.notice_storage;
            }
            case READING_POSITION: {
                return R.string.notice_reading_position;
            }
        }
        throw new IllegalArgumentException("unmapped notice: " + (Object)((Object)value));
    }
}

