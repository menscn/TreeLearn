package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.text.Selection;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.view.ActionMode;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.ChatScreen;
import com.treelearn.app.ui.ChatActions;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChatViewState;
import com.treelearn.core.domain.GenerationModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class WordMenuPopupInstrumentedTest {

    private android.app.Instrumentation instrumentation;
    private Activity activity;
    private ChatScreen screen;
    private ActionMode actionMode;
    private ChatViewState renderedState;

    @Before
    public void setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = instrumentation.startActivitySync(intent);
        assertNotNull("MainActivity failed to launch", activity);
        instrumentation.runOnMainSync(() -> {
            String answerId = UUID.randomUUID().toString();
            ChatMessage answer = ChatMessage.ai(answerId,
                    "Synthetic selectable answer for the menu", "",
                    ChatMessage.Status.STABLE, ChatMessage.Phase.ANSWER,
                    UUID.randomUUID().toString(), 1, 0, false, System.currentTimeMillis());
            renderedState = ChatViewState.initial(UUID.randomUUID().toString(),
                    GenerationModel.FLASH, false, false)
                    .withPhase(ChatViewState.Phase.READY)
                    .withBranchTitle("Synthetic branch")
                    .withMessages(Collections.singletonList(answer));
            screen = new ChatScreen(activity);
            screen.setActions(noopActions());
            activity.setContentView(screen);
            screen.render(renderedState);
        });
        instrumentation.waitForIdleSync();
    }

    @After
    public void tearDown() {
        if (actionMode != null) {
            instrumentation.runOnMainSync(actionMode::finish);
            instrumentation.waitForIdleSync();
            actionMode = null;
        }
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
        }
    }

    @Test
    public void selectedAnswerOpensSixCellWordMenu() throws Exception {
        PopupWindow popup;
        instrumentation.runOnMainSync(() -> {
            TextView answer = screen.findViewById(R.id.chat_answer);
            assertNotNull("the rendered answer TextView should exist", answer);
            answer.requestFocus();
            assertTrue("selectable answer text should expose a Spannable", 
                    answer.getText() instanceof Spannable);
            Selection.setSelection((Spannable) answer.getText(), 0, 8);
            try {
                Object row = rowFor(screen);
                Class<?> callbackClass = null;
                for (Class<?> nested : row.getClass().getDeclaredClasses()) {
                    if (nested.getSimpleName().equals("SelectionMenuCallback")) {
                        callbackClass = nested;
                        break;
                    }
                }
                assertNotNull("ChatScreen must expose its custom selection callback", callbackClass);
                Constructor<?> constructor = callbackClass.getDeclaredConstructor(row.getClass());
                constructor.setAccessible(true);
                ActionMode.Callback callback = (ActionMode.Callback) constructor.newInstance(row);
                actionMode = answer.startActionMode(callback, ActionMode.TYPE_FLOATING);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
        instrumentation.waitForIdleSync();

        assertNotNull("the custom selection ActionMode should start", actionMode);
        popup = wordMenuPopup(screen);
        assertNotNull("the real selection path should create WordMenuPopup", popup);
        assertTrue("the WordMenuPopup should be visible", popup.isShowing());

        int[] cells = {
                R.id.word_menu_ask_ai,
                R.id.word_menu_fork,
                R.id.word_menu_highlight,
                R.id.word_menu_note,
                R.id.word_menu_copy,
                R.id.word_menu_select_all
        };
        for (int cellId : cells) {
            View cell = popup.getContentView().findViewById(cellId);
            assertNotNull("missing word-menu cell " + cellId, cell);
            assertEquals("word-menu cell should be visible " + cellId,
                    View.VISIBLE, cell.getVisibility());
            assertTrue("word-menu cell should have a measured hit area " + cellId,
                    cell.getWidth() > 0 && cell.getHeight() > 0);
        }
        savePopupScreenshot();

        instrumentation.runOnMainSync(actionMode::finish);
        instrumentation.waitForIdleSync();
        assertFalse("finishing selection should close WordMenuPopup", popup.isShowing());
    }

    @Test
    public void physicalLongPressKeepsSelectionAndMenuAfterGestureReturns() throws Exception {
        TextView answer = screen.findViewById(R.id.chat_answer);
        assertNotNull("the rendered answer TextView should exist", answer);
        int[] location = new int[2];
        answer.getLocationOnScreen(location);
        final float x = location[0] + answer.getWidth() / 2f;
        final float y = location[1] + answer.getHeight() / 2f;
        long downTime = SystemClock.uptimeMillis();
        instrumentation.runOnMainSync(() -> {
            answer.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime,
                    MotionEvent.ACTION_DOWN, x, y, 0));
        });
        SystemClock.sleep(Math.max(320L, ViewConfiguration.getLongPressTimeout() - 120L));
        long upTime = SystemClock.uptimeMillis();
        instrumentation.runOnMainSync(() -> {
            answer.dispatchTouchEvent(MotionEvent.obtain(downTime, upTime,
                    MotionEvent.ACTION_UP, x, y, 0));
        });
        instrumentation.waitForIdleSync();
        SystemClock.sleep(350L);
        instrumentation.waitForIdleSync();

        PopupWindow popup = wordMenuPopup(screen);
        int start = answer.getSelectionStart();
        int end = answer.getSelectionEnd();
        assertTrue("the long press should leave a non-empty selection", start >= 0 && end > start);
        assertNotNull("the physical long press should create WordMenuPopup", popup);
        assertTrue("the custom menu should survive the long-press gesture", popup.isShowing());
    }

    @Test
    public void highlightAppearsBeforeReadback() throws Exception {
        instrumentation.runOnMainSync(() -> {
            TextView answer = screen.findViewById(R.id.chat_answer);
            answer.requestFocus();
            Selection.setSelection((Spannable) answer.getText(), 0, 8);
            try {
                Object row = rowFor(screen);
                Class<?> callbackClass = null;
                for (Class<?> nested : row.getClass().getDeclaredClasses()) {
                    if (nested.getSimpleName().equals("SelectionMenuCallback")) {
                        callbackClass = nested;
                        break;
                    }
                }
                assertNotNull("ChatScreen must expose its custom selection callback", callbackClass);
                Constructor<?> constructor = callbackClass.getDeclaredConstructor(row.getClass());
                constructor.setAccessible(true);
                ActionMode.Callback callback = (ActionMode.Callback) constructor.newInstance(row);
                actionMode = answer.startActionMode(callback, ActionMode.TYPE_FLOATING);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
        instrumentation.waitForIdleSync();

        PopupWindow popup = wordMenuPopup(screen);
        assertNotNull("the custom menu should be open before highlight", popup);
        instrumentation.runOnMainSync(() ->
                popup.getContentView().findViewById(R.id.word_menu_highlight).performClick());
        instrumentation.waitForIdleSync();

        TextView answer = screen.findViewById(R.id.chat_answer);
        Spannable text = (Spannable) answer.getText();
        BackgroundColorSpan[] yellowSpans = text.getSpans(0, text.length(), BackgroundColorSpan.class);
        assertTrue("highlight should paint before a storage read returns", yellowSpans.length > 0);
    }

    @Test
    public void movingBeforeFastLongPressCancelsSelectionMenu() throws Exception {
        TextView answer = screen.findViewById(R.id.chat_answer);
        int[] location = new int[2];
        answer.getLocationOnScreen(location);
        float downX = location[0] + answer.getWidth() / 2f;
        float downY = location[1] + answer.getHeight() / 2f;
        long downTime = SystemClock.uptimeMillis();
        instrumentation.runOnMainSync(() -> answer.dispatchTouchEvent(MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, downX, downY, 0)));
        SystemClock.sleep(80L);
        instrumentation.runOnMainSync(() -> answer.dispatchTouchEvent(MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE,
                downX + answer.getWidth(), downY, 0)));
        SystemClock.sleep(360L);
        instrumentation.runOnMainSync(() -> answer.dispatchTouchEvent(MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                downX + answer.getWidth(), downY, 0)));
        instrumentation.waitForIdleSync();

        assertTrue("moving before the threshold should not open the custom menu",
                wordMenuPopup(screen) == null);
    }

    @Test
    public void unrelatedStateRenderKeepsSelectionMenuAndSelection() throws Exception {
        PopupWindow popup;
        instrumentation.runOnMainSync(() -> {
            TextView answer = screen.findViewById(R.id.chat_answer);
            answer.requestFocus();
            Selection.setSelection((Spannable) answer.getText(), 0, 8);
            try {
                Object row = rowFor(screen);
                Class<?> callbackClass = null;
                for (Class<?> nested : row.getClass().getDeclaredClasses()) {
                    if (nested.getSimpleName().equals("SelectionMenuCallback")) {
                        callbackClass = nested;
                        break;
                    }
                }
                assertNotNull("ChatScreen must expose its custom selection callback", callbackClass);
                Constructor<?> constructor = callbackClass.getDeclaredConstructor(row.getClass());
                constructor.setAccessible(true);
                ActionMode.Callback callback = (ActionMode.Callback) constructor.newInstance(row);
                actionMode = answer.startActionMode(callback, ActionMode.TYPE_FLOATING);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
        instrumentation.waitForIdleSync();
        popup = wordMenuPopup(screen);
        assertNotNull("the selection menu should be open before refresh", popup);
        assertTrue("the selection menu should be visible before refresh", popup.isShowing());

        instrumentation.runOnMainSync(() -> screen.render(renderedState));
        instrumentation.waitForIdleSync();

        TextView answer = screen.findViewById(R.id.chat_answer);
        assertTrue("an unrelated state render must preserve the selection",
                answer.getSelectionStart() >= 0 && answer.getSelectionEnd() > answer.getSelectionStart());
        assertTrue("an unrelated state render must preserve the custom menu", popup.isShowing());
    }

    private void savePopupScreenshot() throws Exception {
        File externalFiles = instrumentation.getTargetContext().getExternalFilesDir(null);
        assertNotNull("target external files directory should be available", externalFiles);
        File screenshot = new File(externalFiles, "word-menu-popup.png");
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("UiAutomation.takeScreenshot should return a bitmap", bitmap);
        try (FileOutputStream output = new FileOutputStream(screenshot)) {
            assertTrue("the popup screenshot should be encoded as PNG",
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            bitmap.recycle();
        }
        assertTrue("the popup screenshot should be saved", screenshot.isFile()
                && screenshot.length() > 0);
        System.out.println("WORD_MENU_SCREENSHOT=" + screenshot.getAbsolutePath());
    }

    private static Object rowFor(ChatScreen chatScreen) throws ReflectiveOperationException {
        Field rowsField = ChatScreen.class.getDeclaredField("rows");
        rowsField.setAccessible(true);
        Map<?, ?> rows = (Map<?, ?>) rowsField.get(chatScreen);
        assertEquals("one synthetic answer row should be rendered", 1, rows.size());
        return rows.values().iterator().next();
    }

    private static PopupWindow wordMenuPopup(ChatScreen chatScreen)
            throws ReflectiveOperationException {
        Field menuField = ChatScreen.class.getDeclaredField("wordMenu");
        menuField.setAccessible(true);
        Object menu = menuField.get(chatScreen);
        if (menu == null) {
            return null;
        }
        Field popupField = menu.getClass().getDeclaredField("popup");
        popupField.setAccessible(true);
        return (PopupWindow) popupField.get(menu);
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
