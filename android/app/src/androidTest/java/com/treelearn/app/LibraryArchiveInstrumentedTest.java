package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.generation.GenerationTelemetry;
import com.treelearn.app.library.LibraryPresenter;
import com.treelearn.app.library.LibrarySink;
import com.treelearn.app.library.LibraryViewState;
import com.treelearn.app.navigation.SqliteNavigationStore;
import com.treelearn.app.screen.LibraryScreen;
import com.treelearn.app.wiring.LibraryWiring;
import com.treelearn.app.wiring.SqliteLibraryStore;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.AiArtifactDao;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.BranchCreation;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.ContextSnapshotDao;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * T-053 acceptance evidence for the library's 「已完成」 segment, its restore and its subtree delete,
 * executed on a real device against real framework SQLite.
 *
 * <p><strong>What is driven here.</strong> The screen is a real {@link LibraryScreen} built on this
 * instrumentation's own context, wired to a real {@link LibraryPresenter} whose data path is the real
 * {@link LibraryWiring} over a fresh database. Every move is a tap on a control the product really
 * draws - the segment pill, the row's restore and delete buttons, the inline confirmation - so the
 * assertions cover the screen's own wiring rather than only the presenter's state.
 *
 * <p><strong>Why this suite owns its own screen.</strong> {@code AppContainer} is a process-wide
 * singleton whose connection other suites unlink by calling {@code deleteDatabase}, so a screen built
 * through {@code MainActivity} would read whatever file the container happened to open first. Owning
 * the store, the wiring and the screen is what makes "the library lists the archived branch" an
 * assertion about the library rather than about test ordering.
 *
 * <p><strong>The tree every case works on.</strong> One topic, one root, and under it a fork P with a
 * live sibling B and an archived fork A that carries a live descendant G:
 * {@code root ─ P ─ {B, A ─ G}}, with A archived. That shape is what makes "only the target subtree
 * disappears" and "the descendants were not re-attached" assertions rather than hopes.
 *
 * <p>Holds no real user content and no API key: every literal is an obviously synthetic placeholder.
 */
