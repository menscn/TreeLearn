package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * T-052 acceptance evidence for the bottom-tab shell and the library screen, executed on a real
 * device: the three tabs switch and highlight, a second-level screen hides the bar and returns to
 * the tab that opened it, the library's own back arrow leaves a live search before it steps out, the
 * library lists the topics the store actually holds, and an empty store gets the guidance sentence
 * rather than a blank list.
 *
 * <p><strong>Every move is made through a control the product really draws</strong> - the tab items,
 * a library row, the library's and the conversation's back buttons, the navigation page's back
 * button. Nothing here calls a private method or a platform back gesture, because the point of the
 * slice is the shell's own wiring rather than the presenter underneath it (the phase rules are pinned
 * on the JVM by {@code LibraryPresenterTest}).
 *
 * <p><strong>Two environment facts this suite depends on, stated rather than assumed.</strong> (1) The
 * database file is never deleted here: {@code AppContainer} is a process-wide singleton whose
 * {@code SQLiteOpenHelper} keeps its connection, so unlinking the file would leave the shell reading
 * one inode while this fixture writes another. The fixture clears rows instead (a topic delete
 * cascades to its branches and messages), which is visible to the shell's connection because it is
 * the same file. (2) The process the shell reads is the one this class runs in, so the container is
 * built from the rows seeded below; a full-suite run that has already built the container and then
 * unlinked the file is outside what this suite can control.
 *
 * <p>Holds no real user content and no API key: every literal is an obviously synthetic placeholder.
 */
