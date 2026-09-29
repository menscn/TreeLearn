package com.treelearn.app;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.PopupWindow;
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

import java.lang.reflect.Field;
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

@RunWith(AndroidJUnit4.class)
public final class ChildBranchPopupLifecycleInstrumentedTest {

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
    public void answerButtonLoadsChildrenAndPopupSurvivesRefreshAndSelection() throws Exception {
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
            android.view.View button = screen.findViewById(R.id.act_children);
            assertNotNull("answer child button should be rendered", button);
            button.performClick();
        });
        instrumentation.waitForIdleSync();
        assertEquals("button tap should read child branches through the presenter", 1,
                childrenReads.get());
        PopupWindow popup = popupOf(screen);
        assertNotNull("the child popup was not created", popup);
        assertTrue("the child popup should be visible after the answer button tap", popup.isShowing());
        assertTrue("the child popup should have a measured width and height",
                popup.getWidth() > 0 && popup.getHeight() > 0);
        TextView title = popup.getContentView().findViewById(R.id.child_branches_title);
        assertNotNull("the child popup should render a title", title);
        assertEquals("子分支列表 · 2", title.getText().toString());
        android.view.View childRows = popup.getContentView().findViewById(R.id.child_branches_list);
        assertEquals("the popup should render both child branches", 2,
                ((ViewGroup) childRows).getChildCount());
        assertEquals("Synthetic sibling", ((TextView) childRows.findViewById(
                R.id.child_branch_title)).getText().toString());
        assertTrue("the first child row should have a measured hit area",
                ((ViewGroup) childRows).getChildAt(0).getWidth() > 0
                        && ((ViewGroup) childRows).getChildAt(0).getHeight() > 0);
        assertEquals("Synthetic child", ((TextView) ((ViewGroup) childRows).getChildAt(1)
                .findViewById(R.id.child_branch_title)).getText().toString());
        saveChildPopupScreenshot();
        saveChildPopupContentScreenshot(popup);

        instrumentation.runOnMainSync(presenter::onResumed);
        instrumentation.waitForIdleSync();
        assertTrue("a presenter refresh must not dismiss the child popup",
                popup.isShowing());

        instrumentation.runOnMainSync(() -> {
            android.view.View rows = popup.getContentView().findViewById(R.id.child_branches_list);
            assertNotNull("the child branch list should be rendered", rows);
            ((android.view.ViewGroup) rows).getChildAt(1).performClick();
        });
        assertEquals("selecting a child should report its branch id", childBranchId,
                selectedBranch.get());
    }

    private void saveChildPopupScreenshot() throws Exception {
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("UiAutomation.takeScreenshot should return a bitmap", bitmap);
        savePng(instrumentation.getTargetContext(), bitmap, "child-branch-popup.png");
    }

    private void saveChildPopupContentScreenshot(PopupWindow popup) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(popup.getWidth(), popup.getHeight(),
                Bitmap.Config.ARGB_8888);
        instrumentation.runOnMainSync(() -> popup.getContentView().draw(new Canvas(bitmap)));
        savePng(instrumentation.getTargetContext(), bitmap, "child-branch-popup-content.png");
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
        System.out.println("CHILD_BRANCH_SCREENSHOT=" + uri);
    }

    private static PopupWindow popupOf(ChatScreen screen) throws ReflectiveOperationException {
        Object childPopup = field(screen, "childrenPopup");
        return childPopup == null ? null : (PopupWindow) field(childPopup, "popup");
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
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