@RunWith(AndroidJUnit4.class)
public final class LibraryArchiveInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    /** Synthetic model id; never a real credential. */
    private static final String MODEL = "deepseek-flash";

    /** Synthetic policy version owned by the learning-answer purpose. */
    private static final String POLICY_VERSION = "tl-learning-answer/1.0.0";

    private static final String TOPIC = "5a5a5a5a-0000-4000-8000-0000000000a1";
    private static final String TOPIC_TITLE = "SYNTHETIC-DONE-TOPIC-TITLE";
    private static final String PARENT_TITLE = "SYNTHETIC-DONE-PARENT-BRANCH";
    private static final String SIBLING_TITLE = "SYNTHETIC-DONE-SIBLING-BRANCH";
    private static final String ARCHIVED_TITLE = "SYNTHETIC-DONE-ARCHIVED-BRANCH";
    private static final String DESCENDANT_TITLE = "SYNTHETIC-DONE-DESCENDANT";
    private static final String SUMMARY_TEXT = "SYNTHETIC-DONE-SUMMARY";

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private TreeLearnDatabaseHelper helper;
    private DatabaseTransactionRunner runner;
    private SqliteNavigationStore navigation;
    private LibraryWiring wiring;
    private RecordingSink sink;
    private LibraryPresenter presenter;
    private LibraryScreen screen;

    private Tree tree;

    @Before
    public void setUp() {
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        helper = new TreeLearnDatabaseHelper(context());
        runner = new DatabaseTransactionRunner();
        navigation = new SqliteNavigationStore(helper, runner, coordinator());
        wiring = new LibraryWiring(new SqliteLibraryStore(helper), navigation);
        tree = seed();

        screen = new LibraryScreen(context());
        sink = new RecordingSink();
        presenter = new LibraryPresenter(sink, Runnable::run, Runnable::run, wiring);
        screen.setActions(presenter);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    // ------------------------------------------------------------------
    // 1. The archived branch is listed by the completed segment, and only there.
    // ------------------------------------------------------------------

    @Test
    public void anArchivedBranchIsListedOnceTheCompletedSegmentIsSelected() {
        presenter.start();
        assertTrue("the working segment lists the topic the branch belongs to: " + texts(R.id.library_rows),
                texts(R.id.library_rows).contains(TOPIC_TITLE));

        tap(R.id.library_segment_done);

        assertTrue("the completed segment must list the archived branch; the list holds: "
                + texts(R.id.library_rows), texts(R.id.library_rows).contains(ARCHIVED_TITLE));
        assertEquals("the completed segment exposes its item count",
                context().getString(R.string.library_segment_done) + " 1",
                ((TextView) view(R.id.library_segment_done)).getText().toString());
        assertNotNull("archived rows use the prototype card surface",
                rowView(ARCHIVED_TITLE, R.id.library_done_row).getBackground());

        // The row's own three facts, read off the row the product drew: its title, where it came from,
        // and the archived mark. The source and the ancestor path are derived from the same read the
        // row was built from, so no second query can disagree with them.
        assertEquals(ARCHIVED_TITLE, textInRow(ARCHIVED_TITLE, R.id.library_done_title));
        assertEquals(context().getString(R.string.done_archived_mark),
                textInRow(ARCHIVED_TITLE, R.id.library_done_mark));
        String meta = textInRow(ARCHIVED_TITLE, R.id.library_done_meta);
        assertTrue("the row names the topic it came from: " + meta, meta.contains(TOPIC_TITLE));
        assertTrue("and the ancestor path inside that topic: " + meta, meta.contains(PARENT_TITLE));

        tap(R.id.library_segment_in_progress);

        assertTrue("the working segment still lists the topic: " + texts(R.id.library_rows),
                texts(R.id.library_rows).contains(TOPIC_TITLE));
        assertFalse("an archived branch is off the working list: " + texts(R.id.library_rows),
                texts(R.id.library_rows).contains(ARCHIVED_TITLE));
    }

    // ------------------------------------------------------------------
    // 2. Restore: back to the working tree, and the descendants are not re-attached.
    // ------------------------------------------------------------------

    @Test
    public void restoringAnArchivedBranchReturnsItToTheWorkingTreeWithoutReattachingDescendants() {
        presenter.start();
        tap(R.id.library_segment_done);
        assertTrue("precondition: the branch is listed as completed",
                texts(R.id.library_rows).contains(ARCHIVED_TITLE));

        tapInRow(ARCHIVED_TITLE, R.id.library_done_restore);

        assertFalse("a restored branch leaves the completed segment: " + texts(R.id.library_rows),
                texts(R.id.library_rows).contains(ARCHIVED_TITLE));
        assertFalse("the branch is back in the working tree, not archived",
                navigation.load(TOPIC).node(tree.archived.id).archived);
        tap(R.id.library_segment_in_progress);
        assertTrue("and its topic is still a working topic: " + texts(R.id.library_rows),
                texts(R.id.library_rows).contains(TOPIC_TITLE));

        // The half that must be asserted explicitly: restoring flips one flag and re-attaches nothing.
        assertNotNull("the descendant is still in the tree at all",
                navigation.load(TOPIC).node(tree.descendant.id));
        assertEquals("the descendant still hangs off the branch it was forked from",
                tree.archived.id,
                navigation.load(TOPIC).node(tree.descendant.id).parentBranchId);
        assertEquals("and the stored row says the same thing", tree.archived.id,
                branchColumn(tree.descendant.id, TreeLearnSchema.Branches.PARENT_ID));
    }

    // ------------------------------------------------------------------
    // 3. Delete: confirmed first, then the whole subtree and nothing else.
    // ------------------------------------------------------------------

    @Test
    public void deletingAnArchivedSubtreeTakesItsDescendantsAndLeavesTheRestOfTheTree() {
        presenter.start();
        tap(R.id.library_segment_done);

        tapInRow(ARCHIVED_TITLE, R.id.library_done_delete);

        assertEquals("nothing may be written before the confirmation", 1, countBranch(tree.archived.id));
        assertEquals("nor may a descendant be touched", 1, countBranch(tree.descendant.id));
        String prompt = textInRow(ARCHIVED_TITLE, R.id.library_done_prompt);
        assertEquals("the confirmation names the exact scope the presenter counted",
                context().getString(R.string.done_delete_prompt, ARCHIVED_TITLE, 2), prompt);

        tapInRow(ARCHIVED_TITLE, R.id.library_done_cancel);
        assertEquals("cancelling the confirmation writes nothing either", 1,
                countBranch(tree.archived.id));
        assertEquals("and the row returns to its normal shape", View.GONE,
                rowView(ARCHIVED_TITLE, R.id.library_done_prompt).getVisibility());

        tapInRow(ARCHIVED_TITLE, R.id.library_done_delete);
        tapInRow(ARCHIVED_TITLE, R.id.library_done_confirm);

        assertEquals("the target branch is gone", 0, countBranch(tree.archived.id));
        assertEquals("its descendant went with it", 0, countBranch(tree.descendant.id));
        assertEquals("the sibling branch survives", 1, countBranch(tree.sibling.id));
        assertEquals("the branch it hung off survives", 1, countBranch(tree.parent.id));
        assertEquals("the root branch survives", 1, countBranch(tree.root.id));
        assertEquals("and the topic itself survives a subtree delete", 1, countTopic());
        assertNoDanglingReferences();
        assertFalse("the completed segment no longer lists it: " + texts(R.id.library_rows),
                texts(R.id.library_rows).contains(ARCHIVED_TITLE));
    }

    // ------------------------------------------------------------------
    // 4. An empty completed segment, and a branch without a summary.
    // ------------------------------------------------------------------

    @Test
    public void theCompletedSegmentSaysThereIsNothingThereRatherThanShowingABlankList() {
        navigation.setArchived(tree.archived.id, false, T0 + 20_000L);
        presenter.start();

        tap(R.id.library_segment_done);

        assertEquals("an empty completed segment is its own state", View.VISIBLE,
                view(R.id.library_state).getVisibility());
        String sentence = text(R.id.library_state);
        assertTrue("it names the fact: " + sentence,
                sentence.contains(context().getString(R.string.done_empty)));
        assertTrue("and it points at the next step: " + sentence,
                sentence.contains(context().getString(R.string.done_empty_guide)));
        assertFalse("with words of its own, not the working segment's: " + sentence,
                sentence.contains(context().getString(R.string.library_empty)));
    }

    @Test
    public void aBranchWithoutASummaryDrawsOneLineFewerAndNeverAPlaceholder() {
        presenter.start();
        tap(R.id.library_segment_done);

        assertEquals("a branch with no summary must not draw the summary line", View.GONE,
                rowView(ARCHIVED_TITLE, R.id.library_done_summary).getVisibility());
        List<String> withoutSummary = texts(R.id.library_rows);
        for (String sentence : withoutSummary) {
            assertFalse("no explanation may stand in for the missing summary: " + sentence,
                    sentence.contains(context().getString(R.string.navigation_summary_empty)));
        }

        installSummary(tree.archived.id, SUMMARY_TEXT);
        presenter.onResumed();

        assertEquals("with a summary the row draws it", View.VISIBLE,
                rowView(ARCHIVED_TITLE, R.id.library_done_summary).getVisibility());
        List<String> withSummary = texts(R.id.library_rows);
        assertEquals("a stored summary is exactly one line more, and still nothing else",
                withoutSummary.size() + 1, withSummary.size());
        assertTrue("and the line is the stored summary: " + withSummary,
                withSummary.contains(SUMMARY_TEXT));
    }

    // ------------------------------------------------------------------
    // Fixtures and drivers.
    // ------------------------------------------------------------------

    /** The one tree every case works on: {@code root ─ P ─ {B, A(archived) ─ G}}. */
    private static final class Tree {

        final Branch root;
        final Branch parent;
        final Branch sibling;
        final Branch archived;
        final Branch descendant;

        Tree(Branch root, Branch parent, Branch sibling, Branch archived, Branch descendant) {
            this.root = root;
            this.parent = parent;
            this.sibling = sibling;
            this.archived = archived;
            this.descendant = descendant;
        }
    }

    private Tree seed() {
        SQLiteDatabase database = db();
        TopicDao.insert(database, new Topic(TOPIC, null, T0, T0));
        Branch root = Branch.root(TOPIC, T0);
        BranchDao.insert(database, root);
        root.setTitle(TOPIC_TITLE, T0 + 1L);
        BranchDao.update(database, root);

        Branch parent = titled(forkOf(root, answerOn(root, T0 + 1_000L), 1, T0 + 3_000L),
                PARENT_TITLE, T0 + 3_500L);
        Answered parentAnswer = answerOn(parent, T0 + 4_000L);
        Branch sibling = titled(forkOf(parent, parentAnswer, 2, T0 + 5_000L), SIBLING_TITLE,
                T0 + 5_500L);
        Branch archived = titled(forkOf(parent, parentAnswer, 3, T0 + 6_000L), ARCHIVED_TITLE,
                T0 + 6_500L);
        Branch descendant = titled(
                forkOf(archived, answerOn(archived, T0 + 7_000L), 4, T0 + 8_000L), DESCENDANT_TITLE,
                T0 + 8_500L);

        navigation.setArchived(archived.id, true, T0 + 9_000L);
        return new Tree(root, parent, sibling, archived, descendant);
    }

    private Branch titled(Branch branch, String title, long now) {
        branch.setTitle(title, now);
        BranchDao.update(db(), branch);
        return branch;
    }

    /** One branch's conversation: the learner's question, the answer position, one stable revision. */
    private static final class Answered {

        final Message position;
        final AnswerRevision revision;

        Answered(Message position, AnswerRevision revision) {
            this.position = position;
            this.revision = revision;
        }
    }

    private Answered answerOn(Branch branch, long now) {
        MessageDao.insert(db(), Message.user(branch.id, 0, "SYNTHETIC-DONE-QUESTION", now));
        Message position = Message.ai(branch.id, 1, now + 1_000L);
        MessageDao.insert(db(), position);
        DomainResult<AnswerRevision> created = AnswerRevision.create(position.id, 1,
                "SYNTHETIC-DONE-REASONING", "SYNTHETIC-DONE-ANSWER", GenerationModel.fromWireId(MODEL),
                false, POLICY_VERSION, now + 1_000L);
        assertTrue("fixture: the revision must be accepted: " + (created.isOk() ? "" : created.error()),
                created.isOk());
        AnswerRevision revision = created.value();
        AnswerRevisionDao.insert(db(), revision);
        assertTrue("fixture: the answer position must display its revision",
                position.activateRevision(revision.id).isOk());
        MessageDao.updateActiveReferences(db(), position);
        return new Answered(position, revision);
    }

    /** One fork, written through the entry point that owns the three-write fork rule. */
    private Branch forkOf(Branch parent, Answered source, int ordinal, long now) {
        String snapshotId = String.format("5a5a5a5a-0000-4000-8000-0000000000c%d", ordinal);
        DomainResult<BranchSource> provenance = BranchSource.fromOperation(parent.id,
                source.position.id, snapshotId, GenerationState.STABLE, source.revision.id,
                BranchAnchor.wholeAnswer());
        assertTrue("fixture: the provenance must be accepted: "
                + (provenance.isOk() ? "" : provenance.error()), provenance.isOk());
        DomainResult<Branch> fork = Branch.child(TOPIC, provenance.value(), now);
        assertTrue("fixture: the fork must be accepted: " + (fork.isOk() ? "" : fork.error()),
                fork.isOk());
        Branch branch = fork.value();
        BranchCreation.Outcome outcome = new BranchCreation(runner).create(db(), branch,
                new ContextSnapshotDao.Row(snapshotId, branch.id, 1, "synthetic-digest",
                        "{\"schema\":1,\"entries\":[]}", now));
        assertTrue("fixture: the fork must commit: " + outcome, outcome.isCreated());
        return outcome.branch;
    }

    /** The summary artifact exactly as the derived service writes one, for one branch. */
    private void installSummary(String branchId, String summary) {
        AiArtifactDao.insert(db(), new AiArtifactDao.Row(
                "5a5a5a5a-0000-4000-8000-0000000000d1", ArtifactKind.BRANCH_SUMMARY.wireId, branchId,
                ArtifactKind.BRANCH_SUMMARY.purpose.policyPrefix, "tl-branch-summary/1.0.0",
                "{\"summary\":\"" + summary + "\",\"key_points\":[]}", null,
                "synthetic-summary-digest", T0, T0));
    }

    private GenerationCoordinator coordinator() {
        return new GenerationCoordinator(helper, runner,
                (model, request, sink) -> {
                    throw new UnsupportedOperationException("this suite never opens a stream");
                },
                Clock.fixed(Instant.ofEpochMilli(T0), ZoneOffset.UTC),
                new GenerationTelemetry() {
                    @Override
                    public void record(GenerationTelemetry.Event event, String operationId,
                            String branchId, int chars, AiGatewayError.Kind errorKind) {
                        // Redacted by construction: this suite asserts the library, not telemetry.
                    }
                });
    }

    /** What the presenter renders, handed to the real screen exactly as the host does it. */
    private final class RecordingSink implements LibrarySink {

        LibraryViewState last;
        final List<String> opened = new ArrayList<String>();

        @Override
        public void render(LibraryViewState state) {
            last = state;
            screen.render(state);
        }

        @Override
        public void openBranch(String branchId) {
            opened.add(branchId);
        }
    }

    // ------------------------------------------------------------------
    // Drivers and readers, all through the screen the product draws.
    // ------------------------------------------------------------------

    private void tap(final int id) {
        instrumentation.runOnMainSync(() -> view(id).performClick());
    }

    /**
     * Taps a control inside the row that carries {@code rowTitle}, never a bare id guess.
     *
     * <p>The lookup happens on the main thread and the failure is built out here, off it: {@link
     * #texts} posts to the main thread, so naming the list inside the runnable above would raise
     * "This method can not be called from the main application thread" and hide the row that was
     * actually missing.
     */
    private void tapInRow(final String rowTitle, final int controlId) {
        final View[] found = new View[2];
        instrumentation.runOnMainSync(() -> {
            View row = row(rowTitle);
            if (row != null) {
                found[0] = row;
                found[1] = row.findViewById(controlId);
            }
        });
        if (found[0] == null) {
            fail("no completed row titled " + rowTitle + " is on screen, so " + controlId
                    + " cannot be tapped; the list holds: " + texts(R.id.library_rows));
        }
        assertNotNull("the row titled " + rowTitle + " draws no control " + controlId, found[1]);
        instrumentation.runOnMainSync(() -> found[1].performClick());
    }

    private View rowView(String rowTitle, int id) {
        final View[] found = new View[1];
        instrumentation.runOnMainSync(() -> {
            View row = row(rowTitle);
            assertNotNull("no completed row titled " + rowTitle + " is on screen", row);
            found[0] = row.findViewById(id);
        });
        return found[0];
    }

    private String textInRow(String rowTitle, int id) {
        View target = rowView(rowTitle, id);
        final String[] found = new String[1];
        instrumentation.runOnMainSync(() -> found[0] = ((TextView) target).getText().toString());
        return found[0];
    }

    /** The row whose title line reads {@code rowTitle}, or null. */
    private View row(String rowTitle) {
        View container = view(R.id.library_rows);
        if (!(container instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) container;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.findViewById(R.id.library_done_title) != null
                    && rowTitle.equals(((TextView) child.findViewById(R.id.library_done_title))
                            .getText().toString())) {
                return child;
            }
        }
        return null;
    }

    private View view(int id) {
        return screen.findViewById(id);
    }

    private String text(int id) {
        final String[] found = new String[1];
        instrumentation.runOnMainSync(() -> found[0] = ((TextView) view(id)).getText().toString());
        return found[0];
    }

    /** Every string the screen currently draws under {@code containerId}, for an honest failure. */
    private List<String> texts(final int containerId) {
        final List<String> found = new ArrayList<String>();
        instrumentation.runOnMainSync(() -> collect(view(containerId), found));
        return found;
    }

    private static void collect(View view, List<String> into) {
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE) {
            into.add(((TextView) view).getText().toString());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), into);
            }
        }
    }

    // ------------------------------------------------------------------
    // Raw readers, so a count is the store's own answer.
    // ------------------------------------------------------------------

    private int countBranch(String branchId) {
        return count("SELECT COUNT(*) FROM " + TreeLearnSchema.Branches.TABLE + " WHERE "
                + TreeLearnSchema.Branches.ID + "=?", branchId);
    }

    private int countTopic() {
        return count("SELECT COUNT(*) FROM " + TreeLearnSchema.Topics.TABLE, null);
    }

    private int count(String sql, String argument) {
        String[] args = argument == null ? null : new String[]{argument};
        try (Cursor cursor = db().rawQuery(sql, args)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : Integer.MIN_VALUE;
        }
    }

    private String branchColumn(String branchId, String column) {
        try (Cursor cursor = db().rawQuery("SELECT " + column + " FROM "
                        + TreeLearnSchema.Branches.TABLE + " WHERE " + TreeLearnSchema.Branches.ID + "=?",
                new String[]{branchId})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private void assertNoDanglingReferences() {
        try (Cursor cursor = db().rawQuery("PRAGMA foreign_key_check", null)) {
            assertEquals("PRAGMA foreign_key_check must report no dangling reference", 0,
                    cursor.getCount());
        }
    }

    private SQLiteDatabase db() {
        return helper.getWritableDatabase();
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
