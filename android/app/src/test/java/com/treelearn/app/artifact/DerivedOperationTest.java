package com.treelearn.app.artifact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.ai.prompt.DeepSeekPromptRegistry;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.data.db.dao.AiArtifactDao;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Test;

/**
 * T-011 plain-JVM evidence for the conditional write-back of technical design 10.
 *
 * <p>Nothing here needs an emulator: the decision is "is this result still the one the current
 * baseline deserves?", and that decision is a function of the request, the live baseline and what is
 * already stored. The store double expresses exactly the two questions {@link DerivedStore} asks and
 * refuses to answer them outside the transaction, so a read-decide-write that escaped the
 * transaction boundary fails the run instead of passing quietly.
 *
 * <p>The SQLite statements themselves are {@code AiArtifactDao}'s, whose single-statement CAS is
 * already covered by T-024 on a device; this suite covers the layer that decides when to call it.
 * No assertion label, fixture or failure message carries a real question, answer or reasoning text.
 */
public final class DerivedOperationTest {

    private static final long NOW = 1_700_000_000_000L;

    private static final GenerationModel MODEL = GenerationModel.FLASH;

    private static final PromptBudget ROOMY = new PromptBudget(200_000, 4_000);

    private static final String MARKER = "OP-MARKER-9D31";

    private static final String SUMMARY_OUTPUT = "{\"summary\":\"主线\",\"key_points\":[\"要点\"]}";

    private static final String STORED_SUMMARY = "{\"summary\":\"旧主线\",\"key_points\":[]}";

    @Test
    public void aLegalResultIsWrittenForTheBaselineItWasDerivedFrom() {
        String branch = Guard.newId();
        String revision = Guard.newId();
        DerivedRequest request = DerivedRequest.branchSummary(branch, revision, birth(), messages(), MODEL,
                ROOMY);
        RecordingStore store = new RecordingStore();
        store.live = revision;
        ScriptedCaller caller = new ScriptedCaller(SUMMARY_OUTPUT);

        assertEquals(ArtifactOutcome.APPLIED, operation(store, new RecordingTelemetry(), caller, request).run());

        assertEquals(1, caller.calls);
        assertEquals(1, store.writes);
        AiArtifactDao.Row stored = store.only();
        assertEquals(ArtifactKind.BRANCH_SUMMARY.wireId, stored.kind);
        assertEquals(branch, stored.scopeId);
        assertEquals(PromptPurpose.BRANCH_SUMMARY.policyPrefix, stored.promptPurpose);
        assertEquals("tl-branch-summary/1.0.0", stored.promptPolicyVersion);
        assertEquals(revision, stored.basedOnRevisionId);
        assertEquals("{\"summary\":\"主线\",\"key_points\":[\"要点\"]}", stored.payload);
        assertEquals(request.inputDigest(), stored.inputDigest);
        assertEquals(NOW, stored.createdAt);
        assertEquals(NOW, stored.updatedAt);
    }

    @Test
    public void aLateResultWhoseRevisionMovedOnIsDiscardedAndKeepsTheStoredPayload() {
        String branch = Guard.newId();
        String stale = Guard.newId();
        DerivedRequest request = DerivedRequest.branchSummary(branch, stale, birth(), messages(), MODEL,
                ROOMY);
        RecordingStore store = new RecordingStore();
        store.seed(ArtifactKind.BRANCH_SUMMARY, branch, STORED_SUMMARY);
        store.live = Guard.newId();

        assertEquals(ArtifactOutcome.STALE_DISCARDED,
                operation(store, new RecordingTelemetry(), new ScriptedCaller(SUMMARY_OUTPUT), request).run());

        assertEquals(0, store.writes);
        assertEquals("a stale write-back must leave the stored payload untouched", STORED_SUMMARY,
                store.only().payload);
    }

