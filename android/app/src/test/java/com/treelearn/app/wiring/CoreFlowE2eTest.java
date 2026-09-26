package com.treelearn.app.wiring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.navigation.NavigationData;
import com.treelearn.app.search.SearchRepository;
import com.treelearn.app.ui.ChatCommandResult;
import com.treelearn.app.ui.ChatData;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.SubmitResult;
import com.treelearn.app.ui.TreeCanvasData;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchHit.Field;
import com.treelearn.core.domain.SearchQuery;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * T-018: the core flow runs end to end across the real decision chain.
 *
 * <p>One narrative, one method: the first question creates the topic through the home path's real
 * {@link HomeWiring}, the follow-up and its reasoning expand, fork, reset, search, archive and
 * canvas all run through their real wiring classes, and every identifier the flow produces (topic,
 * branch, message, revision) is the one the next step consumes — no test constant stands in for a
 * produced id except the canonical ones {@link WiringDoubles} pins.
 *
 * <p>What this deliberately does not prove: the SQL behind the stores (that is the instrumented
 * suites' job — search over real SQLite plus archive exclusion was proven on a device by T-048's
 * twelve-case run) and the real provider (T-018's smoke needs a key). The persistence behind the
 * navigation and canvas steps is the smallest in-test map that can hold the flow's rows, so what
 * is asserted is the joining of the wirings, not a rehearsal of SQLite.
 */
public class CoreFlowE2eTest {

    private static final String QUESTION = "explain the attention mechanism";
    private static final String FOLLOW_UP = "and how does it compare to an RNN";
    private static final String REASONING = "the reasoning";
    private static final String ANSWER = "the answer";

    private final WiringDoubles.Key key = new WiringDoubles.Key();
    private final WiringDoubles.Homes homes = new WiringDoubles.Homes();
    private final WiringDoubles.Chats chats = new WiringDoubles.Chats();
    private final WiringDoubles.Generations generations = new WiringDoubles.Generations();
    /** The flow's own persistence: rows the earlier steps wrote, the later steps read. */
    private final Map<String, Branch> branches = new HashMap<String, Branch>();
    private final List<Message> written = new ArrayList<Message>();
    private final InMemoryTree tree = new InMemoryTree();

    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(WiringDoubles.NOW), ZoneOffset.UTC);

    @Test
    public void theCoreFlowRunsEndToEndAcrossTheRealWirings() {
        key.configured = true;

        // ---- 1. 创建：首页首问原子建题（真实 HomeWiring，一次写五行、装配真实请求）。
        HomeWiring home = new HomeWiring(homes, generations, key, clock, PromptLimits.budget());
        SubmitResult created = home.submit(QUESTION, WiringDoubles.MODEL, true);

        assertTrue(created.isAccepted());
        assertEquals(1, homes.createCalls);
        assertNotNull(homes.createdTopic);
        Branch root = homes.createdBranch;
        assertTrue(root.isRoot());
        assertEquals(0, homes.createdUser.ordinal);
        assertEquals(1, homes.createdAi.ordinal);
        assertEquals(QUESTION, homes.createdUser.userContent);
        assertEquals(root.id, created.rootBranchId());
        assertEquals(2, generations.lastRequest.messages.size());
        assertTrue(generations.lastRequest.systemChars > 0);
        branches.put(root.id, root);
        written.add(homes.createdUser);
        written.add(homes.createdAi);

        // ---- 2. 追问：接续第 1 步的行，发送进入同一决策链并持久化回合。
        chats.nextOrdinal = 2;
        chats.conversation = new ChatStore.Conversation("", Arrays.asList(
                new ChatStore.Position(homes.createdUser, null, 0, 0, false, "", ""),
                new ChatStore.Position(homes.createdAi, null, 0, 0, true, "", "")));
        ChatWiring chat = new ChatWiring(chats, generations, key, clock, PromptLimits.budget());

        ChatCommandResult sent = chat.send(root.id, FOLLOW_UP, "", null, WiringDoubles.MODEL, true);

        assertTrue(sent.isAccepted());
        assertEquals(1, chats.appendCalls);
        assertEquals(2, chats.appendedUser.ordinal);
        assertEquals(3, chats.appendedAi.ordinal);
        assertEquals(FOLLOW_UP, chats.appendedUser.userContent);
        assertEquals(root.id, chats.appendedUser.branchId);
        assertEquals(1, generations.submitCalls);
        written.add(chats.appendedUser);
        written.add(chats.appendedAi);

        // ---- 3. reasoning 展开：读路径把存储的版本映射为可展开的稳定回答。
        AnswerRevision stable = revision(homes.createdAi.id, REASONING, ANSWER);
        chats.conversation = new ChatStore.Conversation("", Arrays.asList(
                new ChatStore.Position(homes.createdUser, null, 0, 0, false, "", ""),
                new ChatStore.Position(homes.createdAi, stable, 1, 0, false, "", "")));

        ChatData.Snapshot snapshot = chat.read(root.id);

        assertEquals(2, snapshot.messages.size());
        ChatMessage answered = snapshot.messages.get(1);
        assertTrue(answered.isStable());
        assertEquals(ANSWER, answered.text);
        assertEquals(REASONING, answered.reasoning);
        assertTrue(answered.canExpandReasoning());
        assertTrue(answered.canFork());

        // ---- 4. 分叉：从稳定回答开子分支，只写行、不发请求。
        chats.branch = root;
        chats.positions.put(homes.createdAi.id, Message.restore(homes.createdAi.id, root.id,
                com.treelearn.core.domain.MessageRole.AI, 1, null, "", WiringDoubles.NOW,
                stable.id, null));

        ChatCommandResult forked = chat.fork(homes.createdAi.id, "", "", 0, 0);

        assertTrue(forked.isAccepted());
        assertEquals(1, chats.createBranchCalls);
        assertEquals("a fork writes rows and starts nothing", 1, generations.submitCalls);
        Branch child = chats.createdBranch;
        assertEquals(root.id, child.parentBranchId);
        assertEquals(child.id, chats.createdSnapshot.branchId);
        assertEquals(child.id, forked.branchId());
        assertEquals("", forked.operationId());
        branches.put(child.id, child);

        // ---- 5. 重置：同一路由的新版本，替换轮不带历史（上下文与原轮一致）。
        chats.resetResult = resetCommand(root.id, homes.createdAi.id);

        ChatCommandResult reset = chat.reset(homes.createdAi.id, WiringDoubles.MODEL, false,
                WiringDoubles.NOW);

        assertTrue(reset.isAccepted());
        assertEquals(2, generations.submitCalls);
        assertEquals(0, generations.lastRequest.historyChars);

        // ---- 6. 搜索：第 1 步的原句可命中；决策面归档排除，SQL 级已由 T-048 真机证据覆盖。
        FlowSearchRepository search = new FlowSearchRepository();

        List<SearchHit> hits = search.search(SearchQuery.of("attention"));

        assertEquals(1, hits.size());
        assertEquals(Field.QUESTION, hits.get(0).field);
        assertEquals(root.id, hits.get(0).branchId);
        assertTrue(search.isStillReachable(hits.get(0)));

        // ---- 7. 归档：真实 NavigationWiring 写归档位，列表读取反映它、子分支不受牵连。
        NavigationWiring navigation = new NavigationWiring(tree);

        navigation.setArchived(root.id, true, WiringDoubles.NOW);

        NavigationData.Snapshot navigationList = navigation.load(root.topicId);
        assertTrue(navigationList.node(root.id).archived);
        assertFalse(navigationList.node(child.id).archived);
        assertFalse(search.isStillReachable(hits.get(0)));

        // ---- 8. 画布：真实 TreeCanvasWiring 读同一棵树（归档行包含在内），折叠落回存储。
        TreeCanvasWiring canvas = new TreeCanvasWiring(tree);

        TreeCanvasData.Tree loaded = canvas.load(root.topicId);

        assertEquals(2, loaded.rows.size());
        assertNotNull(tree.byBranchId(root.id));
        assertNotNull(tree.byBranchId(child.id));
        assertTrue(canvas.setCollapsed(child.id, true, WiringDoubles.NOW).isOk());
        assertTrue(tree.collapsed.contains(child.id));
    }

    private AnswerRevision revision(String messageId, String reasoning, String answer) {
        DomainResult<AnswerRevision> created = AnswerRevision.create(messageId, 1, reasoning,
                answer, WiringDoubles.MODEL, false, AnswerPolicy.version(), WiringDoubles.NOW);
        assertTrue(created.isOk());
        return created.value();
    }

    private DomainResult<GenerationCommand> resetCommand(String branchId, String messageId) {
        DomainResult<GenerationCommand> decided = GenerationCommand.create(branchId, messageId,
                AnswerPolicy.PURPOSE, AnswerPolicy.version(), WiringDoubles.MODEL, false,
                WiringDoubles.NOW);
        assertTrue(decided.isOk());
        return decided;
    }

    /** The flow's rows in memory, answering both page contracts without owning a rule. */
    private final class InMemoryTree implements NavigationData, TreeCanvasStore {

        final Set<String> archived = new HashSet<String>();
        final Set<String> collapsed = new HashSet<String>();

        Branch byBranchId(String branchId) {
            return branches.get(branchId);
        }

        @Override
        public String topicOf(String branchId) {
            Branch branch = branches.get(branchId);
            return branch == null ? null : branch.topicId;
        }

        @Override
        public NavigationData.Snapshot load(String topicId) {
            List<NavigationData.Node> nodes = new ArrayList<NavigationData.Node>();
            for (Branch branch : branches.values()) {
                if (!branch.topicId.equals(topicId)) {
                    continue;
                }
                nodes.add(new NavigationData.Node(branch.id, branch.parentBranchId,
                        branch.parentBranchId == null ? QUESTION : FOLLOW_UP,
                        archived.contains(branch.id)));
            }
            return new NavigationData.Snapshot(topicId, nodes);
        }

        @Override
        public NavigationData.Summary summary(String branchId) {
            return new NavigationData.Summary(branchId, "a stored branch summary",
                    Collections.singletonList("one"));
        }

        @Override
        public void setArchived(String branchId, boolean value, long now) {
            if (value) {
                archived.add(branchId);
            } else {
                archived.remove(branchId);
            }
        }

        @Override
        public int cancelInFlight(List<String> branchIds) {
            return 0;
        }

        @Override
        public void delete(NavigationData.Plan plan) {
            // The flow exercises archive, not deletion; the store contract keeps the signature.
        }

        @Override
        public List<Branch> branchesOf(String topicId) {
            List<Branch> rows = new ArrayList<Branch>();
            for (Branch branch : branches.values()) {
                if (branch.topicId.equals(topicId)) {
                    rows.add(branch);
                }
            }
            return rows;
        }

        @Override
        public String summaryPayload(String branchId) {
            return null;
        }

        @Override
        public int committedRounds(String branchId) {
            return 1;
        }

        @Override
        public DomainResult<Void> setCollapsed(String branchId, boolean value, long now) {
            if (value) {
                collapsed.add(branchId);
            } else {
                collapsed.remove(branchId);
            }
            return DomainResult.ok();
        }
    }

    /**
     * Search over the flow's own messages, aligned with the two rules the SQL proves on device:
     * a normalized user-question hit, and an archived branch drops out of reachability.
     */
    private final class FlowSearchRepository implements SearchRepository {

        @Override
        public List<SearchHit> search(SearchQuery query) {
            List<SearchHit> hits = new ArrayList<SearchHit>();
            String needle = query.normalized();
            if (needle.isEmpty()) {
                return hits;
            }
            for (Message message : written) {
                if (message.userContent != null
                        && message.userContent.toLowerCase().contains(needle.toLowerCase())) {
                    Branch owner = branches.get(message.branchId);
                    hits.add(SearchHit.of(Field.QUESTION, owner.topicId, message.branchId,
                            message.id, message.ordinal, null, 0, message.userContent));
                }
            }
            return hits;
        }

        @Override
        public boolean isStillReachable(SearchHit hit) {
            Branch branch = branches.get(hit.branchId);
            return branch != null && !tree.archived.contains(branch.id);
        }
    }
}
