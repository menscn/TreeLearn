package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.ChatScreen;
import com.treelearn.app.ui.ChatActions;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChatViewState;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.core.domain.GenerationModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class RecommendationForkHotZoneInstrumentedTest {

    private android.app.Instrumentation instrumentation;
    private Activity activity;

    @Before
    public void setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = instrumentation.startActivitySync(intent);
        assertNotNull("MainActivity failed to launch", activity);
        instrumentation.waitForIdleSync();
    }

    @After
    public void tearDown() {
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
        }
    }

    @Test
    public void recommendationForkIconForwardsTheTap() {
        AtomicReference<String> forkedQuestion = new AtomicReference<String>();
        String answerId = UUID.randomUUID().toString();
        ChatMessage answer = ChatMessage.ai(answerId, "Synthetic answer", "",
                ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER,
                UUID.randomUUID().toString(), 0, 0, false, System.currentTimeMillis());
        ChatViewState state = ChatViewState.initial(UUID.randomUUID().toString(),
                        GenerationModel.FLASH, false, true)
                .withPhase(ChatViewState.Phase.READY)
                .withBranchTitle("Synthetic branch")
                .withMessages(Collections.singletonList(answer))
                .withRecommendations(Collections.singletonList(new Recommendation(
                        "Explain this result", Recommendation.Kind.FOLLOW_UP)), answerId);
        ChatActions actions = (ChatActions) Proxy.newProxyInstance(
                ChatActions.class.getClassLoader(), new Class<?>[]{ChatActions.class},
                (proxy, method, arguments) -> {
                    if ("onRecommendationForked".equals(method.getName())) {
                        forkedQuestion.set((String) arguments[0]);
                    }
                    return defaultValue(method.getReturnType());
                });
        ChatScreen screen = new ChatScreen(activity);
        FrameLayout host = activity.findViewById(R.id.shell_host);
        assertNotNull("MainActivity shell host should be available", host);
        instrumentation.runOnMainSync(() -> {
            screen.setActions(actions);
            host.removeAllViews();
            host.addView(screen, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            screen.render(state);
            host.requestLayout();
            activity.getWindow().getDecorView().requestLayout();
        });
        instrumentation.waitForIdleSync();

        View fork = screen.findViewById(R.id.suggestion_fork);
        assertNotNull("recommendation fork icon should be rendered", fork);
        assertTrue("recommendation fork icon should be visible", fork.getVisibility() == View.VISIBLE);
        assertTrue("recommendation fork icon should have a measured touch target",
                fork.getMeasuredWidth() > 0 && fork.getMeasuredHeight() > 0);
        assertTrue("recommendation fork icon should be clickable", fork.isClickable());
        final boolean[] clicked = new boolean[1];
        instrumentation.runOnMainSync(() -> clicked[0] = fork.performClick());
        assertTrue("recommendation fork icon should consume the click", clicked[0]);
        assertEquals("Explain this result", forkedQuestion.get());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }
}