    @Test
    public void aLateResultForADigestBaselineIsDiscardedTheSameWay() {
        String topic = Guard.newId();
        DerivedRequest older = DerivedRequest.homeRecommendations(topic, outline("旧主题"), 2, MODEL, ROOMY);
        RecordingStore store = new RecordingStore();
        store.seed(ArtifactKind.HOME_RECOMMENDATIONS, topic, "{\"items\":[{\"topic\":\"旧\"}]}");
        store.live = DerivedRequest.homeRecommendations(topic, outline("新主题"), 2, MODEL, ROOMY)
                .baselineRef();

        assertEquals(ArtifactOutcome.STALE_DISCARDED, operation(store, new RecordingTelemetry(),
                new ScriptedCaller("{\"items\":[{\"topic\":\"候选\"}]}"), older).run());
        assertEquals(0, store.writes);
    }

    @Test
    public void aResultForTheCurrentOutlineIsWritten() {
        String topic = Guard.newId();
        DerivedRequest request = DerivedRequest.homeRecommendations(topic, outline("主题"), 2, MODEL, ROOMY);
        RecordingStore store = new RecordingStore();
        store.live = request.baselineRef();

        assertEquals(ArtifactOutcome.APPLIED, operation(store, new RecordingTelemetry(),
                new ScriptedCaller("{\"items\":[{\"topic\":\"候选\"}]}"), request).run());
        assertEquals("{\"items\":[{\"topic\":\"候选\"}]}", store.only().payload);
        assertNull("a home recommendation is never bound to a revision", store.only().basedOnRevisionId);
    }

    @Test
    public void aCallbackAfterTheScopeWasDeletedNeverCreatesAnArtifact() {
        String branch = Guard.newId();
        String revision = Guard.newId();
        DerivedRequest request = DerivedRequest.branchSummary(branch, revision, birth(), messages(), MODEL,
                ROOMY);
        RecordingStore store = new RecordingStore();
        store.live = null;

        assertEquals(ArtifactOutcome.STALE_DISCARDED,
                operation(store, new RecordingTelemetry(), new ScriptedCaller(SUMMARY_OUTPUT), request).run());
        assertEquals("a retired scope must never gain an artifact back", 0, store.rows.size());
        assertEquals(0, store.writes);
    }

    @Test
    public void anOutputOutsideTheEnvelopeKeepsTheOldArtifact() {
        String branch = Guard.newId();
        String revision = Guard.newId();
        String[] refused = {
                "{\"summary\":\"主线\"}",
                "{\"summary\":\"主线\",\"key_points\":\"甲\"}",
                "{\"summary\":\"主线\",\"key_points\":[]}\n希望这有帮助。",
                "```json\n{\"summary\":\"主线\",\"key_points\":[]}\n```",
                "",
        };
        for (int index = 0; index < refused.length; index++) {
            DerivedRequest request = DerivedRequest.branchSummary(branch, revision, birth(), messages(),
                    MODEL, ROOMY);
            RecordingStore store = new RecordingStore();
            store.seed(ArtifactKind.BRANCH_SUMMARY, branch, STORED_SUMMARY);
            store.live = revision;
            ArtifactOutcome expected = refused[index].isEmpty()
                    ? ArtifactOutcome.GENERATION_FAILED : ArtifactOutcome.PAYLOAD_INVALID;
            assertEquals("case " + index, expected,
                    operation(store, new RecordingTelemetry(), new ScriptedCaller(refused[index]), request)
                            .run());
            assertEquals("case " + index + " must keep the stored payload", STORED_SUMMARY,
                    store.only().payload);
        }
    }

    @Test
    public void aPromptThatCannotBeBuiltIsRefusedWithoutCallingTheGateway() {
        DerivedRequest request = DerivedRequest.rootTitle(Guard.newId(), "根问题", MODEL,
                new PromptBudget(300, 200));
        RecordingStore store = new RecordingStore();
        store.live = request.scopeId();
        ScriptedCaller caller = new ScriptedCaller("{\"title\":\"熵\"}");

        assertEquals(ArtifactOutcome.BUILD_REFUSED,
                operation(store, new RecordingTelemetry(), caller, request).run());
        assertEquals("a refused build must not reach the provider", 0, caller.calls);
        assertEquals(0, store.writes);
    }

