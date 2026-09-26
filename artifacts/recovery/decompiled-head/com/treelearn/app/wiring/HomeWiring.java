/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.prompt.DeepSeekContextEncoder
 *  com.treelearn.ai.prompt.DeepSeekRequest
 *  com.treelearn.ai.prompt.DeepSeekRequestAssembler
 *  com.treelearn.ai.prompt.PromptBudget
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.ContextPlan
 *  com.treelearn.core.domain.DomainResult
 *  com.treelearn.core.domain.GenerationCommand
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.Message
 *  com.treelearn.core.domain.PromptPurpose
 *  com.treelearn.core.domain.Topic
 */
package com.treelearn.app.wiring;

import com.treelearn.ai.prompt.DeepSeekContextEncoder;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.ArtifactPayload;
import com.treelearn.app.artifact.ArtifactPayloadCodec;
import com.treelearn.app.artifact.DerivedResult;
import com.treelearn.app.artifact.DerivedService;
import com.treelearn.app.artifact.TreeOutline;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.ui.HomeData;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.KeyVault;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.RootQuestionSubmitter;
import com.treelearn.app.ui.StarterRecommendations;
import com.treelearn.app.ui.SubmitResult;
import com.treelearn.app.ui.TopicCard;
import com.treelearn.app.wiring.AnswerPolicy;
import com.treelearn.app.wiring.BranchHeading;
import com.treelearn.app.wiring.GenerationPort;
import com.treelearn.app.wiring.HomeStore;
import com.treelearn.app.wiring.RefusalNotices;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.Topic;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HomeWiring
implements HomeData,
RootQuestionSubmitter {
    private static final int SUMMARY_ITEM_CEILING = 8;
    private final HomeStore store;
    private final GenerationPort generation;
    private final KeyVault keyVault;
    private final Clock clock;
    private final PromptBudget budget;
    private final DerivedService derived;

    public HomeWiring(HomeStore store, GenerationPort generation, KeyVault keyVault, Clock clock, PromptBudget budget) {
        this(store, generation, keyVault, clock, budget, null);
    }

    public HomeWiring(HomeStore store, GenerationPort generation, KeyVault keyVault, Clock clock, PromptBudget budget, DerivedService derived) {
        this.store = HomeWiring.require(store, "store");
        this.generation = HomeWiring.require(generation, "generation");
        this.keyVault = HomeWiring.require(keyVault, "keyVault");
        this.clock = HomeWiring.require(clock, "clock");
        this.budget = HomeWiring.require(budget, "budget");
        this.derived = derived;
    }

    @Override
    public List<TopicCard> recentTopics() {
        List<HomeStore.Row> rows = this.store.recentTopics();
        ArrayList<TopicCard> cards = new ArrayList<TopicCard>(rows.size());
        for (HomeStore.Row row : rows) {
            cards.add(new TopicCard(row.topicId, row.resumeBranchId, HomeWiring.titleOf(row), HomeWiring.summaryOf(row), row.branchCount, row.unreadCount, row.lastUsedAt));
        }
        return Collections.unmodifiableList(cards);
    }

    @Override
    public HomeData.Recommendations recommendations(boolean hasTopics) {
        List<HomeStore.Row> rows;
        if (hasTopics && this.derived != null && this.keyVault.isConfigured() && !(rows = this.store.recentTopics()).isEmpty()) {
            HomeStore.Row row = rows.get(0);
            String title = BranchHeading.of(row.rootTitle, row.rootQuestion);
            List<TreeOutline.Entry> outline = Collections.singletonList(TreeOutline.Entry.topic(title, HomeWiring.summaryOf(row)));
            List<String> questions = this.derived.homeRecommendations(row.topicId, outline, GenerationModel.FLASH);
            if (!questions.isEmpty()) {
                ArrayList<Recommendation> items = new ArrayList<Recommendation>();
                for (String question : questions) {
                    items.add(new Recommendation(question, Recommendation.Kind.FOLLOW_UP));
                }
                return HomeData.Recommendations.fromHistory(items);
            }
        }
        return HomeData.Recommendations.starterFallback(StarterRecommendations.all());
    }

    @Override
    public void renameTopic(String topicId, String title, long now) {
        this.store.renameTopic(Guard.id((String)topicId, (String)"topicId"), Guard.nonBlank((String)title, (String)"title"), now);
    }

    @Override
    public void deleteTopic(String topicId) {
        this.store.deleteTopic(Guard.id((String)topicId, (String)"topicId"));
    }

    @Override
    public SubmitResult submit(String question, GenerationModel model, boolean thinkingEnabled) {
        String currentInput;
        GenerationCoordinator.Assembler assembler;
        Guard.nonBlank((String)question, (String)"question");
        Guard.object((Object)model, (String)"model");
        if (!this.keyVault.isConfigured()) {
            return SubmitResult.refused(HomeViewState.Notice.KEY_MISSING);
        }
        long now = this.clock.millis();
        Topic topic = Topic.create((long)now);
        Branch rootBranch = Branch.root((String)topic.id, (long)now);
        Message userMessage = Message.user((String)rootBranch.id, (int)0, (String)question, (String)"", (long)now);
        Message aiMessage = Message.ai((String)rootBranch.id, (int)1, (long)now);
        DomainResult pinned = GenerationCommand.create((String)rootBranch.id, (String)aiMessage.id, (PromptPurpose)AnswerPolicy.PURPOSE, (String)AnswerPolicy.version(), (GenerationModel)model, (boolean)thinkingEnabled, (long)now);
        if (pinned.isFailure()) {
            throw new IllegalStateException("the prompt registry's own version was refused by the domain: " + pinned.error());
        }
        GenerationCommand command = (GenerationCommand)pinned.value();
        GenerationCoordinator.SubmitOutcome outcome = this.generation.submitFirstTopic(command, assembler = arg_0 -> this.lambda$submit$0(currentInput = question, arg_0), (operation, acceptedAt) -> this.store.createFirstTopic(topic, rootBranch, userMessage, aiMessage, operation).isCreated());
        if (outcome.isAccepted()) {
            if (this.derived != null) {
                this.derived.rootTitle(rootBranch, question, model);
            }
            return SubmitResult.accepted(rootBranch.id);
        }
        return SubmitResult.refused(RefusalNotices.of(outcome.refusal));
    }

    private static String titleOf(HomeStore.Row row) {
        return BranchHeading.of(row.rootTitle, row.rootQuestion);
    }

    private static String summaryOf(HomeStore.Row row) {
        if (row.summaryPayload == null || row.summaryPayload.isEmpty()) {
            return "";
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(ArtifactKind.BRANCH_SUMMARY, row.summaryPayload, 8);
        ArtifactPayload payload = parsed.payload();
        return payload == null || payload.summary == null ? "" : payload.summary;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    private /* synthetic */ DeepSeekRequest lambda$submit$0(String currentInput, GenerationCommand forCommand) {
        return DeepSeekRequestAssembler.assemble((PromptPurpose)forCommand.purpose, (boolean)forCommand.thinkingEnabled, (List)DeepSeekContextEncoder.encode((ContextPlan)new ContextPlan(Collections.emptyList())), (String)currentInput, (PromptBudget)this.budget);
    }
}

