package com.treelearn.app.wiring;

import com.treelearn.ai.prompt.DeepSeekContextEncoder;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.DerivedService;
import com.treelearn.app.artifact.ArtifactPayload;
import com.treelearn.app.artifact.ArtifactPayloadCodec;
import com.treelearn.app.artifact.DerivedResult;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.ui.HomeData;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.KeyVault;
import com.treelearn.app.ui.RootQuestionSubmitter;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.StarterRecommendations;
import com.treelearn.app.ui.SubmitResult;
import com.treelearn.app.ui.TopicCard;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.treelearn.app.artifact.TreeOutline;

/**
 * The home screen's real data path: the stored topics, and the first topic the learner creates.
 *
 * <p>There is no second implementation
 * of anything here: the list is {@link com.treelearn.data.db.dao.TopicDao}'s statement, the first
 * topic's five row groups are
 * {@link com.treelearn.data.db.dao.FirstTopicCreation}'s transaction, the request is
 * {@code DeepSeekRequestAssembler}'s build, and the attempt is {@code GenerationCoordinator}'s
 * admission. What this class adds is the joining: which ids the first topic needs, which question
 * becomes the request's current input, and which notice a refusal becomes.
 *
 * <p><strong>Failures stay visible.</strong> No key, a refused write and a refused admission all
 * come back as a {@link SubmitResult} the screen draws. Nothing is silently swallowed and nothing is
 * faked into a success.
 *
 * <p><strong>Nothing here logs or stores content.</strong> The question is passed to
 * {@code Message.user} and to the assembler, and it goes nowhere else - not into a field that
 * outlives the call, not into an exception message, not into telemetry.
 */
public final class HomeWiring implements HomeData, RootQuestionSubmitter {

    /**
     * The ceiling for a recommendation-shaped payload. A branch summary carries no recommendation
     * array, so this number is never reached; it is the codec's required floor, stated once rather
     * than passed as a bare literal at the call site.
     */
    private static final int SUMMARY_ITEM_CEILING = 8;

    private final HomeStore store;
    private final GenerationPort generation;
    private final KeyVault keyVault;
    private final Clock clock;
    private final PromptBudget budget;
    private final DerivedService derived;

    public HomeWiring(HomeStore store, GenerationPort generation, KeyVault keyVault, Clock clock,
            PromptBudget budget) {
        this(store, generation, keyVault, clock, budget, null);
    }

    public HomeWiring(HomeStore store, GenerationPort generation, KeyVault keyVault, Clock clock,
            PromptBudget budget, DerivedService derived) {
        this.store = require(store, "store");
        this.generation = require(generation, "generation");
        this.keyVault = require(keyVault, "keyVault");
        this.clock = require(clock, "clock");
        this.budget = require(budget, "budget");
        this.derived = derived;
    }

    @Override
    public List<TopicCard> recentTopics() {
        List<HomeStore.Row> rows = store.recentTopics();
        List<TopicCard> cards = new ArrayList<TopicCard>(rows.size());
        for (HomeStore.Row row : rows) {
            cards.add(new TopicCard(row.topicId, row.resumeBranchId, titleOf(row), summaryOf(row),
                    row.branchCount, row.unreadCount, row.lastUsedAt));
        }
        return Collections.unmodifiableList(cards);
    }

    /**
     * The strip is the starter set, and its argument is deliberately unused.
     *
     * <p>PRD 6.2.17 point 3 makes the history-derived set the answer for a learner who already has
     * topics, and that set is T-011's {@code HOME_RECOMMENDATIONS} artifact. No production producer
     * for a derived artifact is wired in this build - {@code DerivedOperation} has no caller yet -
     * so a history-derived strip cannot be produced, and PRD 6.2.17's own fallback ("a failed
     * recommendation is answered with the starter set") is the honest answer for both cases. It never
     * throws and never comes back empty, which is what this port promises.
     */
    @Override
    public Recommendations recommendations(boolean hasTopics) {
        if (hasTopics && derived != null && keyVault.isConfigured()) {
            List<HomeStore.Row> rows = store.recentTopics();
            if (!rows.isEmpty()) {
                HomeStore.Row row = rows.get(0);
                String title = BranchHeading.of(row.rootTitle, row.rootQuestion);
                List<TreeOutline.Entry> outline = Collections.singletonList(
                        TreeOutline.Entry.topic(title, summaryOf(row)));
                List<String> questions = derived.homeRecommendations(row.topicId, outline,
                        GenerationModel.FLASH);
                if (!questions.isEmpty()) {
                    List<Recommendation> items = new ArrayList<Recommendation>();
                    for (String question : questions) {
                        items.add(new Recommendation(question, Recommendation.Kind.FOLLOW_UP));
                    }
                    return Recommendations.fromHistory(items);
                }
            }
        }
        return Recommendations.starterFallback(StarterRecommendations.all());
    }