@RunWith(AndroidJUnit4.class)
public final class BottomTabShellInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    private static final String TOPIC_A = "d1d1d1d1-0000-4000-8000-00000000d1a1";
    private static final String TOPIC_B = "d2d2d2d2-0000-4000-8000-00000000d2b2";

    private static final String TITLE_A = "SYNTHETIC-LIBRARY-TITLE-A";
    private static final String QUESTION_B = "SYNTHETIC-LIBRARY-QUESTION-B";

    /** Comfortably longer than one store read plus one UI post, and shorter than a stuck suite. */
    private static final long WAIT_MS = 10_000L;

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private TreeLearnDatabaseHelper helper;
    private Activity activity;

    @Before
    public void setUp() {
        // Earlier suites unlink the database file; the process-wide container would keep reading
        // that deleted inode. Start this case on a container built from the file that exists now.
        AppContainer.resetForTests();
        helper = new TreeLearnDatabaseHelper(context());
        clearTopics();
    }

    @After
    public void tearDown() {
        if (activity != null) {
            activity.finish();
            activity = null;
        }
        if (helper != null) {
            clearTopics();
            helper.close();
            helper = null;
        }
    }

    // ------------------------------------------------------------------
    // 1. The two tabs and the settings room.
    // ------------------------------------------------------------------

    @Test
    public void theTwoTabsAndHomeSettingsEntryReachTheirScreens() {
        activity = launch();

        assertEquals("the bar is the shell, so it is on screen with the home tab",
                View.VISIBLE, view(R.id.tab_bar).getVisibility());
        android.view.ViewGroup bar = (android.view.ViewGroup) view(R.id.tab_bar_pill);
        assertEquals("only Home and Library remain in the bottom bar", 2,
                bar.getChildCount());
        assertHighlighted(R.id.tab_home);

        click(R.id.tab_library);
        assertNotNull("the library tab mounts the library screen", waitFor(R.id.library_root));
        assertHighlighted(R.id.tab_library);

        click(R.id.tab_home);
        assertNotNull("the home tab mounts the home screen again", waitFor(R.id.home_draft));
        click(R.id.home_settings_button);
        assertNotNull("the home gear opens settings", waitFor(R.id.settings_root));
        assertBarHidden("settings is a room, not a bottom tab");

        click(R.id.settings_back);
        assertNotNull("settings returns to the home that opened it", waitFor(R.id.home_draft));
        assertHighlighted(R.id.tab_home);

        click(R.id.tab_library);
        assertNotNull("and the round trip can be walked again", waitFor(R.id.library_root));
        assertHighlighted(R.id.tab_library);
    }

    // ------------------------------------------------------------------
    // 2. Second-level screens: no bar, and back lands on the tab that opened them.
    // ------------------------------------------------------------------

    /**
     * The library's own arrow is the same request the system gesture makes, and it is consumed in
     * the same order: a live query goes first and the library's own lists come back, then a second
     * press leaves for the home tab. The test drives the drawn arrow both times rather than the
     * gesture, and reads the scope line back, so "the search went away" is a visible fact.
     */
    @Test
    public void theLibraryArrowLeavesTheSearchStateThenStepsBackToHome() {
        seedTopic(TOPIC_A, TITLE_A, T0, 0);
        activity = launch();

        click(R.id.tab_library);
        assertNotNull("the library tab mounts the library screen", waitFor(R.id.library_root));
        assertEquals("the arrow is drawn on the library header, as on the settings header",
                View.VISIBLE, view(R.id.library_back).getVisibility());
        assertEquals("and no search is live, so its scope line is away",
                View.GONE, view(R.id.library_search_scope).getVisibility());

        // A live query is the first thing the arrow takes away.
        final EditText box = (EditText) view(R.id.library_search_input);
        instrumentation.runOnMainSync(() -> box.setText("SYNTHETIC-QUERY"));
        assertNotNull("typing mounts the search scope line", awaitVisible(R.id.library_search_scope));

        click(R.id.library_back);
        assertEquals("the first press leaves the search state, clearing the box",
                "", box.getText().toString());
        assertNotNull("the library's own lists are what it lands on", waitFor(R.id.library_rows));
        assertEquals("and the search scope line is taken away with it",
                View.GONE, view(R.id.library_search_scope).getVisibility());
        assertHighlighted(R.id.tab_library);

        // A library showing its own lists is the second press's business: it steps back to home.
        click(R.id.library_back);
        assertNotNull("with no query live the arrow steps back to the home tab",
                waitFor(R.id.home_draft));
        assertHighlighted(R.id.tab_home);
    }

    @Test
    public void aConversationOpenedFromHomeHidesTheBarAndBackLandsOnHome() {
        seedTopic(TOPIC_A, TITLE_A, T0, 0);
        activity = launch();

        clickRow(awaitChildren(R.id.home_recent_list));
        assertNotNull("a tapped recent-learning row opens the conversation on its branch",
                waitFor(R.id.chat_root));
        assertBarHidden("a conversation is a second-level screen");

        click(R.id.chat_back);
        assertNotNull("leaving it lands back on the home screen", waitFor(R.id.home_draft));
        assertHighlighted(R.id.tab_home);
    }

    @Test
    public void aConversationOpenedFromTheLibraryHidesTheBarAndNavigationLandsBackOnTheLibrary() {
        seedTopic(TOPIC_A, TITLE_A, T0, 0);
        activity = launch();

        click(R.id.tab_library);
        assertNotNull(waitFor(R.id.library_root));
        clickRow(awaitChildren(R.id.library_rows));
        assertNotNull("a tapped library row opens the conversation on its branch",
                waitFor(R.id.chat_root));
        assertBarHidden("a conversation is a second-level screen");

        // 2026-10-25 (T-2026-10-25-096): the tree entry lives in the reading header now, not in the
        // topbar; it reports the same navigation gesture.
        click(R.id.chat_branch_tree);
        assertNotNull("the conversation's navigation entry opens the navigation page",
                waitFor(R.id.navigation_root));
        assertBarHidden("the navigation page is a second-level screen");

        click(R.id.navigation_back);
        assertNotNull("leaving the page returns to the conversation it was opened from",
                waitFor(R.id.chat_root));
        assertBarHidden("and the sheet is still a second-level screen on the way back");

        click(R.id.chat_back);
        assertNotNull("leaving the conversation lands on the tab that opened it",
                waitFor(R.id.library_root));
        assertHighlighted(R.id.tab_library);
    }

    // ------------------------------------------------------------------
    // 3. The library lists what the store holds, field by field.
    // ------------------------------------------------------------------

    @Test
    public void theLibraryListsTheStoredTopicsWithTheirOwnFields() {
        seedTopic(TOPIC_A, TITLE_A, T0 + 1_000L, 3);
        seedTopic(TOPIC_B, null, T0, 0);
        activity = launch();

        click(R.id.tab_library);
        ViewGroup rows = awaitChildren(R.id.library_rows);

        assertEquals("the in-progress segment exposes its item count",
                context().getString(R.string.library_segment_in_progress) + " 2",
                ((TextView) view(R.id.library_segment_in_progress)).getText().toString());
        assertEquals("the completed segment exposes its item count",
                context().getString(R.string.library_segment_done) + " 0",
                ((TextView) view(R.id.library_segment_done)).getText().toString());
        // 2026-10-25 (T-2026-10-25-102): two working topics and no archived branch is one populated
        // side, so the capsule is not drawn and the label names it instead.
        assertEquals("no switch is drawn while only one side has rows", View.GONE,
                view(R.id.library_segment).getVisibility());
        String liveLabel = ((TextView) view(R.id.library_segment_summary)).getText().toString();
        assertTrue("the label names the live side: " + liveLabel,
                liveLabel.contains(context().getString(R.string.library_unit_topics, 2)));
        assertNotNull("library rows use the prototype card surface", rows.getChildAt(0).getBackground());

        assertEquals("one row per stored topic", 2, rows.getChildCount());
        assertEquals("the list is newest first", 2, storedTopicCount());

        View newest = awaitLaidOut(rows);
        assertEquals("the derived root title is the display identity",
                TITLE_A, text(newest, R.id.library_topic_title));
        String meta = text(newest, R.id.library_topic_meta);
        assertTrue("the row carries the branch count: " + meta,
                meta.startsWith(context().getString(R.string.library_topic_meta, 1, "")));
        assertTrue("and the unread the store counted: " + meta,
                meta.endsWith(context().getString(R.string.library_topic_unread, 3)));
        assertEquals("a topic with unread answers shows the dot", View.VISIBLE,
                newest.findViewById(R.id.library_topic_unread).getVisibility());

        // 2026-10-22 Scheme A removed the decorative accent bar from the library card
        // (T-2026-10-22-057); the card surface and the unread dot above are what the row says.
        View oldest = rows.getChildAt(1);
        assertEquals("a topic whose title was never derived is named by its first question",
                QUESTION_B, text(oldest, R.id.library_topic_title));
        assertEquals("a topic with nothing unread hides the dot", View.GONE,
                oldest.findViewById(R.id.library_topic_unread).getVisibility());
    }

    @Test
    public void anEmptyLibraryShowsTheGuidanceInsteadOfABlankList() {
        activity = launch();

        click(R.id.tab_library);
        assertNotNull(waitFor(R.id.library_root));
        assertNotNull("the library screen is up", waitFor(R.id.library_rows));
        assertEquals("the prototype search scope stays hidden outside search",
                View.GONE, view(R.id.library_search_scope).getVisibility());

        TextView state = awaitState();
        assertEquals("an empty store shows the empty state", View.VISIBLE, state.getVisibility());
        assertEquals(View.GONE, view(R.id.library_rows).getVisibility());
        String sentence = state.getText().toString();
        assertTrue("the empty state names the fact: " + sentence,
                sentence.contains(context().getString(R.string.library_empty)));
        assertTrue("and it points at the next step: " + sentence,
                sentence.contains(context().getString(R.string.library_empty_guide)));
    }

    // ------------------------------------------------------------------
    // Fixtures and drivers.
    // ------------------------------------------------------------------

    private Activity launch() {
        Intent intent = new Intent(context(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity started = instrumentation.startActivitySync(intent);
        assertNotNull("MainActivity failed to launch", started);
        return started;
    }

    /**
     * One topic exactly as the product writes one - a topic row, its root branch, the learner's first
     * question - plus, when asked for, the unread gap a stable answer leaves behind
     * ({@link Branch#onStableAnswer} moves the sequence the list counts).
     */
    private void seedTopic(String topicId, String derivedTitle, long now, int unreadAnswers) {
        SQLiteDatabase database = db();
        TopicDao.insert(database, new Topic(topicId, null, now, now));
        Branch root = Branch.root(topicId, now);
        BranchDao.insert(database, root);
        String question = topicId.equals(TOPIC_B) ? QUESTION_B : "SYNTHETIC-LIBRARY-QUESTION";
        MessageDao.insert(database, Message.user(root.id, 0, question, now));
        MessageDao.insert(database, Message.ai(root.id, 1, now));
        if (derivedTitle != null) {
            root.setTitle(derivedTitle, now);
        }
        for (int i = 0; i < unreadAnswers; i++) {
            root.onStableAnswer(now);
        }
        BranchDao.update(database, root);
    }

    private void clearTopics() {
        db().delete(TreeLearnSchema.Topics.TABLE, null, null);
    }

    private int storedTopicCount() {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(*) FROM " + TreeLearnSchema.Topics.TABLE, null)) {
            assertTrue("the count query must return a row", cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    /** Taps a row of a list that has already been awaited, so no tap lands on an empty container. */
    private void clickRow(ViewGroup rows) {
        final View row = rows.getChildAt(0);
        instrumentation.runOnMainSync(row::performClick);
    }

    /**
     * Waits for a list to receive its rows. The library and the home list are filled from a store read
     * that comes back through a UI post, so a tap must not be attempted before it lands.
     */
    private ViewGroup awaitChildren(int containerId) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final View[] found = new View[1];
            instrumentation.runOnMainSync(() -> {
                View container = activity.findViewById(containerId);
                if (container instanceof ViewGroup && ((ViewGroup) container).getChildCount() > 0) {
                    found[0] = container;
                }
            });
            if (found[0] != null) {
                return (ViewGroup) found[0];
            }
            SystemClock.sleep(50L);
        }
        throw new AssertionError("no row arrived in view " + containerId + " within " + WAIT_MS
                + "ms");
    }

    /**
     * Waits for the first row to be measured. A row is added before the next layout pass, so a
     * geometry assertion made immediately after {@link #awaitChildren} would read zeroes.
     */
    private View awaitLaidOut(ViewGroup rows) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final View[] found = new View[1];
            instrumentation.runOnMainSync(() -> {
                View row = rows.getChildAt(0);
                if (row != null && row.getHeight() > 0) {
                    found[0] = row;
                }
            });
            if (found[0] != null) {
                return found[0];
            }
            SystemClock.sleep(50L);
        }
        throw new AssertionError("the library's first row was never laid out");
    }

    private TextView awaitState() {
        long deadline = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final TextView[] found = new TextView[1];
            instrumentation.runOnMainSync(() -> {
                TextView state = activity.findViewById(R.id.library_state);
                if (state != null && state.getVisibility() == View.VISIBLE
                        && state.getText().length() > 0) {
                    found[0] = state;
                }
            });
            if (found[0] != null) {
                return found[0];
            }
            SystemClock.sleep(50L);
        }
        throw new AssertionError("the library never settled on a state sentence");
    }

    /**
     * Waits for a view the state decides to show. A state-backed home view may exist in the layout
     * before its content is visible, so a tap must wait for the visibility rather than only the view.
     */
    private View awaitVisible(int id) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            View[] found = new View[1];
            instrumentation.runOnMainSync(() -> {
                View target = activity.findViewById(id);
                if (target != null && target.getVisibility() == View.VISIBLE) {
                    found[0] = target;
                }
            });
            if (found[0] != null) {
                return found[0];
            }
            SystemClock.sleep(50L);
        }
        throw new AssertionError("view " + id + " never became visible within " + WAIT_MS + "ms");
    }

    private View waitFor(int id) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            View[] found = new View[1];
            instrumentation.runOnMainSync(() -> found[0] = activity.findViewById(id));
            if (found[0] != null) {
                return found[0];
            }
            SystemClock.sleep(50L);
        }
        throw new AssertionError("view " + id + " never appeared within " + WAIT_MS + "ms");
    }

    private View view(int id) {
        View[] found = new View[1];
        instrumentation.runOnMainSync(() -> found[0] = activity.findViewById(id));
        assertNotNull("view " + id + " is missing", found[0]);
        return found[0];
    }

    private void click(int id) {
        final View target = view(id);
        instrumentation.runOnMainSync(target::performClick);
    }

    private String text(View root, int id) {
        String[] found = new String[1];
        instrumentation.runOnMainSync(() -> found[0] =
                ((TextView) root.findViewById(id)).getText().toString());
        return found[0];
    }

    /**
     * The highlight is two visible facts, not one: the label's ink and the icon's tint. Both are read
     * back per tab, so a bar that highlighted the wrong item could not pass by highlighting anything.
     */
    private void assertHighlighted(int selectedTabId) {
        for (int tabId : new int[]{R.id.tab_home, R.id.tab_library}) {
            int expected = context().getColor(
                    tabId == selectedTabId ? R.color.tl_green_deep : R.color.tl_ink3);
            assertEquals("tab " + tabId + " label", expected, labelColor(tabId));
            assertEquals("tab " + tabId + " icon tint", expected, iconTint(tabId));
        }
    }

    /**
     * The two readers below look their tab up themselves rather than through {@link #view}: they are
     * already running on the main thread, and asking {@code view} for the same work would nest one
     * {@code runOnMainSync} inside another - which the platform refuses outright. Icon and label are
     * named per tab, so neither reader guesses at a shared id.
     */
    private int labelColor(int tabId) {
        int[] color = new int[1];
        instrumentation.runOnMainSync(() -> color[0] =
                ((TextView) activity.findViewById(tabId).findViewById(labelOf(tabId)))
                        .getCurrentTextColor());
        return color[0];
    }

    private int iconTint(int tabId) {
        int[] color = new int[1];
        instrumentation.runOnMainSync(() -> color[0] = ((ImageView) activity.findViewById(tabId)
                .findViewById(iconOf(tabId))).getImageTintList().getDefaultColor());
        return color[0];
    }

    private static int labelOf(int tabId) {
        if (tabId == R.id.tab_home) {
            return R.id.tab_home_label;
        }
        return tabId == R.id.tab_home ? R.id.tab_home_label : R.id.tab_library_label;
    }

    private static int iconOf(int tabId) {
        if (tabId == R.id.tab_home) {
            return R.id.tab_home_icon;
        }
        return tabId == R.id.tab_home ? R.id.tab_home_icon : R.id.tab_library_icon;
    }

    private void assertBarHidden(String why) {
        assertEquals(why, View.GONE, view(R.id.tab_bar).getVisibility());
    }

    private SQLiteDatabase db() {
        return helper.getWritableDatabase();
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
