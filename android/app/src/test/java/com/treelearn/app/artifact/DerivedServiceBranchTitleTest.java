package com.treelearn.app.artifact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.treelearn.app.wiring.ChatStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.ai.prompt.PromptBudget;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * The branch-title request is built from the conversation the wiring already holds: the source
 * anchor's quote and the branch's first user question. These cases pin who gets a request and
 * who does not, so the lifecycle wiring cannot silently widen or drift from prompt design 6.1.
 */
public class DerivedServiceBranchTitleTest {

    private static final String TOPIC_ID = "11111111-1111-4111-8111-111111111111";
    private static final String PARENT_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String SOURCE_MESSAGE_ID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    private static final String SOURCE_REVISION_ID = "ffffffff-ffff-4fff-8fff-ffffffffffff";
    private static final String SNAPSHOT_ID = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";
    private static final String QUOTE = "the sentence the learner selected";
    private static final String FIRST_QUESTION = "first question in this branch";
    private static final long NOW = 1760000000000L;
    private static final GenerationModel MODEL = GenerationModel.PRO;
    private static final PromptBudget ROOMY = new PromptBudget(200_000, 4_000);

    private static Branch childBranch() {
        DomainResult<BranchAnchor> anchor =
                BranchAnchor.ofRange(0, QUOTE.length(), QUOTE, "hash");
        assertTrue(anchor.isOk());
        BranchSource source = BranchSource.restore(PARENT_ID, SOURCE_MESSAGE_ID,
                SOURCE_REVISION_ID, anchor.value(), SNAPSHOT_ID);
        DomainResult<Branch> created = Branch.child(TOPIC_ID, source, NOW);
        assertTrue(created.isOk());
        return created.value();
    }

    private static ChatStore.Position user(String branchId, String text) {
        return new ChatStore.Position(Message.user(branchId, 0, text, NOW),
                null, 0, 0, false, "", "");
    }

    private static ChatStore.Conversation conversation(Branch branch, ChatStore.Position... positions) {
        return new ChatStore.Conversation("", Arrays.asList(positions), branch,
                Collections.<Message>emptyList());
    }

    @Test
    public void childBranchWithTitleableFactsGetsTheBranchTitleRequest() {
        Branch branch = childBranch();
        ChatStore.Conversation conversation = conversation(branch,
                user(branch.id, FIRST_QUESTION));

        DerivedRequest request = DerivedService.branchTitleRequest(conversation, MODEL, ROOMY);

        assertNotNull("a forked branch with a quote and a first question is titleable", request);
        assertEquals(ArtifactKind.BRANCH_TITLE, request.kind());
        assertEquals(branch.id, request.scopeId());
        assertNull("the title is not revision-bound", request.basedOnRevisionId());
        DerivedRequest expected = DerivedRequest.branchTitle(branch.id, QUOTE, FIRST_QUESTION,
                MODEL, ROOMY);
        assertEquals("the inputs are exactly the quote and the first question",
                expected.inputDigest(), request.inputDigest());
    }

    @Test
    public void rootBranchGetsNoBranchTitleRequest() {
        Branch root = Branch.root(TOPIC_ID, NOW);
        ChatStore.Conversation conversation = conversation(root,
                user(root.id, FIRST_QUESTION));

        assertNull("the root branch's title is the ROOT_TITLE kind, not BRANCH_TITLE",
                DerivedService.branchTitleRequest(conversation, MODEL, ROOMY));
    }

    @Test
    public void conversationWithoutAUserMessageGetsNoRequest() {
        Branch branch = childBranch();
        ChatStore.Conversation conversation = conversation(branch);

        assertNull(DerivedService.branchTitleRequest(conversation, MODEL, ROOMY));
    }

    @Test
    public void conversationWithoutABranchRowGetsNoRequest() {
        ChatStore.Conversation conversation = new ChatStore.Conversation("",
                Collections.<ChatStore.Position>emptyList());

        assertNull(DerivedService.branchTitleRequest(conversation, MODEL, ROOMY));
    }

    private static void assertTrue(boolean condition) {
        org.junit.Assert.assertTrue(condition);
    }
}
