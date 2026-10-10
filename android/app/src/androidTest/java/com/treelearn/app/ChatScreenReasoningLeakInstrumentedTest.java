package com.treelearn.app;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

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
public final class ChatScreenReasoningLeakInstrumentedTest {

    private Activity activity;

    @After
    public void tearDown() {
        if (activity != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        }
    }

    @Test
    public void thinkingDisabledAnswerProjectionHidesFramingAndReasoning() {
        android.app.Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = instrumentation.startActivitySync(intent);
        assertTrue("test activity must launch", activity != null);

        String messageId = UUID.randomUUID().toString();
        String answerId = UUID.randomUUID().toString();
        String answerContent = "<treelearn_reasoning>synthetic internal-only marker"
                + "</treelearn_reasoning><treelearn_answer>visible answer"
                + "</treelearn_answer>";
        TextView[] answerView = new TextView[1];
        instrumentation.runOnMainSync(() -> {
            ChatScreen screen = new ChatScreen(activity);
            screen.setActions(noopActions());
            activity.setContentView(screen);
            ChatMessage message = ChatMessage.ai(messageId, answerContent, "",
                    ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, answerId,
                    1, 0, false, System.currentTimeMillis());
            ChatViewState state = ChatViewState.initial(UUID.randomUUID().toString(),
                    GenerationModel.FLASH, false, false)
                    .withPhase(ChatViewState.Phase.READY)
                    .withBranchTitle("Synthetic branch")
                    .withMessages(Collections.singletonList(message));
            screen.render(state);
            answerView[0] = screen.findViewById(R.id.chat_answer);
        });
        instrumentation.waitForIdleSync();

        assertTrue("rendered answer view must exist", answerView[0] != null);
        assertTrue("rendered answer view must be visible",
                answerView[0].getVisibility() == View.VISIBLE);
        String visibleText = answerView[0].getText().toString();
        assertTrue("visible answer must contain only the framed answer",
                "visible answer".contentEquals(visibleText));
        assertTrue("visible answer must exclude internal-only content",
                !visibleText.contains("synthetic internal-only marker"));
        assertTrue("visible answer must exclude protocol framing",
                !visibleText.contains("treelearn_reasoning")
                        && !visibleText.contains("treelearn_answer"));
    }

    @Test
    public void thinkingEnabledKeepsFramedReasoningInTheDisclosure() {
        android.app.Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = instrumentation.startActivitySync(intent);
        assertTrue("test activity must launch", activity != null);

        String messageId = UUID.randomUUID().toString();
        String answerId = UUID.randomUUID().toString();
        String answerContent = "<treelearn_reasoning>private material"
                + "</treelearn_reasoning><treelearn_answer>visible result"
                + "</treelearn_answer>";
        TextView[] answerView = new TextView[1];
        TextView[] reasoningView = new TextView[1];
        instrumentation.runOnMainSync(() -> {
            ChatScreen screen = new ChatScreen(activity);
            screen.setActions(noopActions());
            activity.setContentView(screen);
            ChatMessage message = ChatMessage.ai(messageId, answerContent, "",
                    ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER, answerId,
                    1, 0, false, System.currentTimeMillis());
            ChatViewState state = ChatViewState.initial(UUID.randomUUID().toString(),
                    GenerationModel.FLASH, true, false)
                    .withPhase(ChatViewState.Phase.READY)
                    .withBranchTitle("Synthetic branch")
                    .withMessages(Collections.singletonList(message))
                    .withReasoningExpanded(messageId, true);
            screen.render(state);
            answerView[0] = screen.findViewById(R.id.chat_answer);
            reasoningView[0] = screen.findViewById(R.id.reasoning_body);
        });
        instrumentation.waitForIdleSync();

        assertTrue("answer view must contain only the answer projection",
                answerView[0] != null && "visible result".contentEquals(answerView[0].getText()));
        assertTrue("enabled thinking keeps framed reasoning in the disclosure",
                reasoningView[0] != null && reasoningView[0].getVisibility() == View.VISIBLE
                        && "private material".contentEquals(reasoningView[0].getText()));
    }

    private static ChatActions noopActions() {
        return (ChatActions) java.lang.reflect.Proxy.newProxyInstance(
                ChatActions.class.getClassLoader(), new Class<?>[]{ChatActions.class},
                (proxy, method, arguments) -> {
                    Class<?> result = method.getReturnType();
                    if (result == boolean.class) { return false; }
                    if (result == int.class) { return 0; }
                    if (result == long.class) { return 0L; }
                    return null;
                });
    }
}
