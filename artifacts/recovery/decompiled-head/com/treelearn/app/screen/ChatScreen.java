/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.graphics.drawable.Drawable
 *  android.text.Editable
 *  android.text.TextWatcher
 *  android.text.format.DateUtils
 *  android.text.method.LinkMovementMethod
 *  android.util.AttributeSet
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.widget.Button
 *  android.widget.EditText
 *  android.widget.LinearLayout
 *  android.widget.PopupMenu
 *  android.widget.ScrollView
 *  android.widget.TextView
 *  android.widget.TextView$BufferType
 *  android.widget.Toast
 *  com.treelearn.app.R$color
 *  com.treelearn.app.R$drawable
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.ReadingAnchor
 *  com.treelearn.core.domain.ReadingPosition
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.treelearn.app.R;
import com.treelearn.app.screen.AnswerActionBar;
import com.treelearn.app.screen.ChatMotion;
import com.treelearn.app.screen.ChildBranchPopup;
import com.treelearn.app.screen.MarkdownRenderer;
import com.treelearn.app.screen.ModelThinkingPicker;
import com.treelearn.app.screen.NoticeText;
import com.treelearn.app.screen.ReasoningDisclosure;
import com.treelearn.app.screen.WordMenuPopup;
import com.treelearn.app.ui.ChatActions;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChatViewState;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.SourceCard;
import com.treelearn.app.ui.TextSelection;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationModel;
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

