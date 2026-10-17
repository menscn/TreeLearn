package com.treelearn.app.ui;

import com.treelearn.app.reading.ReadingSession;
import com.treelearn.app.reading.ReadingStore;
import com.treelearn.app.search.web.ChatSearchStep;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.ReadingPosition;
import com.treelearn.app.ui.ChatSpeaker;
import com.treelearn.app.ui.ChatViewState.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executor;

/**
 * The conversation screen's decisions, with no Android type in sight.
 *
 * <p>Seven rules live here, and each one is a requirement rather than a rendering choice:
 *
 * <ul>
 *   <li><strong>The reasoning is a disclosure.</strong> It starts closed, it opens per message, and an
 *       answer with no reasoning has no entry at all (task file: the empty entry is a defect).
 *   <li><strong>Copy carries the whole answer.</strong> The text is composed once by
 *       {@link ChatMessage#copyText} and handed to the sink; the presenter keeps no copy of it and
 *       never logs it, because an answer and its reasoning are the most sensitive content the app
 *       handles.
 *   <li><strong>Retry, reset and version switching are forwarded, never performed.</strong> The
 *       presenter only chooses which of them a position permits; the rows are written by T-010's use
 *       cases behind {@link ChatCommands}. Nothing in the UI layer can move a revision pointer.
 *   <li><strong>The store is the only source of messages.</strong> A send does not append a row here;
 *       it clears the draft and reads again, so a reload can never duplicate or lose a turn.
 *   <li><strong>A failure keeps the draft.</strong> Only an accepted send clears it and the quote, the
 *       same rule the home composer follows, so a refused attempt never costs the learner their words.
 *   <li><strong>The strip belongs to the latest stable answer.</strong> It is attached only when the
 *       read's strip names the version that answer displays, which is what hides it the moment a
 *       follow-up is asked or a version is switched (BASE-16).
 *   <li><strong>The reading position is the session's, not the screen's.</strong> Where the learner
 *       was and whether the unread badge may clear are {@link ReadingSession}'s two rules; this class
 *       only chooses when to ask - on the first layout after a read, whenever the viewport settles,
 *       and on leaving the branch.
 *   <li><strong>A refused reading write is told, not thrown.</strong> The store's reading columns are
 *       fail-closed: a write that did not land throws instead of answering success. Both background
 *       writes are caught here and answered with the notice the brief promises, because an exception
 *       on the background executor reaches the process handler and ends the app rather than telling
 *       anyone. The failed write is simply not made: the next visit tries again.
 * </ul>
 *
 * <p>Every store read and every command runs on the caller-supplied background executor; every state
 * is handed to the sink on the UI thread. Transitions are applied to the <em>current</em> state when
 * they arrive rather than to a copy captured before the read, so a draft typed while the branch was
 * loading is not overwritten by the load result and an open reasoning block stays open across a read.
 */
public final class ChatPresenter implements ChatActions {

    private final ChatSink sink;
    private final UiThread ui;
    private final Executor background;
    private final KeyVault key;
    private final ChatData data;
    private final ChatCommands commands;
    private final SettingsStore settings;
    private final CopyLabels labels;
    private final String branchId;
    /** The attachment store (T-097); null in tests that do not exercise attachments. */
    private final com.treelearn.app.files.AttachmentStore attachments;
    /** The chat-role assignment source (T-103); null keeps the legacy global-tier behaviour. */
    private final ChatRoleSource chatRoleSource;
    private final ReadingSession session;
    /** T-076: where the composer's work waits out a process rebuild (073 §5). */
    private final ChatDraftStore drafts;
    /** Translates finished answers into the learner's language; null when the feature is not wired. */
    private final Translator translator;
    /** The web-search step; null when search is not wired for this conversation. */
    private final ChatSearchStep search;
    /** Reads answers aloud; null when text-to-speech is not wired. */
    private final ChatSpeaker speaker;

    private ChatViewState state;
    /** The branch the last read carried, or null when it carried none. */
    private Branch readingBranch;
    /**
     * T-065: whether the anchor quote prefill has been attempted; one attempt per branch, so a
     * quote the learner cleared is not forced back by the reads that follow.
     */
    private boolean anchorQuotePrefilled;
    /** The same conversation as stored rows: what the reading rules resolve against. */
    private List<Message> readingRows = Collections.emptyList();
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final Runnable generationChanged = this::onGenerationChanged;
    /** T-075: the learner's stop request, consumed by the terminal read it explains. */
    private boolean stopRequested;
    /** T-075: an accepted send whose rows no read has brought back yet; it reads as sending. */
    private boolean awaitingFirstRow;
    /** T-075: the newest row's id and content sizes, so a growth while detached is one count. */
    private String tailFingerprint = "";

    public ChatPresenter(ChatSink sink, UiThread ui, Executor background, KeyVault key, ChatData data,
            ChatCommands commands, SettingsStore settings, CopyLabels labels, String branchId,
            ReadingStore reading) {
        this(sink, ui, background, key, data, commands, settings, labels, branchId, reading,
                ChatDraftStore.NOOP, null);
    }

    public ChatPresenter(ChatSink sink, UiThread ui, Executor background, KeyVault key, ChatData data,
            ChatCommands commands, SettingsStore settings, CopyLabels labels, String branchId,
            ReadingStore reading, ChatDraftStore drafts) {
        this(sink, ui, background, key, data, commands, settings, labels, branchId, reading, drafts,
                null);
    }

