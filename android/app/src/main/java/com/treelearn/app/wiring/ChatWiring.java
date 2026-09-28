package com.treelearn.app.wiring;

import com.treelearn.ai.prompt.DeepSeekContextEncoder;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.app.artifact.HomeDerivedPort;
import com.treelearn.app.files.AttachmentTextExtractor;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.search.web.ChatSearchStep;
import com.treelearn.app.ui.ChatCommandResult;
import com.treelearn.app.ui.ChatCommands;
import com.treelearn.app.ui.ChatData;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.KeyVault;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.SourceCard;
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
import com.treelearn.data.db.dao.BranchCreation;
import com.treelearn.data.db.dao.ContextSnapshotDao;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * The conversation's real data path: what the chat screen reads, and the five commands it can run.
 *
 * <p>As on the home side, it owns no rule that already has an owner: a retry, a reset and a version switch are
 * {@link com.treelearn.app.generation.AnswerVersions}, a fork is {@link BranchCreation}, the request
 * is {@code DeepSeekRequestAssembler}'s build, and the attempt is {@code GenerationCoordinator}'s
 * admission. What is decided here is what the screen cannot get anywhere else: which status a stored
 * position is in, which question a request's current input is, and which notice a refusal becomes.
 *
 * <p><strong>The strip is a read-back, not a second derivation.</strong> T-011's {@code
 * ANSWER_RECOMMENDATIONS} is ensured on the background thread a read kicks off and then read back
 * here, bound to the newest stable answer's revision. A read therefore never spends a model call of
 * its own, and a strip derived for a revision the screen is no longer showing reads as no strip -
 * which is why the revision is part of the snapshot and not re-derived at the row.
 *
 * <p><strong>What a read cannot show.</strong> An attempt that ended without a stable answer reads
 * as {@code FAILED} with no partial text: the partial content of a finished attempt is not reachable
 * through a query that has a caller today, and inventing one here would be a second read path for
 * the same rows.
 *
 * <p><strong>Failures stay visible.</strong> No key, a refused decision, a refused fork and a
 * refused admission all come back as a {@link ChatCommandResult} the presenter draws. No command
 * reports success it did not get.
 */
public final class ChatWiring implements ChatData, ChatCommands {

    /** T-065: the collapsed source-card line excerpts the source answer to this many characters. */
    private static final int SUMMARY_LIMIT = 120;

    private final ChatStore store;
    private final GenerationPort generation;
    private final KeyVault keyVault;
    private final Clock clock;
    private final PromptBudget budget;
    private final HomeDerivedPort derived;
    private final BooleanSupplier answerSuggestions;
    private final ChatSearchStep search;
    /** The attachment store (T-096): read for the cached text, written for the reference row. */
    private final com.treelearn.app.files.AttachmentStore attachments;
    private final Set<String> derivedInFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Runnable>> generationListeners =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<Runnable>>();
    private final GenerationCoordinator.Observer generationObserver =
            (operationId, branchId, state) -> notifyGenerationListeners(branchId);

    /**
     * Tells one branch's listeners that its rows may have changed.
     *
     * <p>Two events reach this: a stream state change, and the derived strip landing after the
     * answer stabilised. They are one method because they mean one thing to a listener - "re-read,
     * something you draw may differ" - and a second copy would be a second place to forget.
     */
    private void notifyGenerationListeners(String branchId) {
        List<Runnable> listeners = generationListeners.get(branchId);
        if (listeners != null) {
            for (Runnable listener : listeners) {
                listener.run();
            }
        }
    }

    public ChatWiring(ChatStore store, GenerationPort generation, KeyVault keyVault, Clock clock,
            PromptBudget budget) {
        this(store, generation, keyVault, clock, budget, null, () -> true);
    }

    public ChatWiring(ChatStore store, GenerationPort generation, KeyVault keyVault, Clock clock,
            PromptBudget budget, HomeDerivedPort derived, BooleanSupplier answerSuggestions) {
        this(store, generation, keyVault, clock, budget, derived, answerSuggestions, null);
    }

