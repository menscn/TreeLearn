package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

import com.treelearn.app.R;
import com.treelearn.app.ui.SpeechInputPolicy;
import com.treelearn.app.ui.ChatActions;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChatSpeaker;
import com.treelearn.app.ui.ChatViewState;
import com.treelearn.app.ui.ChatViewState.Stage;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.SourceCard;
import com.treelearn.app.ui.TextSelection;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.ReadingPosition;

import java.util.ArrayList;
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
 *   <li><strong>Rows are keyed and reused by message id.</strong> Each read updates the rows it needs
 *       without tearing down the message views, so a streaming answer keeps its layout and text
 *       selection handles instead of flickering through a full list rebuild.
 *   <li><strong>The selection is read, not remembered.</strong> 追问, 新建主题 and 复制 all take
 *       whatever the answer's own text view currently has selected and hand that one range over;
 *       with nothing selected the screen says so instead of acting on an empty sentence.
 *   <li><strong>The two recommendation targets stay apart.</strong> The row asks a follow-up, its own
 *       44dp zone opens a branch; neither is reachable from the other's gesture.
 *   <li><strong>Answer text is platform-selectable, and the menu over it is the app's.</strong> The
 *       native selection handles let the learner drag either edge of a partial passage; the platform's
 *       own toolbar is emptied, so the four rows they see come from {@link WordMenu}, in the order and
 *       with the labels the app decides (PRD 6.2.19).
 *   <li><strong>The reading position is measured here and decided elsewhere.</strong> The screen
 *       reports how tall a row is, which rows the viewport shows and where the learner stopped - all
 *       facts only a laid-out view has. Where that resolves to, and whether the unread badge may
 *       clear, are the reading session's answers, and nothing here reads or writes a database.
 * </ul>
 */
public final class ChatScreen extends LinearLayout implements ChatSpeaker.Sink {

    /** Scroll events arrive in bursts; the view is read once they stop. */
    private static final long SCROLL_SETTLE_MS = 150L;

    private final int basePaddingLeft;
    private final int basePaddingTop;
    private final int basePaddingRight;
    private final int basePaddingBottom;

    private final TextView title;
    private final ScrollView scroll;
    private final LinearLayout messageFlow;
    private final View loading;
    private final View error;
    /** The reading-state notice: a write the store refused, empty the rest of the time. */
    private final TextView notice;
    /** T-065: the source card at the conversation's top; hidden on a root branch. */
    private final View sourceCard;
    private final TextView sourceSummary;
    private final TextView sourceToggle;
    private final LinearLayout sourceHistory;
    private final View quoteRow;
    private final TextView quoteText;
    private final View attachRow;
    private final TextView attachText;
    private View attachEntry;
    private Runnable onAttachPressed;
    /** 2026-09-28: the three 问 AI quick queries and the row that carries them under the quote. */
    private final View askChipsRow;
    private final View askChipWhy;
    private final View askChipWhat;
    private final View askChipHow;
    private final EditText draft;
    private final android.widget.ImageView voiceKey;
    private SpeechInputPolicy speechPolicy = new SpeechInputPolicy();
    private boolean listening;
    private Runnable onVoicePressed;
    private Runnable onVoiceReleased;
    private Runnable onVoiceGuidance;
    private final ModelThinkingPicker modelChip;
    private final TextView searchChip;
    private final Button send;
    /** T-075: the follow-latest pill (073 §6); hidden while the view follows on its own. */
    private final Button followLatest;
    private final ImageView fullscreenEntry;
    private final View fullscreen;
    private final TextView fullscreenQuote;
    private final EditText fullscreenDraft;

    /** The rows of the last render, by message id, so a selection can be read back. */
    private final Map<String, Row> rows = new LinkedHashMap<String, Row>();

    /** The two cards that hang over an answer: the selection menu, and the child-branch list. */
    private WordMenuPopup wordMenu;
    private ChildBranchPopup childrenPopup;

