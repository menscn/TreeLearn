/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.prompt.DeepSeekContextEncoder
 *  com.treelearn.ai.prompt.DeepSeekRequestAssembler
 *  com.treelearn.ai.prompt.PromptBudget
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.BranchAnchor
 *  com.treelearn.core.domain.BranchSource
 *  com.treelearn.core.domain.ContextPlan
 *  com.treelearn.core.domain.ContextPlan$Entry
 *  com.treelearn.core.domain.ContextPlan$EntryKind
 *  com.treelearn.core.domain.ContextSnapshot
 *  com.treelearn.core.domain.ContextSnapshot$Source
 *  com.treelearn.core.domain.ContextSnapshotCodec
 *  com.treelearn.core.domain.DomainResult
 *  com.treelearn.core.domain.GenerationCommand
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.GenerationState
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.Message
 *  com.treelearn.core.domain.PromptPurpose
 *  com.treelearn.data.db.dao.BranchCreation$Outcome
 *  com.treelearn.data.db.dao.ContextSnapshotDao$Row
 */
package com.treelearn.app.wiring;

import com.treelearn.ai.prompt.DeepSeekContextEncoder;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.app.artifact.DerivedService;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.ui.ChatCommandResult;
import com.treelearn.app.ui.ChatCommands;
import com.treelearn.app.ui.ChatData;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.KeyVault;
import com.treelearn.app.ui.SourceCard;
import com.treelearn.app.wiring.AnswerPolicy;
import com.treelearn.app.wiring.ChatStore;
import com.treelearn.app.wiring.GenerationPort;
import com.treelearn.app.wiring.RefusalNotices;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.ContextSnapshotCodec;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.data.db.dao.BranchCreation;
import com.treelearn.data.db.dao.ContextSnapshotDao;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ChatWiring
implements ChatData,
ChatCommands {
    private static final int SUMMARY_LIMIT = 120;
    private final ChatStore store;
    private final GenerationPort generation;
    private final KeyVault keyVault;
    private final Clock clock;
    private final PromptBudget budget;
    private final DerivedService derived;
    private final Set<String> derivedInFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Runnable>> generationListeners = new ConcurrentHashMap();
    private final GenerationCoordinator.Observer generationObserver = (operationId, branchId, state) -> {
        List listeners = this.generationListeners.get(branchId);
        if (listeners != null) {
            for (Runnable listener : listeners) {
                listener.run();
            }
        }
    };

    public ChatWiring(ChatStore store, GenerationPort generation, KeyVault keyVault, Clock clock, PromptBudget budget) {
        this(store, generation, keyVault, clock, budget, null);
    }

    public ChatWiring(ChatStore store, GenerationPort generation, KeyVault keyVault, Clock clock, PromptBudget budget, DerivedService derived) {
        this.store = ChatWiring.require(store, "store");
        this.generation = ChatWiring.require(generation, "generation");
        this.keyVault = ChatWiring.require(keyVault, "keyVault");
        this.clock = ChatWiring.require(clock, "clock");
        this.budget = ChatWiring.require(budget, "budget");
        this.derived = derived;
        this.generation.addObserver(this.generationObserver);
    }

    @Override
    public void addGenerationListener(String branchId, Runnable listener) {
        this.generationListeners.computeIfAbsent(Guard.id((String)branchId, (String)"branchId"), ignored -> new CopyOnWriteArrayList()).add(listener);
    }

    @Override
    public void removeGenerationListener(String branchId, Runnable listener) {
        CopyOnWriteArrayList<Runnable> listeners = this.generationListeners.get(branchId);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public ChatData.Snapshot read(String branchId) {
        Guard.id((String)branchId, (String)"branchId");
        ChatStore.Conversation conversation = this.store.read(branchId);
        if (this.derived != null && this.keyVault.isConfigured() && ChatWiring.hasStableAnswer(conversation) && this.derivedInFlight.add(branchId)) {
            ChatStore.Conversation captured = conversation;
            Thread worker = new Thread(() -> {
                try {
                    this.derived.ensureConversationArtifacts(captured, GenerationModel.FLASH);
                }
                finally {
                    this.derivedInFlight.remove(branchId);
                }
            }, "treelearn-derived-read");
            worker.setDaemon(true);
            worker.start();
        }
        ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>(conversation.positions.size());
        for (ChatStore.Position position : conversation.positions) {
            messages.add(ChatWiring.view(position));
        }
        return ChatData.Snapshot.of(conversation.branchTitle, messages, Collections.emptyList(), "", conversation.branch, conversation.messages, this.sourceCard(conversation.branch));
    }

    private SourceCard sourceCard(Branch branch) {
        List<ContextPlan.Entry> upstream;
        if (branch == null || branch.source == null) {
            return null;
        }
        try {
            upstream = this.store.history(branch.id, 0);
        }
        catch (ChatStore.SnapshotUnreadable unreadable) {
            upstream = Collections.emptyList();
        }
        return new SourceCard(ChatWiring.summaryFor(branch.source.anchor, upstream), ChatWiring.render(upstream));
    }

    private static String summaryFor(BranchAnchor anchor, List<ContextPlan.Entry> upstream) {
        if (!anchor.isWholeAnswer() && anchor.quote != null && !anchor.quote.isEmpty()) {
            return anchor.quote;
        }
        String sourceAnswer = "";
        for (ContextPlan.Entry entry : upstream) {
            if (entry.kind != ContextPlan.EntryKind.STABLE_REVISION) continue;
            sourceAnswer = entry.answerText;
        }
        return ChatWiring.excerpt(sourceAnswer);
    }

    private static String excerpt(String text) {
        if (text == null || text.length() <= 120) {
            return text == null ? "" : text;
        }
        return text.substring(0, 120) + "\u2026";
    }

    private static List<SourceCard.Entry> render(List<ContextPlan.Entry> upstream) {
        ArrayList<SourceCard.Entry> rows = new ArrayList<SourceCard.Entry>(upstream.size());
        for (ContextPlan.Entry entry : upstream) {
            if (entry.kind == ContextPlan.EntryKind.USER_MESSAGE) {
                rows.add(SourceCard.Entry.user(entry.userText));
                continue;
            }
            if (entry.kind != ContextPlan.EntryKind.STABLE_REVISION) continue;
            rows.add(SourceCard.Entry.answer(entry.answerText));
        }
        return rows;
    }

    private static boolean hasStableAnswer(ChatStore.Conversation conversation) {
        for (ChatStore.Position position : conversation.positions) {
            if (position.activeRevision == null) continue;
            return true;
        }
        return false;
    }

    @Override
    public List<ChildBranch> children(String branchId, String messageId) {
        Guard.id((String)branchId, (String)"branchId");
        Guard.id((String)messageId, (String)"messageId");
        return this.store.children(branchId, messageId);
    }

    @Override
    public ChatCommandResult send(String branchId, String text, String quote, GenerationModel model, boolean thinkingEnabled) {
        Guard.id((String)branchId, (String)"branchId");
        Guard.nonBlank((String)text, (String)"text");
        Guard.object((Object)model, (String)"model");
        HomeViewState.Notice missing = this.missingKey();
        if (missing != null) {
            return ChatCommandResult.refused(missing);
        }
        long now = this.clock.millis();
        int questionOrdinal = this.store.nextOrdinal(branchId);
        Message userMessage = Message.user((String)branchId, (int)questionOrdinal, (String)text.trim(), (String)(quote == null ? "" : quote.trim()), (long)now);
        Message aiMessage = Message.ai((String)branchId, (int)(questionOrdinal + 1), (long)now);
        GenerationCommand command = ChatWiring.pin(branchId, aiMessage.id, model, thinkingEnabled, now);
        this.store.appendTurn(userMessage, aiMessage);
        return this.submit(command, questionOrdinal, userMessage.userContent, userMessage.quotedText());
    }

    @Override
    public ChatCommandResult retry(String messageId, GenerationModel model, boolean thinkingEnabled, long now) {
        return this.replace(messageId, model, thinkingEnabled, now, false);
    }

    @Override
    public ChatCommandResult reset(String messageId, GenerationModel model, boolean thinkingEnabled, long now) {
        return this.replace(messageId, model, thinkingEnabled, now, true);
    }

    @Override
    public ChatCommandResult selectRevision(String messageId, String revisionId, long now) {
        Guard.id((String)messageId, (String)"messageId");
        Guard.id((String)revisionId, (String)"revisionId");
        DomainResult<AnswerRevision> switched = this.store.selectRevision(messageId, revisionId, now);
        if (switched.isFailure()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        return ChatCommandResult.succeeded();
    }

    @Override
    public ChatCommandResult fork(String messageId, String question, String quote, int startInclusive, int endExclusive) {
        DomainResult anchor;
        Guard.id((String)messageId, (String)"messageId");
        HomeViewState.Notice missing = this.missingKey();
        if (missing != null) {
            return ChatCommandResult.refused(missing);
        }
        Message source = this.store.position(messageId);
        if (source == null || source.isUser()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        ChatStore.Conversation conversation = this.store.read(source.branchId);
        ChatStore.Position sourcePosition = ChatWiring.find(conversation.positions, messageId);
        Branch parent = this.store.branch(source.branchId);
        if (sourcePosition == null || sourcePosition.activeRevision == null || parent == null) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        String anchored = quote == null ? "" : quote;
        DomainResult domainResult = anchor = anchored.isEmpty() ? DomainResult.ok((Object)BranchAnchor.wholeAnswer()) : BranchAnchor.ofRange((int)startInclusive, (int)endExclusive, (String)anchored, (String)ContextSnapshotCodec.digestOf((String)anchored));
        if (anchor.isFailure()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        long now = this.clock.millis();
        List<ContextSnapshot.Source> sources = ChatWiring.sourcesThrough(conversation.positions, sourcePosition);
        String snapshotId = Guard.newId();
        DomainResult provenance = BranchSource.fromOperation((String)source.branchId, (String)messageId, (String)snapshotId, (GenerationState)GenerationState.STABLE, (String)sourcePosition.activeRevision.id, (BranchAnchor)((BranchAnchor)anchor.value()));
        if (provenance.isFailure()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        DomainResult fork = Branch.child((String)parent.topicId, (BranchSource)((BranchSource)provenance.value()), (long)now);
        if (fork.isFailure()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        Branch child = (Branch)fork.value();
        ContextSnapshot derived = ContextSnapshot.create((String)child.id, sources, (long)now);
        DomainResult pinned = ContextSnapshot.fromStored((String)snapshotId, (String)child.id, (int)derived.schemaVersion, (String)derived.digest, (String)derived.payload, (long)derived.createdAt);
        if (pinned.isFailure()) {
            throw new IllegalStateException("the context codec refused its own payload: " + pinned.error());
        }
        ContextSnapshot snapshot = (ContextSnapshot)pinned.value();
        BranchCreation.Outcome created = this.store.createBranch(child, new ContextSnapshotDao.Row(snapshot.id, child.id, snapshot.schemaVersion, snapshot.digest, snapshot.payload, snapshot.createdAt));
        if (created.isRefused()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        return ChatCommandResult.openedBranch(child.id);
    }

    @Override
    public List<AnswerRevision> versions(String messageId) {
        Guard.id((String)messageId, (String)"messageId");
        return this.store.versions(messageId);
    }

    private ChatCommandResult replace(String messageId, GenerationModel model, boolean thinkingEnabled, long now, boolean reset) {
        DomainResult<GenerationCommand> decided;
        Guard.id((String)messageId, (String)"messageId");
        Guard.object((Object)model, (String)"model");
        HomeViewState.Notice missing = this.missingKey();
        if (missing != null) {
            return ChatCommandResult.refused(missing);
        }
        DomainResult<GenerationCommand> domainResult = decided = reset ? this.store.reset(messageId, model, thinkingEnabled, now) : this.store.retry(messageId, model, thinkingEnabled, now);
        if (decided.isFailure()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        Message position = this.store.position(messageId);
        if (position == null) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        Asked asked = ChatWiring.questionBehind(this.store.read((String)position.branchId).positions, position);
        if (asked == null) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        return this.submit((GenerationCommand)decided.value(), asked.ordinal, asked.text, asked.quote);
    }

    private ChatCommandResult submit(GenerationCommand command, int questionOrdinal, String input, String quote) {
        ContextPlan requestHistory;
        String branchId = command.branchId;
        try {
            requestHistory = this.historyForRequest(branchId, questionOrdinal, command.model);
        }
        catch (ChatStore.SnapshotUnreadable broken) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        GenerationCoordinator.Assembler assembler = forCommand -> DeepSeekRequestAssembler.assemble((PromptPurpose)forCommand.purpose, (boolean)forCommand.thinkingEnabled, (List)DeepSeekContextEncoder.encode((ContextPlan)requestHistory), (String)DeepSeekContextEncoder.currentInput((String)input, (String)quote), (PromptBudget)this.budget);
        GenerationCoordinator.SubmitOutcome outcome = this.generation.submit(command, assembler);
        if (outcome.isAccepted()) {
            return ChatCommandResult.ran(outcome.operationId);
        }
        return ChatCommandResult.refused(RefusalNotices.of(outcome.refusal));
    }

    private ContextPlan historyForRequest(String branchId, int questionOrdinal, GenerationModel model) {
        ArrayList<ContextPlan.Entry> recent;
        ArrayList<ContextPlan.Entry> older;
        ContextPlan compressed;
        List<ContextPlan.Entry> all = this.store.history(branchId, questionOrdinal);
        int stableCount = 0;
        for (ContextPlan.Entry entry : all) {
            if (entry.kind != ContextPlan.EntryKind.STABLE_REVISION) continue;
            ++stableCount;
        }
        if (stableCount <= 10 || this.derived == null || !this.keyVault.isConfigured()) {
            return new ContextPlan(all);
        }
        int split = all.size();
        int rounds = 0;
        for (int i = all.size() - 1; i >= 0; --i) {
            if (all.get((int)i).kind != ContextPlan.EntryKind.STABLE_REVISION || ++rounds != 10) continue;
            split = i;
            break;
        }
        if (split <= 0 || split >= all.size()) {
            return new ContextPlan(all);
        }
        if (split > 0 && all.get((int)split).kind == ContextPlan.EntryKind.STABLE_REVISION && all.get((int)(split - 1)).kind == ContextPlan.EntryKind.USER_MESSAGE) {
            --split;
        }
        String latestRevision = null;
        for (int i = all.size() - 1; i >= 0; --i) {
            if (all.get((int)i).kind != ContextPlan.EntryKind.STABLE_REVISION) continue;
            latestRevision = all.get((int)i).refId;
            break;
        }
        return (compressed = this.derived.compressHistory(branchId, latestRevision, older = new ArrayList<ContextPlan.Entry>(all.subList(0, split)), recent = new ArrayList<ContextPlan.Entry>(all.subList(split, all.size())), model)) == null ? new ContextPlan(all) : compressed;
    }

    private static GenerationCommand pin(String branchId, String messageId, GenerationModel model, boolean thinkingEnabled, long now) {
        DomainResult pinned = GenerationCommand.create((String)branchId, (String)messageId, (PromptPurpose)AnswerPolicy.PURPOSE, (String)AnswerPolicy.version(), (GenerationModel)model, (boolean)thinkingEnabled, (long)now);
        if (pinned.isFailure()) {
            throw new IllegalStateException("the prompt registry's own version was refused by the domain: " + pinned.error());
        }
        return (GenerationCommand)pinned.value();
    }

    private static ChatMessage view(ChatStore.Position position) {
        Message row = position.message;
        if (row.isUser()) {
            return ChatMessage.user(row.id, row.userContent, row.quotedText(), row.createdAt);
        }
        AnswerRevision displayed = position.activeRevision;
        if (displayed != null) {
            return ChatMessage.ai(row.id, displayed.answerContent, displayed.reasoningContent, ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, displayed.id, displayed.revisionNo, position.revisionCount, position.childBranchCount, position.attemptRunning, row.createdAt);
        }
        if (position.attemptRunning) {
            boolean answering = !position.attemptAnswer.isEmpty();
            return ChatMessage.ai(row.id, position.attemptAnswer, position.attemptReasoning, ChatMessage.Status.GENERATING, answering ? ChatMessage.Phase.ANSWER : ChatMessage.Phase.REASONING, "", position.revisionCount, position.childBranchCount, true, row.createdAt);
        }
        return ChatMessage.ai(row.id, "", "", ChatMessage.Status.FAILED, ChatMessage.Phase.ANSWER, "", position.revisionCount, position.childBranchCount, false, row.createdAt);
    }

    private static List<ContextSnapshot.Source> sourcesThrough(List<ChatStore.Position> positions, ChatStore.Position upto) {
        ArrayList<ContextSnapshot.Source> sources = new ArrayList<ContextSnapshot.Source>();
        for (ChatStore.Position position : positions) {
            if (position.message.isUser()) {
                sources.add(ContextSnapshot.Source.userMessage((String)position.message.id));
            } else if (position.activeRevision != null) {
                sources.add(ContextSnapshot.Source.stableRevision((String)position.activeRevision.id));
            }
            if (position != upto) continue;
            break;
        }
        return sources;
    }

    private static ChatStore.Position find(List<ChatStore.Position> positions, String messageId) {
        for (ChatStore.Position position : positions) {
            if (!messageId.equals(position.message.id)) continue;
            return position;
        }
        return null;
    }

    private static Asked questionBehind(List<ChatStore.Position> positions, Message position) {
        ChatStore.Position question = null;
        for (ChatStore.Position candidate : positions) {
            if (candidate.message.ordinal >= position.ordinal) break;
            if (!candidate.message.isUser()) continue;
            question = candidate;
        }
        return question == null ? null : new Asked(question.message.userContent, question.message.ordinal, question.message.quotedText());
    }

    private HomeViewState.Notice missingKey() {
        return this.keyVault.isConfigured() ? null : HomeViewState.Notice.KEY_MISSING;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    private static final class Asked {
        final String text;
        final int ordinal;
        final String quote;

        Asked(String text, int ordinal, String quote) {
            this.text = text;
            this.ordinal = ordinal;
            this.quote = quote;
        }
    }
}