public final class ChatScreen
extends LinearLayout {
    private static final long SCROLL_SETTLE_MS = 150L;
    private final TextView title;
    private final ScrollView scroll;
    private final LinearLayout messageFlow;
    private final View loading;
    private final View error;
    private final TextView notice;
    private final View sourceCard;
    private final TextView sourceSummary;
    private final TextView sourceToggle;
    private final LinearLayout sourceHistory;
    private final View quoteRow;
    private final TextView quoteText;
    private final EditText draft;
    private final ModelThinkingPicker modelChip;
    private final Button send;
    private final Button followLatest;
    private final Map<String, Row> rows = new LinkedHashMap<String, Row>();
    private WordMenuPopup wordMenu;
    private ChildBranchPopup childrenPopup;
    private ChatActions actions;
    private boolean rendering;
    private ChatViewState.SendState sendState = ChatViewState.SendState.DISABLED;
    private boolean rowsReported;
    private ReadingPosition pendingPosition;
    private String renderedTail = "";
    private boolean renderedFollowing = true;
    private ChatViewState.Stage renderedStage = ChatViewState.Stage.IDLE;
    private ReadingAnchor settledAnchor;
    private Set<String> settledVisible = Collections.emptySet();
    private final Runnable scrollSettled = this::reportScroll;

    public ChatScreen(Context context) {
        this(context, null);
    }

    public ChatScreen(Context context, AttributeSet attributes) {
        super(context, attributes);
        this.setOrientation(1);
        LayoutInflater.from((Context)context).inflate(R.layout.screen_chat, (ViewGroup)this, true);
        this.title = (TextView)this.findViewById(R.id.chat_title);
        this.scroll = (ScrollView)this.findViewById(R.id.chat_scroll);
        this.messageFlow = (LinearLayout)this.findViewById(R.id.chat_messages);
        this.loading = this.findViewById(R.id.chat_loading);
        this.error = this.findViewById(R.id.chat_error);
        this.notice = (TextView)this.findViewById(R.id.chat_notice);
        this.sourceCard = this.findViewById(R.id.chat_source_card);
        this.sourceSummary = (TextView)this.findViewById(R.id.chat_source_summary);
        this.sourceToggle = (TextView)this.findViewById(R.id.chat_source_toggle);
        this.sourceHistory = (LinearLayout)this.findViewById(R.id.chat_source_history);
        this.quoteRow = this.findViewById(R.id.chat_quote_row);
        this.quoteText = (TextView)this.findViewById(R.id.chat_quote_text);
        this.draft = (EditText)this.findViewById(R.id.chat_draft);
        this.modelChip = (ModelThinkingPicker)this.findViewById(R.id.chat_model_chip);
        this.send = (Button)this.findViewById(R.id.chat_send);
        this.followLatest = (Button)this.findViewById(R.id.chat_follow);
        this.draft.addTextChangedListener(new TextWatcher(){

            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            public void afterTextChanged(Editable text) {
                ChatScreen.this.fire(actions -> actions.onDraftChanged(text.toString()));
            }
        });
        this.draft.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == 4 || event != null && event.getKeyCode() == 66 && event.getAction() == 0) {
                this.fire(actions -> actions.onSend());
                return true;
            }
            return false;
        });
        this.send.setOnClickListener(view -> this.fire(actions -> {
            if (this.sendState == ChatViewState.SendState.STOP) {
                actions.onStopRequested();
            } else {
                actions.onSend();
            }
        }));
        this.followLatest.setOnClickListener(view -> this.fire(actions -> actions.onFollowLatestPressed()));
        this.findViewById(R.id.chat_back).setOnClickListener(view -> this.fire(actions -> actions.onBackPressed()));
        this.findViewById(R.id.chat_overview).setOnClickListener(view -> this.fire(actions -> actions.onOverviewPressed()));
        this.findViewById(R.id.chat_nav).setOnClickListener(view -> this.fire(actions -> actions.onNavigationPressed()));
        this.findViewById(R.id.chat_settings).setOnClickListener(view -> this.fire(actions -> actions.onSettingsPressed()));
        this.findViewById(R.id.chat_quote_clear).setOnClickListener(view -> this.fire(actions -> actions.onQuoteCleared()));
        this.findViewById(R.id.chat_source_header).setOnClickListener(view -> this.fire(actions -> actions.onSourceCardToggled()));
        this.messageFlow.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> this.reportRowsLaidOut());
        this.scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            this.scroll.removeCallbacks(this.scrollSettled);
            this.scroll.postDelayed(this.scrollSettled, 150L);
        });
    }

    public void setActions(final ChatActions actions) {
        this.actions = actions;
        this.modelChip.setListener(new ModelThinkingPicker.Listener(){

            @Override
            public void onModelSelected(GenerationModel model) {
                actions.onModelSelected(model);
            }

            @Override
            public void onThinkingToggled(boolean enabled) {
                actions.onThinkingToggled(enabled);
            }
        });
    }

    public void showVersions(String messageId, List<AnswerRevision> versions) {
        if (versions.isEmpty()) {
            return;
        }
        PopupMenu menu = new PopupMenu(this.getContext(), (View)this);
        HashMap<Integer, String> byItem = new HashMap<Integer, String>();
        for (AnswerRevision revision : versions) {
            byItem.put(revision.revisionNo, revision.id);
            menu.getMenu().add(0, revision.revisionNo, revision.revisionNo, (CharSequence)this.getContext().getString(R.string.chat_version_item, new Object[]{revision.revisionNo}));
        }
        menu.setOnMenuItemClickListener(item -> {
            String revisionId = (String)byItem.get(item.getItemId());
            if (revisionId == null) {
                return false;
            }
            this.fire(actions -> actions.onRevisionSelected(messageId, revisionId));
            return true;
        });
        menu.show();
    }

    public void render(ChatViewState state) {
        this.rendering = true;
        try {
            this.dismissPopups();
            this.title.setText((CharSequence)state.branchTitle);
            this.loading.setVisibility(state.phase == ChatViewState.Phase.LOADING ? 0 : 8);
            this.error.setVisibility(state.phase == ChatViewState.Phase.ERROR ? 0 : 8);
            this.renderMessages(state);
            this.renderNotice(state.notice);
            this.renderSourceCard(state);
            this.renderQuote(state.quote);
            this.renderFollow(state);
            if (!state.draft.contentEquals((CharSequence)this.draft.getText())) {
                this.draft.setText((CharSequence)state.draft);
                this.draft.setSelection(this.draft.getText().length());
            }
            this.renderComposer(state);
            this.modelChip.render(state.model, state.thinkingEnabled);
            if (state.phase == ChatViewState.Phase.READY) {
                this.settle(state);
                this.renderStageChange(state);
            } else {
                this.renderedStage = ChatViewState.Stage.IDLE;
            }
        }
        finally {
            this.rendering = false;
        }
    }

    private void settle(ChatViewState state) {
        ChatMessage tail = state.messages.isEmpty() ? null : state.messages.get(state.messages.size() - 1);
        String fingerprint = tail == null ? "" : tail.messageId + "#" + tail.text.length() + "#" + tail.reasoning.length();
        boolean contentChanged = !fingerprint.equals(this.renderedTail);
        this.renderedTail = fingerprint;
        boolean following = state.followLatest;
        if (this.pendingPosition != null) {
            this.applyPosition();
        } else if (following && (contentChanged || !this.renderedFollowing)) {
            this.scroll.post(() -> this.scroll.fullScroll(130));
        }
        this.renderedFollowing = following;
    }

    public void renderPosition(ReadingPosition position) {
        this.pendingPosition = position;
        if (!this.rows.isEmpty()) {
            this.applyPosition();
        }
    }

    private void applyPosition() {
        Row row;
        ReadingPosition position = this.pendingPosition;
        this.pendingPosition = null;
        Row row2 = row = position == null || position.messageId == null ? null : this.rows.get(position.messageId);
        if (row == null) {
            this.scroll.post(() -> this.scroll.fullScroll(130));
            return;
        }
        int target = Math.max(0, row.root.getTop() + position.listItemOffset - position.viewportOffset);
        this.scroll.post(() -> this.scroll.scrollTo(0, target));
    }

    private void reportRowsLaidOut() {
        if (this.rowsReported || this.rows.isEmpty()) {
            return;
        }
        int itemHeightPx = this.rows.values().iterator().next().root.getHeight();
        if (itemHeightPx <= 0) {
            return;
        }
        this.rowsReported = true;
        Set<String> visible = this.visibleRows();
        this.settledVisible = visible;
        this.fire(actions -> actions.onRowsLaidOut(itemHeightPx, visible));
    }

    private Set<String> visibleRows() {
        int scrollY = this.scroll.getScrollY();
        int bottomOfViewport = scrollY + this.scroll.getHeight();
        LinkedHashSet<String> visible = new LinkedHashSet<String>();
        for (Map.Entry<String, Row> entry : this.rows.entrySet()) {
            View root = entry.getValue().root;
            if (root.getBottom() <= scrollY || root.getTop() >= bottomOfViewport) continue;
            visible.add(entry.getKey());
        }
        return visible;
    }

    private void renderNotice(HomeViewState.Notice value) {
        if (value == null) {
            this.notice.setVisibility(8);
            this.notice.setText((CharSequence)"");
            return;
        }
        this.notice.setText((CharSequence)this.getContext().getString(NoticeText.of(value)));
        this.notice.setVisibility(0);
    }

    private void reportScroll() {
        int scrollY = this.scroll.getScrollY();
        ReadingAnchor anchor = null;
        for (Map.Entry<String, Row> entry : this.rows.entrySet()) {
            View root = entry.getValue().root;
            if (anchor != null || root.getBottom() <= scrollY) continue;
            int into = scrollY - root.getTop();
            anchor = ReadingAnchor.of((String)entry.getKey(), null, (int)Math.max(0, into), (int)Math.max(0, -into));
        }
        this.settledAnchor = anchor;
        Set<String> visible = this.visibleRows();
        if (visible.equals(this.settledVisible)) {
            return;
        }
        this.settledVisible = visible;
        this.fire(actions -> actions.onViewportChanged(visible));
    }

    private void renderMessages(ChatViewState state) {
        this.rows.clear();
        this.messageFlow.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from((Context)this.getContext());
        for (int i = 0; i < state.messages.size(); ++i) {
            ChatMessage message = state.messages.get(i);
            Row row = new Row(inflater);
            row.render(state, message, i == state.messages.size() - 1);
            this.rows.put(message.messageId, row);
            this.messageFlow.addView(row.root);
        }
    }

    private void renderQuote(String quote) {
        boolean quoted = !quote.isEmpty();
        this.quoteRow.setVisibility(quoted ? 0 : 8);
        this.quoteText.setText((CharSequence)(quoted ? this.getContext().getString(R.string.chat_quoted, new Object[]{quote}) : ""));
    }

    private void renderComposer(ChatViewState state) {
        this.draft.setHint((CharSequence)this.getContext().getString(state.archived ? R.string.chat_draft_hint_archived : (!state.keyConfigured ? R.string.chat_draft_hint_no_key : R.string.chat_draft_hint)));
        this.sendState = state.sendState();
        this.send.setEnabled(this.sendState != ChatViewState.SendState.DISABLED);
        this.send.setBackgroundResource(this.sendState == ChatViewState.SendState.DISABLED ? R.drawable.bg_field : R.drawable.bg_button_primary);
        this.send.setContentDescription((CharSequence)this.getContext().getString(this.sendState == ChatViewState.SendState.STOP ? R.string.cd_chat_send_stop : R.string.cd_chat_send));
        int icon = this.sendState == ChatViewState.SendState.STOP ? R.drawable.ic_chat_stop : R.drawable.ic_chat_send;
        Drawable drawable2 = this.getResources().getDrawable(icon, this.getContext().getTheme());
        drawable2.setTint(this.getContext().getColor(this.sendState == ChatViewState.SendState.DISABLED ? R.color.tl_ink3 : R.color.tl_surface));
        this.send.setCompoundDrawablesWithIntrinsicBounds(drawable2, null, null, null);
    }

    private void renderFollow(ChatViewState state) {
        boolean detached = state.phase == ChatViewState.Phase.READY && !state.followLatest;
        this.followLatest.setVisibility(detached ? 0 : 8);
        this.followLatest.setText((CharSequence)(detached && state.missedCount > 0 ? this.getContext().getString(R.string.chat_back_to_latest_count, new Object[]{state.missedCount}) : this.getContext().getString(R.string.chat_back_to_latest)));
    }

    private void renderStageChange(ChatViewState state) {
        int sentence;
        ChatViewState.Stage stage = state.stage;
        if (stage == this.renderedStage || this.rows.isEmpty()) {
            return;
        }
        ChatViewState.Stage previous = this.renderedStage;
        this.renderedStage = stage;
        Row newest = this.rows.get(state.messages.get((int)(state.messages.size() - 1)).messageId);
        if (newest == null) {
            return;
        }
        if (stage == ChatViewState.Stage.STABLE || stage == ChatViewState.Stage.CANCELED) {
            newest.enterStage(stage == ChatViewState.Stage.CANCELED);
        } else if (stage == ChatViewState.Stage.ANSWERING && previous == ChatViewState.Stage.REASONING) {
            newest.enterAnswer();
        }
        int n = stage == ChatViewState.Stage.REASONING ? R.string.chat_generating_reasoning : (stage == ChatViewState.Stage.ANSWERING || stage == ChatViewState.Stage.SENDING ? R.string.chat_generating_answer : (stage == ChatViewState.Stage.CANCELED ? R.string.chat_stopped : (sentence = stage == ChatViewState.Stage.FAILED ? R.string.chat_failed : 0)));
        if (sentence != 0) {
            this.announceForAccessibility(this.getContext().getString(sentence));
        }
    }

    private void renderSourceCard(ChatViewState state) {
        SourceCard card = state.sourceCard;
        if (card == null || card.summary.isEmpty()) {
            this.sourceCard.setVisibility(8);
            return;
        }
        this.sourceCard.setVisibility(0);
        this.sourceSummary.setText((CharSequence)this.getContext().getString(R.string.source_card_summary, new Object[]{card.summary}));
        this.sourceToggle.setText((CharSequence)this.getContext().getString(state.sourceCardExpanded ? R.string.source_card_collapse : R.string.source_card_expand));
        this.sourceHistory.setVisibility(state.sourceCardExpanded ? 0 : 8);
        this.sourceHistory.removeAllViews();
        if (!state.sourceCardExpanded) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from((Context)this.getContext());
        for (SourceCard.Entry entry : card.history) {
            View row = inflater.inflate(R.layout.item_source_history, (ViewGroup)this.sourceHistory, false);
            TextView text = (TextView)row.findViewById(R.id.source_history_text);
            text.setText((CharSequence)this.getContext().getString(entry.user ? R.string.source_history_question : R.string.source_history_answer, new Object[]{entry.text}));
            this.sourceHistory.addView(row);
        }
    }

    private void fire(Consumer<ChatActions> call) {
        if (this.actions != null && !this.rendering) {
            call.accept(this.actions);
        }
    }

    public void showChildren(String messageId, List<ChildBranch> children, ChildBranchPopup.Listener listener) {
        this.dismissPopups();
        Row row = this.rows.get(messageId);
        if (row == null || children.isEmpty()) {
            return;
        }
        this.childrenPopup = ChildBranchPopup.show(row.childrenAnchor(), children, listener);
    }

    private void dismissPopups() {
        if (this.wordMenu != null) {
            this.wordMenu.close();
            this.wordMenu = null;
        }
        if (this.childrenPopup != null) {
            this.childrenPopup.close();
            this.childrenPopup = null;
        }
    }

    protected void onDetachedFromWindow() {
        this.dismissPopups();
        this.scroll.removeCallbacks(this.scrollSettled);
        ReadingAnchor anchor = this.settledAnchor;
        if (anchor != null) {
            this.fire(actions -> actions.onLeaving(anchor));
        }
        super.onDetachedFromWindow();
    }

    private final class Row
    implements ReasoningDisclosure.Listener,
    AnswerActionBar.Listener,
    WordMenuPopup.Listener {
        private final View root;
        private final View questionBlock;
        private final TextView question;
        private final TextView quoteChip;
        private final View answerBlock;
        private final TextView time;
        private final TextView answer;
        private final View caret;
        private final TextView generating;
        private final View failure;
        private final TextView canceled;
        private final Button retry;
        private final ReasoningDisclosure disclosure;
        private final AnswerActionBar actionBar;
        private final LinearLayout suggestions;
        private String messageId = "";
        private ChatMessage message;
        private float pressX;
        private float pressY;

        Row(LayoutInflater inflater) {
            this.root = inflater.inflate(R.layout.item_chat_message, (ViewGroup)ChatScreen.this.messageFlow, false);
            this.questionBlock = this.root.findViewById(R.id.chat_question_block);
            this.question = (TextView)this.root.findViewById(R.id.chat_question);
            this.quoteChip = (TextView)this.root.findViewById(R.id.chat_quote_chip);
            this.answerBlock = this.root.findViewById(R.id.chat_answer_block);
            this.time = (TextView)this.root.findViewById(R.id.chat_time);
            this.answer = (TextView)this.root.findViewById(R.id.chat_answer);
            this.answer.setMovementMethod(LinkMovementMethod.getInstance());
            this.caret = this.root.findViewById(R.id.chat_caret);
            this.generating = (TextView)this.root.findViewById(R.id.chat_generating_note);
            this.failure = this.root.findViewById(R.id.chat_failure);
            this.canceled = (TextView)this.root.findViewById(R.id.chat_canceled);
            this.retry = (Button)this.root.findViewById(R.id.chat_retry);
            this.disclosure = (ReasoningDisclosure)this.root.findViewById(R.id.chat_reasoning);
            this.actionBar = (AnswerActionBar)this.root.findViewById(R.id.chat_actions);
            this.suggestions = (LinearLayout)this.root.findViewById(R.id.chat_suggestions);
            this.disclosure.setListener(this);
            this.actionBar.setListener(this);
            this.retry.setOnClickListener(view -> ChatScreen.this.fire(actions -> actions.onRetryPressed(this.messageId)));
            this.answer.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == 0) {
                    this.pressX = event.getX();
                    this.pressY = event.getY();
                }
                return false;
            });
            this.answer.setOnLongClickListener(view -> {
                this.openWordMenu();
                return true;
            });
        }

        void render(ChatViewState state, ChatMessage message, boolean newestRow) {
            this.messageId = message.messageId;
            this.message = message;
            this.questionBlock.setVisibility(message.isAi() ? 8 : 0);
            this.answerBlock.setVisibility(message.isAi() ? 0 : 8);
            if (!message.isAi()) {
                this.renderQuestion(message);
                return;
            }
            this.time.setText(DateUtils.getRelativeTimeSpanString((long)message.createdAt, (long)System.currentTimeMillis(), (long)60000L));
            this.disclosure.render(message, state.isReasoningEffectivelyExpanded(message.messageId), state.thinkingEnabled);
            this.answer.setText(MarkdownRenderer.render(message.text, code -> ChatScreen.this.fire(actions -> actions.onSelectionCopyPressed(code))), TextView.BufferType.SPANNABLE);
            this.renderGenerationSlots(state, message, newestRow);
            this.retry.setEnabled(message.canRetry());
            this.actionBar.setVisibility(message.isStable() ? 0 : 8);
            this.actionBar.render(message);
            this.renderSuggestions(state, message);
        }

        private void renderGenerationSlots(ChatViewState state, ChatMessage message, boolean newestRow) {
            boolean reasoning = message.isGenerating() && message.phase() == ChatMessage.Phase.REASONING;
            boolean answering = message.isGenerating() && message.phase() == ChatMessage.Phase.ANSWER;
            this.generating.setText(this.generatingNote(message, state.thinkingEnabled));
            this.generating.setVisibility(reasoning ? 0 : 8);
            this.caret.setVisibility(answering ? 0 : 8);
            if (answering && !ChatMotion.reduced(ChatScreen.this.getContext().getContentResolver()) && this.caret.getAnimation() == null) {
                this.caret.startAnimation(ChatMotion.caretBlink());
            } else if (!answering || ChatMotion.reduced(ChatScreen.this.getContext().getContentResolver())) {
                this.caret.clearAnimation();
            }
            boolean stopped = newestRow && state.stage == ChatViewState.Stage.CANCELED;
            this.failure.setVisibility(message.isFailed() && !stopped ? 0 : 8);
            this.canceled.setVisibility(stopped && message.isFailed() ? 0 : 8);
        }

        void enterStage(boolean stopped) {
            this.fade((View)(stopped ? this.canceled : this.actionBar), 150L);
        }

        void enterAnswer() {
            if (!ChatMotion.reduced(ChatScreen.this.getContext().getContentResolver())) {
                this.fade((View)this.answer, 220L);
            }
        }

        private void fade(View view, long durationMs) {
            if (ChatMotion.reduced(ChatScreen.this.getContext().getContentResolver())) {
                view.setAlpha(1.0f);
                return;
            }
            view.setAlpha(0.0f);
            view.animate().alpha(1.0f).setDuration(durationMs).start();
        }

        private void renderQuestion(ChatMessage message) {
            this.question.setText((CharSequence)message.text);
            boolean quoted = !message.quote.isEmpty();
            this.quoteChip.setVisibility(quoted ? 0 : 8);
            this.quoteChip.setText((CharSequence)(quoted ? ChatScreen.this.getContext().getString(R.string.chat_quoted, new Object[]{message.quote}) : ""));
        }

        private CharSequence generatingNote(ChatMessage message, boolean thinkingEnabled) {
            if (!message.isGenerating()) {
                return "";
            }
            return ChatScreen.this.getContext().getString(thinkingEnabled && message.phase() == ChatMessage.Phase.REASONING ? R.string.chat_generating_reasoning : R.string.chat_generating_answer);
        }

        private void renderSuggestions(ChatViewState state, ChatMessage message) {
            this.suggestions.removeAllViews();
            if (!state.recommendationsVisible() || !message.messageId.equals(state.recommendationsMessageId)) {
                return;
            }
            LayoutInflater inflater = LayoutInflater.from((Context)ChatScreen.this.getContext());
            for (Recommendation suggestion : state.recommendations) {
                View item = inflater.inflate(R.layout.item_chat_suggestion, (ViewGroup)this.suggestions, false);
                ((TextView)item.findViewById(R.id.suggestion_text)).setText((CharSequence)suggestion.question);
                item.setOnClickListener(view -> ChatScreen.this.fire(actions -> actions.onRecommendationFollowed(suggestion.question)));
                item.findViewById(R.id.suggestion_fork).setOnClickListener(view -> ChatScreen.this.fire(actions -> actions.onRecommendationForked(suggestion.question)));
                this.suggestions.addView(item);
            }
        }

        private String selectedText() {
            int start = this.answer.getSelectionStart();
            int end = this.answer.getSelectionEnd();
            if (start < 0 || end <= start) {
                return "";
            }
            return this.answer.getText().subSequence(start, end).toString();
        }

        private String quotedFromAnswer() {
            String selection = this.selectedText();
            if (selection.trim().isEmpty()) {
                Toast.makeText((Context)ChatScreen.this.getContext(), (int)R.string.chat_quote_hint, (int)0).show();
                return "";
            }
            return selection;
        }

        @Override
        public void onReasoningToggled(String id2) {
            ChatScreen.this.fire(actions -> actions.onReasoningToggled(id2));
        }

        @Override
        public void onQuotePressed(String id2) {
            String selection = this.quotedFromAnswer();
            if (!selection.isEmpty()) {
                ChatScreen.this.fire(actions -> actions.onQuotePressed(id2, selection));
            }
        }

        @Override
        public void onCopyPressed(String id2) {
            ChatScreen.this.fire(actions -> actions.onCopyPressed(id2));
        }

        @Override
        public void onResetPressed(String id2) {
            ChatScreen.this.fire(actions -> actions.onResetPressed(id2));
        }

        @Override
        public void onForkPressed(String id2) {
            ChatScreen.this.fire(actions -> actions.onForkPressed(id2, "", 0, 0));
        }

        @Override
        public void onChildrenPressed(String id2) {
            ChatScreen.this.fire(actions -> actions.onChildrenPressed(id2));
        }

        @Override
        public void onVersionPressed(String id2) {
            ChatScreen.this.fire(actions -> actions.onVersionPressed(id2));
        }

        View childrenAnchor() {
            return this.actionBar;
        }

        private void openWordMenu() {
            TextSelection selection = TextSelection.sentence(this.answer.getText().toString(), this.answer.getOffsetForPosition(this.pressX, this.pressY));
            if (selection.isEmpty()) {
                return;
            }
            ChatScreen.this.dismissPopups();
            ChatScreen.this.wordMenu = new WordMenuPopup(this.answer, this.message, this);
            ChatScreen.this.wordMenu.show(selection);
        }

        @Override
        public void onQuoteRequested() {
            this.onQuotePressed(this.messageId);
        }

        @Override
        public void onForkRequested() {
            TextSelection selection = TextSelection.of(this.answer.getSelectionStart(), this.answer.getSelectionEnd());
            String quote = selection.read(this.answer.getText().toString());
            ChatScreen.this.fire(actions -> actions.onForkPressed(this.messageId, quote, selection.start, selection.end));
        }

        @Override
        public void onCopyRequested() {
            this.onSelectionCopy(this.messageId);
        }

        private void onSelectionCopy(String id2) {
            ChatScreen.this.fire(actions -> actions.onSelectionCopyPressed(this.selectedText()));
        }
    }
}