    public ChatWiring(ChatStore store, GenerationPort generation, KeyVault keyVault, Clock clock,
            PromptBudget budget, HomeDerivedPort derived, BooleanSupplier answerSuggestions,
            ChatSearchStep search) {
        this(store, generation, keyVault, clock, budget, derived, answerSuggestions, search, null);
    }

    public ChatWiring(ChatStore store, GenerationPort generation, KeyVault keyVault, Clock clock,
            PromptBudget budget, HomeDerivedPort derived, BooleanSupplier answerSuggestions,
            ChatSearchStep search, com.treelearn.app.files.AttachmentStore attachments) {
        this.store = require(store, "store");
        this.generation = require(generation, "generation");
        this.keyVault = require(keyVault, "keyVault");
        this.clock = require(clock, "clock");
        this.budget = require(budget, "budget");
        this.derived = derived;
        this.answerSuggestions = require(answerSuggestions, "answerSuggestions");
        this.search = search;
        this.attachments = attachments;
        this.generation.addObserver(generationObserver);
    }

    @Override
    public void addGenerationListener(String branchId, Runnable listener) {
        generationListeners.computeIfAbsent(Guard.id(branchId, "branchId"),
                ignored -> new CopyOnWriteArrayList<Runnable>()).add(listener);
    }