    @Test
    public void aCompressionWithoutReasoningFailsClosedBeforeTheCall() {
        DerivedRequest request = DerivedRequest.contextCompression(Guard.newId(), Guard.newId(),
                Arrays.asList(new ArtifactInputs.CompressionTurn("问题",
                        revision("", "回答"))), MODEL, ROOMY);
        RecordingStore store = new RecordingStore();
        ScriptedCaller caller = new ScriptedCaller("{\"summary\":\"短\"}");

        assertEquals(ArtifactOutcome.REASONING_OMITTED,
                operation(store, new RecordingTelemetry(), caller, request).run());
        assertEquals("dropping the reasoning is never the way to fit the window", 0, caller.calls);
        assertEquals(0, store.writes);
    }

    @Test
    public void aCompressionThatDidNotShrinkTheContextFailsClosed() {
        String branch = Guard.newId();
        String revision = Guard.newId();
        DerivedRequest request = DerivedRequest.contextCompression(branch, revision,
                Arrays.asList(new ArtifactInputs.CompressionTurn("问题", revision("短推理", "短回答"))),
                MODEL, ROOMY);
        RecordingStore store = new RecordingStore();
        store.live = revision;
        StringBuilder oversized = new StringBuilder();
        for (int index = 0; index < 40; index++) {
            oversized.append("很长");
        }
        assertEquals(ArtifactOutcome.OVER_WINDOW, operation(store, new RecordingTelemetry(),
                new ScriptedCaller("{\"summary\":\"" + oversized + "\"}"), request).run());
        assertEquals("an unfaithful compression must fail closed rather than be stored", 0, store.writes);
    }

    @Test
    public void aCompressionThatAnswersWithNothingFailsClosed() {
        String branch = Guard.newId();
        String revision = Guard.newId();
        DerivedRequest request = DerivedRequest.contextCompression(branch, revision,
                Arrays.asList(new ArtifactInputs.CompressionTurn("问题", revision("推理" + MARKER, "回答"))),
                MODEL, ROOMY);
        RecordingStore store = new RecordingStore();
        store.live = revision;

        assertEquals(ArtifactOutcome.SUMMARY_EMPTY, operation(store, new RecordingTelemetry(),
                new ScriptedCaller("{\"summary\":\"\"}"), request).run());
        assertEquals(0, store.writes);
    }

    @Test
    public void theRecordCarriesTheKindARandomIdAndTheDigestPrefixAndNothingElse() {
        String branch = Guard.newId();
        String revision = Guard.newId();
        DerivedRequest request = DerivedRequest.branchSummary(branch, revision, birth(), messages(), MODEL,
                ROOMY);
        RecordingStore store = new RecordingStore();
        store.live = revision;
        RecordingTelemetry telemetry = new RecordingTelemetry();

        assertEquals(ArtifactOutcome.APPLIED,
                operation(store, telemetry, new ScriptedCaller(SUMMARY_OUTPUT), request).run());

        assertEquals(Arrays.asList(DerivedTelemetry.Event.START, DerivedTelemetry.Event.APPLIED),
                telemetry.events);
        for (String attemptId : telemetry.attemptIds) {
            assertEquals("every record carries a canonical random id", attemptId,
                    Guard.id(attemptId, "attemptId"));
        }
        for (String prefix : telemetry.prefixes) {
            assertEquals(request.inputDigest().substring(0, 8), prefix);
        }
        String joined = String.join(" ", telemetry.lines);
        assertTrue(joined.contains(ArtifactKind.BRANCH_SUMMARY.wireId));
        assertFalse("a record must not carry the envelope", joined.contains(request.inputJson()));
        assertFalse("a record must not carry the reasoning", joined.contains(MARKER));
        assertFalse("a record must not carry the prompt body", joined
                .contains(DeepSeekPromptRegistry.systemContent(PromptPurpose.BRANCH_SUMMARY, false)));
    }

