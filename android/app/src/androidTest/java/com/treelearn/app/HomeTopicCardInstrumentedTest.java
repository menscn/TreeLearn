package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
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
 * T-2026-09-27-006 acceptance evidence for the home history-topic card and the home empty state,
 * executed on a real device: a stored topic is drawn as a card carrying its title and its meta line
 * (branch count + last use), the card still opens that topic's conversation, and a home with no
 * topics says so instead of drawing a counter over nothing.
 *
 * <p><strong>What is driven here.</strong> The real shell - {@code MainActivity} mounted over the
 * real {@code AppContainer} - because the assertion "the card opens the conversation" is a claim
 * about the shell's wiring, not about the card alone. The rows are seeded through the same DAOs the
 * product writes with, so the card under test is built from a store read rather than from a
 * hand-made {@code TopicCard}.
 *
 * <p><strong>Two environment facts this suite depends on, stated rather than assumed.</strong> (1)
 * The database file is never deleted here: {@code AppContainer} is a process-wide singleton whose
 * {@code SQLiteOpenHelper} keeps its connection, so unlinking the file would leave the shell reading
 * one inode while this fixture writes another. The fixture clears rows instead (a topic delete
 * cascades to its branches and messages), which is visible to the shell's connection because it is
 * the same file. (2) The process the shell reads is the one this class runs in, so the container is
 * built from the rows seeded below; a full-suite run that has already built the container and then
 * unlinked the file is outside what this suite can control.
 *
 * <p>The summary direction is asserted as "absent", not as "shown": a topic's root summary comes
 * from a derived artifact the store has not produced in this fixture, and the rule under test there
 * is the line-count one - no summary means one line fewer, never a placeholder. The summary-shown
 * direction is covered by {@code LibraryTopicSummaryInstrumentedTest} on the library surface, which
 * shares the same field.
 *
 * <p>Holds no real user content and no API key: every literal is an obviously synthetic placeholder.
 */
@RunWith(AndroidJUnit4.class)
public final class HomeTopicCardInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    private static final String TOPIC_A = "e1e1e1e1-0000-4000-8000-00000000e1a1";
    private static final String TITLE_A = "SYNTHETIC-HOME-TOPIC-TITLE";

    /** Comfortably longer than one store read plus one UI post, and shorter than a stuck suite. */
    private static final long WAIT_MS = 10_000L;

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    private TreeLearnDatabaseHelper helper;
    private Activity activity;

    @Before
    public void setUp() {
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
    // 1. A stored topic reads as a card, and the card still opens its conversation.
    // ------------------------------------------------------------------

    @Test
    public void aStoredTopicIsDrawnAsACardWithItsTitleAndMetaAndStillOpensTheConversation() {
        seedTopic(TOPIC_A, TITLE_A, T0, 2);
        activity = launch();

        ViewGroup list = awaitChildren(R.id.home_recent_list);
        View card = awaitLaidOut(list);

        assertEquals("the card is named by its root title", TITLE_A, text(card, R.id.home_topic_title));

        String meta = text(card, R.id.home_topic_meta);
        assertTrue("the meta line counts the topic's branches: " + meta,
                meta.startsWith(context().getString(R.string.home_topic_meta, 1, "")));
        assertTrue("and it carries a non-empty last-use reading: " + meta,
                meta.length() > context().getString(R.string.home_topic_meta, 1, "").length());

        assertEquals("a topic with unread answers shows the dot", View.VISIBLE,
                card.findViewById(R.id.home_topic_unread).getVisibility());
        assertEquals("a topic with no derived summary draws one line fewer, never a placeholder",
                View.GONE, card.findViewById(R.id.home_topic_summary).getVisibility());

        instrumentation.runOnMainSync(card::performClick);
        assertNotNull("a tapped history card opens the conversation on its branch",
                waitFor(R.id.chat_root));
    }

    // ------------------------------------------------------------------
    // 2. An empty home names the fact and draws no counter.
    // ------------------------------------------------------------------

    @Test
    public void anEmptyHomeNamesTheFactAndDrawsNoCounter() {
        activity = launch();

        TextView empty = awaitVisibleText(R.id.home_topics_empty);
        assertEquals("the empty sentence is the prototype's", context().getString(R.string.home_topics_empty),
                empty.getText().toString());
        assertEquals("a home with no topics draws no counter", View.GONE,
                view(R.id.home_recent_count).getVisibility());
        assertEquals("and it draws no card list", View.GONE,
                view(R.id.home_recent_list).getVisibility());
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
        MessageDao.insert(database, Message.user(root.id, 0, "SYNTHETIC-HOME-QUESTION", now));
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

    /**
     * Waits for a list to receive its rows. The home list is filled from a store read that comes
     * back through a UI post, so a tap must not be attempted before it lands.
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
        throw new AssertionError("the home's first card was never laid out");
    }

    /** Waits for a text view the state decides to show, then returns it. */
    private TextView awaitVisibleText(int id) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final TextView[] found = new TextView[1];
            instrumentation.runOnMainSync(() -> {
                View target = activity.findViewById(id);
                if (target instanceof TextView && target.getVisibility() == View.VISIBLE
                        && ((TextView) target).getText().length() > 0) {
                    found[0] = (TextView) target;
                }
            });
            if (found[0] != null) {
                return found[0];
            }
            SystemClock.sleep(50L);
        }
        throw new AssertionError("text view " + id + " never became visible within " + WAIT_MS
                + "ms");
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

    private String text(View root, int id) {
        String[] found = new String[1];
        instrumentation.runOnMainSync(() -> {
            View child = root.findViewById(id);
            assertNotNull("the card is missing " + id, child);
            found[0] = ((TextView) child).getText().toString();
        });
        return found[0];
    }

    private SQLiteDatabase db() {
        return helper.getWritableDatabase();
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
