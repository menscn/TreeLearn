/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.Message
 *  com.treelearn.core.domain.ReadingAnchor
 *  com.treelearn.core.domain.ReadingPosition
 */
package com.treelearn.app.ui;

import com.treelearn.app.reading.ReadingSession;
import com.treelearn.app.reading.ReadingStore;
import com.treelearn.app.ui.ChatActions;
import com.treelearn.app.ui.ChatCommandResult;
import com.treelearn.app.ui.ChatCommands;
import com.treelearn.app.ui.ChatData;
import com.treelearn.app.ui.ChatDraftStore;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChatSink;
import com.treelearn.app.ui.ChatViewState;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.CopyLabels;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.KeyVault;
import com.treelearn.app.ui.SettingsStore;
import com.treelearn.app.ui.UiThread;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.ReadingPosition;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatPresenter
implements ChatActions {
    private final ChatSink sink;
    private final UiThread ui;
    private final Executor background;
    private final KeyVault key;
    private final ChatData data;
    private final ChatCommands commands;
    private final SettingsStore settings;
    private final CopyLabels labels;
    private final String branchId;
    private final ReadingSession session;
    private final ChatDraftStore drafts;
    private ChatViewState state;
    private Branch readingBranch;
    private boolean anchorQuotePrefilled;
    private List<Message> readingRows = Collections.emptyList();
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final Runnable generationChanged = this::onGenerationChanged;
    private boolean stopRequested;
    private boolean awaitingFirstRow;
    private String tailFingerprint = "";

    public ChatPresenter(ChatSink sink, UiThread ui, Executor background, KeyVault key, ChatData data, ChatCommands commands, SettingsStore settings, CopyLabels labels, String branchId, ReadingStore reading) {
        this(sink, ui, background, key, data, commands, settings, labels, branchId, reading, ChatDraftStore.NOOP);
    }

    public ChatPresenter(ChatSink sink, UiThread ui, Executor background, KeyVault key, ChatData data, ChatCommands commands, SettingsStore settings, CopyLabels labels, String branchId, ReadingStore reading, ChatDraftStore drafts) {
        this.sink = ChatPresenter.require(sink, "sink");
        this.ui = ChatPresenter.require(ui, "ui");
        this.background = ChatPresenter.require(background, "background");
        this.key = ChatPresenter.require(key, "key");
        this.data = ChatPresenter.require(data, "data");
        this.commands = ChatPresenter.require(commands, "commands");
        this.settings = ChatPresenter.require(settings, "settings");
        this.labels = ChatPresenter.require(labels, "labels");
        this.branchId = Guard.id((String)branchId, (String)"branchId");
        this.drafts = drafts == null ? ChatDraftStore.NOOP : drafts;
        this.session = new ReadingSession(ChatPresenter.require(reading, "reading"));
        this.state = ChatViewState.initial(this.branchId, settings.model(), settings.thinkingEnabled(), key.isConfigured());
        data.addGenerationListener(this.branchId, this.generationChanged);
    }

    public ChatViewState state() {
        return this.state;
    }

    private void onGenerationChanged() {
        this.background.execute(this::read);
    }

    public void start() {
        this.render(this.restored().withPhase(ChatViewState.Phase.LOADING));
        this.background.execute(this::read);
    }

    private ChatViewState restored() {
        ChatDraftStore.ChatDraft kept = this.drafts.load(this.branchId);
        if (kept == null || kept.text.isEmpty() && kept.quote.isEmpty()) {
            return this.refreshed();
        }
        return this.refreshed().withDraft(kept.text).withQuote(kept.quote);
    }

    public void onResumed() {
        this.render(this.refreshed());
        this.background.execute(this::read);
    }

    private ChatViewState refreshed() {
        return this.state.withKeyConfigured(this.key.isConfigured()).withModel(this.settings.model()).withThinkingEnabled(this.settings.thinkingEnabled());
    }

    private void read() {
        ChatData.Snapshot snapshot;
        try {
            snapshot = this.data.read(this.branchId);
        }
        catch (RuntimeException unreadable) {
            this.ui.post(() -> this.render(this.state.withPhase(ChatViewState.Phase.ERROR)));
            return;
        }
        boolean generating = false;
        for (ChatMessage message : snapshot.messages) {
            if (!message.isGenerating()) continue;
            generating = true;
            break;
        }
        if (!generating) {
            this.polling.set(false);
        }
        this.ui.post(() -> this.render(this.apply(snapshot)));
    }

    private ChatViewState apply(ChatData.Snapshot snapshot) {
        ChatMessage newest = snapshot.messages.isEmpty() ? null : snapshot.messages.get(snapshot.messages.size() - 1);
        boolean stripBelongsHere = newest != null && newest.isStable() && !snapshot.recommendationsRevisionId.isEmpty() && snapshot.recommendationsRevisionId.equals(newest.activeRevisionId);
        this.readingBranch = snapshot.branch;
        this.readingRows = snapshot.readingRows;
        ChatViewState next = this.state.withPhase(ChatViewState.Phase.READY).withBranchTitle(snapshot.branchTitle).withMessages(snapshot.messages).withSourceCard(snapshot.sourceCard).withArchived(snapshot.branch != null && snapshot.branch.isArchived()).withRecommendations(stripBelongsHere ? snapshot.answerRecommendations : Collections.emptyList(), stripBelongsHere ? newest.messageId : "");
        return this.prefillAnchorQuote(this.withGenerationState(next, snapshot.messages), snapshot.branch);
    }

    private ChatViewState withGenerationState(ChatViewState next, List<ChatMessage> messages) {
        boolean streaming;
        ChatViewState.Stage stage = this.stageOf(messages);
        ChatViewState reattached = next;
        String fingerprint = ChatPresenter.fingerprintOf(messages);
        boolean bl = streaming = stage == ChatViewState.Stage.REASONING || stage == ChatViewState.Stage.ANSWERING;
        if (streaming && !next.followLatest && !fingerprint.equals(this.tailFingerprint)) {
            reattached = next.withFollow(next.followLatest, next.missedCount + 1);
        }
        this.tailFingerprint = fingerprint;
        this.awaitingFirstRow = this.awaitingFirstRow && messages.isEmpty();
        boolean terminal = stage == ChatViewState.Stage.STABLE || stage == ChatViewState.Stage.FAILED || stage == ChatViewState.Stage.CANCELED;
        this.stopRequested = this.stopRequested && !terminal;
        return reattached.withStage(stage);
    }

    private ChatViewState.Stage stageOf(List<ChatMessage> messages) {
        ChatMessage newest;
        ChatMessage chatMessage = newest = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        if (newest == null) {
            return this.awaitingFirstRow ? ChatViewState.Stage.SENDING : ChatViewState.Stage.IDLE;
        }
        if (newest.isGenerating()) {
            return newest.phase() == ChatMessage.Phase.REASONING ? ChatViewState.Stage.REASONING : ChatViewState.Stage.ANSWERING;
        }
        if (newest.isFailed()) {
            return this.stopRequested ? ChatViewState.Stage.CANCELED : ChatViewState.Stage.FAILED;
        }
        return newest.isStable() ? ChatViewState.Stage.STABLE : ChatViewState.Stage.IDLE;
    }

    private static String fingerprintOf(List<ChatMessage> messages) {
        ChatMessage newest = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        return newest == null ? "" : newest.messageId + "#" + newest.text.length() + "#" + newest.reasoning.length();
    }

    private ChatViewState prefillAnchorQuote(ChatViewState next, Branch branch) {
        if (this.anchorQuotePrefilled) {
            return next;
        }
        this.anchorQuotePrefilled = true;
        String anchored = ChatPresenter.anchoredQuote(branch);
        if (!anchored.isEmpty() && next.quote.isEmpty()) {
            return next.withQuote(anchored);
        }
        return next;
    }

    private static String anchoredQuote(Branch branch) {
        if (branch == null || branch.source == null || branch.source.anchor.isWholeAnswer()) {
            return "";
        }
        String quote = branch.source.anchor.quote;
        return quote == null ? "" : quote;
    }

    @Override
    public void onSourceCardToggled() {
        this.render(this.state.withSourceCardExpanded(!this.state.sourceCardExpanded));
    }

    @Override
    public void onDraftChanged(String text) {
        this.drafts.save(this.branchId, new ChatDraftStore.ChatDraft(text, this.state.quote, this.state.model, this.state.thinkingEnabled));
        ChatViewState next = this.state.withDraft(text);
        this.render(this.state.notice == null ? next : next.withNotice(null));
    }

    @Override
    public void onStopRequested() {
        this.stopRequested = true;
    }

    @Override
    public void onFollowLatestPressed() {
        this.render(this.state.withFollow(true, 0));
    }

    @Override
    public void onReasoningToggled(String messageId) {
        ChatMessage message = this.state.message(messageId);
        if (message == null || !message.canExpandReasoning()) {
            return;
        }
        this.render(this.state.withReasoningExpanded(messageId, !this.state.isReasoningEffectivelyExpanded(messageId)));
    }

    @Override
    public void onCopyPressed(String messageId) {
        ChatMessage message = this.state.message(messageId);
        if (message == null || !message.canCopy()) {
            return;
        }
        this.sink.copyToClipboard(message.copyText(this.labels));
    }

    @Override
    public void onQuotePressed(String messageId, String anchorText) {
        String anchor;
        ChatMessage message = this.state.message(messageId);
        String string2 = anchor = anchorText == null ? "" : anchorText.trim();
        if (message == null || !message.canQuote() || anchor.isEmpty()) {
            return;
        }
        this.render(this.state.withQuote(anchor).withNotice(null));
    }

    @Override
    public void onQuoteCleared() {
        this.render(this.state.withQuote(""));
    }

    @Override
    public void onSend() {
        String text = this.state.draft.trim();
        if (text.isEmpty() || this.state.sending) {
            return;
        }
        if (!this.key.isConfigured()) {
            this.render(this.state.withNotice(HomeViewState.Notice.KEY_MISSING));
            this.sink.openSettings();
            return;
        }
        if (this.state.archived) {
            return;
        }
        GenerationModel model = this.state.model;
        boolean thinking = this.state.thinkingEnabled;
        String quote = this.state.quote;
        this.stopRequested = false;
        this.render(this.state.withSending(true).withStage(ChatViewState.Stage.SENDING).withFollow(true, 0));
        this.background.execute(() -> this.publish(this.commands.send(this.branchId, text, quote, model, thinking), false, true));
    }

    @Override
    public void onRetryPressed(String messageId) {
        this.position(messageId, true);
    }

    @Override
    public void onResetPressed(String messageId) {
        this.position(messageId, false);
    }

    private void position(String messageId, boolean retry) {
        ChatMessage message = this.state.message(messageId);
        if (message == null || (retry ? !message.canRetry() : !message.canReset())) {
            return;
        }
        GenerationModel model = this.state.model;
        boolean thinking = this.state.thinkingEnabled;
        long now = System.currentTimeMillis();
        this.background.execute(() -> this.publish(retry ? this.commands.retry(messageId, model, thinking, now) : this.commands.reset(messageId, model, thinking, now), false, false));
    }

    @Override
    public void onForkPressed(String messageId, String quote, int startInclusive, int endExclusive) {
        ChatMessage message = this.state.message(messageId);
        if (message == null || !message.canFork()) {
            return;
        }
        this.fork(message.messageId, "", quote == null ? "" : quote, startInclusive, endExclusive);
    }

    @Override
    public void onRecommendationFollowed(String question) {
        if (!this.state.recommendationsVisible() || !ChatPresenter.usable(question)) {
            return;
        }
        this.render(this.state.withDraft(question.trim()));
        this.onSend();
    }

    @Override
    public void onRecommendationForked(String question) {
        if (!this.state.recommendationsVisible() || !ChatPresenter.usable(question)) {
            return;
        }
        this.fork(this.state.recommendationsMessageId, question.trim(), "", 0, 0);
    }

    @Override
    public void onChildrenPressed(String messageId) {
        ChatMessage message = this.state.message(messageId);
        if (message == null || !message.hasChildren()) {
            return;
        }
        this.background.execute(() -> {
            List<ChildBranch> children = this.data.children(this.branchId, messageId);
            this.ui.post(() -> this.sink.openChildren(this.branchId, messageId, children));
        });
    }

    @Override
    public void onSelectionCopyPressed(String selection) {
        String text;
        String string2 = text = selection == null ? "" : selection.trim();
        if (text.isEmpty()) {
            return;
        }
        this.sink.copyToClipboard(text);
    }

    @Override
    public void onVersionPressed(String messageId) {
        ChatMessage message = this.state.message(messageId);
        if (message == null || !message.hasVersionHistory()) {
            return;
        }
        this.background.execute(() -> {
            List<AnswerRevision> versions = this.commands.versions(messageId);
            List<Object> offered = versions == null ? Collections.emptyList() : versions;
            this.ui.post(() -> this.sink.openVersions(messageId, offered));
        });
    }

    @Override
    public void onRevisionSelected(String messageId, String revisionId) {
        ChatMessage message = this.state.message(messageId);
        if (message == null || !message.canSelectRevision(revisionId)) {
            return;
        }
        this.background.execute(() -> this.publish(this.commands.selectRevision(messageId, revisionId, System.currentTimeMillis()), false, false));
    }

    @Override
    public void onSettingsPressed() {
        this.sink.openSettings();
    }

    @Override
    public void onOverviewPressed() {
        this.sink.openOverview(this.branchId);
    }

    @Override
    public void onNavigationPressed() {
        this.sink.openNavigation(this.branchId);
    }

    @Override
    public void onModelSelected(GenerationModel model) {
        this.settings.setModel(ChatPresenter.require(model, "model"));
        this.render(this.state.withModel(model));
    }

    @Override
    public void onThinkingToggled(boolean enabled) {
        this.settings.setThinkingEnabled(enabled);
        this.render(this.state.withThinkingEnabled(enabled));
    }

    @Override
    public void onBackPressed() {
        this.sink.close();
    }

    @Override
    public void onRowsLaidOut(int itemHeightPx, Set<String> visibleMessageIds) {
        if (this.readingBranch == null || itemHeightPx <= 0) {
            return;
        }
        Branch branch = this.readingBranch;
        List<Message> rows = this.readingRows;
        Set<Object> visible = visibleMessageIds == null ? Collections.emptySet() : visibleMessageIds;
        this.background.execute(() -> {
            ReadingPosition position = this.session.open(branch, rows, itemHeightPx);
            boolean advanced = this.seen(branch, rows, visible);
            this.ui.post(() -> this.sink.renderPosition(this.branchId, position));
            if (advanced) {
                this.background.execute(this::read);
            }
        });
    }

    @Override
    public void onLeaving(ReadingAnchor anchor) {
        if (this.readingBranch == null || anchor == null) {
            return;
        }
        Branch branch = this.readingBranch;
        this.background.execute(() -> this.remember(branch, anchor));
    }

    @Override
    public void onViewportChanged(Set<String> visibleMessageIds) {
        this.updateFollow(visibleMessageIds);
        if (this.readingBranch == null || visibleMessageIds == null) {
            return;
        }
        Branch branch = this.readingBranch;
        List<Message> rows = this.readingRows;
        this.background.execute(() -> {
            if (this.seen(branch, rows, visibleMessageIds)) {
                this.background.execute(this::read);
            }
        });
    }

    private void updateFollow(Set<String> visible) {
        ChatMessage newest;
        ChatMessage chatMessage = newest = this.state.messages.isEmpty() ? null : this.state.messages.get(this.state.messages.size() - 1);
        if (newest == null || visible == null) {
            return;
        }
        boolean near = visible.contains(newest.messageId);
        if (near && (!this.state.followLatest || this.state.missedCount > 0)) {
            this.render(this.state.withFollow(true, 0));
        } else if (!near && this.state.followLatest) {
            this.render(this.state.withFollow(false, this.state.missedCount));
        }
    }

    private void remember(Branch branch, ReadingAnchor anchor) {
        try {
            this.session.remember(branch, anchor);
        }
        catch (RuntimeException refused) {
            this.noticeReadingFailure();
        }
    }

    private boolean seen(Branch branch, List<Message> rows, Set<String> visible) {
        try {
            return this.session.onViewport(branch, rows, visible);
        }
        catch (RuntimeException refused) {
            this.noticeReadingFailure();
            return false;
        }
    }

    private void noticeReadingFailure() {
        this.ui.post(() -> this.render(this.state.withNotice(HomeViewState.Notice.READING_POSITION)));
    }

    private void fork(String messageId, String question, String quote, int startInclusive, int endExclusive) {
        this.background.execute(() -> this.publish(this.commands.fork(messageId, question, quote, startInclusive, endExclusive), true, false));
    }

    private void publish(ChatCommandResult result, boolean mayOpenBranch, boolean clearDraft) {
        this.ui.post(() -> {
            if (result.isAccepted()) {
                ChatViewState next = this.state.withSending(false).withNotice(null);
                this.render(clearDraft ? next.withDraft("").withQuote("") : next);
                if (mayOpenBranch && !result.branchId().isEmpty()) {
                    this.sink.openBranch(result.branchId());
                }
                if (clearDraft) {
                    this.drafts.clear(this.branchId);
                    this.awaitingFirstRow = true;
                    this.startGenerationPolling();
                }
            } else {
                this.render(this.state.withSending(false).withNotice(result.notice()));
            }
            this.background.execute(this::read);
        });
    }

    private void startGenerationPolling() {
        if (!this.polling.compareAndSet(false, true)) {
            return;
        }
        Thread poller = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 180000L;
            while (this.polling.get() && System.currentTimeMillis() < deadline) {
                this.background.execute(this::read);
                try {
                    Thread.sleep(750L);
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            this.polling.set(false);
        }, "treelearn-chat-poll");
        poller.setDaemon(true);
        poller.start();
    }

    private static boolean usable(String question) {
        return question != null && !question.trim().isEmpty();
    }

    private void render(ChatViewState next) {
        this.state = next;
        this.sink.render(next);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}