    @Test
    public void twoAttemptsOfTheSameInputCarryTwoDifferentIds() {
        String branch = Guard.newId();
        String revision = Guard.newId();
        DerivedRequest request = DerivedRequest.branchSummary(branch, revision, birth(), messages(), MODEL,
                ROOMY);
        RecordingTelemetry first = new RecordingTelemetry();
        RecordingTelemetry second = new RecordingTelemetry();
        for (RecordingTelemetry telemetry : Arrays.asList(first, second)) {
            RecordingStore store = new RecordingStore();
            store.live = revision;
            assertEquals(ArtifactOutcome.APPLIED,
                    operation(store, telemetry, new ScriptedCaller(SUMMARY_OUTPUT), request).run());
        }
        assertNotEquals("the id must never be derived from the input", first.attemptIds.get(0),
                second.attemptIds.get(0));
    }

    @Test
    public void everyKindStoresItsOwnUsageAndVersionAndNeverTheTeachingOne() {
        Map<ArtifactKind, DerivedRequest> requests = allRequests();
        Map<ArtifactKind, String> outputs = new LinkedHashMap<ArtifactKind, String>();
        outputs.put(ArtifactKind.ROOT_TITLE, "{\"title\":\"熵\"}");
        outputs.put(ArtifactKind.BRANCH_TITLE, "{\"title\":\"分支\"}");
        outputs.put(ArtifactKind.BRANCH_SUMMARY, SUMMARY_OUTPUT);
        outputs.put(ArtifactKind.ANSWER_RECOMMENDATIONS, "{\"items\":[{\"question\":\"下一步?\"}]}");
        outputs.put(ArtifactKind.HOME_RECOMMENDATIONS, "{\"items\":[{\"topic\":\"新主题\"}]}");
        outputs.put(ArtifactKind.CONTEXT_COMPRESSION, "{\"summary\":\"短\"}");

        assertEquals(ArtifactKind.values().length, requests.size());
        for (ArtifactKind kind : ArtifactKind.values()) {
            DerivedRequest request = requests.get(kind);
            RecordingStore store = new RecordingStore();
            store.live = request.baselineRef();
            assertEquals(kind + " must apply", ArtifactOutcome.APPLIED,
                    operation(store, new RecordingTelemetry(), new ScriptedCaller(outputs.get(kind)),
                            request).run());
            AiArtifactDao.Row stored = store.only();
            assertEquals(kind.purpose.policyPrefix, stored.promptPurpose);
            assertEquals(request.policyVersion(), stored.promptPolicyVersion);
            assertNotEquals(PromptPurpose.LEARNING_ANSWER.policyPrefix, stored.promptPurpose);
        }
    }

    private static DerivedOperation operation(DerivedStore store, DerivedTelemetry telemetry,
            DerivedCaller caller, DerivedRequest request) {
        return new DerivedOperation(store, telemetry, caller, request, NOW);
    }

    private static Map<ArtifactKind, DerivedRequest> allRequests() {
        Map<ArtifactKind, DerivedRequest> requests = new LinkedHashMap<ArtifactKind, DerivedRequest>();
        requests.put(ArtifactKind.ROOT_TITLE,
                DerivedRequest.rootTitle(Guard.newId(), "根问题", MODEL, ROOMY));
        requests.put(ArtifactKind.BRANCH_TITLE,
                DerivedRequest.branchTitle(Guard.newId(), "锚点", "首问", MODEL, ROOMY));
        requests.put(ArtifactKind.BRANCH_SUMMARY,
                DerivedRequest.branchSummary(Guard.newId(), Guard.newId(), birth(), messages(), MODEL,
                        ROOMY));
        requests.put(ArtifactKind.ANSWER_RECOMMENDATIONS, DerivedRequest.answerRecommendations(
                Guard.newId(), Guard.newId(), "最新回答", outline("分支主题"), 3, MODEL, ROOMY));
        requests.put(ArtifactKind.HOME_RECOMMENDATIONS,
                DerivedRequest.homeRecommendations(Guard.newId(), outline("主题"), 3, MODEL, ROOMY));
        requests.put(ArtifactKind.CONTEXT_COMPRESSION, DerivedRequest.contextCompression(Guard.newId(),
                Guard.newId(), Arrays.asList(new ArtifactInputs.CompressionTurn("问题",
                        revision("推理" + MARKER, "回答"))), MODEL, ROOMY));
        return requests;
    }