    public ChatPresenter(ChatSink sink, UiThread ui, Executor background, KeyVault key, ChatData data,
            ChatCommands commands, SettingsStore settings, CopyLabels labels, String branchId,
            ReadingStore reading, Translator translator) {
        this(sink, ui, background, key, data, commands, settings, labels, branchId, reading,
                ChatDraftStore.NOOP, translator);
    }

    public ChatPresenter(ChatSink sink, UiThread ui, Executor background, KeyVault key, ChatData data,
            ChatCommands commands, SettingsStore settings, CopyLabels labels, String branchId,
            ReadingStore reading, ChatDraftStore drafts, Translator translator) {
        this(sink, ui, background, key, data, commands, settings, labels, branchId, reading, drafts,
                translator, null);
    }

    /** Where the chat-role assignment comes from (T-103); null keeps the legacy tier path. */
    public interface ChatRoleSource {

        ModelSettingsPresenter.Assignment read();
    }

    public ChatPresenter(ChatSink sink, UiThread ui, Executor background, KeyVault key, ChatData data,
            ChatCommands commands, SettingsStore settings, CopyLabels labels, String branchId,
            ReadingStore reading, ChatRoleSource chatRoleSource) {
        this(sink, ui, background, key, data, commands, settings, labels, branchId, reading, null,
                null, null, null, null, chatRoleSource);
    }

    public ChatPresenter(ChatSink sink, UiThread ui, Executor background, KeyVault key, ChatData data,
            ChatCommands commands, SettingsStore settings, CopyLabels labels, String branchId,
            ReadingStore reading, ChatDraftStore drafts, Translator translator,
            ChatSearchStep search) {
        this(sink, ui, background, key, data, commands, settings, labels, branchId, reading, drafts,
                translator, search, null, null);
    }

    public ChatPresenter(ChatSink sink, UiThread ui, Executor background, KeyVault key, ChatData data,
            ChatCommands commands, SettingsStore settings, CopyLabels labels, String branchId,
            ReadingStore reading, ChatDraftStore drafts, Translator translator, ChatSearchStep search,
            ChatSpeaker speaker) {
        this(sink, ui, background, key, data, commands, settings, labels, branchId, reading, drafts,
                translator, search, speaker, null);
    }

    public ChatPresenter(ChatSink sink, UiThread ui, Executor background, KeyVault key, ChatData data,
            ChatCommands commands, SettingsStore settings, CopyLabels labels, String branchId,
            ReadingStore reading, ChatDraftStore drafts, Translator translator, ChatSearchStep search,
            ChatSpeaker speaker, com.treelearn.app.files.AttachmentStore attachments) {
        this(sink, ui, background, key, data, commands, settings, labels, branchId, reading, drafts,
                translator, search, speaker, attachments, null);
    }

    public ChatPresenter(ChatSink sink, UiThread ui, Executor background, KeyVault key, ChatData data,
            ChatCommands commands, SettingsStore settings, CopyLabels labels, String branchId,
            ReadingStore reading, ChatDraftStore drafts, Translator translator, ChatSearchStep search,
            ChatSpeaker speaker, com.treelearn.app.files.AttachmentStore attachments,
            ChatRoleSource chatRoleSource) {
        this.sink = require(sink, "sink");
        this.ui = require(ui, "ui");
        this.background = require(background, "background");
        this.key = require(key, "key");
        this.data = require(data, "data");
        this.commands = require(commands, "commands");
        this.settings = require(settings, "settings");
        this.labels = require(labels, "labels");
        this.branchId = Guard.id(branchId, "branchId");
        this.drafts = drafts == null ? ChatDraftStore.NOOP : drafts;
        this.translator = translator;
        this.search = search;
        this.speaker = speaker;
        this.attachments = attachments;
        this.chatRoleSource = chatRoleSource;
        this.session = new ReadingSession(require(reading, "reading"));
        this.state = ChatViewState.initial(this.branchId, settings.model(), settings.thinkingEnabled(),
                key.isConfigured()).withSearchEnabled(settings.searchEnabled(), searchLabel());
        data.addGenerationListener(this.branchId, generationChanged);
    }

    private String searchLabel() {
        return search == null ? "" : search.engineLabel();
    }

    /** The last state handed to the sink; the tests read it, the screen does not. */
    public ChatViewState state() {
        return state;
    }

    private void onGenerationChanged() {
        background.execute(this::read);
    }

    /** First load: the reading phase is shown while the branch is read. */
    public void start() {
        start("");
    }

    /** Loads a branch and, when supplied, submits its first question after the branch is ready. */
    public void start(String initialQuestion) {
        final String firstQuestion = initialQuestion == null ? "" : initialQuestion.trim();
        render(restored().withPhase(ChatViewState.Phase.LOADING));
        background.execute(() -> {
            read();
            resolveChatRole();
            if (!firstQuestion.isEmpty()) {
                ui.post(() -> sendInitialQuestion(firstQuestion));
            }
        });
    }

    private void sendInitialQuestion(String question) {
        render(state.withDraft(question));
        send(question, true);
    }

