package com.treelearn.app;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.reading.ReadingStore;
import com.treelearn.app.screen.ChatScreen;
import com.treelearn.app.ui.ChatCommands;
import com.treelearn.app.ui.ChatActions;
import com.treelearn.app.ui.ChatData;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChatPresenter;
import com.treelearn.app.ui.ChatSink;
import com.treelearn.app.ui.ChatViewState;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.CopyLabels;
import com.treelearn.app.ui.KeyVault;
import com.treelearn.app.ui.SettingsStore;
import com.treelearn.core.domain.Annotation;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.ReadingAnchor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The child-branch stack's real lifecycle: the answer's 子分支 key asks the presenter, the read
 * lands inline under the answer, a refresh keeps it, and a row reports the branch it names.
 *
 * <p>2026-10-22 (T-056): the carrier was a PopupWindow until Scheme A. The proposal draws the
 * children as a stack inside the answer column, so this test now reads the {@code
 * chat_branch_section} the row owns instead of a window that must be found by reflection.
 */
@RunWith(AndroidJUnit4.class)
public final class ChildBranchStackLifecycleInstrumentedTest {

    private android.app.Instrumentation instrumentation;
    private Activity activity;
    private ChatScreen screen;
    private ChatViewState state;
    private String answerId;

    @Before
    public void setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = instrumentation.startActivitySync(intent);
        assertNotNull("MainActivity failed to launch", activity);
        instrumentation.runOnMainSync(() -> {
            answerId = UUID.randomUUID().toString();
            ChatMessage answer = ChatMessage.ai(answerId, "Synthetic answer", "",
                    ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER,
                    UUID.randomUUID().toString(), 1, 1, false, System.currentTimeMillis());
            state = ChatViewState.initial(UUID.randomUUID().toString(), GenerationModel.FLASH,
                    false, false).withPhase(ChatViewState.Phase.READY)
                    .withBranchTitle("Synthetic branch")
                    .withMessages(Collections.singletonList(answer));
            screen = new ChatScreen(activity);
            screen.setActions(noopActions());
            activity.setContentView(screen);
            screen.render(state);
        });
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
    public void theAnswerKeyDrawsTheChildrenInlineAndTheStackSurvivesRefresh() throws Exception {
        AtomicInteger childrenReads = new AtomicInteger();
        AtomicReference<String> selectedBranch = new AtomicReference<String>();
        String childBranchId = UUID.randomUUID().toString();
        List<ChildBranch> children = Arrays.asList(
                new ChildBranch(UUID.randomUUID().toString(), "Synthetic sibling", 0),
                new ChildBranch(childBranchId, "Synthetic child", 1));
        ChatData.Snapshot snapshot = ChatData.Snapshot.of(state.branchTitle, state.messages,
                Collections.emptyList(), "", null, Collections.emptyList(), null);
        ChatData data = new ChatData() {
            @Override
            public Snapshot read(String branchId) {
                return snapshot;
            }

            @Override
            public List<ChildBranch> children(String branchId, String messageId) {
                assertEquals(state.branchId, branchId);
                assertEquals(answerId, messageId);
                childrenReads.incrementAndGet();
                return children;
            }
        };
        ChatSink sink = proxy(ChatSink.class, (instance, method, arguments) -> {
            if ("render".equals(method.getName())) {
                screen.render((ChatViewState) arguments[0]);
            } else if ("renderAnnotations".equals(method.getName())) {
                screen.renderAnnotations((Map<String, List<Annotation>>) arguments[0]);
            } else if ("openChildren".equals(method.getName())) {
                screen.showChildren((String) arguments[0], (List<ChildBranch>) arguments[1],
                        selectedBranch::set);
            }
            return defaultValue(method.getReturnType());
        });
        KeyVault keyVault = proxy(KeyVault.class,
                (instance, method, arguments) -> Boolean.FALSE);
        SettingsStore settings = proxy(SettingsStore.class, (instance, method, arguments) -> {
            if ("model".equals(method.getName())) {
                return GenerationModel.FLASH;
            }
            return defaultValue(method.getReturnType());
        });
        ChatPresenter presenter = new ChatPresenter(sink, Runnable::run, Runnable::run, keyVault,
                data, proxy(ChatCommands.class, (instance, method, arguments) ->
                        defaultValue(method.getReturnType())), settings,
                CopyLabels.of("Reasoning", "Answer"), state.branchId, noPositionStore());
        instrumentation.runOnMainSync(() -> {
            screen.setActions(presenter);
            presenter.start();
        });
        instrumentation.waitForIdleSync();

        instrumentation.runOnMainSync(() -> {
            View button = screen.findViewById(R.id.act_children);
            assertNotNull("answer child button should be rendered", button);
            button.performClick();
        });
        instrumentation.waitForIdleSync();
        assertEquals("button tap should read child branches through the presenter", 1,
                childrenReads.get());

        View section = screen.findViewById(R.id.chat_branch_section);
        assertNotNull("the answer owns an inline branch section", section);
        assertEquals("the stack is visible after the answer key's tap", View.VISIBLE,
                section.getVisibility());
        ViewGroup rows = screen.findViewById(R.id.chat_branch_list);
        assertNotNull("the inline list should be rendered", rows);
        assertEquals("the stack should render both child branches", 2, rows.getChildCount());
        assertEquals("Synthetic sibling", ((TextView) rows.getChildAt(0)
                .findViewById(R.id.child_branch_title)).getText().toString());
        assertTrue("the first child row should have a measured hit area",
                rows.getChildAt(0).getWidth() > 0 && rows.getChildAt(0).getHeight() > 0);
        assertEquals("Synthetic child", ((TextView) rows.getChildAt(1)
                .findViewById(R.id.child_branch_title)).getText().toString());
        assertEquals("the unread count rides on the row", View.VISIBLE,
                rows.getChildAt(1).findViewById(R.id.child_branch_unread).getVisibility());
        saveScreenshot("child-branch-stack.png");

        instrumentation.runOnMainSync(presenter::onResumed);
        instrumentation.waitForIdleSync();
        assertEquals("a presenter refresh must not collapse the stack", View.VISIBLE,
                section.getVisibility());
        assertEquals("and must keep both rows", 2, rows.getChildCount());

        instrumentation.runOnMainSync(() -> rows.getChildAt(1).performClick());
        assertEquals("selecting a child should report its branch id", childBranchId,
                selectedBranch.get());
    }

