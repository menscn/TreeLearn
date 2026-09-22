package com.treelearn.app.screen;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.treelearn.app.R;
import com.treelearn.app.ui.ChatActions;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChatViewState;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.TextSelection;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.ReadingPosition;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The conversation screen as a view (technical design 13, prototype v2.5 {@code ChatScreen}).
 *
 * <p>It owns no decisions: every gesture is forwarded to {@link ChatActions} and every pixel is painted
 * from a {@link ChatViewState}. Three things are worth knowing about it:
 *
 * <ul>
 *   <li><strong>Rows are rebuilt from the state, never recycled.</strong> Each read builds the rows it
 *       needs and drops the rest, so an open reasoning block lives only in the state's set of message
 *       ids. There is nothing to leak between answers because nothing is reused - at the cost of
 *       rebuilding during a stream, which is the recorded performance risk of this slice.
 *   <li><strong>The selection is read, not remembered.</strong> "Quote" takes whatever the answer's own
 *       text view currently has selected and hands it to the presenter; with nothing selected the
 *       screen says so instead of quoting an empty sentence.
 *   <li><strong>The two recommendation targets stay apart.</strong> The row asks a follow-up, its own
 *       44dp zone opens a branch; neither is reachable from the other's gesture.
 *   <li><strong>A long press opens the app's own menu.</strong> The gesture selects the sentence under
 *       the press and hands it to {@link WordMenuPopup}, which is where the four items and their
 *       placement live. The listener answers {@code true} on purpose: the platform only starts its own
 *       text toolbar while nothing has handled the long press.
 *   <li><strong>The reading position is measured here and decided elsewhere.</strong> The screen
 *       reports how tall a row is, which rows the viewport shows and where the learner stopped - all
 *       facts only a laid-out view has. Where that resolves to, and whether the unread badge may
 *       clear, are the reading session's answers, and nothing here reads or writes a database.
 * </ul>
 */
public final class ChatScreen extends LinearLayout {

    /** Scroll events arrive in bursts; the view is read once they stop. */
    private static final long SCROLL_SETTLE_MS = 150L;

    private final TextView title;
    private final ScrollView scroll;
    private final LinearLayout messageFlow;
    private final View loading;
    private final View error;
    /** The reading-state notice: a write the store refused, empty the rest of the time. */
    private final TextView notice;
    private final View quoteRow;
    private final TextView quoteText;
    private final EditText draft;
    private final ModelThinkingPicker modelChip;
    private final Button send;

    /** The rows of the last render, by message id, so a selection can be read back. */
    private final Map<String, Row> rows = new LinkedHashMap<String, Row>();

    /** The two cards that hang over an answer: the word menu, and the child-branch list. */
    private WordMenuPopup wordMenu;
    private ChildBranchPopup childrenPopup;

    private ChatActions actions;
    private boolean rendering;
    /** Rows are reported to the presenter once per visit, after the first layout that has one. */
    private boolean rowsReported;
    /** The position the presenter resolved; applied once, after which the view belongs to the learner. */
    private ReadingPosition pendingPosition;
    /** The last message id of the last ready render, so only a new turn scrolls to the bottom. */
    private String renderedTail = "";
    /** Where the learner stopped, as the last settle measured it; null until the view settles once. */
    private ReadingAnchor settledAnchor;
    /** What the last settle reported as visible, so a scroll that shows nothing new stays silent. */
    private Set<String> settledVisible = Collections.emptySet();
    private final Runnable scrollSettled = this::reportScroll;

    public ChatScreen(Context context) {
        this(context, null);
    }

    public ChatScreen(Context context, AttributeSet attributes) {
        super(context, attributes);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_chat, this, true);
        title = findViewById(R.id.chat_title);
        scroll = findViewById(R.id.chat_scroll);
        messageFlow = findViewById(R.id.chat_messages);
        loading = findViewById(R.id.chat_loading);
        error = findViewById(R.id.chat_error);
        notice = findViewById(R.id.chat_notice);
        quoteRow = findViewById(R.id.chat_quote_row);
        quoteText = findViewById(R.id.chat_quote_text);
        draft = findViewById(R.id.chat_draft);
        modelChip = findViewById(R.id.chat_model_chip);
        send = findViewById(R.id.chat_send);

