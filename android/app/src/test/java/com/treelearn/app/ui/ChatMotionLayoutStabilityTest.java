package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * T-075: the layout-stability assertions (073 visual spec §4 总则, task hard constraint) - the
 * caret, the generating note, the failure bar and the canceled mark are reserved slots inside the
 * message row's own content area, so appearing or disappearing never moves a neighbouring control's
 * hit box: the composer and the app bar live outside the scroll region entirely, and the action bar
 * is visible only in states where none of the placeholders is.
 *
 * <p>Like {@code ChatMessageRowLayoutTest} this reads the layouts as files, so the gate runs on the
 * JVM without inflating a view.
 */
public final class ChatMotionLayoutStabilityTest {

    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app");

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String elementAround(String xml, String id) {
        int idIndex = xml.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("id " + id + " must exist", idIndex >= 0);
        int elementEnd = xml.indexOf('>', idIndex);
        assertTrue("malformed layout", elementEnd >= 0);
        return xml.substring(Math.max(0, xml.lastIndexOf('<', idIndex)), elementEnd);
    }

    @Test
    public void theFollowButtonFloatsOverTheScrollRegionNeverOverTheComposer() throws Exception {
        String body = read(RES.resolve(Paths.get("layout", "screen_chat_body.xml")));
        int scroll = body.indexOf("android:id=\"@+id/chat_scroll\"");
        int follow = body.indexOf("android:id=\"@+id/chat_follow\"");
        int composer = body.indexOf("android:id=\"@+id/chat_composer\"");
        assertTrue("the follow button must exist", follow >= 0);
        assertTrue("the button belongs to the scroll region, before the composer",
                scroll >= 0 && follow > scroll && follow < composer);
        // 2026-10-16 user decision: the key became a fixed 44dp chevron, so the float position is the
        // slot's and the missed count is a badge. A box that never resizes is the whole point: the
        // text pill this replaced grew wider every time a stream item joined its label.
        assertTrue("the slot must overlay (gravity bottom), not push, the content",
                elementAround(body, "chat_follow_slot").contains("android:layout_gravity=\"bottom"));
        assertTrue("the key must keep the 44dp hot zone",
                elementAround(body, "chat_follow").contains("@dimen/touch_min"));
        assertTrue("the key is an icon button, not a text button",
                elementAround(body, "chat_follow").startsWith("<ImageButton"));
        assertTrue("the key draws a down chevron",
                elementAround(body, "chat_follow").contains("@drawable/ic_chevron_down"));
        assertTrue("the icon-only key keeps the label as its description",
                elementAround(body, "chat_follow").contains(
                        "android:contentDescription=\"@string/chat_back_to_latest\""));
        assertTrue("the key must carry no text of its own",
                !elementAround(body, "chat_follow").contains("android:text="));
        assertTrue("the count must ride a badge, so it cannot resize the key",
                elementAround(body, "chat_follow_count").contains("@drawable/bg_count_badge"));
    }

    @Test
    public void theGenerationPlaceholdersLiveInsideTheRowContentArea() throws Exception {
        String row = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        int answerBlock = row.indexOf("android:id=\"@+id/chat_answer_block\"");
        int suggestions = row.indexOf("android:id=\"@+id/chat_suggestions\"");
        for (String id : new String[] {"chat_caret", "chat_generating_note", "chat_failure",
                "chat_canceled", "chat_actions"}) {
            int at = row.indexOf("android:id=\"@+id/" + id + "\"");
            assertTrue(id + " must exist exactly once as a row slot",
                    at >= 0 && row.lastIndexOf("android:id=\"@+id/" + id + "\"") == at);
            assertTrue(id + " must live inside the answer block's own content area",
                    at > answerBlock && at < suggestions);
        }
    }

    @Test
    public void thePlaceholderSlotsAreSiblingsNotNeighborsOfTheActionBar() throws Exception {
        String row = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        int caret = row.indexOf("android:id=\"@+id/chat_caret\"");
        int note = row.indexOf("android:id=\"@+id/chat_generating_note\"");
        int failure = row.indexOf("android:id=\"@+id/chat_failure\"");
        int canceled = row.indexOf("android:id=\"@+id/chat_canceled\"");
        int actions = row.indexOf("android:id=\"@+id/chat_actions\"");
        assertTrue("each slot is its own sibling: no slot is nested inside another",
                caret < note && note < failure && failure < canceled && canceled < actions);
    }

    @Test
    public void theFailureBarUsesTheRedTokens() throws Exception {
        String row = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        String failure = elementAround(row, "chat_failure");
        assertTrue("073 §2 S-FAILED: the bar points at the dedicated soft-red surface",
                failure.contains("@drawable/bg_semantic_alert"));
        // The fill moved into a drawable so a backgroundTint cannot flatten the glass edge; the
        // invariant is still "the bar sits on tl_red_soft", read where the colour now lives.
        assertTrue("073 §2 S-FAILED: the bar sits on the soft red",
                read(RES.resolve(Paths.get("drawable", "bg_semantic_alert.xml")))
                        .contains("@color/tl_red_soft"));
        assertTrue("073 §2 S-FAILED: the text is the red ink",
                elementAround(row, "chat_failure_text").contains("@color/tl_red"));
    }

    /**
     * 2026-10-25 (T-2026-10-25-095): the review's AC1 - the interrupt card had the risk sentence
     * sharing a row with the retry key, so on a phone the sentence was whatever width the key left
     * and the key's centre never lined up with the two-line hint it explained. Three rows now: what
     * happened, what the kept text may still be used for, then the key.
     */
    @Test
    public void theFailureBarStacksItsReasonAboveItsKey() throws Exception {
        String row = read(RES.resolve(Paths.get("layout", "item_chat_message.xml")));
        int reason = row.indexOf("android:id=\"@+id/chat_failure_text\"");
        int hint = row.indexOf("android:id=\"@+id/chat_retry_hint\"");
        int retry = row.indexOf("android:id=\"@+id/chat_retry\"");
        assertTrue("what happened, then the kept-text note, then the key - in that order",
                reason >= 0 && reason < hint && hint < retry);
        assertTrue("the note owns the card's full width instead of the key's leftover",
                elementAround(row, "chat_retry_hint").contains("android:layout_width=\"match_parent\""));
        assertTrue("the key closes the card on its own line",
                elementAround(row, "chat_retry").contains("android:layout_marginTop="));
    }

    @Test
    public void theStateChangesAreAnnouncedToTalkBack() throws Exception {
        String screen = read(SRC.resolve(Paths.get("screen", "ChatScreen.java")));
        assertTrue("generation stage changes must be announced (073 §7)",
                screen.contains("announceForAccessibility"));
        String strings = read(RES.resolve(Paths.get("values", "strings.xml")));
        assertTrue("the stopped copy must exist", strings.contains("<string name=\"chat_stopped\">已停止</string>"));
        assertTrue("the follow-latest copy must exist",
                strings.contains("<string name=\"chat_back_to_latest\">回到最新</string>"));
        assertTrue("the follow-latest count copy must exist",
                strings.contains("<string name=\"chat_back_to_latest_count\">回到最新（%1$d）</string>"));
    }
}