    private void saveScreenshot(String name) throws Exception {
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("UiAutomation.takeScreenshot should return a bitmap", bitmap);
        savePng(instrumentation.getTargetContext(), bitmap, name);
    }

    private static void savePng(Context context, Bitmap bitmap, String name) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
        }
        Uri uri = context.getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        assertNotNull("the screenshot should be inserted into MediaStore", uri);
        try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
            assertNotNull("the screenshot output stream should be available", output);
            assertTrue("the screenshot should be encoded as PNG",
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            bitmap.recycle();
        }
        System.out.println("CHILD_BRANCH_STACK_SCREENSHOT=" + uri);
    }

    private static ReadingStore noPositionStore() {
        return new ReadingStore() {
            @Override
            public ReadingAnchor anchorOf(String branchId) { return null; }
            @Override
            public void saveAnchor(String branchId, ReadingAnchor anchor) { }
            @Override
            public void saveSeen(Branch branch) { }
        };
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) { return null; }
        if (type == boolean.class) { return false; }
        if (type == char.class) { return '\0'; }
        if (type == byte.class) { return (byte) 0; }
        if (type == short.class) { return (short) 0; }
        if (type == int.class) { return 0; }
        if (type == long.class) { return 0L; }
        if (type == float.class) { return 0f; }
        if (type == double.class) { return 0d; }
        return null;
    }

    private static ChatActions noopActions() {
        return (ChatActions) Proxy.newProxyInstance(ChatActions.class.getClassLoader(),
                new Class<?>[]{ChatActions.class}, (proxy, method, arguments) -> {
                    Class<?> result = method.getReturnType();
                    if (result == boolean.class) { return false; }
                    if (result == int.class) { return 0; }
                    if (result == long.class) { return 0L; }
                    return null;
                });
    }
}