        draft.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                fire(actions -> actions.onDraftChanged(text.toString()));
            }
        });
        draft.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                fire(actions -> actions.onSend());
                return true;
            }
            return false;
        });
        send.setOnClickListener(view -> fire(actions -> actions.onSend()));
        findViewById(R.id.chat_back).setOnClickListener(view -> fire(actions -> actions.onBackPressed()));
        findViewById(R.id.chat_overview)
                .setOnClickListener(view -> fire(actions -> actions.onOverviewPressed()));
        findViewById(R.id.chat_nav)
                .setOnClickListener(view -> fire(actions -> actions.onNavigationPressed()));
        findViewById(R.id.chat_settings)
                .setOnClickListener(view -> fire(actions -> actions.onSettingsPressed()));
        findViewById(R.id.chat_quote_clear)
                .setOnClickListener(view -> fire(actions -> actions.onQuoteCleared()));

        // Both of these are measurements, not gestures: the presenter needs them before it can say
        // where the learner was, and neither can be taken before the rows have been laid out.
        messageFlow.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop,
                oldRight, oldBottom) -> reportRowsLaidOut());
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            scroll.removeCallbacks(scrollSettled);
            scroll.postDelayed(scrollSettled, SCROLL_SETTLE_MS);
        });
    }

    public void setActions(ChatActions actions) {
        this.actions = actions;
        modelChip.setListener(new ModelThinkingPicker.Listener() {
            @Override
            public void onModelSelected(com.treelearn.core.domain.GenerationModel model) {
                actions.onModelSelected(model);
            }

            @Override
            public void onThinkingToggled(boolean enabled) {
                actions.onThinkingToggled(enabled);
            }
        });
    }

    /** Offers the versions of one position; the pick is reported through {@link ChatActions}. */
    public void showVersions(final String messageId, List<AnswerRevision> versions) {
        if (versions.isEmpty()) {
            return;
        }
        PopupMenu menu = new PopupMenu(getContext(), this);
        final Map<Integer, String> byItem = new HashMap<Integer, String>();
        for (AnswerRevision revision : versions) {
            byItem.put(revision.revisionNo, revision.id);
            menu.getMenu().add(0, revision.revisionNo, revision.revisionNo,
                    getContext().getString(R.string.chat_version_item, revision.revisionNo));
        }
        menu.setOnMenuItemClickListener(item -> {
            final String revisionId = byItem.get(item.getItemId());
            if (revisionId == null) {
                return false;
            }
            fire(actions -> actions.onRevisionSelected(messageId, revisionId));
            return true;
        });
        menu.show();
    }

    public void render(ChatViewState state) {
        rendering = true;
        try {
            dismissPopups();
            title.setText(state.branchTitle);
            loading.setVisibility(state.phase == ChatViewState.Phase.LOADING ? VISIBLE : GONE);
            error.setVisibility(state.phase == ChatViewState.Phase.ERROR ? VISIBLE : GONE);
            renderMessages(state);
            renderNotice(state.notice);
            renderQuote(state.quote);
            if (!state.draft.contentEquals(draft.getText())) {
                draft.setText(state.draft);
                draft.setSelection(draft.getText().length());
            }
            modelChip.render(state.model, state.thinkingEnabled);
            send.setEnabled(state.canSubmit());
            if (state.phase == ChatViewState.Phase.READY) {
                settle(state);
            }
        } finally {
            rendering = false;
        }
    }

    /**
     * Where the view goes when a read lands.
     *
     * <p>A resolved position is applied instead of the bottom, and it is applied once: after that the
     * view belongs to the learner, so a rebuild - a draft keystroke, a stream, an unread clear - must
     * not drag it back. The bottom is scrolled to only when the conversation's tail changed, which
     * shows a new turn without turning a repaint of the same turns into a jump.
     */
    private void settle(ChatViewState state) {
        String tail = state.messages.isEmpty()
                ? "" : state.messages.get(state.messages.size() - 1).messageId;
        boolean tailChanged = !tail.equals(renderedTail);
        renderedTail = tail;
        if (pendingPosition != null) {
            applyPosition();
        } else if (tailChanged) {
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    /**
     * Puts the conversation back on the position the presenter resolved.
     *
     * <p>It is held until the rows exist: a position can arrive while the branch is still loading, and
     * then there is nothing to scroll to. A row that is not in the list any more - the read changed
     * under the position - falls back to the bottom, which is where the presenter's own live end is.
     */
    public void renderPosition(ReadingPosition position) {
        pendingPosition = position;
        if (!rows.isEmpty()) {
            applyPosition();
        }
    }

    private void applyPosition() {
        ReadingPosition position = pendingPosition;
        pendingPosition = null;
        Row row = position == null || position.messageId == null
                ? null : rows.get(position.messageId);
        if (row == null) {
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            return;
        }
        // The stored form is "pixels into this row, and pixels of viewport above it"; scrolling is
        // that pair solved for a scroll offset, which is the whole reason neither is a page offset.
        int target = Math.max(0,
                row.root.getTop() + position.listItemOffset - position.viewportOffset);
        scroll.post(() -> scroll.scrollTo(0, target));
    }

    /**
     * Tells the presenter how tall a row is and what is on screen, once.
     *
     * <p>The stored position is semantic - pixels inside the list item it names - so resolving it
     * needs a real measurement, and a real measurement only exists after a layout pass. Once per
     * visit is enough: the rows of a rebuild are the same height, and a second report would move the
     * view a second time.
     *
     * <p>The visible set goes with the height because this frame may be the visit's only viewport
     * event: a conversation that fits on one screen never scrolls, so nothing ever settles, and the
     * newest answer would stay marked unread while the learner is looking straight at it. The set is
     * measured exactly as a settle measures it, and the presenter judges it exactly as it judges a
     * settle.
     */
    private void reportRowsLaidOut() {
        if (rowsReported || rows.isEmpty()) {
            return;
        }
        int itemHeightPx = rows.values().iterator().next().root.getHeight();
        if (itemHeightPx <= 0) {
            return;
        }
        rowsReported = true;
        Set<String> visible = visibleRows();
        settledVisible = visible;
        fire(actions -> actions.onRowsLaidOut(itemHeightPx, visible));
    }

    /** The row ids the viewport reaches right now, measured from the rows' laid-out bounds. */
    private Set<String> visibleRows() {
        int scrollY = scroll.getScrollY();
        int bottomOfViewport = scrollY + scroll.getHeight();
        Set<String> visible = new LinkedHashSet<String>();
        for (Map.Entry<String, Row> entry : rows.entrySet()) {
            View root = entry.getValue().root;
            if (root.getBottom() > scrollY && root.getTop() < bottomOfViewport) {
                visible.add(entry.getKey());
            }
        }
        return visible;
    }

    /** Draws the failure the presenter reported, or hides the line when there is none. */
    private void renderNotice(HomeViewState.Notice value) {
        if (value == null) {
            notice.setVisibility(GONE);
            notice.setText("");
            return;
        }
        notice.setText(getContext().getString(NoticeText.of(value)));
        notice.setVisibility(VISIBLE);
    }

    /**
     * Reads the view once the scrolling stopped: which rows are on screen, and where the learner is.
     *
     * <p>The visible set is reported only when it changed, so scrolling inside one answer is not an
     * event. The anchor is the top row the viewport reaches, carrying the two offsets the stored form
     * keeps - pixels into that row, and pixels of viewport above it - which is also what "leaving"
     * hands over, so what is saved is what the last settle measured rather than a second opinion.
     */
    private void reportScroll() {
        int scrollY = scroll.getScrollY();
        ReadingAnchor anchor = null;
        for (Map.Entry<String, Row> entry : rows.entrySet()) {
            View root = entry.getValue().root;
            if (anchor == null && root.getBottom() > scrollY) {
                int into = scrollY - root.getTop();
                anchor = ReadingAnchor.of(entry.getKey(), null, Math.max(0, into),
                        Math.max(0, -into));
            }
        }
        settledAnchor = anchor;
        Set<String> visible = visibleRows();
        if (visible.equals(settledVisible)) {
            return;
        }
        settledVisible = visible;
        fire(actions -> actions.onViewportChanged(visible));
    }

    private void renderMessages(ChatViewState state) {
        rows.clear();
        messageFlow.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (ChatMessage message : state.messages) {
            Row row = new Row(inflater);
            row.render(state, message);
            rows.put(message.messageId, row);
            messageFlow.addView(row.root);
        }
    }

    private void renderQuote(String quote) {
        boolean quoted = !quote.isEmpty();
        quoteRow.setVisibility(quoted ? VISIBLE : GONE);
        quoteText.setText(quoted ? getContext().getString(R.string.chat_quoted, quote) : "");
    }

    private void fire(Consumer<ChatActions> call) {
        if (actions != null && !rendering) {
            call.accept(actions);
        }
    }

    /**
     * Opens the child-branch list of one answer under the row that asked for it. The branches were read
     * before this is called, so nothing is looked up here; a tapped row is reported to the host, which
     * is the one place that knows how a branch is opened.
     */
    public void showChildren(String messageId, List<ChildBranch> children,
            ChildBranchPopup.Listener listener) {
        dismissPopups();
        Row row = rows.get(messageId);
        if (row == null || children.isEmpty()) {
            return;
        }
        childrenPopup = ChildBranchPopup.show(row.childrenAnchor(), children, listener);
    }

    /**
     * Both cards point at a row of one render, so a rebuild - a stream, a fold, a new question - takes
     * them with it: a menu left open would be acting on an answer that is no longer on screen.
     */
    private void dismissPopups() {
        if (wordMenu != null) {
            wordMenu.close();
            wordMenu = null;
        }
        if (childrenPopup != null) {
            childrenPopup.close();
            childrenPopup = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissPopups();
        scroll.removeCallbacks(scrollSettled);
        ReadingAnchor anchor = settledAnchor;
        if (anchor != null) {
            fire(actions -> actions.onLeaving(anchor));
        }
        super.onDetachedFromWindow();
    }

    /** One row of the flow, and the forwarding of its gestures. */
    private final class Row implements ReasoningDisclosure.Listener, AnswerActionBar.Listener,
            WordMenuPopup.Listener {

        private final View root;
        private final View questionBlock;
        private final TextView question;
        private final TextView quoteChip;
        private final View answerBlock;
        private final TextView time;
        private final TextView answer;
        private final TextView generating;
        private final View failure;
        private final Button retry;
        private final ReasoningDisclosure disclosure;
        private final AnswerActionBar actionBar;
        private final LinearLayout suggestions;

        private String messageId = "";
        private ChatMessage message;
        private float pressX;
        private float pressY;

        Row(LayoutInflater inflater) {
            root = inflater.inflate(R.layout.item_chat_message, messageFlow, false);
            questionBlock = root.findViewById(R.id.chat_question_block);
            question = root.findViewById(R.id.chat_question);
            quoteChip = root.findViewById(R.id.chat_quote_chip);
            answerBlock = root.findViewById(R.id.chat_answer_block);
            time = root.findViewById(R.id.chat_time);
            answer = root.findViewById(R.id.chat_answer);
            answer.setMovementMethod(LinkMovementMethod.getInstance());
            generating = root.findViewById(R.id.chat_generating_note);
            failure = root.findViewById(R.id.chat_failure);
            retry = root.findViewById(R.id.chat_retry);
            disclosure = root.findViewById(R.id.chat_reasoning);
            actionBar = root.findViewById(R.id.chat_actions);
            suggestions = root.findViewById(R.id.chat_suggestions);
            disclosure.setListener(this);
            actionBar.setListener(this);
            retry.setOnClickListener(view -> fire(actions -> actions.onRetryPressed(messageId)));
            answer.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    pressX = event.getX();
                    pressY = event.getY();
                }
                return false;
            });
            answer.setOnLongClickListener(view -> {
                openWordMenu();
                return true;
            });
        }

        void render(ChatViewState state, ChatMessage message) {
            messageId = message.messageId;
            this.message = message;
            questionBlock.setVisibility(message.isAi() ? GONE : VISIBLE);
            answerBlock.setVisibility(message.isAi() ? VISIBLE : GONE);
            if (!message.isAi()) {
                renderQuestion(message);
                return;
            }
            time.setText(DateUtils.getRelativeTimeSpanString(message.createdAt,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            disclosure.render(message, state.isReasoningExpanded(message.messageId),
                    state.thinkingEnabled);
            answer.setText(MarkdownRenderer.render(message.text), TextView.BufferType.SPANNABLE);
            generating.setText(generatingNote(message, state.thinkingEnabled));
            generating.setVisibility(message.isGenerating() ? VISIBLE : GONE);
            failure.setVisibility(message.isFailed() ? VISIBLE : GONE);
            retry.setEnabled(message.canRetry());
            actionBar.setVisibility(message.isStable() ? VISIBLE : GONE);
            actionBar.render(message);
            renderSuggestions(state, message);
        }

        private void renderQuestion(ChatMessage message) {
            question.setText(message.text);
            boolean quoted = !message.quote.isEmpty();
            quoteChip.setVisibility(quoted ? VISIBLE : GONE);
            quoteChip.setText(quoted ? getContext().getString(R.string.chat_quoted, message.quote) : "");
        }

        private CharSequence generatingNote(ChatMessage message, boolean thinkingEnabled) {
            if (!message.isGenerating()) {
                return "";
            }
            return getContext().getString(thinkingEnabled
                    && message.phase() == ChatMessage.Phase.REASONING
                    ? R.string.chat_generating_reasoning : R.string.chat_generating_answer);
        }

        /** The strip is drawn by the one row that owns it, and by no other (BASE-16). */
        private void renderSuggestions(ChatViewState state, ChatMessage message) {
            suggestions.removeAllViews();
            if (!state.recommendationsVisible()
                    || !message.messageId.equals(state.recommendationsMessageId)) {
                return;
            }
            LayoutInflater inflater = LayoutInflater.from(getContext());
            for (final Recommendation suggestion : state.recommendations) {
                View item = inflater.inflate(R.layout.item_chat_suggestion, suggestions, false);
                ((TextView) item.findViewById(R.id.suggestion_text)).setText(suggestion.question);
                item.setOnClickListener(view -> fire(actions -> actions.onRecommendationFollowed(
                        suggestion.question)));
                item.findViewById(R.id.suggestion_fork).setOnClickListener(view -> fire(
                        actions -> actions.onRecommendationForked(suggestion.question)));
                suggestions.addView(item);
            }
        }

        /** What the answer's own text view has selected right now; empty when nothing is selected. */
        private String selectedText() {
            int start = answer.getSelectionStart();
            int end = answer.getSelectionEnd();
            if (start < 0 || end <= start) {
                return "";
            }
            return answer.getText().subSequence(start, end).toString();
        }

        private String quotedFromAnswer() {
            String selection = selectedText();
            if (selection.trim().isEmpty()) {
                Toast.makeText(getContext(), R.string.chat_quote_hint, Toast.LENGTH_SHORT).show();
                return "";
            }
            return selection;
        }

        @Override
        public void onReasoningToggled(String id) {
            fire(actions -> actions.onReasoningToggled(id));
        }

        @Override
        public void onQuotePressed(String id) {
            final String selection = quotedFromAnswer();
            if (!selection.isEmpty()) {
                fire(actions -> actions.onQuotePressed(id, selection));
            }
        }

        @Override
        public void onCopyPressed(String id) {
            fire(actions -> actions.onCopyPressed(id));
        }

        @Override
        public void onResetPressed(String id) {
            fire(actions -> actions.onResetPressed(id));
        }

        @Override
        public void onForkPressed(String id) {
            fire(actions -> actions.onForkPressed(id));
        }

        @Override
        public void onChildrenPressed(String id) {
            fire(actions -> actions.onChildrenPressed(id));
        }

        @Override
        public void onVersionPressed(String id) {
            fire(actions -> actions.onVersionPressed(id));
        }

        /** The row's 子分支 entry is the list's anchor, so the list opens where it was asked for. */
        View childrenAnchor() {
            return actionBar;
        }

        /**
         * 长按 selects the sentence under the press and opens the menu on it. The press coordinates come
         * from the touch listener because the long-press callback carries none, and an empty answer
         * opens nothing rather than an empty menu.
         */
        private void openWordMenu() {
            TextSelection selection = TextSelection.sentence(answer.getText().toString(),
                    answer.getOffsetForPosition(pressX, pressY));
            if (selection.isEmpty()) {
                return;
            }
            dismissPopups();
            wordMenu = new WordMenuPopup(answer, message, this);
            wordMenu.show(selection);
        }

        @Override
        public void onQuoteRequested() {
            onQuotePressed(messageId);
        }

        @Override
        public void onForkRequested() {
            onForkPressed(messageId);
        }

        @Override
        public void onCopyRequested() {
            onSelectionCopy(messageId);
        }

        /** 复制 in the selection menu copies the passage, not the whole answer (PRD 6.2.19 point 5). */
        private void onSelectionCopy(String id) {
            fire(actions -> actions.onSelectionCopyPressed(selectedText()));
        }
    }
}