    /**
     * T-103: the chip's model comes from the chat-role assignment, told once per entry. Only a
     * substitution — a selection the learner actually made and the store could no longer honour —
     * lands as the one-time notice; a clean resolution, and the builtin default standing in for a
     * role that was never picked, swap the tier silently. The source is null in the legacy path,
     * which then behaves exactly as before — the settings tier stays and no notice is ever raised.
     */
    private void resolveChatRole() {
        if (chatRoleSource == null) {
            return;
        }
        ModelSettingsPresenter.Assignment assignment;
        try {
            assignment = chatRoleSource.read();
        } catch (RuntimeException unreadable) {
            return;
        }
        if (assignment == null) {
            return;
        }
        HomeViewState.Notice notice = null;
        if (assignment.fallback == ModelSettingsPresenter.Fallback.EMPTY) {
            notice = HomeViewState.Notice.NO_MODELS;
        } else if (assignment.fallback.substituted()) {
            notice = HomeViewState.Notice.CHAT_MODEL_FALLBACK;
        }
        final GenerationModel resolved = modelOf(assignment.modelId);
        final HomeViewState.Notice roleNotice = notice;
        ui.post(() -> {
            ChatViewState next = state;
            if (resolved != null) {
                next = next.withModel(resolved);
            }
            if (roleNotice != null) {
                next = next.withNotice(roleNotice);
            }
            if (next != state) {
                render(next);
            }
        });
    }

