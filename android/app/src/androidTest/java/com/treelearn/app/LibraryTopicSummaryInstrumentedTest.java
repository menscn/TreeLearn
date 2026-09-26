package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
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
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.AiArtifactDao;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.BranchDao;
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
 * T-061 acceptance evidence for the library's 「进行中」 row summary (PRD 6.2.16 point 4), executed
 * on a real device against real framework SQLite.
 *
 * <p><strong>What is driven here.</strong> A real {@link LibraryScreen} over a real {@link
 * LibraryPresenter} whose data path is the real {@link LibraryWiring} over a fresh database, the
 * same shape {@link LibraryArchiveInstrumentedTest} drives for the completed segment. The two
 * assertions cover exactly the two facts PRD 6.2.16 point 4 and the PRD's placeholder ban name:
 * a topic with a summary draws it in its row, and a topic without one draws one line fewer - never
 * an explanatory placeholder standing in for the missing summary.
 *
 * <p><strong>Why this suite owns its own screen.</strong> {@code AppContainer} is a process-wide
 * singleton whose connection other suites unlink by calling {@code deleteDatabase}, so a screen
 * built through {@code MainActivity} would read whatever file the container happened to open first.
 * Owning the store, the wiring and the screen is what makes "the row shows the stored summary" an
 * assertion about the library rather than about test ordering.
 *
 * <p>Holds no real user content and no API key: every literal is an obviously synthetic placeholder.
 */
@RunWith(AndroidJUnit4.class)
public final class LibraryTopicSummaryInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    /** Synthetic model id; never a real credential. */
    private static final String MODEL = "deepseek-flash";

    /** Synthetic policy version owned by the learning-answer purpose. */
    private static final String POLICY_VERSION = "tl-learning-answer/1.0.0";

    private static final String TOPIC = "6b6b6b6b-0000-4000-8000-0000000000b1";
    private static final String TOPIC_TITLE = "SYNTHETIC-SUMMARY-TOPIC-TITLE";
    private static final String SUMMARY_TEXT = "SYNTHETIC-TOPIC-SUMMARY-LINE";

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private TreeLearnDatabaseHelper helper;
    private DatabaseTransactionRunner runner;
    private SqliteNavigationStore navigation;
    private LibraryWiring wiring;
    private RecordingSink sink;
    private LibraryPresenter presenter;
    private LibraryScreen screen;
    private Branch root;

    @Before
    public void setUp() {
        context().deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        helper = new TreeLearnDatabaseHelper(context());
        runner = new DatabaseTransactionRunner();
        navigation = new SqliteNavigationStore(helper, runner, coordinator());
        wiring = new LibraryWiring(new SqliteLibraryStore(helper), navigation);
        root = seed();

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
    // 1. A topic with a summary draws it in the working row.
    // ------------------------------------------------------------------

    @Test
    public void aTopicWithASummaryDrawsItInTheWorkingRow() {
        installSummary(root.id, SUMMARY_TEXT);
        presenter.start();

        assertEquals("the summary line is visible on the row that carries the topic",
                View.VISIBLE, rowView(TOPIC_TITLE, R.id.library_topic_summary).getVisibility());
        assertEquals("and it reads exactly the stored summary",
                SUMMARY_TEXT, textInRow(TOPIC_TITLE, R.id.library_topic_summary));

        // The row's 052 facts survive the addition untouched: the title is still the root heading
        // and the meta line is still the count-and-time line.
        assertEquals(TOPIC_TITLE, textInRow(TOPIC_TITLE, R.id.library_topic_title));
        String meta = textInRow(TOPIC_TITLE, R.id.library_topic_meta);
        assertTrue("the meta line still names the branch count: " + meta,
                meta.contains(context().getString(R.string.library_topic_meta, 1, "")));
    }

    // ------------------------------------------------------------------
    // 2. A topic without a summary draws one line fewer, never a placeholder.
    // ------------------------------------------------------------------

    @Test
    public void aTopicWithoutASummaryDrawsOneLineFewerAndNeverAPlaceholder() {
        presenter.start();

        assertEquals("a topic with no summary must not draw the summary line", View.GONE,
                rowView(TOPIC_TITLE, R.id.library_topic_summary).getVisibility());
        List<String> withoutSummary = texts(R.id.library_rows);
        String forbidden = context().getString(R.string.navigation_summary_empty);
        for (String sentence : withoutSummary) {
            assertFalse("no explanation may stand in for the missing summary: " + sentence,
                    sentence.contains(forbidden));
        }

        installSummary(root.id, SUMMARY_TEXT);
        presenter.onResumed();

        assertEquals("with a summary the row draws it", View.VISIBLE,
                rowView(TOPIC_TITLE, R.id.library_topic_summary).getVisibility());
        List<String> withSummary = texts(R.id.library_rows);
        assertEquals("a stored summary is exactly one line more, and still nothing else",
                withoutSummary.size() + 1, withSummary.size());
        assertTrue("and the line is the stored summary: " + withSummary,
                withSummary.contains(SUMMARY_TEXT));
    }

    // ------------------------------------------------------------------
    // Fixtures and drivers.
    // ------------------------------------------------------------------

    /** One topic, one root branch: the smallest shape the working list draws a row for. */
    private Branch seed() {
        SQLiteDatabase database = db();
        TopicDao.insert(database, new Topic(TOPIC, null, T0, T0));
        Branch rootBranch = Branch.root(TOPIC, T0);
        BranchDao.insert(database, rootBranch);
        rootBranch.setTitle(TOPIC_TITLE, T0 + 1L);
        BranchDao.update(database, rootBranch);
        return rootBranch;
    }

    /** The summary artifact exactly as the derived service writes one, for the root branch. */
    private void installSummary(String branchId, String summary) {
        AiArtifactDao.insert(db(), new AiArtifactDao.Row(
                "6b6b6b6b-0000-4000-8000-0000000000d1", ArtifactKind.BRANCH_SUMMARY.wireId, branchId,
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
    // Readers, all through the screen the product draws.
    // ------------------------------------------------------------------

    private View rowView(String rowTitle, int id) {
        final View[] found = new View[1];
        instrumentation.runOnMainSync(() -> {
            View row = row(rowTitle);
            if (row == null) {
                fail("no working row titled " + rowTitle + " is on screen, the list holds: "
                        + texts(R.id.library_rows));
            }
            found[0] = row.findViewById(id);
        });
        assertNotNull("the row titled " + rowTitle + " draws no view " + id, found[0]);
        return found[0];
    }

    private String textInRow(String rowTitle, int id) {
        View target = rowView(rowTitle, id);
        final String[] found = new String[1];
        instrumentation.runOnMainSync(() -> found[0] = ((TextView) target).getText().toString());
        return found[0];
    }

    /** The working row whose title line reads {@code rowTitle}, or null. */
    private View row(String rowTitle) {
        View container = view(R.id.library_rows);
        if (!(container instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) container;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.findViewById(R.id.library_topic_title) != null
                    && rowTitle.equals(((TextView) child.findViewById(R.id.library_topic_title))
                            .getText().toString())) {
                return child;
            }
        }
        return null;
    }

    private View view(int id) {
        return screen.findViewById(id);
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

    private SQLiteDatabase db() {
        return helper.getWritableDatabase();
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