    private ChatActions actions;
    private boolean rendering;
    /** T-076: the send key's face as the last render drew it, so a click stops or sends, not both. */
    private ChatViewState.SendState sendState = ChatViewState.SendState.DISABLED;
    private ChatSpeaker speaker;
    private ChatSpeaker.State speakState = ChatSpeaker.State.initial();
    /** Rows are reported to the presenter once per visit, after the first layout that has one. */
    private boolean rowsReported;
    private boolean renderedFullscreen;
    private boolean fullscreenEntryAvailable;
    private int fullscreenEntryHint;
    private ChatViewState lastRenderedState;
    /** The position the presenter resolved; applied once, after which the view belongs to the learner. */
    private ReadingPosition pendingPosition;
    /** The last message id of the last ready render, so only a new turn scrolls to the bottom. */
    private String renderedTail = "";
    /** T-075: what the last render's follow state was, so a re-attach scrolls exactly once. */
    private boolean renderedFollowing = true;
    /** T-075: the stage the last render drew, so a transition's fade and sentence fire once. */
    private Stage renderedStage = Stage.IDLE;
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
        basePaddingLeft = getPaddingLeft();
        basePaddingTop = getPaddingTop();
        basePaddingRight = getPaddingRight();
        basePaddingBottom = getPaddingBottom();
        setOnApplyWindowInsetsListener((view, insets) -> {
            setPadding(basePaddingLeft, basePaddingTop + insets.getSystemWindowInsetTop(),
                    basePaddingRight, basePaddingBottom + insets.getSystemWindowInsetBottom());
            return insets;
        });
        LayoutInflater.from(context).inflate(R.layout.screen_chat, this, true);
        title = findViewById(R.id.chat_title);
        scroll = findViewById(R.id.chat_scroll);
        messageFlow = findViewById(R.id.chat_messages);
        loading = findViewById(R.id.chat_loading);
        error = findViewById(R.id.chat_error);
        notice = findViewById(R.id.chat_notice);
        sourceCard = findViewById(R.id.chat_source_card);
        sourceSummary = findViewById(R.id.chat_source_summary);
        sourceToggle = findViewById(R.id.chat_source_toggle);
        sourceHistory = findViewById(R.id.chat_source_history);
        quoteRow = findViewById(R.id.chat_quote_row);
        quoteText = findViewById(R.id.chat_quote_text);
        askChipsRow = findViewById(R.id.chat_ask_chips);
        askChipWhy = findViewById(R.id.chat_ask_why);
        askChipWhat = findViewById(R.id.chat_ask_what);
        askChipHow = findViewById(R.id.chat_ask_how);
        askChipWhy.setOnClickListener(view -> fireChip(R.string.chat_ask_query_why));
        askChipWhat.setOnClickListener(view -> fireChip(R.string.chat_ask_query_what));
        askChipHow.setOnClickListener(view -> fireChip(R.string.chat_ask_query_how));
        attachRow = findViewById(R.id.chat_attach_row);
        attachText = findViewById(R.id.chat_attach_text);
        attachEntry = findViewById(R.id.chat_attach);
        attachEntry.setOnClickListener(view -> {
            if (onAttachPressed != null) {
                onAttachPressed.run();
            }
        });
        findViewById(R.id.chat_attach_clear)
                .setOnClickListener(view -> fire(actions -> actions.onAttachmentCleared()));
        voiceKey = findViewById(R.id.chat_voice);
        voiceKey.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    if (!speechPolicy.enabled()) {
                        if (onVoiceGuidance != null && speechPolicy.guidanceOnTap()) {
                            onVoiceGuidance.run();
                        }
                        return true;
                    }
                    if (onVoicePressed != null) {
                        onVoicePressed.run();
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    // The touch itself is authoritative about the finger: fire even when
                    // listening never started (e.g. while the permission dialog was up),
                    // so the host can cancel a pending press-held speech start.
                    if (onVoiceReleased != null) {
                        onVoiceReleased.run();
                    }
                    return true;
                default:
                    return true;
            }
        });
        draft = findViewById(R.id.chat_draft);
        modelChip = findViewById(R.id.chat_model_chip);
        searchChip = findViewById(R.id.chat_search_chip);
        send = findViewById(R.id.chat_send);
        followLatest = findViewById(R.id.chat_follow);

        fullscreenEntry = findViewById(R.id.chat_expand);
        fullscreen = findViewById(R.id.chat_fullscreen);
        fullscreenQuote = findViewById(R.id.chat_fullscreen_quote);
        fullscreenDraft = findViewById(R.id.chat_fullscreen_draft);
        draft.addTextChangedListener(draftForwarder());
        draft.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                fire(actions -> actions.onSend());
                return true;
            }
            return false;
        });
        // T-076: one control, three faces. While it draws the stop state the same tap asks the
        // presenter to stop; the state is the render's, never a second opinion of the view's.
        send.setOnClickListener(view -> fire(actions -> {
            if (sendState == ChatViewState.SendState.STOP) {
                actions.onStopRequested();
            } else {
                actions.onSend();
            }
        }));
        followLatest.setOnClickListener(view -> fire(actions -> actions.onFollowLatestPressed()));
        searchChip.setOnClickListener(view -> showSearchPanel());
        fullscreenEntry.setOnClickListener(view -> fire(actions -> {
            if (fullscreenEntryAvailable) {
                actions.onFullscreenEditOpened();
            } else if (fullscreenEntryHint != 0) {
                Toast.makeText(getContext(), fullscreenEntryHint, Toast.LENGTH_SHORT).show();
            }
        }));
        fullscreenDraft.addTextChangedListener(draftForwarder());
        for (int id : new int[]{R.id.chat_fullscreen_close, R.id.chat_fullscreen_cancel, R.id.chat_fullscreen_done}) {
            findViewById(id).setOnClickListener(v -> fire(a -> a.onFullscreenEditClosed()));
        }
        findViewById(R.id.chat_back).setOnClickListener(view -> fire(actions -> actions.onBackPressed()));
        findViewById(R.id.chat_overview)
                .setOnClickListener(view -> fire(actions -> actions.onOverviewPressed()));
        findViewById(R.id.chat_nav)
                .setOnClickListener(view -> fire(actions -> actions.onNavigationPressed()));
        findViewById(R.id.chat_settings)
                .setOnClickListener(view -> fire(actions -> actions.onSettingsPressed()));
        findViewById(R.id.chat_quote_clear)
                .setOnClickListener(view -> fire(actions -> actions.onQuoteCleared()));
        findViewById(R.id.chat_source_header)
                .setOnClickListener(view -> fire(actions -> actions.onSourceCardToggled()));

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

    public void setSpeaker(ChatSpeaker speaker) {
        this.speaker = speaker;
    }

    @Override
    public void render(ChatSpeaker.State state) {
        ChatSpeaker.State previous = this.speakState;
        this.speakState = state;
        // 2026-09-28 user decision: the action row no longer carries a speak button, so speech has no
        // face to repaint. The state is still tracked because the error toast below is the only
        // learner-visible signal left; the wiring stays for the day speech gets a new entry point.
        if (state.phase == ChatSpeaker.Phase.ERROR && previous.phase != ChatSpeaker.Phase.ERROR) {
            Toast.makeText(getContext(), R.string.voice_notice_playback_error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
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
            renderSourceCard(state);
            renderQuote(state.quote);
            renderAskAiChips(state);
            renderAttachment(state.composerAttachment);
            renderFollow(state);
            if (!state.draft.contentEquals(draft.getText())) {
                draft.setText(state.draft);
                draft.setSelection(draft.getText().length());
            }
            renderComposer(state);
            renderFullscreen(state);
            renderSearchChip(state);
            modelChip.render(state.model, state.thinkingEnabled);
            if (state.phase == ChatViewState.Phase.READY) {
                settle(state);
                renderStageChange(state);
            } else { renderedStage = Stage.IDLE; }
        } finally {
            rendering = false;
        }
    }

    /**
     * Where the view goes when a read lands.
     *
     * <p>A resolved position is applied instead of the bottom, and it is applied once: after that the
     * view belongs to the learner. T-075 hands that ownership a shape: while the follow state says
     * following, new or grown content pulls the view down; once detached, nothing does (073 §6) -
     * the fingerprint covers the tail's content sizes, so a repaint of the same words never scrolls,
     * and a re-attach from the follow-latest button lands exactly once.
     */
    private void settle(ChatViewState state) {
        ChatMessage tail = state.messages.isEmpty() ? null : state.messages.get(state.messages.size() - 1);
        String fingerprint = tail == null ? "" : tail.messageId + "#" + tail.text.length() + "#" + tail.reasoning.length();
        boolean contentChanged = !fingerprint.equals(renderedTail);
        renderedTail = fingerprint;
        boolean following = state.followLatest;
        if (pendingPosition != null) {
            applyPosition();
        } else if (following && (contentChanged || !renderedFollowing)) {
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
        renderedFollowing = following;
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

    private void renderSearchChip(ChatViewState state) {
        lastRenderedState = state;
        searchChip.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(
                state.searchEnabled ? R.color.tl_green_soft : R.color.tl_surface)));
        searchChip.setTextColor(getResources().getColor(
                state.searchEnabled ? R.color.tl_green_deep : R.color.tl_ink3));
    }

    private void showSearchPanel() {
        ChatViewState state = lastRenderedState;
        if (state == null) {
            return;
        }
        Dialog dialog = new Dialog(getContext());
        View content = LayoutInflater.from(getContext()).inflate(R.layout.panel_search_picker, this, false);
        TextView toggle = (TextView) content.findViewById(R.id.search_panel_toggle);
        toggle.setText(state.searchEnabled ? R.string.search_panel_toggle_on : R.string.search_panel_toggle_off);
        toggle.setOnClickListener(view -> fire(actions -> actions.onSearchToggled(!state.searchEnabled)));
        ((TextView) content.findViewById(R.id.search_panel_engine)).setText(
                getContext().getString(R.string.search_panel_engine, state.searchEngineLabel));
        content.findViewById(R.id.search_panel_settings).setOnClickListener(view -> {
            fire(actions -> actions.onSearchOpenSettings());
            dialog.dismiss();
        });
        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.setBackgroundDrawable(new ColorDrawable(0));
        }
        dialog.show();
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
        LayoutInflater inflater = LayoutInflater.from(getContext());
        Map<String, Row> nextRows = new LinkedHashMap<String, Row>();
        for (int i = 0; i < state.messages.size(); i++) {
            ChatMessage message = state.messages.get(i);
            Row row = rows.get(message.messageId);
            if (row == null) {
                row = new Row(inflater);
            }
            row.render(state, message, i == state.messages.size() - 1);
            nextRows.put(message.messageId, row);
            View current = messageFlow.getChildAt(i);
            if (current != row.root) {
                if (row.root.getParent() == messageFlow) {
                    messageFlow.removeView(row.root);
                }
                messageFlow.addView(row.root, i);
            }
        }
        while (messageFlow.getChildCount() > state.messages.size()) {
            messageFlow.removeViewAt(messageFlow.getChildCount() - 1);
        }
        rows.clear();
        rows.putAll(nextRows);
    }

    /** T-097: the host owns the SAF picker; the screen only reports the attach key. */
    public void setOnAttachPressed(Runnable onAttachPressed) {
        this.onAttachPressed = onAttachPressed;
    }

    /**
     * T-095: the host owns the speech policy (permission decisions are host-side); the screen
     * only reads it in the key's touch handler and draws what it says.
     */
    public void setSpeechPolicy(SpeechInputPolicy policy) {
        this.speechPolicy = policy == null ? new SpeechInputPolicy() : policy;
        voiceKey.setAlpha(speechPolicy.enabled() ? 1f : 0.4f);
    }

    /** SPEC-081: no recognition service, no key - hidden, not greyed, not a placeholder. */
    public void setVoiceAvailable(boolean available) {
        voiceKey.setVisibility(GONE);
        if (!available) {
            setListening(false);
        }
    }

    /** The recording indicator: tint and the accessibility label swap while listening. */
    public void setListening(boolean listening) {
        this.listening = listening;
        voiceKey.setImageTintList(android.content.res.ColorStateList.valueOf(getContext()
                .getColor(listening ? R.color.tl_green_deep : R.color.tl_ink2)));
        voiceKey.setContentDescription(getContext().getString(listening
                ? R.string.cd_chat_voice_listening : R.string.cd_chat_voice));
        voiceKey.setAlpha(speechPolicy.enabled() ? 1f : 0.4f);
    }

    /** Press-and-hold: the host checks permission and starts the recognizer session. */
    public void setOnVoicePressed(Runnable onVoicePressed) {
        this.onVoicePressed = onVoicePressed;
    }

    /** Release: the host stops the session; the final text lands via the presenter action. */
    public void setOnVoiceReleased(Runnable onVoiceReleased) {
        this.onVoiceReleased = onVoiceReleased;
    }

    /** The one-time guidance after a denied permission (SPEC-081: 首次点按给开启引导). */
    public void setOnVoiceGuidance(Runnable onVoiceGuidance) {
        this.onVoiceGuidance = onVoiceGuidance;
    }

    private void renderQuote(String quote) {
        boolean quoted = !quote.isEmpty();
        quoteRow.setVisibility(quoted ? VISIBLE : GONE);
        quoteText.setText(quoted ? getContext().getString(R.string.chat_quoted, quote) : "");
    }

    /**
     * 2026-09-28: the 问 AI quick queries ride the quote row - same signal, one draw. The chips
     * are visible only when the state says so and a quote stands behind them; a quote without the
     * chips (a prefilled branch anchor, say) draws the quote row alone.
     */
    private void renderAskAiChips(ChatViewState state) {
        askChipsRow.setVisibility(state.askAiChips && !state.quote.isEmpty() ? VISIBLE : GONE);
    }

    /** One chip tap: its query goes out as-is, carrying the quote; the draft stays untouched. */
    private void fireChip(int queryRes) {
        fire(actions -> actions.onAskAiChipPressed(getContext().getString(queryRes)));
    }

    /** T-097: the composer's attachment chip, or nothing while the composer carries none. */
    private void renderAttachment(ChatViewState.ComposerAttachment attachment) {
        if (attachment == null) {
            attachRow.setVisibility(GONE);
            return;
        }
        attachRow.setVisibility(VISIBLE);
        switch (attachment.state) {
            case PARSING:
                attachText.setText(R.string.chat_attachment_parsing);
                attachText.setTextColor(getContext().getColor(R.color.tl_ink3));
                break;
            case FAILED:
                attachText.setText(R.string.chat_attachment_failed);
                attachText.setTextColor(getContext().getColor(R.color.tl_amber));
                break;
            case READY:
            default:
                attachText.setText(getContext().getString(R.string.chat_attached,
                        attachment.displayName,
                        android.text.format.Formatter.formatShortFileSize(getContext(),
                                attachment.sizeBytes)));
                attachText.setTextColor(getContext().getColor(R.color.tl_ink2));
        }
    }

    /**
     * T-076: the composer's two stateful surfaces (073 §5). The draft's hint names why sending is
     * blocked - archived, no key, or the plain invitation - and the send key swaps between its
     * three faces on the one control, so no state change can reflow the composer or move a hit box.
     */
    private void renderComposer(ChatViewState state) {
        draft.setHint(getContext().getString(
                state.archived ? R.string.chat_draft_hint_archived
                        : !state.keyConfigured ? R.string.chat_draft_hint_no_key
                        : R.string.chat_draft_hint));
        sendState = state.sendState();
        send.setEnabled(sendState != ChatViewState.SendState.DISABLED);
        // 2026-09-27 user decision: the send control is the circular fab (prototype .send-fab) in
        // every state - its selector already draws the disabled step, so no rectangular swap here.
        send.setBackgroundResource(R.drawable.bg_send_fab);
        send.setContentDescription(getContext().getString(sendState == ChatViewState.SendState.STOP
                ? R.string.cd_chat_send_stop : R.string.cd_chat_send));
        int icon = sendState == ChatViewState.SendState.STOP
                ? R.drawable.ic_chat_stop : R.drawable.ic_chat_send;
        Drawable drawable = getResources().getDrawable(icon, getContext().getTheme());
        drawable.setTint(getContext().getColor(sendState == ChatViewState.SendState.DISABLED
                ? R.color.tl_ink3 : R.color.tl_surface));
        send.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
    }

    private void renderFullscreen(ChatViewState state) {
        fullscreenEntryAvailable = state.fullscreenEditEntryAvailable();
        fullscreenEntryHint = state.archived ? R.string.chat_draft_hint_archived
                : !state.keyConfigured ? R.string.chat_draft_hint_no_key : 0;
        fullscreenEntry.setColorFilter(getContext().getColor(
                fullscreenEntryAvailable ? R.color.tl_green_deep : R.color.tl_ink3));
        fullscreen.setVisibility(state.fullscreenEditing ? VISIBLE : GONE);
        if (state.fullscreenEditing) {
            boolean quoted = !state.quote.isEmpty();
            fullscreenQuote.setVisibility(quoted ? VISIBLE : GONE);
            fullscreenQuote.setText(quoted
                    ? getContext().getString(R.string.chat_quoted, state.quote) : "");
            if (!state.draft.contentEquals(fullscreenDraft.getText())) {
                fullscreenDraft.setText(state.draft);
                fullscreenDraft.setSelection(fullscreenDraft.getText().length());
            }
            if (!renderedFullscreen) {
                fullscreenDraft.requestFocus();
                announceForAccessibility(getContext().getString(R.string.chat_fullscreen_entered));
            }
        } else if (renderedFullscreen) {
            announceForAccessibility(getContext().getString(R.string.chat_fullscreen_exited));
            draft.requestFocus();
            draft.setSelection(draft.getText().length());
        }
        renderedFullscreen = state.fullscreenEditing;
    }

    /** Returns true if it closed the fullscreen editor, so the host can skip its own back handling. */
    public boolean onSystemBack() {
        if (!renderedFullscreen) {
            return false;
        }
        fire(actions -> actions.onFullscreenEditClosed());
        return true;
    }

    /**
     * T-075: the follow-latest pill (073 §6). Visible only while the learner is detached, and only
     * on a ready screen; while it counts missed growths the count rides the label.
     */
    private void renderFollow(ChatViewState state) {
        boolean detached = state.phase == ChatViewState.Phase.READY && !state.followLatest;
        followLatest.setVisibility(detached ? VISIBLE : GONE);
        followLatest.setText(detached && state.missedCount > 0
                ? getContext().getString(R.string.chat_back_to_latest_count, state.missedCount)
                : getContext().getString(R.string.chat_back_to_latest));
    }

    private TextWatcher draftForwarder() {
        return new TextWatcher() {
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
        };
    }

    /**
     * T-075: what a stage transition does, once - the one-shot entry fades of 073 §4 and the
     * TalkBack sentence of §7. The fades read the system low-motion switch and go static there;
     * the announcements never do (§7: low motion does not reduce what is spoken).
     */
    private void renderStageChange(ChatViewState state) {
        Stage stage = state.stage;
        if (stage == renderedStage || rows.isEmpty()) return;
        Stage previous = renderedStage;
        renderedStage = stage;
        Row newest = rows.get(state.messages.get(state.messages.size() - 1).messageId);
        if (newest == null) return;
        if (stage == Stage.STABLE || stage == Stage.CANCELED) newest.enterStage(stage == Stage.CANCELED);
        else if (stage == Stage.ANSWERING && previous == Stage.REASONING) newest.enterAnswer();
        int sentence = stage == Stage.REASONING ? R.string.chat_generating_reasoning
                : stage == Stage.ANSWERING || stage == Stage.SENDING ? R.string.chat_generating_answer
                : stage == Stage.CANCELED ? R.string.chat_stopped
                : stage == Stage.FAILED ? R.string.chat_failed : 0;
        if (sentence != 0) announceForAccessibility(getContext().getString(sentence));
    }

    /**
     * T-065: the source card, collapsed to the summary line until the learner opens it.
     *
     * <p>A root branch has no card and a missing upstream row renders as one line fewer - never as
     * a placeholder (PRD §6.2.8 point 7's rule, applied to this list). The card offers no action:
     * the header tap only folds and unfolds, and the history rows are plain text with no listener.
     */
    private void renderSourceCard(ChatViewState state) {
        SourceCard card = state.sourceCard;
        if (card == null || card.summary.isEmpty()) {
            sourceCard.setVisibility(GONE);
            return;
        }
        sourceCard.setVisibility(VISIBLE);
        sourceSummary.setText(getContext().getString(R.string.source_card_summary, card.summary));
        sourceToggle.setText(getContext().getString(state.sourceCardExpanded
                ? R.string.source_card_collapse : R.string.source_card_expand));
        sourceHistory.setVisibility(state.sourceCardExpanded ? VISIBLE : GONE);
        sourceHistory.removeAllViews();
        if (!state.sourceCardExpanded) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (SourceCard.Entry entry : card.history) {
            View row = inflater.inflate(R.layout.item_source_history, sourceHistory, false);
            TextView text = (TextView) row.findViewById(R.id.source_history_text);
            text.setText(getContext().getString(entry.user
                    ? R.string.source_history_question : R.string.source_history_answer,
                    entry.text));
            sourceHistory.addView(row);
        }
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
     * Opens one row's selection card over the passage the platform handles cover, closing whichever
     * card was already up: two menus over two answers at once would be two selections to reason about.
     */
    private void showWordMenu(Row row) {
        dismissPopups();
        if (row != null) {
            wordMenu = row.openWordMenu();
        }
    }

    /**
     * A card must not outlive the selection or the row it was opened from. The fields are cleared
     * before the cards are told to close, so a card's own "I am gone" callback cannot start the
     * dismissal over from the top.
     */
    private void dismissPopups() {
        WordMenuPopup menu = wordMenu;
        wordMenu = null;
        if (menu != null) {
            menu.close();
        }
        ChildBranchPopup children = childrenPopup;
        childrenPopup = null;
        if (children != null) {
            children.close();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissPopups();
        scroll.removeCallbacks(scrollSettled);
        ReadingAnchor anchor;
        if (speaker != null) {
            speaker.leavePage();
        }
        if ((anchor = settledAnchor) != null) {
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
        /**
         * Where the answer's parts are laid out, in document order: prose runs, and tables as real
         * tables (2026-09-28 user decision). The row owns this host instead of a single text view,
         * because a table has to be a view to be aligned.
         */
        private final LinearLayout answerBody;
        private final View caret;
        private final TextView generating;
        private final View failure;
        private final TextView canceled;
        private final Button retry;
        private final ReasoningDisclosure disclosure;
        private final AnswerActionBar actionBar;
        private final LinearLayout suggestions;
        private final View translation;
        private final TextView translationHeader;
        private final TextView translationText;

        private String messageId = "";
        private ChatMessage message;
        private ChatViewState lastState;
        private String renderedAnswerText;
        /**
         * The answer row's own text views, in document order. The first is the layout's {@code
         * chat_answer}; the rest are the runs after each table. Selection, quote and copy all read
         * from this list, and none of it is set here: the row is now a sequence, so which view owns
         * the selection is a runtime answer.
         */
        private final List<TextView> answerViews = new ArrayList<TextView>();
        /** The view this row reads a selection from; the first run, so it is never null. */
        private TextView answer;
        /** The views this row inflated for its tables, so a re-render can release their callbacks. */
        private final List<View> answerTableViews = new ArrayList<View>();
        /** The platform's selection action mode: kept for the handles it owns, emptied of its menu. */
        private ActionMode selectionMode;
        /** This row's own card while one is up; stale references are harmless, refresh() checks. */
        private WordMenuPopup card;

        Row(LayoutInflater inflater) {
            root = inflater.inflate(R.layout.item_chat_message, messageFlow, false);
            questionBlock = root.findViewById(R.id.chat_question_block);
            question = root.findViewById(R.id.chat_question);
            quoteChip = root.findViewById(R.id.chat_quote_chip);
            answerBlock = root.findViewById(R.id.chat_answer_block);
            answer = root.findViewById(R.id.chat_answer);
            answerBody = root.findViewById(R.id.chat_answer_body);
            answer.setTextIsSelectable(true);
            answer.setMovementMethod(LinkMovementMethod.getInstance());
            answer.setCustomSelectionActionModeCallback(new SelectionMenuCallback());
            caret = root.findViewById(R.id.chat_caret);
            generating = root.findViewById(R.id.chat_generating_note);
            failure = root.findViewById(R.id.chat_failure);
            canceled = root.findViewById(R.id.chat_canceled);
            retry = root.findViewById(R.id.chat_retry);
            disclosure = root.findViewById(R.id.chat_reasoning);
            actionBar = root.findViewById(R.id.chat_actions);
            suggestions = root.findViewById(R.id.chat_suggestions);
            translation = root.findViewById(R.id.chat_translation);
            translationHeader = (TextView) root.findViewById(R.id.chat_translation_header);
            translationText = (TextView) root.findViewById(R.id.chat_translation_text);
            disclosure.setListener(this);
            actionBar.setListener(this);
            translationHeader.setOnClickListener(view -> ChatScreen.this.fire(actions -> {
                if (lastState != null && lastState.translationFailed(messageId)) {
                    actions.onTranslatePressed(messageId);
                } else {
                    actions.onTranslationToggled(messageId);
                }
            }));
            retry.setOnClickListener(view -> fire(actions -> actions.onRetryPressed(messageId)));
        }

        void render(ChatViewState state, ChatMessage message, boolean newestRow) {
            messageId = message.messageId;
            this.message = message;
            questionBlock.setVisibility(message.isAi() ? GONE : VISIBLE);
            answerBlock.setVisibility(message.isAi() ? VISIBLE : GONE);
            if (!message.isAi()) {
                renderQuestion(message);
                return;
            }
            // 2026-09-27 user decision: the time lives in the action row now (prototype .ai-time),
            // so it appears with the stable answer instead of on the tag line.
            actionBar.renderTime(message.createdAt);
            disclosure.render(message,
                    state.isReasoningEffectivelyExpanded(message.messageId),
                    state.thinkingEnabled);
            // Matrix 1.2: a tap on a code block copies it, through the same clipboard path a
            // selection copy takes - one write path, one sensitive-clip marking.
            // 2026-09-28: the answer is now a sequence, because a table is a view and not text.
            if (renderedAnswerText == null || !renderedAnswerText.equals(message.text)) {
                renderAnswerBody(message.text);
                renderedAnswerText = message.text;
            }
            renderGenerationSlots(state, message, newestRow);
            renderTranslation(state, message);
            retry.setEnabled(message.canRetry());
            actionBar.setVisibility(message.isStable() ? VISIBLE : GONE);
            actionBar.render(message);
            renderSuggestions(state, message);
        }

        /**
         * T-075: the generation slots of one row (073 §2/§4). Exactly one of them shows at a
         * time - reasoning note, caret, failure bar, stopped mark, action bar - so the row's own
         * content area is the only thing a state change reflows and no neighbouring control's hit
         * box ever moves.
         */
        private void renderGenerationSlots(ChatViewState state, ChatMessage message, boolean newestRow) {
            boolean reasoning = message.isGenerating() && message.phase() == ChatMessage.Phase.REASONING;
            boolean answering = message.isGenerating() && message.phase() == ChatMessage.Phase.ANSWER;
            generating.setText(generatingNote(message, state.thinkingEnabled));
            generating.setVisibility(reasoning ? VISIBLE : GONE);
            caret.setVisibility(answering ? VISIBLE : GONE);
            if (answering && !ChatMotion.reduced(getContext().getContentResolver())
                    && caret.getAnimation() == null) {
                caret.startAnimation(ChatMotion.caretBlink());
            } else if (!answering || ChatMotion.reduced(getContext().getContentResolver())) {
                caret.clearAnimation();
            }
            boolean stopped = newestRow && state.stage == Stage.CANCELED;
            failure.setVisibility(message.isFailed() && !stopped ? VISIBLE : GONE);
            canceled.setVisibility(stopped && message.isFailed() ? VISIBLE : GONE);
        }

        private void renderTranslation(ChatViewState state, ChatMessage message) {
            boolean visible = state.translationVisible(message.messageId);
            translation.setVisibility(visible ? VISIBLE : GONE);
            if (!visible) {
                translationHeader.setText("");
                translationText.setText("");
                return;
            }
            if (state.isTranslating(message.messageId)) {
                translationHeader.setText(R.string.chat_translation_running);
                translationText.setVisibility(GONE);
                return;
            }
            if (state.translationFailed(message.messageId)) {
                translationHeader.setText(R.string.chat_translation_failed);
                translationText.setVisibility(GONE);
                return;
            }
            boolean expanded = state.translationExpanded(message.messageId);
            translationHeader.setText(getContext().getString(
                    expanded ? R.string.chat_translation_collapse : R.string.chat_translation_expand));
            translationText.setVisibility(expanded ? VISIBLE : GONE);
            translationText.setText(state.translationText(message.messageId));
        }

        /** The one-shot entry fade a stage transition asked for; static under low motion. */
        void enterStage(boolean stopped) { fade(stopped ? canceled : actionBar, ChatMotion.QUICK_MS); }

        /** 073 §4.1: the first answer block's fade-in as the stream starts answering. */
        void enterAnswer() {
            if (!ChatMotion.reduced(getContext().getContentResolver())) fade(answer, ChatMotion.ENTER_MS);
        }

        private void fade(View view, long durationMs) {
            if (ChatMotion.reduced(getContext().getContentResolver())) { view.setAlpha(1f); return; }
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(durationMs).start();
        }

        /**
         * Draws the answer as prose runs and table blocks (2026-09-28 user decision: a table is a
         * view, not text).
         *
         * <p>The first prose run reuses the layout's own {@code chat_answer} view rather than
         * inflating a copy, so the view a row starts with is the view it keeps when the answer has
         * no table - and so one fixed view still exists under the id, which is what the layout
         * tests and the selection callbacks read. Later runs are inflated; only the first is the
         * anchor.
         */
        private void renderAnswerBody(String markdown) {
            List<MarkdownRenderer.Segment> segments = MarkdownRenderer.renderSegments(markdown,
                    code -> fire(actions -> actions.onSelectionCopyPressed(code)));
            answerBody.removeAllViews();
            answerViews.clear();
            answerTableViews.clear();
            for (MarkdownRenderer.Segment segment : segments) {
                if (segment.isTable) {
                    View table = inflateTable(segment);
                    answerTableViews.add(table);
                    answerBody.addView(table);
                } else if (segment.text.length() > 0) {
                    TextView view = answerViews.isEmpty() ? adoptAnswerView() : proseView();
                    view.setText(segment.text, TextView.BufferType.SPANNABLE);
                    answerViews.add(view);
                    answerBody.addView(view);
                }
            }
            if (!answerViews.isEmpty()) {
                answer = answerViews.get(0);
            }
        }

        /** Moves the layout's own answer view into the host, once. */
        private TextView adoptAnswerView() {
            ViewGroup parent = (ViewGroup) answer.getParent();
            if (parent != null) {
                parent.removeView(answer);
            }
            answer.setVisibility(VISIBLE);
            return answer;
        }

        /** A prose run after the first: the same view, minus its fixed id. */
        private TextView proseView() {
            TextView view = (TextView) LayoutInflater.from(getContext())
                    .inflate(R.layout.view_chat_answer_text, answerBody, false);
            view.setTextIsSelectable(true);
            view.setMovementMethod(LinkMovementMethod.getInstance());
            view.setCustomSelectionActionModeCallback(new SelectionMenuCallback());
            return view;
        }

        /**
         * One table, as a real table. Cells are {@link TextView}s in a {@link TableLayout}, so the
         * columns align because layout aligns them - the box-character version could not, because a
         * wrapped row moved every following glyph. The hairline between rows is the table's own
         * background showing through the 1dp gap between two rows of cells.
         */
        private View inflateTable(MarkdownRenderer.Segment table) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            View scroll = inflater.inflate(R.layout.item_chat_table, answerBody, false);
            TableLayout layout = scroll.findViewById(R.id.chat_table);
            int columns = 0;
            for (List<CharSequence> row : table.table) {
                columns = Math.max(columns, row.size());
            }
            for (int r = 0; r < table.table.size(); r++) {
                boolean header = table.hasHeader && r == 0;
                TableRow row = (TableRow) inflater.inflate(R.layout.item_chat_table_row, layout, false);
                List<CharSequence> cells = table.table.get(r);
                for (int c = 0; c < columns; c++) {
                    TextView cell = (TextView) inflater.inflate(header
                            ? R.layout.item_chat_table_header_cell
                            : R.layout.item_chat_table_cell, row, false);
                    if (c < cells.size()) {
                        cell.setText(cells.get(c));
                    }
                    row.addView(cell);
                }
                layout.addView(row);
            }
            // Stretch only when the table fits; past that the scroll view takes over, because a
            // stretched column would squash its text instead of letting the table run wider.
            for (int c = 0; c < columns; c++) {
                layout.setColumnStretchable(c, true);
            }
            return scroll;
        }
        private void renderQuestion(ChatMessage message) {
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

        /**
         * The platform's own selection menu, replaced (PRD 6.2.19 points 1-2, 8).
         *
         * <p>The action mode is kept, because it is what draws the draggable handles over the answer.
         * Its menu is not used: a platform menu can neither be ordered nor labelled nor kept out of its
         * overflow sheet by the app, which is how 新建主题 ended up behind a 更多 sheet while 全选 / 复制
         * / 翻译 held the visible slots. Every row the learner sees comes from {@link WordMenuPopup},
         * which this row draws over the passage those handles cover.
         */
        private final class SelectionMenuCallback implements ActionMode.Callback {

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                selectionMode = mode;
                emptyPlatformToolbar(mode, menu);
                showWordMenu();
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                // The platform re-adds its own items whenever the selection moves; emptying the menu
                // again here is what keeps them away while the learner drags a handle. The card follows
                // that same movement, which is what the platform's own toolbar would have done.
                selectionMode = mode;
                emptyPlatformToolbar(mode, menu);
                if (card != null) {
                    card.refresh();
                }
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                // No item of ours lives in the platform menu; every action is answered by the card.
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                selectionMode = null;
                dismissPopups();
            }
        }

        /**
         * Empties the platform selection menu, twice. Which of the two callbacks - the app's and the
         * text editor's - runs first is not the same on every build, so the queued pass is what makes
         * the outcome the same everywhere: whatever was added after this callback is cleared on the next
         * turn of the main thread, and an empty toolbar is one that hides itself.
         */
        private void emptyPlatformToolbar(ActionMode mode, Menu menu) {
            menu.clear();
            answer.post(() -> {
                if (selectionMode == mode) {
                    mode.getMenu().clear();
                }
            });
        }

        /** Opens this row's card, through the screen that owns the one-card-at-a-time rule. */
        private void showWordMenu() {
            ChatScreen.this.showWordMenu(this);
        }

        /**
         * The run the handles are currently on. The answer is a sequence now, so a selection lives in
         * one of several views and only that one has a range; the first run with one is the answer.
         */
        private TextView selectingRun() {
            for (TextView view : answerViews) {
                if (view.getSelectionStart() >= 0 && view.getSelectionEnd() > view.getSelectionStart()) {
                    return view;
                }
            }
            return answer;
        }

        /** The row's own card over the passage the handles cover; null when the selection is empty. */
        private WordMenuPopup openWordMenu() {
            TextView run = selectingRun();
            TextSelection selection = TextSelection.of(run.getSelectionStart(),
                    run.getSelectionEnd());
            if (selection.isEmpty()) {
                return null;
            }
            WordMenuPopup menu = new WordMenuPopup(run, message, this);
            menu.setOnClosed(this::onWordMenuClosed);
            menu.show(selection);
            card = menu;
            return menu;
        }

        /** The card is gone - a row was chosen, the learner tapped away, or the selection ended. */
        private void onWordMenuClosed() {
            ActionMode mode = selectionMode;
            if (mode != null) {
                mode.finish();
            }
        }

        @Override
        public void onQuoteRequested() {
            onQuotePressed(messageId);
        }

        @Override
        public void onForkRequested() {
            // T-065: the menu's fork carries the selected sentence and the offsets it spans, read from
            // the answer's own text view - the same selection 追问 and 复制 act on. The quote is the
            // offset substring itself, never a trimmed copy, so the stored anchor's quote fills its
            // span exactly. Nothing selected means a whole-answer fork, as before.
            TextView run = selectingRun();
            TextSelection selection = TextSelection.of(run.getSelectionStart(),
                    run.getSelectionEnd());
            String quote = selection.read(run.getText().toString());
            fire(actions -> actions.onForkPressed(messageId, quote, selection.start, selection.end));
        }

        @Override
        public void onCopyRequested() {
            // 复制 in the selection menu copies the passage, not the whole answer (PRD 6.2.19 point 5).
            fire(actions -> actions.onSelectionCopyPressed(selectedText()));
        }

        /**
         * What the answer has selected right now; empty when nothing is. Scans the runs in document
         * order, because a table splits the answer into more than one text view and the selection is
         * always in exactly one of them.
         */
        private String selectedText() {
            for (TextView view : answerViews) {
                int start = view.getSelectionStart();
                int end = view.getSelectionEnd();
                if (start >= 0 && end > start) {
                    return view.getText().subSequence(start, end).toString();
                }
            }
            return "";
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

        /**
         * The answer text's selection menu is the quote entry now that the action row dropped its
         * own button (2026-09-28 user decision), so this is called from the menu, not from a row.
         */
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
            // The bottom action bar forks without a selection: the whole answer stays the anchor.
            fire(actions -> actions.onForkPressed(id, "", 0, 0));
        }

        @Override
        public void onChildrenPressed(String id) {
            fire(actions -> actions.onChildrenPressed(id));
        }

        /** The row's 子分支 entry is the list's anchor, so the list opens where it was asked for. */
        View childrenAnchor() {
            return actionBar;
        }

    }
}