    private static List<ContextPlan.Entry> birth() {
        return Arrays.asList(ContextPlan.Entry.userMessage(Guard.newId(), "根问题"));
    }

    private static List<ContextPlan.Entry> messages() {
        return Arrays.asList(ContextPlan.Entry.stableRevision(Guard.newId(), "推理" + MARKER, "回答"));
    }

    private static List<TreeOutline.Entry> outline(String title) {
        return Arrays.asList(TreeOutline.Entry.branch(title, "锚点", "摘要"));
    }

    private static AnswerRevision revision(String reasoning, String answer) {
        return AnswerRevision.create(Guard.newId(), 1, reasoning, answer, MODEL, false,
                "tl-learning-answer/1.0.0", NOW).value();
    }

    private static final class ScriptedCaller implements DerivedCaller {

        private final String output;

        int calls;

        ScriptedCaller(String output) {
            this.output = output;
        }

        @Override
        public String call(DeepSeekRequest request, GenerationModel model) {
            calls++;
            return output;
        }
    }

    private static final class RecordingTelemetry implements DerivedTelemetry {

        final List<Event> events = new ArrayList<Event>();

        final List<String> attemptIds = new ArrayList<String>();

        final List<String> prefixes = new ArrayList<String>();

        final List<String> lines = new ArrayList<String>();

        @Override
        public void record(Event event, ArtifactKind kind, String attemptId, String digestPrefix) {
            events.add(event);
            attemptIds.add(attemptId);
            prefixes.add(digestPrefix);
            lines.add(event + " " + kind.wireId + " " + attemptId + " " + digestPrefix);
        }
    }

    private static final class RecordingStore implements DerivedStore {

        final Map<String, AiArtifactDao.Row> rows = new LinkedHashMap<String, AiArtifactDao.Row>();

        String live;

        int transactions;

        int writes;

        private boolean insideTransaction;

        void seed(ArtifactKind kind, String scopeId, String payload) {
            rows.put(key(kind.wireId, scopeId), new AiArtifactDao.Row(Guard.newId(), kind.wireId,
                    scopeId, kind.purpose.policyPrefix,
                    DeepSeekPromptRegistry.spec(kind.purpose).policyVersion, payload, null,
                    "0".repeat(64), NOW, NOW));
        }

        AiArtifactDao.Row only() {
            assertEquals("exactly one artifact is stored for a single-scope fixture", 1, rows.size());
            return rows.values().iterator().next();
        }

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            transactions++;
            assertFalse("the write-back opens one transaction, not nested ones", insideTransaction);
            insideTransaction = true;
            try {
                return work.get();
            } finally {
                insideTransaction = false;
            }
        }

        @Override
        public String liveBaseline(ArtifactKind kind, String scopeId) {
            if (!insideTransaction) {
                throw new IllegalStateException("the live baseline must be re-read inside the transaction");
            }
            return live;
        }

        @Override
        public void write(AiArtifactDao.Row row) {
            if (!insideTransaction) {
                throw new IllegalStateException("the write-back must happen inside the transaction");
            }
            writes++;
            rows.put(key(row.kind, row.scopeId), row);
        }

        private static String key(String kind, String scopeId) {
            return kind + "|" + scopeId;
        }
    }
}