    private static GenerationModel modelOf(String modelId) {
        if (modelId == null) {
            return null;
        }
        try {
            return GenerationModel.fromWireId(modelId);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /**
     * T-076: what a process rebuild brings back (073 §5). The stored record's words and quote are
     * put into the composer before the first render; the two preferences are not taken from it -
     * they are global settings the {@code refreshed()} read already answers, so a per-branch copy
     * would be a second source of the same fact.
     */
    private ChatViewState restored() {
        ChatDraftStore.ChatDraft kept = drafts.load(branchId);
        if (kept == null || (kept.text.isEmpty() && kept.quote.isEmpty())) {
            return refreshed();
        }
        return refreshed().withDraft(kept.text).withQuote(kept.quote);
    }

    /**
     * Coming back to the conversation: the key or the composer settings may have changed in settings
     * or on the home screen, and the attempt may have produced content while the screen was away. The
     * current phase is kept so the list does not flash, and the draft and the open blocks are kept.
     */
    public void onResumed() {
        render(refreshed());
        background.execute(this::read);
    }

    /** The vault and the preferences are re-read rather than remembered, so a key added in settings counts. */
    private ChatViewState refreshed() {
        return state.withKeyConfigured(key.isConfigured()).withModel(settings.model())
                .withThinkingEnabled(settings.thinkingEnabled());
    }

    private void read() {
        final ChatData.Snapshot snapshot;
        try {
            snapshot = data.read(branchId);
        } catch (RuntimeException unreadable) {
            // The learner sees one static sentence and nothing else, so the store's own reason has to
            // go somewhere a person can read later - otherwise a broken read is undiagnosable.
            sink.reportUnreadable(branchId, unreadable.getClass().getName() + ": "
                    + unreadable.getMessage());
            ui.post(() -> render(state.withPhase(ChatViewState.Phase.ERROR)));
            return;
        }
        boolean generating = false;
        for (ChatMessage message : snapshot.messages) {
            if (message.isGenerating()) {
                generating = true;
                break;
            }
        }
        if (!generating) {
            polling.set(false);
        }
        ui.post(() -> {
            render(apply(snapshot));
            // T-B: the marks are not view state - they are handed to the screen beside it, the
            // same one-channel-per-fact rule openChildren follows. Every read re-pushes, so a
            // highlight written by a command lands the way one stored long ago does.
            sink.renderAnnotations(snapshot.annotations);
        });
    }

    /**
     * The one place a read becomes a state, including the strip's attachment rule.
     *
     * <p>The strip is kept only when the read names a version that the newest row displays and that row
     * is a stable answer. Once a follow-up has been asked the newest row is a question, once an attempt
     * is running it is an unfinished answer, and once a version has been switched the strip names the
     * version that is no longer displayed - all three hide it without a second rule anywhere.
     */
    private ChatViewState apply(ChatData.Snapshot snapshot) {
        ChatMessage newest = snapshot.messages.isEmpty()
                ? null : snapshot.messages.get(snapshot.messages.size() - 1);
        boolean stripBelongsHere = newest != null && newest.isStable()
                && !snapshot.recommendationsRevisionId.isEmpty()
                && snapshot.recommendationsRevisionId.equals(newest.activeRevisionId);
        readingBranch = snapshot.branch;
        readingRows = snapshot.readingRows;
        ChatViewState next = state.withPhase(ChatViewState.Phase.READY)
                .withBranchTitle(snapshot.branchTitle)
                .withMessages(snapshot.messages)
                .withSourceCard(snapshot.sourceCard)
                // T-076: the archived flag rides the read's branch row (073 §5's S-ARCHIVED).
                .withArchived(snapshot.branch != null && snapshot.branch.isArchived())
                .withRecommendations(
                        stripBelongsHere ? snapshot.answerRecommendations
                                : Collections.<Recommendation>emptyList(),
                        stripBelongsHere ? newest.messageId : "");
        return prefillAnchorQuote(withGenerationState(next, snapshot.messages), snapshot.branch);
    }

    /**
     * T-075: the generation stage and the scroll-follow count, derived once per read in one place.
     *
     * <p>The stage reads the newest row; the missed count advances only while an attempt is
     * streaming, the learner is detached, and the content actually grew - a repaint of the same
     * words is not something the follow-latest button should count.
     */
    private ChatViewState withGenerationState(ChatViewState next, List<ChatMessage> messages) {
        Stage stage = stageOf(messages);
        ChatViewState reattached = next;
        String fingerprint = fingerprintOf(messages);
        boolean streaming = stage == Stage.REASONING || stage == Stage.ANSWERING;
        if (streaming && !next.followLatest && !fingerprint.equals(tailFingerprint)) {
            reattached = next.withFollow(next.followLatest, next.missedCount + 1);
        }
        tailFingerprint = fingerprint;
        awaitingFirstRow = awaitingFirstRow && messages.isEmpty();
        boolean terminal = stage == Stage.STABLE || stage == Stage.FAILED
                || stage == Stage.CANCELED;
        stopRequested = stopRequested && !terminal;
        return reattached.withStage(stage);
    }

    /**
     * The stage the newest row puts the screen in. An empty conversation reads as sending while the
     * accepted send's first row has not landed yet, and as idle once it is known the conversation
     * really is empty.
     */
    private Stage stageOf(List<ChatMessage> messages) {
        ChatMessage newest = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        if (newest == null) { return awaitingFirstRow ? Stage.SENDING : Stage.IDLE; }
        if (newest.isGenerating()) {
            return newest.phase() == ChatMessage.Phase.REASONING ? Stage.REASONING : Stage.ANSWERING;
        }
        if (newest.isFailed()) { return stopRequested ? Stage.CANCELED : Stage.FAILED; }
        return newest.isStable() ? Stage.STABLE : Stage.IDLE;
    }

    /** What "the content grew" means: the newest row's id plus the sizes of its two streams. */
    private static String fingerprintOf(List<ChatMessage> messages) {
        ChatMessage newest = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        return newest == null ? "" : newest.messageId + "#" + newest.text.length()
                + "#" + newest.reasoning.length();
    }

    /**
     * T-065: entering a child branch whose provenance anchors a sentence puts that sentence in the
     * quote row, once. The prefill is a one-shot per presenter - a presenter is built per branch -
     * so a quote the learner cleared afterwards is not forced back on the next read, and a quote
     * the learner already carries is never overwritten.
     */
    private ChatViewState prefillAnchorQuote(ChatViewState next, Branch branch) {
        if (anchorQuotePrefilled) {
            return next;
        }
        anchorQuotePrefilled = true;
        String anchored = anchoredQuote(branch);
        if (!anchored.isEmpty() && next.quote.isEmpty()) {
            return next.withQuote(anchored);
        }
        return next;
    }

    /** The sentence the branch's provenance anchors, or empty for a whole-answer fork. */
    private static String anchoredQuote(Branch branch) {
        if (branch == null || branch.source == null || branch.source.anchor.isWholeAnswer()) {
            return "";
        }
        String quote = branch.source.anchor.quote;
        return quote == null ? "" : quote;
    }

    @Override
    public void onSourceCardToggled() {
        render(state.withSourceCardExpanded(!state.sourceCardExpanded));
    }

    @Override
    public void onDraftChanged(String text) {
        // T-076: every keystroke waits out a rebuild; the quote and the two preferences ride along
        // so the record is always the whole composer as it stands.
        drafts.save(branchId, new ChatDraftStore.ChatDraft(text, state.quote, state.model,
                state.thinkingEnabled));
        ChatViewState next = state.withDraft(text);
        render(state.notice == null ? next : next.withNotice(null));
    }

    /**
     * T-095: the recognizer's final text joins the draft (SPEC-081: 追加进草稿、不直接发送).
     * The append rides the same save-render path as a keystroke (onDraftChanged), so persistence,
     * notice clearing and the composer record stay in exactly one place. A blank recognition -
     * the [OFF] complete-silence case - changes nothing.
     */
    @Override
    public void onSpeechRecognized(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        onDraftChanged(state.draft + text);
    }

    /**
     * T-075: the learner wants the running attempt stopped. The command itself is the composer
     * slice's; here the intent is recorded so the terminal read reports canceled instead of failed -
     * cancel and failure are different sentences (069 E3), and the intent is consumed by the
     * terminal it explains, so a stale stop can never label a later failure as canceled.
     */
    @Override
    public void onFullscreenEditOpened() {
        render(state.withFullscreenEditing(true));
    }

    @Override
    public void onFullscreenEditClosed() {
        render(state.withFullscreenEditing(false));
    }

    @Override
    public void onStopRequested() {
        stopRequested = true;
    }

    @Override
    public void onFollowLatestPressed() {
        render(state.withFollow(true, 0));
    }

    @Override
    public void onFollowLatestPaused() {
        if (state.followLatest) {
            render(state.withFollow(false, state.missedCount));
        }
    }

    @Override
    public void onReasoningToggled(String messageId) {
        ChatMessage message = state.message(messageId);
        if (message == null || (!message.canExpandReasoning()
                && AnswerContentProjection.project(message).reasoning.isEmpty())) {
            return;
        }
        // T-066: the tap flips the effective state - which during a reasoning stream is open by
        // default - and the flip is recorded as an explicit intent that overrides that default.
        render(state.withReasoningExpanded(messageId,
                !state.isReasoningEffectivelyExpanded(messageId)));
    }

    @Override
    public void onCopyPressed(String messageId) {
        ChatMessage message = state.message(messageId);
        if (message == null || !message.canCopy()) {
            return;
        }
        AnswerContentProjection projection = AnswerContentProjection.project(message);
        String copied = projection.answer;
        String reasoning = message.reasoning.isEmpty() ? projection.reasoning : message.reasoning;
        if (state.thinkingEnabled && !reasoning.isEmpty()) {
            copied = labels.reasoning + "\n" + reasoning + "\n\n" + labels.answer + "\n"
                    + projection.answer;
        }
        sink.copyToClipboard(copied);
    }

    @Override
    public void onQuotePressed(String messageId, String anchorText) {
        ChatMessage message = state.message(messageId);
        String anchor = anchorText == null ? "" : anchorText.trim();
        if (message == null || !message.canQuote() || anchor.isEmpty()) {
            return;
        }
        // 2026-09-28: the quote arriving from the selection menu is also what raises the three 问 AI
        // quick queries - the chips are the quote's affordance, so they rise and fall with it.
        render(state.withQuote(anchor).withAskAiChips(true).withNotice(null));
    }

    @Override
    public void onQuoteCleared() {
        render(state.withQuote("").withAskAiChips(false));
    }

    @Override
    public void onSend() {
        send(state.draft.trim(), true);
    }

    /**
     * 2026-09-28: one of the 问 AI quick queries goes out directly. The query is the message text,
     * the quote the menu set stays the cargo, and the learner's own draft travels unchanged - the
     * chip answers the selection, not what they were typing. It rides the one send path on purpose:
     * the same key, archived and missing-key and parsing guards, so a chip can never send what a
     * typed question could not.
     */
    @Override
    public void onAskAiChipPressed(String query) {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty() || state.quote.isEmpty()) {
            return;
        }
        send(text, false);
    }

    /**
     * The one send path: every entry, typed or chip, funnels through here. {@code clearDraft} says
     * whether the composer's own text left with this message - true for a typed send, false for a
     * chip, whose text was never in the draft.
     */
    private void send(final String text, final boolean clearDraft) {
        if (text.isEmpty() || state.sending) {
            return;
        }
        if (!key.isConfigured()) {
            render(state.withNotice(HomeViewState.Notice.KEY_MISSING));
            sink.openSettings();
            return;
        }
        if (state.archived) {
            // T-076: an archived branch is read-only (073 §5); nothing is sent and nothing is lost.
            return;
        }
        final GenerationModel model = state.model;
        final boolean thinking = state.thinkingEnabled;
        final String quote = state.quote;
        // T-097: the attachment rides only once its parse has finished; a parsing chip keeps the
        // composer's send disabled by falling through to nothing (SPEC-082 状态流: 解析中禁止发送).
        ChatViewState.ComposerAttachment attachment = state.composerAttachment;
        final String attachmentId;
        if (attachment != null
                && attachment.state == ChatViewState.ComposerAttachment.ParseState.READY) {
            attachmentId = attachment.attachmentId;
        } else if (attachment != null
                && attachment.state == ChatViewState.ComposerAttachment.ParseState.PARSING) {
            return;
        } else {
            attachmentId = null;
        }
        // T-075: a new turn re-attaches the follow - the learner asked, so they want to see. The
        // chips collapse with every send (2026-09-28: one tap, one query, then they are gone).
        stopRequested = false;
        if (speaker != null) {
            speaker.interrupt();
        }
        render(state.withAskAiChips(false).withSending(true).withStage(Stage.SENDING)
                .withFollow(true, 0));
        // The quote always leaves with an accepted send - it rode this message; only the draft's
        // clearing depends on whose words went out (a chip's query was never in the draft).
        background.execute(() -> {
            ChatCommandResult result = commands.send(branchId, text, quote, attachmentId, model,
                    thinking);
            boolean readBeforePublish = result.isAccepted();
            if (readBeforePublish) {
                read();
            }
            publish(result, false, clearDraft, true, "", true, readBeforePublish);
        });
    }

    /**
     * T-097: one picked file enters the composer. The mutual exclusion with the quote is decided
     * here, once (SPEC-082 拍板 4): attaching clears a pending quote, so a quote the learner set
     * on screen goes with it. Registration and the parse run on the background thread; the chip
     * shows "parsing" until they land.
     */
    @Override
    public void onAttachmentPicked(String displayName, String mimeType, byte[] bytes) {
        if (attachments == null || displayName == null || bytes == null || bytes.length == 0) {
            return;
        }
        render(state.withQuote("")
                .withAskAiChips(false)
                .withComposerAttachment(new ChatViewState.ComposerAttachment(
                        Guard.newId(), displayName, bytes.length,
                        ChatViewState.ComposerAttachment.ParseState.PARSING)));
        background.execute(() -> {
            try {
                String id = attachments.register(displayName, mimeType, bytes,
                        System.currentTimeMillis());
                attachments.finalizeParse(id, System.currentTimeMillis());
                com.treelearn.app.files.AttachmentStore.Parsed parsed = attachments.parseOutcome(id);
                boolean ok = com.treelearn.app.files.AttachmentStore.PARSE_STATE_PARSED.equals(
                        parsed.state);
                ui.post(() -> {
                    if (state.composerAttachment == null) {
                        return;
                    }
                    render(state.withComposerAttachment(
                            new ChatViewState.ComposerAttachment(id, displayName, bytes.length,
                                    ok ? ChatViewState.ComposerAttachment.ParseState.READY
                                            : ChatViewState.ComposerAttachment.ParseState.FAILED)));
                });
            } catch (RuntimeException failed) {
                ui.post(() -> render(state.withComposerAttachment(
                        new ChatViewState.ComposerAttachment(Guard.newId(), displayName,
                                bytes.length, ChatViewState.ComposerAttachment.ParseState.FAILED))));
            }
        });
    }

    /** T-097: removing the chip removes nothing stored — the record survives as unreferenced. */
    @Override
    public void onAttachmentCleared() {
        render(state.withComposerAttachment(null));
    }

    @Override
    public void onRetryPressed(String messageId) {
        position(messageId, true);
    }

    @Override
    public void onResetPressed(String messageId) {
        position(messageId, false);
    }

    @Override
    public void onTranslatePressed(String messageId) {
        ChatMessage message = state.message(messageId);
        if (translator == null || message == null || !message.canTranslate()
                || state.isTranslating(messageId)) {
            return;
        }
        GenerationModel model = state.model;
        render(state.withTranslationStarted(messageId));
        String answer = AnswerContentProjection.project(message).answer;
        background.execute(() -> translator.translate(model, answer,
                (ok, translation) -> ui.post(() -> render(
                        state.withTranslationFinished(messageId, ok ? translation : "")))));
    }

    @Override
    public void onTranslationToggled(String messageId) {
        if (!state.translationVisible(messageId)) {
            return;
        }
        render(state.withTranslationToggled(messageId));
    }

    @Override
    public void onSpeakPressed(String messageId) {
        ChatMessage message = state.message(messageId);
        if (speaker == null || message == null || !message.canSpeak()) {
            return;
        }
        speaker.onSpeakPressed(messageId, AnswerContentProjection.project(message).answer);
    }

    /**
     * Retry and reset share one route: both replace the attempt at the same position, which is exactly
     * why T-010 gives them one shared judgement plus one extra condition each. The legacy inputs are
     * read now, at the moment the learner presses, and not later from whatever the settings say when
     * the command is finally assembled.
     */
    private void position(final String messageId, final boolean retry) {
        final ChatMessage message = state.message(messageId);
        if (message == null || (retry ? !message.canRetry() : !message.canReset())) {
            return;
        }
        final GenerationModel model = state.model;
        final boolean thinking = state.thinkingEnabled;
        final long now = System.currentTimeMillis();
        background.execute(() -> publish(retry
                ? commands.retry(messageId, model, thinking, now)
                : commands.reset(messageId, model, thinking, now), false, false));
    }

    @Override
    public void onForkPressed(String messageId, String quote, int startInclusive, int endExclusive) {
        ChatMessage message = state.message(messageId);
        if (message == null || !message.canFork()) {
            return;
        }
        fork(message.messageId, "", quote == null ? "" : quote, startInclusive, endExclusive);
    }

    @Override
    public void onRecommendationFollowed(String question) {
        if (!state.recommendationsVisible() || !usable(question)) {
            return;
        }
        render(state.withDraft(question.trim()));
        onSend();
    }

    @Override
    public void onRecommendationForked(String question) {
        if (!state.recommendationsVisible() || !usable(question)) {
            return;
        }
        // The suggestion fork asks the question directly and anchors no sentence: the quote and the
        // question are two different forks' cargoes, and they never travel together.
        fork(state.recommendationsMessageId, question.trim(), "", 0, 0, true);
    }

    @Override
    public void onChildrenPressed(String messageId) {
        ChatMessage message = state.message(messageId);
        if (message == null || !message.hasChildren()) {
            render(state.withNotice(HomeViewState.Notice.STORAGE));
            return;
        }
        background.execute(() -> {
            try {
                final List<ChildBranch> children = data.children(branchId, messageId);
                ui.post(() -> {
                    if (children == null || children.isEmpty()) {
                        render(state.withNotice(HomeViewState.Notice.STORAGE));
                    } else {
                        sink.openChildren(messageId, children);
                    }
                });
            } catch (RuntimeException failure) {
                ui.post(() -> render(state.withNotice(HomeViewState.Notice.STORAGE)));
            }
        });
    }

    public void onChildBranchChosen(String childBranchId) {
        openConversationBranch(childBranchId, "");
    }

    /**
     * A passage the learner selected is copied as it stands (PRD 6.2.19 point 5). There is no version
     * to check: the screen offers the item only while a selection exists, and an empty selection is
     * refused here so the clipboard is never written with nothing.
     */
    @Override
    public void onSelectionCopyPressed(String selection) {
        String text = selection == null ? "" : selection.trim();
        if (text.isEmpty()) {
            return;
        }
        sink.copyToClipboard(text);
    }

    @Override
    public void onHighlightPressed(String messageId, int proseIndex, int startInclusive,
            int endInclusive) {
        ChatMessage message = state.message(messageId);
        if (message == null || !message.canQuote() || proseIndex < 0
                || startInclusive < 0 || endInclusive <= startInclusive
                || endInclusive > message.text.length()) {
            return;
        }
        background.execute(() -> publish(commands.applyHighlight(messageId, message.text,
                proseIndex, startInclusive, endInclusive), false, false));
    }

    @Override
    public void onNotePressed(String messageId, int proseIndex, int startInclusive,
            int endInclusive) {
        // The note editor and persistence path belong to T-D. Keep the menu contract wired without
        // inventing a second storage path or silently changing the selected range here.
    }

    @Override
    public void onVersionPressed(String messageId) {
        ChatMessage message = state.message(messageId);
        if (message == null || !message.hasVersionHistory()) {
            return;
        }
        background.execute(() -> {
            final List<AnswerRevision> versions = commands.versions(messageId);
            final List<AnswerRevision> offered = versions == null
                    ? Collections.<AnswerRevision>emptyList() : versions;
            ui.post(() -> sink.openVersions(messageId, offered));
        });
    }

    @Override
    public void onRevisionSelected(String messageId, String revisionId) {
        ChatMessage message = state.message(messageId);
        if (message == null || !message.canSelectRevision(revisionId)) {
            return;
        }
        background.execute(() -> publish(
                commands.selectRevision(messageId, revisionId, System.currentTimeMillis()), false,
                false));
    }

    @Override
    public void onSettingsPressed() {
        sink.openSettings();
    }

    /**
     * The overview entry is navigation and nothing else: the canvas opens on the topic this branch
     * belongs to, centred on this branch, and the conversation keeps the branch it was showing
     * (prototype v2.5: the entry only changes which screen is on top).
     */
    @Override
    public void onOverviewPressed() {
        sink.openOverview(branchId);
    }

    /** The navigation entry: the overview's move, onto the branch list instead of the canvas. */
    @Override
    public void onNavigationPressed() {
        sink.openNavigation(branchId);
    }

    @Override
    public void onModelSelected(GenerationModel model) {
        // T-103: the chip's choice is a session override — it lives in the view state and dies with
        // the presenter. The durable chat-role choice belongs to the settings screen (088), and a
        // second writer of the same fact is exactly how the two surfaces would drift apart.
        render(state.withModel(require(model, "model")));
    }

    @Override
    public void onThinkingToggled(boolean enabled) {
        settings.setThinkingEnabled(enabled);
        render(state.withThinkingEnabled(enabled));
    }

    @Override
    public void onSearchToggled(boolean enabled) {
        if (enabled && search != null && !search.hasCredential()) {
            render(state.withNotice(HomeViewState.Notice.SEARCH_KEY_MISSING));
            sink.openSettings();
            return;
        }
        settings.setSearchEnabled(enabled);
        render(state.withSearchEnabled(enabled, searchLabel()));
    }

    @Override
    public void onSearchOpenSettings() {
        sink.openSettings();
    }

    @Override
    public void onBackPressed() {
        if (readingBranch != null && readingBranch.parentBranchId != null) {
            sink.openBranch(readingBranch.parentBranchId);
            return;
        }
        sink.close();
    }

    private void openConversationBranch(String targetBranchId, String initialQuestion) {
        if (targetBranchId == null || targetBranchId.isEmpty()) {
            return;
        }
        sink.openBranch(targetBranchId, initialQuestion == null ? "" : initialQuestion);
    }

    /**
     * Where the branch opens: the stored position, resolved against the rows that exist right now, and
     * the visit's first viewport report.
     *
     * <p>The height comes from the view because the stored anchor is semantic - pixels inside the
     * list item it names - and only a laid-out list knows how tall that item is. It is a store read
     * like any other, so it runs on the background executor. A stored position whose message is gone
     * comes back as the live end, which is the same place a cleared column leaves it.
     *
     * <p>The visible set rides along because this frame is the only viewport report a conversation
     * that fits on one screen ever makes: nothing scrolls, so nothing settles. It is handed to the
     * same session call the settle path uses rather than judged here, so an answer the learner has
     * not reached still leaves the badge alone.
     */
    @Override
    public void onRowsLaidOut(int itemHeightPx, Set<String> visibleMessageIds) {
        if (readingBranch == null || itemHeightPx <= 0) {
            return;
        }
        final Branch branch = readingBranch;
        final List<Message> rows = readingRows;
        final Set<String> visible = visibleMessageIds == null
                ? Collections.<String>emptySet() : visibleMessageIds;
        background.execute(() -> {
            final ReadingPosition position = session.open(branch, rows, itemHeightPx);
            final boolean advanced = seen(branch, rows, visible);
            ui.post(() -> sink.renderPosition(branchId, position));
            if (advanced) {
                background.execute(this::read);
            }
        });
    }

    /**
     * The position the learner stopped at, written through the session. The anchor is what the screen
     * measured and is never composed here.
     */
    @Override
    public void onLeaving(ReadingAnchor anchor) {
        if (readingBranch == null || anchor == null) {
            return;
        }
        final Branch branch = readingBranch;
        background.execute(() -> remember(branch, anchor));
    }

    /**
     * The unread clear, judged by {@code ViewportSeen} through the session.
     *
     * <p>It advances only when the newest unread answer itself entered the viewport, and a write is
     * owed only when the sequence moved - a scroll that changes nothing writes nothing, which is what
     * keeps a list that is scrolled constantly from writing constantly. When it does move, the badge
     * the branch carries is stale, so the branch is read again; that read is not a loop, because the
     * next viewport event finds nothing left to clear.
     */
    @Override
    public void onViewportChanged(Set<String> visibleMessageIds) {
        updateFollow(visibleMessageIds);
        if (readingBranch == null || visibleMessageIds == null) {
            return;
        }
        final Branch branch = readingBranch;
        final List<Message> rows = readingRows;
        background.execute(() -> {
            if (seen(branch, rows, visibleMessageIds)) {
                background.execute(this::read);
            }
        });
    }

    /**
     * T-075: the follow decision, from the viewport facts the screen already reports (073 §6). The
     * newest row on screen is following; scrolled away is detached - the view belongs to the reader,
     * and nothing here pulls it back. A read while detached counts what grew, so the follow-latest
     * button can name the missed amount.
     */
    private void updateFollow(Set<String> visible) {
        ChatMessage newest = state.messages.isEmpty() ? null
                : state.messages.get(state.messages.size() - 1);
        if (newest == null || visible == null) {
            return;
        }
        boolean near = visible.contains(newest.messageId);
        if (near && (!state.followLatest || state.missedCount > 0)) {
            render(state.withFollow(true, 0));
        } else if (!near && state.followLatest) {
            render(state.withFollow(false, state.missedCount));
        }
    }

    /**
     * The write that records where the learner stopped, with the one failure the store may raise.
     *
     * <p>{@code ReadingPositionDao} and {@code BranchDao} are fail-closed: a write that did not land
     * throws rather than answering success. This runs on the background executor, where an escaping
     * exception reaches the process's default handler and takes the app down, so it is caught here.
     */
    private void remember(Branch branch, ReadingAnchor anchor) {
        try {
            session.remember(branch, anchor);
        } catch (RuntimeException refused) {
            noticeReadingFailure();
        }
    }

    /**
     * The unread write, judged by {@code ViewportSeen} through the session.
     *
     * <p>Answers false when the write the judgement owed was refused, so the caller neither re-reads
     * the badge nor pretends the sequence moved.
     */
    private boolean seen(Branch branch, List<Message> rows, Set<String> visible) {
        try {
            return session.onViewport(branch, rows, visible);
        } catch (RuntimeException refused) {
            noticeReadingFailure();
            return false;
        }
    }

    /** The one place a refused reading write becomes a sentence: the notice the brief promises. */
    private void noticeReadingFailure() {
        ui.post(() -> render(state.withNotice(HomeViewState.Notice.READING_POSITION)));
    }

    /**
     * A refused reading write reported by another layer (for example the screen resolving a stored
     * position). Forwarded here so the one sentence the brief promises is the only path.
     */
    public void notifyReadingPositionRefused() {
        noticeReadingFailure();
    }

    private void fork(final String messageId, final String question, final String quote,
            final int startInclusive, final int endExclusive) {
        fork(messageId, question, quote, startInclusive, endExclusive, false);
    }

    private void fork(final String messageId, final String question, final String quote,
            final int startInclusive, final int endExclusive, final boolean autoSendQuestion) {
        background.execute(() -> publish(
                commands.fork(messageId, question, quote, startInclusive, endExclusive), true,
                false, false, autoSendQuestion ? question : ""));
    }

    /**
     * What every accepted or refused command does next, in one place.
     *
     * <p>An accepted send reads the authoritative store on the background worker before this UI
     * callback clears the draft and dismisses the keyboard, so the message is already in state. A
     * refused command keeps everything the learner had and renders the failure category. When no
     * pre-publish read has run, the follow-up read is unconditional: a refusal can still have changed
     * the branch (a retry refused because an attempt is already running means the screen is about to
     * see that attempt's output).
     */
    private void publish(final ChatCommandResult result, final boolean mayOpenBranch,
            final boolean clearDraft) {
        publish(result, mayOpenBranch, clearDraft, clearDraft);
    }

    /**
     * 2026-09-28: the split a chip send needs. A typed send clears the draft and the quote together
     * (the draft went out as the message); a chip send carries its own query and must keep the
     * learner's unfinished draft, while the quote still leaves - it rode this message and a stale
     * quote row would anchor a question the learner never asked.
     */
    private void publish(final ChatCommandResult result, final boolean mayOpenBranch,
            final boolean clearDraft, final boolean clearQuote) {
        publish(result, mayOpenBranch, clearDraft, clearQuote, "");
    }

    private void publish(final ChatCommandResult result, final boolean mayOpenBranch,
            final boolean clearDraft, final boolean clearQuote, final String initialQuestion) {
        publish(result, mayOpenBranch, clearDraft, clearQuote, initialQuestion, false);
    }

    private void publish(final ChatCommandResult result, final boolean mayOpenBranch,
            final boolean clearDraft, final boolean clearQuote, final String initialQuestion,
            final boolean dismissKeyboard) {
        publish(result, mayOpenBranch, clearDraft, clearQuote, initialQuestion, dismissKeyboard,
                false);
    }

    private void publish(final ChatCommandResult result, final boolean mayOpenBranch,
            final boolean clearDraft, final boolean clearQuote, final String initialQuestion,
            final boolean dismissKeyboard, final boolean readBeforePublish) {
        ui.post(() -> {
            if (result.isAccepted()) {
                ChatViewState next = state.withSending(false).withNotice(result.postNotice());
                if (clearDraft) {
                    render(next.withDraft("").withQuote("").withComposerAttachment(null));
                } else if (clearQuote) {
                    render(next.withQuote("").withComposerAttachment(null));
                } else {
                    render(next);
                }
                if (dismissKeyboard) {
                    sink.dismissKeyboard();
                }
                if (mayOpenBranch && !result.branchId().isEmpty()) {
                    openConversationBranch(result.branchId(), initialQuestion);
                }
                if (clearDraft) {
                    // T-076: the sent turn is the store's now; the kept draft record is dropped.
                    drafts.clear(branchId);
                } else if (clearQuote) {
                    // The chip send keeps the draft, so the record keeps it too - minus the quote,
                    // which left with the message and must not come back on the next read.
                    drafts.save(branchId, new ChatDraftStore.ChatDraft(state.draft, "",
                            state.model, state.thinkingEnabled));
                }
                if (clearDraft || clearQuote) {
                    awaitingFirstRow = true;
                    startGenerationPolling();
                }
            } else {
                render(state.withSending(false).withNotice(result.notice()));
            }
            if (!readBeforePublish) {
                background.execute(this::read);
            }
        });
    }

    /** The coordinator receives stream callbacks off-screen, so the presenter must re-read until terminal. */
    private void startGenerationPolling() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        Thread poller = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 180_000L;
            while (polling.get() && System.currentTimeMillis() < deadline) {
                background.execute(this::read);
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            polling.set(false);
        }, "treelearn-chat-poll");
        poller.setDaemon(true);
        poller.start();
    }

    private static boolean usable(String question) {
        return question != null && !question.trim().isEmpty();
    }

    private void render(ChatViewState next) {
        state = next;
        sink.render(next);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    /**
     * Translates a finished answer into the learner's language. Implemented by the wiring layer so the
     * presenter stays free of any model or network type; the callback reports success and the translated
     * text (or empty on failure) on the background thread it was given.
     */
    public interface Translator {
        void translate(GenerationModel model, String text, Callback done);

        /** The answer to one {@link #translate}: {@code ok} is false when no translation came back. */
        interface Callback {
            void onFinished(boolean ok, String translation);
        }
    }
}
