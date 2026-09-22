package com.treelearn.app.screen;

import com.treelearn.app.R;
import com.treelearn.app.ui.HomeViewState;

/**
 * The one place a {@link HomeViewState.Notice} category becomes a sentence.
 *
 * <p>The vocabulary is read by every screen that can be told about the same failure - the home page
 * and the conversation - and two switches would be two answers to what one failure is called, which is
 * how the same refusal ends up described two ways. Every category has its own arm, and the default
 * refuses instead of borrowing another category's words: a notice no screen has text for is a build
 * defect, not something to draw as a storage failure.
 */
final class NoticeText {

    private NoticeText() {
    }

    /** The text for one category; an unmapped category refuses rather than being mislabelled. */
    static int of(HomeViewState.Notice value) {
        switch (value) {
            case KEY_MISSING:
                return R.string.notice_key_missing;
            case KEY_REJECTED:
                return R.string.notice_key_rejected;
            case BALANCE_UNAVAILABLE:
                return R.string.notice_balance_unavailable;
            case REQUEST_REJECTED:
                return R.string.notice_request_rejected;
            case RATE_LIMITED:
                return R.string.notice_rate_limited;
            case PROVIDER_UNAVAILABLE:
                return R.string.notice_provider_unavailable;
            case NETWORK_INTERRUPTED:
                return R.string.notice_network_interrupted;
            case STORAGE:
                return R.string.notice_storage;
            case READING_POSITION:
                return R.string.notice_reading_position;
            default:
                throw new IllegalArgumentException("unmapped notice: " + value);
        }
    }
}
