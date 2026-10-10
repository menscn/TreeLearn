package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.EditText;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.ChatMotion;
import com.treelearn.app.screen.HomeMotion;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * T-2026-09-27-007 acceptance evidence for the home's entrance motion, executed on a real device:
 * the home enters with the prototype's slide and fade while the library and the settings are swapped
 * in exactly as before, each history card waits its own turn behind the ones before it, the entrance
 * is a drawing transform rather than a change to any view's own state, a redraw is not a second
 * entrance, and a card still opens its conversation while it is entering.
 *
 * <p><strong>Why the entrance is read at the moment it is attached, never after.</strong> A view
 * animation that runs to its end does not stay attached: when it expires, the parent clears it
 * during the next draw (the SDK's {@code ViewGroup.finishAnimatingView} calls {@code clearAnimation}
 * on any child whose animation finished without {@code fillAfter}), so reading
 * {@code view.getAnimation()} a few hundred milliseconds after a launch reads null whether or not an
 * entrance ever ran - which is exactly the mistake that makes a missing entrance invisible to a
 * test. This suite therefore attaches probes at {@code onActivityPostCreated} - the end of
 * {@code performCreate}, when the shell has mounted the home and attached its entrance and not one
 * frame has been drawn yet - and every later swap and every card row is captured by a hierarchy
 * listener a few milliseconds after it is made. Every assertion reads the animation object the shell
 * actually handed the platform - the same object that will run - so a screen that quietly stopped
 * staggering the cards cannot pass this suite, and no assertion races a 280ms window.
 *
 * <p><strong>The one thing this suite asserts about the technique, and why it matters.</strong>
 * {@link #theEntranceIsDrawnNotMoved} pins that the entrance leaves every view's own alpha and
 * translation at rest. A view animation is applied to the matrix the view is drawn through, so the
 * measurement pass, the scroll range and the touch targets are untouched by construction; a property
 * animation would instead have written a transient state onto the views themselves. That is also
 * why the low-motion path - where the screen attaches no animation at all - cannot leave anything
 * translucent: skipping the entrance and running it differ only in whether an animation object is
 * attached, never in the state the views are left in.
 *
 * <p><strong>The precondition this suite depends on, stated rather than assumed.</strong> The
 * entrance is gated by the system's animator scale ({@code ChatMotion.reduced}), so on a device
 * with animations switched off in developer options the home is correctly still and there is no
 * animation to read. {@link #assumeTheSystemKeepsItsAnimations} asserts that precondition first, so
 * such a device fails with a sentence naming the device state rather than with a mysterious null.
 * This suite does not change that setting: the handset is shared with another line of work, and a
 * global switch flipped by a test would be a side effect on someone else's run.
 *
 * <p>Holds no real user content and no API key: every literal is an obviously synthetic
 * placeholder.
 */
@RunWith(AndroidJUnit4.class)
public final class HomeMotionInstrumentedTest {

    private static final long T0 = 1_756_100_000_000L;

    private static final String TOPIC_A = "f1f1f1f1-0000-4000-8000-00000000f1a1";
    private static final String TOPIC_B = "f1f1f1f1-0000-4000-8000-00000000f1a2";
    private static final String TOPIC_C = "f1f1f1f1-0000-4000-8000-00000000f1a3";
    private static final String TITLE_A = "SYNTHETIC-MOTION-TOPIC-ONE";
    private static final String TITLE_B = "SYNTHETIC-MOTION-TOPIC-TWO";
    private static final String TITLE_C = "SYNTHETIC-MOTION-TOPIC-THREE";

    /** Comfortably longer than one store read plus one UI post, and shorter than a stuck suite. */
    private static final long WAIT_MS = 10_000L;

    /** One read of "this view was just swapped in (or just added): here is its animation, if any." */
    private static final class Captured {
        final View view;
        final Animation animation;

        Captured(View view, Animation animation) {
            this.view = view;
            this.animation = animation;
        }
    }

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    /** Every first-level screen swapped in after the probes were installed, in swap order. */
    private final List<Captured> swaps = Collections.synchronizedList(new ArrayList<Captured>());
    /** Every history card row the home drew after the probes were installed, in draw order. */
    private final List<Captured> cards = Collections.synchronizedList(new ArrayList<Captured>());

    /** The home's entrance, read at {@code onActivityCreated} - attached, but not yet drawn. */
    private volatile Animation firstEntrance;

    private TreeLearnDatabaseHelper helper;
    private Activity activity;

    private final Application.ActivityLifecycleCallbacks probes =
            new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity created, Bundle state) {
                }

                @Override
                public void onActivityPostCreated(Activity created, Bundle state) {
                    // performCreate's very end: the shell has mounted the home and attached its
                    // entrance, and not a single frame has been drawn yet - so the entrance is
                    // there to be read, and every later swap and row will pass the probes below.
                    if (!(created instanceof MainActivity)) {
                        return;
                    }
                    View home = created.findViewById(R.id.home_screen);
                    firstEntrance = home == null ? null : ((View) home.getParent()).getAnimation();

                    ViewGroup host = created.findViewById(R.id.shell_host);
                    if (host != null) {
                        host.setOnHierarchyChangeListener(swapProbe);
                    }
                    ViewGroup list = created.findViewById(R.id.home_recent_list);
                    if (list != null) {
                        list.setOnHierarchyChangeListener(cardProbe);
                    }
                }

                @Override
                public void onActivityStarted(Activity started) {
                }

                @Override
                public void onActivityResumed(Activity resumed) {
                }

                @Override
                public void onActivityPaused(Activity paused) {
                }

                @Override
                public void onActivityStopped(Activity stopped) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity saved, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity destroyed) {
                }
            };

    /**
     * Captures each swap the moment its view is in the host. The read is posted because the shell
     * starts the entrance right after {@code addView}, within this same main-thread message.
     */
    private final ViewGroup.OnHierarchyChangeListener swapProbe =
            new ViewGroup.OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(View parent, View child) {
                    child.post(() -> swaps.add(new Captured(child, child.getAnimation())));
                }

                @Override
                public void onChildViewRemoved(View parent, View child) {
                }
            };

    /** Captures each history row the moment it is drawn, so its stagger is read before it ends. */
    private final ViewGroup.OnHierarchyChangeListener cardProbe =
            new ViewGroup.OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(View parent, View child) {
                    child.post(() -> cards.add(new Captured(child, child.getAnimation())));
                }

                @Override
                public void onChildViewRemoved(View parent, View child) {
                }
            };

    @Before
    public void setUp() {
        helper = new TreeLearnDatabaseHelper(context());
        clearTopics();
        ((Application) context().getApplicationContext())
                .registerActivityLifecycleCallbacks(probes);
    }

    @After
    public void tearDown() {
        if (activity != null) {
            activity.finish();
            activity = null;
        }
        // unregisterActivityLifecycleCallbacks needs API 29; this handset runs 13, and on 28 the
        // probes would simply stay registered for the rest of this process.
        if (Build.VERSION.SDK_INT >= 29) {
            ((Application) context().getApplicationContext())
                    .unregisterActivityLifecycleCallbacks(probes);
        }
        if (helper != null) {
            clearTopics();
            helper.close();
            helper = null;
        }
    }

    // ------------------------------------------------------------------
    // 1. The home enters; the other two first-level screens still do not.
    // ------------------------------------------------------------------

    @Test
    public void onlyTheHomeEntersWithTheMotionAndTheOtherFirstLevelScreensDoNot() {
        assumeTheSystemKeepsItsAnimations();
        seedTopic(TOPIC_A, TITLE_A, T0);

        activity = launch();
        Animation home = firstEntrance;
        assertNotNull("the home must enter with the prototype's motion", home);
        assertEquals("the screen entrance is the prototype's 280ms",
                HomeMotion.SCREEN_ENTER_MS, home.getDuration());
        assertEquals("and it starts at once", 0L, home.getStartOffset());

        click(R.id.tab_library);
        assertNotNull("the library tab mounts the library screen", waitFor(R.id.library_root));
        assertNull("the library is swapped in exactly as it was - no entrance motion",
                awaitSwap(R.id.library_root).animation);

        click(R.id.home_settings_button);
        assertNotNull("the home gear mounts the settings screen", waitFor(R.id.settings_root));
        assertNull("the settings too", awaitSwap(R.id.settings_root).animation);

        click(R.id.settings_back);
        Animation again = awaitSwap(R.id.home_screen).animation;
        assertNotNull("coming back to the home replays its entrance", again);
        assertEquals("and it is the same 280ms entrance", HomeMotion.SCREEN_ENTER_MS,
                again.getDuration());
    }

    // ------------------------------------------------------------------
    // 2. Each card waits its own turn.
    // ------------------------------------------------------------------

    @Test
    public void eachHistoryCardWaitsItsOwnTurnBehindTheOnesBeforeIt() {
        assumeTheSystemKeepsItsAnimations();
        seedTopic(TOPIC_A, TITLE_A, T0);
        seedTopic(TOPIC_B, TITLE_B, T0 + 60_000L);
        seedTopic(TOPIC_C, TITLE_C, T0 + 120_000L);

        activity = launch();

        awaitCardCount(3);
        for (int order = 0; order < 3; order++) {
            Captured card = cards.get(order);
            assertNotNull("card " + order + " must enter", card.animation);
            assertEquals("every card takes the prototype's 350ms",
                    HomeMotion.CARD_ENTER_MS, card.animation.getDuration());
            assertEquals("card " + order + " starts after the ones before it",
                    HomeMotion.cardDelayMs(order), card.animation.getStartOffset());
        }
    }

    // ------------------------------------------------------------------
    // 3. The entrance draws; it does not move the layout.
    // ------------------------------------------------------------------

    @Test
    public void theEntranceIsDrawnNotMoved() {
        assumeTheSystemKeepsItsAnimations();
        seedTopic(TOPIC_A, TITLE_A, T0);

        activity = launch();
        ViewGroup list = (ViewGroup) view(R.id.home_recent_list);
        View surface = view(R.id.home_screen);
        View card = awaitFirstChild(list);

        instrumentation.runOnMainSync(() -> {
            assertEquals("the entrance must not leave the screen translucent",
                    1f, surface.getAlpha(), 0f);
            assertEquals("nor shifted, which would move what the finger hits",
                    0f, surface.getTranslationX(), 0f);
            assertEquals("the same for a card", 1f, card.getAlpha(), 0f);
            assertEquals(0f, card.getTranslationY(), 0f);
        });
    }

    // ------------------------------------------------------------------
    // 4. A redraw is not a second entrance.
    // ------------------------------------------------------------------

    @Test
    public void aLaterRedrawDoesNotReplayTheEntrance() {
        assumeTheSystemKeepsItsAnimations();
        seedTopic(TOPIC_A, TITLE_A, T0);

        activity = launch();
        awaitCardCount(1);
        Captured first = cards.get(0);
        assertNotNull("the first draw is the entrance", first.animation);

        // A keystroke is the cheapest honest redraw: it travels the same state-to-render path the
        // composer uses on every key, which is where a re-applied entrance would flicker.
        instrumentation.runOnMainSync(() -> ((EditText) activity.findViewById(R.id.home_draft))
                .setText("SYNTHETIC-MOTION-DRAFT"));

        Captured redrawn = awaitCardDrawnAfter(first.view);
        assertNull("a redraw is not an entrance - the history cards must not enter again",
                redrawn.animation);
        assertEquals("and the redraw still holds the card's title", TITLE_A,
                text(redrawn.view, R.id.home_topic_title));
    }

    // ------------------------------------------------------------------
    // 5. The card is usable while it enters.
    // ------------------------------------------------------------------

    @Test
    public void aHistoryCardOpensItsConversationWhileItIsStillEntering() {
        assumeTheSystemKeepsItsAnimations();
        seedTopic(TOPIC_A, TITLE_A, T0);

        activity = launch();
        ViewGroup list = (ViewGroup) view(R.id.home_recent_list);
        View card = awaitFirstChild(list);

        assertEquals("a card is fully present while it enters, never half-drawn",
                View.VISIBLE, card.getVisibility());
        assertTrue("and it remains the tap target", card.isClickable());

        instrumentation.runOnMainSync(card::performClick);
        assertNotNull("a tapped card opens its conversation even mid-entrance",
                waitFor(R.id.chat_root));
    }

    // ------------------------------------------------------------------
    // The precondition, and the fixtures and drivers.
    // ------------------------------------------------------------------

    /**
     * The entrance only exists while the system allows animations. A device that has them switched
     * off is a device-state problem, and it must be named as such instead of surfacing as a null.
     */
    private void assumeTheSystemKeepsItsAnimations() {
        float scale = Settings.Global.getFloat(context().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        assertFalse("this handset has animator_duration_scale=" + scale + ", so the home correctly "
                        + "stays still and there is no entrance to read; enable animations in "
                        + "developer options before running this suite",
                ChatMotion.isStatic(scale));
    }

    private Activity launch() {
        Intent intent = new Intent(context(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity started = instrumentation.startActivitySync(intent);
        assertNotNull("MainActivity failed to launch", started);
        return started;
    }

    /** One topic exactly as the product writes one: a topic row and its root branch. */
    private void seedTopic(String topicId, String derivedTitle, long now) {
        SQLiteDatabase database = db();
        TopicDao.insert(database, new Topic(topicId, null, now, now));
        Branch root = Branch.root(topicId, now);
        BranchDao.insert(database, root);
        MessageDao.insert(database, Message.user(root.id, 0, "SYNTHETIC-MOTION-QUESTION", now));
        MessageDao.insert(database, Message.ai(root.id, 1, now));
        root.setTitle(derivedTitle, now);
        BranchDao.update(database, root);
    }

    private void clearTopics() {
        db().delete(TreeLearnSchema.Topics.TABLE, null, null);
    }

    /** Waits for the next swap whose screen contains this root id, then returns and drops it. */
    private Captured awaitSwap(int rootId) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            synchronized (swaps) {
                for (Captured candidate : swaps) {
                    if (candidate.view.findViewById(rootId) != null) {
                        swaps.remove(candidate);
                        return candidate;
                    }
                }
            }
            SystemClock.sleep(20L);
        }
        throw new AssertionError("no swap carrying view id " + rootId + " within " + WAIT_MS + "ms");
    }

    /** Waits until the home has drawn at least this many card rows. */
    private void awaitCardCount(int count) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            synchronized (cards) {
                if (cards.size() >= count) {
                    return;
                }
            }
            SystemClock.sleep(20L);
        }
        throw new AssertionError("the home never drew " + count + " card rows within "
                + WAIT_MS + "ms");
    }

    /** Waits for the first row of this list to be measured, so a geometry read is not a zero. */
    private View awaitFirstChild(ViewGroup rows) {
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
            SystemClock.sleep(20L);
        }
        throw new AssertionError("the home's first card was never laid out");
    }

    /** Waits for the home to draw a row that is not the given one, i.e. for the next render. */
    private Captured awaitCardDrawnAfter(View previous) {
        long deadline = SystemClock.uptimeMillis() + WAIT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            synchronized (cards) {
                for (Captured candidate : cards) {
                    if (candidate.view != previous) {
                        return candidate;
                    }
                }
            }
            SystemClock.sleep(20L);
        }
        throw new AssertionError("the home list was never redrawn within " + WAIT_MS + "ms");
    }

    /** Taps a tab on the main thread; the lookup happens inline, never via a nested main-sync. */
    private void click(int id) {
        instrumentation.runOnMainSync(() -> {
            View target = activity.findViewById(id);
            assertNotNull("view " + id + " is missing", target);
            target.performClick();
        });
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
            found[0] = ((android.widget.TextView) child).getText().toString();
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
