package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ScrollView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.ChatScreen;
import com.treelearn.app.ui.ChatActions;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChatViewState;
import com.treelearn.core.domain.GenerationModel;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class ChatScreenFollowModeInstrumentedTest {

    private Activity activity;

    @After
    public void tearDown() {
        if (activity != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        }
    }

    @Test
    public void oversizedStreamingAnswerPausesAndShowsFollowButton() {
        android.app.Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = instrumentation.startActivitySync(intent);
        assertTrue("test activity must launch", activity != null);

        String messageId = UUID.randomUUID().toString();
        ChatMessage message = ChatMessage.ai(messageId, longAnswer(), "",
                ChatMessage.Status.GENERATING, ChatMessage.Phase.ANSWER, "", 0, 0,
                true, System.currentTimeMillis());
        ChatViewState state = ChatViewState.initial(UUID.randomUUID().toString(),
                GenerationModel.FLASH, false, false)
                .withPhase(ChatViewState.Phase.READY)
                .withBranchTitle("Synthetic branch")
                .withMessages(Collections.singletonList(message))
                .withFollow(true, 0);
        ScrollView[] scroll = new ScrollView[1];
        ImageButton[] follow = new ImageButton[1];
        instrumentation.runOnMainSync(() -> {
            ChatScreen screen = new ChatScreen(activity);
            screen.setActions(recordingActions(new int[1], new int[1]));
            activity.setContentView(screen);
            screen.render(state);
            scroll[0] = screen.findViewById(R.id.chat_scroll);
            follow[0] = screen.findViewById(R.id.chat_follow);
        });
        SystemClock.sleep(250L);
        instrumentation.runOnMainSync(() -> { });

        assertTrue("fixture must exceed the message viewport",
                scroll[0].getChildAt(0).getHeight() > scroll[0].getHeight());
        assertEquals("an overflowing answer pauses auto-follow and exposes recovery",
                View.VISIBLE, follow[0].getVisibility());
    }

    @Test
    public void shortAnswerKeepsFollowingWithoutShowingRecovery() {
        int[] pauseCount = {0};
        ChatMessage message = stableAnswer(UUID.randomUUID().toString(), "Short answer.");
        ChatScreen screen = launchScreen(UUID.randomUUID().toString(), message,
                recordingActions(pauseCount, new int[1]));
        settleView();

        // The slot is the only visibility switch; the key inside it never disappears on its own.
        View followSlot = screen.findViewById(R.id.chat_follow_slot);
        assertEquals(View.GONE, followSlot.getVisibility());
        assertEquals(0, pauseCount[0]);
    }

    @Test
    public void followingButtonKeepsTrackingLaterStreamGrowth() {
        int[] pauseCount = {0};
        int[] followCount = {0};
        String branchId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();
        ChatMessage message = generatingAnswer(messageId, longAnswer());
        ChatViewState state = state(branchId, message);
        ChatScreen screen = launchScreen(state, recordingActions(pauseCount, followCount));
        settleView();

        ImageButton follow = screen.findViewById(R.id.chat_follow);
        View followSlot = screen.findViewById(R.id.chat_follow_slot);
        ScrollView scroll = screen.findViewById(R.id.chat_scroll);
        assertEquals(View.VISIBLE, followSlot.getVisibility());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(follow::performClick);
        assertEquals(1, followCount[0]);
        assertEquals(View.GONE, followSlot.getVisibility());

        String grownAnswer = longAnswer() + "One more generated line.\n";
        ChatMessage grown = generatingAnswer(messageId, grownAnswer);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> screen.render(state.withMessages(Collections.singletonList(grown))));
        settleView();

        assertTrue("reattached follow reaches the newest stream content",
                scroll.getScrollY() + scroll.getHeight() >= scroll.getChildAt(0).getHeight() - 1);
        assertEquals("the same answer does not auto-pause again after explicit reattach",
                1, pauseCount[0]);
    }

    @Test
    public void draggingInsideTheNewestAnswerSwitchesToManualReading() {
        int[] pauseCount = {0};
        String branchId = UUID.randomUUID().toString();
        ChatMessage message = generatingAnswer(UUID.randomUUID().toString(), longAnswer());
        ChatScreen screen = launchScreen(state(branchId, message),
                recordingActions(pauseCount, new int[1]));
        settleView();

        ImageButton follow = screen.findViewById(R.id.chat_follow);
        View followSlot = screen.findViewById(R.id.chat_follow_slot);
        ScrollView scroll = screen.findViewById(R.id.chat_scroll);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(follow::performClick);
        settleView();
        int bottom = scroll.getScrollY();
        sendDragDown(scroll);
        settleView();

        assertTrue("the gesture must move the answer away from its latest position",
                scroll.getScrollY() < bottom);
        assertEquals("manual movement inside the same row pauses follow", 2, pauseCount[0]);
        assertEquals(View.VISIBLE, followSlot.getVisibility());
    }

    @Test
    public void manualReadingSurvivesLaterStreamGrowth() {
        int[] pauseCount = {0};
        String branchId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();
        ChatViewState state = state(branchId, generatingAnswer(messageId, longAnswer()));
        ChatScreen screen = launchScreen(state, recordingActions(pauseCount, new int[1]));
        settleView();

        ImageButton follow = screen.findViewById(R.id.chat_follow);
        View followSlot = screen.findViewById(R.id.chat_follow_slot);
        ScrollView scroll = screen.findViewById(R.id.chat_scroll);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(follow::performClick);
        settleView();
        sendDragDown(scroll);
        settleView();

        assertEquals("manual movement detaches the restored follow", 2, pauseCount[0]);
        assertEquals(View.VISIBLE, followSlot.getVisibility());
        ChatMessage grown = generatingAnswer(messageId,
                longAnswer() + "Additional streamed content.\n");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> screen.render(state.withMessages(Collections.singletonList(grown))));
        settleView();

        assertEquals("stream growth keeps the recovery button visible", View.VISIBLE,
                follow.getVisibility());
        assertTrue("stream growth must not pull manual reading back to the end",
                scroll.getScrollY() + scroll.getHeight()
                        < scroll.getChildAt(0).getHeight() - 1);
    }

    @Test
    public void switchingBranchesResetsTheOverflowPauseForTheNewAnswer() {
        int[] pauseCount = {0};
        String firstBranch = UUID.randomUUID().toString();
        ChatScreen screen = launchScreen(state(firstBranch,
                generatingAnswer(UUID.randomUUID().toString(), longAnswer())),
                recordingActions(pauseCount, new int[1]));
        settleView();
        assertEquals(1, pauseCount[0]);

        String nextBranch = UUID.randomUUID().toString();
        ChatViewState nextState = state(nextBranch,
                generatingAnswer(UUID.randomUUID().toString(), longAnswer()));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> screen.render(nextState));
        settleView();

        assertEquals("a new branch gets its own overflow pause", 2, pauseCount[0]);
        assertEquals(View.VISIBLE, screen.findViewById(R.id.chat_follow).getVisibility());
    }

    /**
     * 2026-10-16 user decision: "有时候没有自动消失，我已经滚到最底部了". A manual scroll that
     * settles on the newest content is the learner asking for the latest again, so the key re-attaches
     * by itself. Only the manual pause may do this - an answer taller than the viewport is detached by
     * design, which the two overflow cases above hold.
     */
    @Test
    public void restingOnTheNewestContentReEngagesFollowWithoutATap() {
        int[] pauseCount = {0};
        int[] followCount = {0};
        ChatScreen screen = launchScreen(state(UUID.randomUUID().toString(),
                generatingAnswer(UUID.randomUUID().toString(), longAnswer())),
                recordingActions(pauseCount, followCount));
        settleView();

        ImageButton follow = screen.findViewById(R.id.chat_follow);
        ScrollView scroll = screen.findViewById(R.id.chat_scroll);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(follow::performClick);
        settleView();
        sendDragDown(scroll);
        settleView();
        assertEquals("the gesture detaches follow", View.VISIBLE, follow.getVisibility());

        // Scroll straight to the floor rather than via fullScroll(FOCUS_DOWN): with animations
        // enabled the SDK's focus path runs a smooth scroll over ~250ms, so the positions on the
        // way are not a resting place - the product rightly keeps the key until the scroll
        // settles. A direct scrollTo is one position and one settle, which is what "resting on
        // the newest content" means.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            int floor = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
            scroll.scrollTo(0, floor);
        });

        View slot = screen.findViewById(R.id.chat_follow_slot);
        assertTrue("resting on the newest content hides the key (y=" + scroll.getScrollY()
                        + " floor=" + (scroll.getChildAt(0).getHeight() - scroll.getHeight())
                        + " pauses=" + pauseCount[0] + ")",
                awaitSlotGone(slot));
        assertEquals("and it tells the presenter, exactly as the key itself would",
                2, followCount[0]);
    }

    /** The reattachment is reported after the scroll settle, so it is waited for, not slept on. */
    private static boolean awaitSlotGone(View slot) {
        long deadline = SystemClock.uptimeMillis() + 3_000L;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] gone = {false};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> gone[0] = slot.getVisibility() == View.GONE);
            if (gone[0]) {
                return true;
            }
            SystemClock.sleep(25L);
        }
        return false;
    }

    private static String longAnswer() {
        StringBuilder answer = new StringBuilder();
        for (int index = 0; index < 80; index++) {
            answer.append("Long answer line ").append(index).append('\n');
        }
        return answer.toString();
    }

    private ChatScreen launchScreen(String branchId, ChatMessage message, ChatActions actions) {
        return launchScreen(state(branchId, message), actions);
    }

    private ChatScreen launchScreen(ChatViewState state, ChatActions actions) {
        android.app.Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = instrumentation.startActivitySync(intent);
        assertTrue("test activity must launch", activity != null);
        ChatScreen[] screen = new ChatScreen[1];
        instrumentation.runOnMainSync(() -> {
            screen[0] = new ChatScreen(activity);
            screen[0].setActions(actions);
            activity.setContentView(screen[0]);
            screen[0].render(state);
        });
        return screen[0];
    }

    private void settleView() {
        SystemClock.sleep(250L);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { });
    }

    private static ChatViewState state(String branchId, ChatMessage message) {
        return ChatViewState.initial(branchId, GenerationModel.FLASH, false, false)
                .withPhase(ChatViewState.Phase.READY)
                .withBranchTitle("Synthetic branch")
                .withMessages(Collections.singletonList(message))
                .withFollow(true, 0);
    }

    private static ChatMessage generatingAnswer(String messageId, String answer) {
        return ChatMessage.ai(messageId, answer, "", ChatMessage.Status.GENERATING,
                ChatMessage.Phase.ANSWER, "", 0, 0, true, System.currentTimeMillis());
    }

    private static ChatMessage stableAnswer(String messageId, String answer) {
        return ChatMessage.ai(messageId, answer, "", ChatMessage.Status.STABLE,
                ChatMessage.Phase.ANSWER, UUID.randomUUID().toString(), 1, 0, false,
                System.currentTimeMillis());
    }

    private static void sendDragDown(ScrollView scroll) {
        int[] location = new int[2];
        scroll.getLocationOnScreen(location);
        float x = location[0] + scroll.getWidth() / 2f;
        float startY = location[1] + scroll.getHeight() * 0.25f;
        float endY = location[1] + scroll.getHeight() * 0.75f;
        long downTime = SystemClock.uptimeMillis();
        android.app.Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                x, startY, 0);
        instrumentation.sendPointerSync(down);
        down.recycle();
        for (int step = 1; step <= 4; step++) {
            long eventTime = downTime + step * 30L;
            float y = startY + (endY - startY) * step / 4f;
            MotionEvent move = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE,
                    x, y, 0);
            instrumentation.sendPointerSync(move);
            move.recycle();
        }
        // A finger held still before it lifts: without this the last 30ms of travel is a fling,
        // whose scroller keeps moving after the test's fullScroll and re-detaches the view.
        MotionEvent hold = MotionEvent.obtain(downTime, downTime + 420L, MotionEvent.ACTION_MOVE,
                x, endY, 0);
        instrumentation.sendPointerSync(hold);
        hold.recycle();
        long upTime = downTime + 470L;
        MotionEvent up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP,
                x, endY, 0);
        instrumentation.sendPointerSync(up);
        up.recycle();
    }

    private static ChatActions recordingActions(int[] pauseCount, int[] followCount) {
        return (ChatActions) java.lang.reflect.Proxy.newProxyInstance(
                ChatActions.class.getClassLoader(), new Class<?>[]{ChatActions.class},
                (proxy, method, arguments) -> {
                    if ("onFollowLatestPaused".equals(method.getName())) {
                        pauseCount[0]++;
                    } else if ("onFollowLatestPressed".equals(method.getName())) {
                        followCount[0]++;
                    }
                    Class<?> result = method.getReturnType();
                    if (result == boolean.class) { return false; }
                    if (result == int.class) { return 0; }
                    if (result == long.class) { return 0L; }
                    return null;
                });
    }
}