    @Override
    public void removeGenerationListener(String branchId, Runnable listener) {
        CopyOnWriteArrayList<Runnable> listeners = generationListeners.get(branchId);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public Snapshot read(String branchId) {
        Guard.id(branchId, "branchId");
        ChatStore.Conversation conversation = store.read(branchId);
        if (derived != null && keyVault.isConfigured() && hasStableAnswer(conversation)
                && derivedInFlight.add(branchId)) {
            final ChatStore.Conversation captured = conversation;
            Thread worker = new Thread(() -> {
                try {
                    derived.ensureConversationArtifacts(captured, GenerationModel.FLASH,
                            this.answerSuggestions.getAsBoolean());
                } finally {
                    derivedInFlight.remove(branchId);
                }
                // The strip is derived after the answer is stable, and by then the generation poll
                // has already stopped - without this the strip would be written and never read,
                // which is exactly the state this wiring shipped in: a complete write path and a
                // read that always answered "none". One nudge per completion, through the listener
                // the presenter already registered, so the next read finds the row.
                notifyGenerationListeners(branchId);
            }, "treelearn-derived-read");
            worker.setDaemon(true);
            worker.start();
        }
        List<ChatMessage> messages = new ArrayList<ChatMessage>(conversation.positions.size());
        for (ChatStore.Position position : conversation.positions) {
            messages.add(view(position));
        }
        // The strip is scoped to the branch, so a read that carries no branch row has nothing to ask
        // for - which is also why the revision is read off the positions here rather than stored
        // beside them: the displayed answer's version is the baseline, and that is a fact about the
        // conversation, not about whether the branch row happened to come along.
        String recommendationsRevisionId = "";
        List<String> answerRecommendations = Collections.emptyList();
        if (conversation.branch != null) {
            for (ChatStore.Position position : conversation.positions) {
                if (!position.message.isUser() && position.activeRevision != null) {
                    recommendationsRevisionId = position.activeRevision.id;
                }
            }
            if (derived != null && !recommendationsRevisionId.isEmpty()) {
                answerRecommendations = derived.answerRecommendations(conversation.branch.id,
                        recommendationsRevisionId);
            }
        }
        return Snapshot.of(conversation.branchTitle, messages, newQuestions(answerRecommendations),
                answerRecommendations.isEmpty() ? "" : recommendationsRevisionId, conversation.branch,
                conversation.messages, sourceCard(conversation.branch));
    }

    /**
     * T-011's stored questions as the screen's own value type.
     *
     * <p>The kind is {@code FOLLOW_UP} rather than a second kind for answer suggestions: {@link
     * Recommendation} classifies what a question <em>is</em> - one the learner did not type - and
     * both the home strip and this one carry exactly that.
     */
    private static List<Recommendation> newQuestions(List<String> questions) {
        if (questions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Recommendation> mapped = new ArrayList<Recommendation>(questions.size());
        for (String question : questions) {
            mapped.add(new Recommendation(question, Recommendation.Kind.FOLLOW_UP));
        }
        return mapped;
    }

    /**
     * T-065: the provenance card of a non-root branch, or null for a root branch.
     *
     * <p>The upstream history comes from the one snapshot read-back path T-064 built:
     * {@code history} with a question ordinal of 0 truncates the branch's own rows to nothing and
     * leaves the birth snapshot's prepended entries, decoded and row-resolved exactly as the request
     * path resolves them. No second decode exists to drift from it. A snapshot that cannot be
     * trusted raises there; the card is a rendering, not a request, so the unresolvable history is
     * simply left out rather than rendered as placeholders - the collapsed line still names where
     * the branch came from, and the request path refuses on its own terms (PRD 6.2.4 exception 2).
     */
    private SourceCard sourceCard(Branch branch) {
        if (branch == null || branch.source == null) {
            return null;
        }
        List<ContextPlan.Entry> upstream;
        try {
            upstream = store.history(branch.id, 0);
        } catch (ChatStore.SnapshotUnreadable unreadable) {
            upstream = Collections.emptyList();
        }
        return new SourceCard(summaryFor(branch.source.anchor, upstream), render(upstream));
    }

    /** The collapsed line: the anchored sentence, or an excerpt of the source answer. */
    private static String summaryFor(BranchAnchor anchor, List<ContextPlan.Entry> upstream) {
        if (!anchor.isWholeAnswer() && anchor.quote != null && !anchor.quote.isEmpty()) {
            return anchor.quote;
        }
        String sourceAnswer = "";
        for (ContextPlan.Entry entry : upstream) {
            if (entry.kind == ContextPlan.EntryKind.STABLE_REVISION) {
                sourceAnswer = entry.answerText;
            }
        }
        return excerpt(sourceAnswer);
    }

    /** Long text is excerpted for the collapsed line; nothing is summarized or paraphrased. */
    private static String excerpt(String text) {
        if (text == null || text.length() <= SUMMARY_LIMIT) {
            return text == null ? "" : text;
        }
        return text.substring(0, SUMMARY_LIMIT) + "…";
    }

    /** The upstream rows as the card renders them: learner messages and answer texts, in order. */
    private static List<SourceCard.Entry> render(List<ContextPlan.Entry> upstream) {
        List<SourceCard.Entry> rows = new ArrayList<SourceCard.Entry>(upstream.size());
        for (ContextPlan.Entry entry : upstream) {
            if (entry.kind == ContextPlan.EntryKind.USER_MESSAGE) {
                rows.add(SourceCard.Entry.user(entry.userText));
            } else if (entry.kind == ContextPlan.EntryKind.STABLE_REVISION) {
                rows.add(SourceCard.Entry.answer(entry.answerText));
            }
        }
        return rows;
    }

    private static boolean hasStableAnswer(ChatStore.Conversation conversation) {
        for (ChatStore.Position position : conversation.positions) {
            if (position.activeRevision != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ChildBranch> children(String branchId, String messageId) {
        Guard.id(branchId, "branchId");
        Guard.id(messageId, "messageId");
        return store.children(branchId, messageId);
    }

    @Override
    public ChatCommandResult send(String branchId, String text, String quote, String attachmentId,
            GenerationModel model, boolean thinkingEnabled) {
        ChatSearchStep.Result grounding;
        Guard.id(branchId, "branchId");
        Guard.nonBlank(text, "text");
        Guard.object(model, "model");
        HomeViewState.Notice missing = missingKey();
        if (missing != null) {
            return ChatCommandResult.refused(missing);
        }
        // T-097: the attachment's cached text rides the current input, ceiling-checked here once.
        // Over the ceiling the send is refused before anything is written — no turn, no reference
        // row — and the notice tells the learner to remove the attachment or pick a smaller file.
        String attachmentText = null;
        if (attachmentId != null && !attachmentId.isEmpty()) {
            if (attachments == null) {
                throw new IllegalStateException("a send with an attachment arrived but no"
                        + " attachment store was wired");
            }
            attachmentText = attachments.textOf(attachmentId);
            if (attachmentText == null
                    || AttachmentTextExtractor.injectable(attachmentText) == null) {
                return ChatCommandResult.refused(HomeViewState.Notice.ATTACHMENT_TOO_LARGE);
            }
        }
        grounding = search == null ? ChatSearchStep.Result.off() : search.run(text.trim());
        if (grounding.status() == ChatSearchStep.Status.NOKEY) {
            return ChatCommandResult.refused(HomeViewState.Notice.SEARCH_KEY_MISSING);
        }
        long now = clock.millis();
        int questionOrdinal = store.nextOrdinal(branchId);
        Message userMessage = Message.user(branchId, questionOrdinal, text.trim(),
                quote == null ? "" : quote.trim(), now);
        Message aiMessage = Message.ai(branchId, questionOrdinal + 1, now);
        GenerationCommand command = pin(branchId, aiMessage.id, model, thinkingEnabled, now);
        // The turn is durable before the attempt is admitted, which is the order the coordinator's
        // own "persist first, then open the call" rule needs. A refused admission therefore leaves a
        // question with an answer position that reads back as FAILED - visible, and retryable - and
        // never a question that silently vanished with its refusal.
        store.appendTurn(userMessage, aiMessage);
        if (attachmentId != null && !attachmentId.isEmpty()) {
            attachments.link(userMessage.id, attachmentId, now);
        }
        // The citation is forwarded from the row that was just written, never judged here: whether a
        // turn has one is the adapter's decision (DeepSeekContextEncoder.currentInput), and it is
        // the same value the screen draws.
        ChatCommandResult submitted = submit(command, questionOrdinal, userMessage.userContent,
                userMessage.quotedText(), grounding.block(), attachmentText);
        if (!submitted.isAccepted()) {
            return submitted;
        }
        // A degraded search (failed/empty) is still a successful turn; the notice travels with it
        // so the presenter can surface what the answer was not grounded on.
        HomeViewState.Notice degradation = degradeOf(grounding.status());
        return degradation == null ? submitted
                : ChatCommandResult.ranWithNotice(submitted.operationId(), degradation);
    }

    private static HomeViewState.Notice degradeOf(ChatSearchStep.Status status) {
        switch (status) {
            case FAILED:
                return HomeViewState.Notice.SEARCH_FAILED;
            case EMPTY:
                return HomeViewState.Notice.SEARCH_EMPTY;
        }
        return null;
    }

    @Override
    public ChatCommandResult retry(String messageId, GenerationModel model, boolean thinkingEnabled,
            long now) {
        return replace(messageId, model, thinkingEnabled, now, false);
    }

    @Override
    public ChatCommandResult reset(String messageId, GenerationModel model, boolean thinkingEnabled,
            long now) {
        return replace(messageId, model, thinkingEnabled, now, true);
    }

    @Override
    public ChatCommandResult selectRevision(String messageId, String revisionId, long now) {
        Guard.id(messageId, "messageId");
        Guard.id(revisionId, "revisionId");
        DomainResult<AnswerRevision> switched = store.selectRevision(messageId, revisionId, now);
        if (switched.isFailure()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        // A version switch starts no attempt: it moves a display pointer and nothing else, so there
        // is no operation to name. Reporting one would be a row id that names no row.
        return ChatCommandResult.succeeded();
    }

    @Override
    public ChatCommandResult fork(String messageId, String question, String quote,
            int startInclusive, int endExclusive) {
        Guard.id(messageId, "messageId");
        // The same guard send and replace answer to (PRD 6.2.3 exception 2): a fork spends store
        // writes and opens a branch the learner could not ask a question in without a key, so the
        // refusal lands before any row is chosen or written.
        HomeViewState.Notice missing = missingKey();
        if (missing != null) {
            return ChatCommandResult.refused(missing);
        }
        Message source = store.position(messageId);
        if (source == null || source.isUser()) {
            // A fork hangs off an answer; a question has no version to fork from.
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        ChatStore.Conversation conversation = store.read(source.branchId);
        ChatStore.Position sourcePosition = find(conversation.positions, messageId);
        Branch parent = store.branch(source.branchId);
        if (sourcePosition == null || sourcePosition.activeRevision == null || parent == null) {
            // Only a position that displays a stable version may be forked, which is the same
            // condition ChatMessage.canFork() shows the learner as an enabled button.
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        // T-065: a fork made from a selected sentence anchors that sentence - quote plus offsets,
        // with the digest derived by the one codec the snapshot already owns, so no second
        // derivation exists to drift. A blank quote is the bottom action bar's whole-answer fork.
        String anchored = quote == null ? "" : quote;
        DomainResult<BranchAnchor> anchor = anchored.isEmpty()
                ? DomainResult.ok(BranchAnchor.wholeAnswer())
                : BranchAnchor.ofRange(startInclusive, endExclusive, anchored,
                        ContextSnapshotCodec.digestOf(anchored));
        if (anchor.isFailure()) {
            // The domain refused the range (an offset span the quote does not fill exactly): the
            // provenance row would be unrestorable, so nothing is written and the refusal is shown.
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        long now = clock.millis();
        // One id has to be chosen before the branch exists, and it is the snapshot's: the branch's id
        // is minted inside Branch.child, while BranchCreation refuses a snapshot row that names any
        // branch other than the one it is handed. So the snapshot's id is pinned here, the provenance
        // pins it, the branch is built in the one creation path, and only then is the payload asked
        // for - from the one factory that derives the digest - and re-read through the one reader that
        // verifies it. Writing the payload and the digest by hand here would be a second
        // implementation of a derivation the codec already owns, and BranchCreation would then be
        // checking a digest nothing had proved.
        List<ContextSnapshot.Source> sources = sourcesThrough(conversation.positions, sourcePosition);
        String snapshotId = Guard.newId();
        DomainResult<BranchSource> provenance = BranchSource.fromOperation(source.branchId, messageId,
                snapshotId, GenerationState.STABLE, sourcePosition.activeRevision.id,
                anchor.value());
        if (provenance.isFailure()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        DomainResult<Branch> fork = Branch.child(parent.topicId, provenance.value(), now);
        if (fork.isFailure()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        Branch child = fork.value();
        ContextSnapshot derived = ContextSnapshot.create(child.id, sources, now);
        DomainResult<ContextSnapshot> pinned = ContextSnapshot.fromStored(snapshotId, child.id,
                derived.schemaVersion, derived.digest, derived.payload, derived.createdAt);
        if (pinned.isFailure()) {
            // The codec refused the payload it had just produced, so this is a build defect rather
            // than something the learner can act on - and a fork whose context cannot be trusted must
            // not be written at all.
            throw new IllegalStateException("the context codec refused its own payload: "
                    + pinned.error());
        }
        ContextSnapshot snapshot = pinned.value();
        BranchCreation.Outcome created = store.createBranch(child, new ContextSnapshotDao.Row(
                snapshot.id, child.id, snapshot.schemaVersion, snapshot.digest, snapshot.payload,
                snapshot.createdAt));
        if (created.isRefused()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        // No attempt is started here. The new branch's first question is the learner's to ask, and a
        // message row written without one would be a turn nobody can answer.
        return ChatCommandResult.openedBranch(child.id);
    }

    @Override
    public List<AnswerRevision> versions(String messageId) {
        Guard.id(messageId, "messageId");
        return store.versions(messageId);
    }

    /** Retry and reset share one route; only the decision they delegate to differs (T-010). */
    private ChatCommandResult replace(String messageId, GenerationModel model, boolean thinkingEnabled,
            long now, boolean reset) {
        Guard.id(messageId, "messageId");
        Guard.object(model, "model");
        HomeViewState.Notice missing = missingKey();
        if (missing != null) {
            return ChatCommandResult.refused(missing);
        }
        DomainResult<GenerationCommand> decided = reset
                ? store.reset(messageId, model, thinkingEnabled, now)
                : store.retry(messageId, model, thinkingEnabled, now);
        if (decided.isFailure()) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        Message position = store.position(messageId);
        if (position == null) {
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        Asked asked = questionBehind(store.read(position.branchId).positions, position);
        if (asked == null) {
            // A position with no question before it is not a conversation turn, so there is nothing
            // a replacement could be assembled from.
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        return submit(decided.value(), asked.ordinal, asked.text, asked.quote);
    }

    /**
     * Assembles and admits one attempt.
     *
     * <p>The history is read here, inside the assembler, and is exactly the stored rows before the
     * question - not a summary the screen assembled. The question itself is the request's current
     * input and is never put in the history as well, which is what would otherwise send it twice. The
     * citation travels inside that same current input (§6.2.19): both routes forward the stored string
     * and the framing is decided once, in the adapter.
     */
    private ChatCommandResult submit(GenerationCommand command, int questionOrdinal,
            final String input, final String quote) {
        return submit(command, questionOrdinal, input, quote, "", null);
    }

    private ChatCommandResult submit(GenerationCommand command, int questionOrdinal,
            final String input, final String quote, final String groundingBlock,
            final String attachmentText) {
        final String branchId = command.branchId;
        RequestHistory requestHistory;
        try {
            requestHistory = historyForRequest(branchId, questionOrdinal, command.model);
        } catch (ChatStore.SnapshotUnreadable broken) {
            // A birth context that cannot be proven intact stops the request rather than being
            // partially believed (PRD 6.2.4 exception 2): the refusal is visible, never a silently
            // shortened history.
            return ChatCommandResult.refused(HomeViewState.Notice.STORAGE);
        }
        GenerationCoordinator.Assembler assembler = forCommand -> DeepSeekRequestAssembler.assemble(
                forCommand.purpose, forCommand.thinkingEnabled,
                DeepSeekContextEncoder.encode(requestHistory.plan),
                groundingBlock.isEmpty() ? DeepSeekContextEncoder.currentInput(input, quote,
                        attachmentText)
                        : groundingBlock + "\n" + DeepSeekContextEncoder.currentInput(input, quote,
                        attachmentText),
                budget);
        GenerationCoordinator.SubmitOutcome outcome = generation.submit(command, assembler);
        if (outcome.isAccepted()) {
            return ChatCommandResult.ran(outcome.operationId);
        }
        return ChatCommandResult.refused(RefusalNotices.of(outcome.refusal, outcome.promptReason,
                requestHistory.compressionApplied));
    }

    /** Keeps the latest ten complete rounds and replaces older rounds with one derived summary. */
    private RequestHistory historyForRequest(String branchId, int questionOrdinal,
            GenerationModel model) {
        List<ContextPlan.Entry> all = store.history(branchId, questionOrdinal);
        int stableCount = 0;
        for (ContextPlan.Entry entry : all) {
            if (entry.kind == ContextPlan.EntryKind.STABLE_REVISION) {
                stableCount++;
            }
        }
        if (stableCount <= 10 || derived == null || !keyVault.isConfigured()) {
            return new RequestHistory(new ContextPlan(all), false);
        }
        int split = all.size();
        int rounds = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).kind == ContextPlan.EntryKind.STABLE_REVISION) {
                rounds++;
                if (rounds == 10) {
                    split = i;
                    break;
                }
            }
        }
        if (split <= 0 || split >= all.size()) {
            return new RequestHistory(new ContextPlan(all), false);
        }
        if (split > 0 && all.get(split).kind == ContextPlan.EntryKind.STABLE_REVISION
                && all.get(split - 1).kind == ContextPlan.EntryKind.USER_MESSAGE) {
            split--;
        }
        String latestRevision = null;
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).kind == ContextPlan.EntryKind.STABLE_REVISION) {
                latestRevision = all.get(i).refId;
                break;
            }
        }
        List<ContextPlan.Entry> older = new ArrayList<ContextPlan.Entry>(all.subList(0, split));
        List<ContextPlan.Entry> recent = new ArrayList<ContextPlan.Entry>(all.subList(split, all.size()));
        ContextPlan compressed = derived.compressHistory(branchId, latestRevision, older, recent, model);
        return compressed == null ? new RequestHistory(new ContextPlan(all), false)
                : new RequestHistory(compressed, true);
    }

    /** Pins the one usage and the one version a conversation position runs under. */
    private static GenerationCommand pin(String branchId, String messageId, GenerationModel model,
            boolean thinkingEnabled, long now) {
        DomainResult<GenerationCommand> pinned = GenerationCommand.create(branchId, messageId,
                AnswerPolicy.PURPOSE, AnswerPolicy.version(), model, thinkingEnabled, now);
        if (pinned.isFailure()) {
            throw new IllegalStateException(
                    "the prompt registry's own version was refused by the domain: " + pinned.error());
        }
        return pinned.value();
    }

    /**
     * One stored position as the screen draws it.
     *
     * <p>A version displayed is {@code STABLE}. No version and an attempt in flight is
     * {@code GENERATING}, and which phase it is in follows from which stream has produced something:
     * an attempt that has only reasoning so far is still reasoning. No version and no attempt is an
     * attempt that ended without a stable answer, which is {@code FAILED} - the status whose whole
     * purpose is to keep a partial answer from being read as the answer.
     */
    private static ChatMessage view(ChatStore.Position position) {
        Message row = position.message;
        if (row.isUser()) {
            return ChatMessage.user(row.id, row.userContent, row.quotedText(), row.createdAt);
        }
        AnswerRevision displayed = position.activeRevision;
        if (displayed != null) {
            return ChatMessage.ai(row.id, displayed.answerContent, displayed.reasoningContent,
                    ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, displayed.id,
                    displayed.revisionNo, position.revisionCount, position.childBranchCount,
                    position.attemptRunning, row.createdAt);
        }
        if (position.attemptRunning) {
            boolean answering = !position.attemptAnswer.isEmpty();
            return ChatMessage.ai(row.id, position.attemptAnswer, position.attemptReasoning,
                    ChatMessage.Status.GENERATING,
                    answering ? ChatMessage.Phase.ANSWER : ChatMessage.Phase.REASONING, "",
                    position.revisionCount, position.childBranchCount, true, row.createdAt);
        }
        return ChatMessage.ai(row.id, "", "", ChatMessage.Status.FAILED, ChatMessage.Phase.ANSWER, "",
                position.revisionCount, position.childBranchCount, false, row.createdAt);
    }

    /**
     * The context a fork is born with: every learner message and every displayed version up to and
     * including the one it forks from, in order. The child branch's snapshot names the rows it was
     * built from, which is what lets it keep showing what it was forked from.
     */
    private static List<ContextSnapshot.Source> sourcesThrough(List<ChatStore.Position> positions,
            ChatStore.Position upto) {
        List<ContextSnapshot.Source> sources = new ArrayList<ContextSnapshot.Source>();
        for (ChatStore.Position position : positions) {
            if (position.message.isUser()) {
                sources.add(ContextSnapshot.Source.userMessage(position.message.id));
            } else if (position.activeRevision != null) {
                sources.add(ContextSnapshot.Source.stableRevision(position.activeRevision.id));
            }
            if (position == upto) {
                break;
            }
        }
        return sources;
    }

    private static ChatStore.Position find(List<ChatStore.Position> positions, String messageId) {
        for (ChatStore.Position position : positions) {
            if (messageId.equals(position.message.id)) {
                return position;
            }
        }
        return null;
    }

    /**
     * The learner's question this position answers, and the ordinal a request's history stops before.
     *
     * <p>Both replacement commands re-answer the same position, so their history is the one the
     * original attempt had: everything before the question, with the question itself as the current
     * input. Reading it from the rows rather than from the screen is what keeps a replacement's
     * context identical to the attempt it replaces.
     *
     * <p>The row also carries the sentence the learner cited (§6.2.19). It travels with the question,
     * so re-answering asks about the same sentence instead of quietly turning into a question about
     * nothing in particular.
     */
    private static Asked questionBehind(List<ChatStore.Position> positions, Message position) {
        ChatStore.Position question = null;
        for (ChatStore.Position candidate : positions) {
            if (candidate.message.ordinal >= position.ordinal) {
                break;
            }
            if (candidate.message.isUser()) {
                question = candidate;
            }
        }
        return question == null ? null
                : new Asked(question.message.userContent, question.message.ordinal,
                        question.message.quotedText());
    }

    private HomeViewState.Notice missingKey() {
        return keyVault.isConfigured() ? null : HomeViewState.Notice.KEY_MISSING;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    /** A question, the citation it asks about, and the ordinal its request's history stops before. */
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

    /** A request's history together with whether the derived service compressed it. */
    private static final class RequestHistory {
        final ContextPlan plan;
        final boolean compressionApplied;

        RequestHistory(ContextPlan plan, boolean compressionApplied) {
            this.plan = plan;
            this.compressionApplied = compressionApplied;
        }
    }
}