    @Override
    public void renameTopic(String topicId, String title, long now) {
        store.renameTopic(Guard.id(topicId, "topicId"), Guard.nonBlank(title, "title"), now);
    }

    @Override
    public void deleteTopic(String topicId) {
        store.deleteTopic(Guard.id(topicId, "topicId"));
    }

    @Override
    public SubmitResult submit(String question, GenerationModel model, boolean thinkingEnabled) {
        Guard.nonBlank(question, "question");
        Guard.object(model, "model");
        if (!keyVault.isConfigured()) {
            // The learner is told first (PRD 6.2.13 exception 1). Nothing is written and no call is
            // opened: a stored topic with no attempt behind it would be a half-created tree.
            return SubmitResult.refused(HomeViewState.Notice.KEY_MISSING);
        }
        long now = clock.millis();
        Topic topic = Topic.create(now);
        Branch rootBranch = Branch.root(topic.id, now);
        Message userMessage = Message.user(rootBranch.id, 0, question, "", now);
        Message aiMessage = Message.ai(rootBranch.id, 1, now);

        DomainResult<GenerationCommand> pinned = GenerationCommand.create(rootBranch.id, aiMessage.id,
                AnswerPolicy.PURPOSE, AnswerPolicy.version(), model, thinkingEnabled, now);
        if (pinned.isFailure()) {
            // The version came from the registry itself, so a refusal here means the registry and the
            // domain disagree about who owns it - a build defect, not something to report as input.
            throw new IllegalStateException(
                    "the prompt registry's own version was refused by the domain: " + pinned.error());
        }
        GenerationCommand command = pinned.value();

        // The first topic's history is empty by construction, and the question is the current input.
        final String currentInput = question;
        GenerationCoordinator.Assembler assembler = forCommand -> DeepSeekRequestAssembler.assemble(
                forCommand.purpose, forCommand.thinkingEnabled,
                DeepSeekContextEncoder.encode(new ContextPlan(Collections.<ContextPlan.Entry>emptyList())),
                currentInput, budget);

        GenerationCoordinator.SubmitOutcome outcome = generation.submitFirstTopic(command, assembler,
                (operation, acceptedAt) -> store.createFirstTopic(topic, rootBranch, userMessage,
                        aiMessage, operation).isCreated());

        if (outcome.isAccepted()) {
            if (derived != null) {
                derived.rootTitle(rootBranch, question, model);
            }
            return SubmitResult.accepted(rootBranch.id);
        }
        return SubmitResult.refused(RefusalNotices.of(outcome.refusal));
    }

    /**
     * The card's title: the stored root title when there is one, otherwise the question the topic was
     * created from. The rule itself is {@link BranchHeading}, which the conversation heading reads
     * too - one implementation, so a topic cannot be named one way in the list and another after it
     * is opened.
     *
     * <p>A topic whose root question is missing as well is a corrupt row, and {@code TopicCard}'s own
     * guard is what refuses it - loudly, as the home screen's error phase, rather than by dropping
     * the topic out of the list without saying so.
     */
    private static String titleOf(HomeStore.Row row) {
        return BranchHeading.of(row.rootTitle, row.rootQuestion);
    }

    /**
     * The card's summary, read from the stored {@code BRANCH_SUMMARY} artifact.
     *
     * <p>Empty is an ordinary answer, not a missing field: a topic is readable before any artifact
     * exists, and PRD 6.2.8 point 7 says an absent summary is not drawn as a placeholder. A payload
     * the codec refuses reads as empty for the same reason - the screen has no way to show a
     * half-parsed envelope, and inventing one is what the codec exists to prevent.
     */
    private static String summaryOf(HomeStore.Row row) {
        if (row.summaryPayload == null || row.summaryPayload.isEmpty()) {
            return "";
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(ArtifactKind.BRANCH_SUMMARY,
                row.summaryPayload, SUMMARY_ITEM_CEILING);
        ArtifactPayload payload = parsed.payload();
        return payload == null || payload.summary == null ? "" : payload.summary;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
