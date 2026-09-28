package com.treelearn.app.screen;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Structural assertions for the home composer (T-2026-09-27-004), read as plain files so they gate the
 * slice on the JVM the way {@code ChatMessageRowLayoutTest} does for the message row.
 *
 * <p>These are pins, not the RED of this task: the task's RED was the colour judgement in
 * {@link PaperRulesTest}, because that is the part with a value to derive. What is checked here is the
 * shape the slice promised - the field is the ruled field, the send control is a circular icon button
 * that still says what it does, both composer pills are pills, and the two paper textures both read
 * their colour from the one judgement. Every one of these names something the pre-slice markup did not
 * have (it had a plain {@code EditText}, a textual {@code Button} on {@code bg_button_primary}, and a
 * single-{@code TextView} key pill), so none of them is a tautology. */
public final class HomeComposerLayoutTest {

    /** Module-relative roots so the test works regardless of where Gradle runs it. */
    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app")
            .toAbsolutePath();

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** The element that owns the id: from the tag opening it to the end of the open tag. */
    private static String elementAround(String xml, String id) {
        int idIndex = xml.indexOf("android:id=\"@+id/" + id + "\"");
        assertTrue("id " + id + " must exist", idIndex >= 0);
        int elementEnd = xml.indexOf('>', idIndex);
        assertTrue("malformed layout", elementEnd >= 0);
        return xml.substring(Math.max(0, xml.lastIndexOf('<', idIndex)), elementEnd);
    }

    private static String composer() throws Exception {
        return read(RES.resolve(Paths.get("layout", "screen_home_body.xml")));
    }

    @Test
    public void theDraftFieldIsTheRuledFieldAndStartsOnTheGridsFirstRow() throws Exception {
        String field = elementAround(composer(), "home_draft");
        assertTrue("the field must be the ruled one, not a plain EditText",
                field.startsWith("<com.treelearn.app.screen.RuleEditText"));
        assertTrue("the rules are the field's background, so it must not paint one of its own",
                field.contains("android:background=\"@null\""));
        // The prototype's min-height:64px is two rows of its own grid. It is set from the grid token in
        // code rather than by minLines, because minLines would stack a third line-height on top of the
        // two the layout already measured (measured: 268px for a field that should be 208).
        assertTrue("minLines must not stack on top of the grid's own two rows",
                !field.contains("android:minLines"));
        String body = read(SRC.resolve(Paths.get("screen", "RuleEditText.java")));
        assertTrue("the empty field's height is derived from the grid token, not spelled in dp",
                body.contains("MIN_ROWS * gridSpacing") && body.contains("R.dimen.space_s8"));
        // The grid's phase is anchored to the top of the content box: any vertical padding would push
        // the text off the first row without moving the rules, which is exactly the drift to prevent.
        assertTrue("no top padding, or the first line leaves the first row",
                field.contains("android:paddingTop=\"0dp\""));
        assertTrue("no bottom padding, for the same reason",
                field.contains("android:paddingBottom=\"0dp\""));
    }

    @Test
    public void theSendControlIsACircularIconButtonThatStillSaysWhatItDoes() throws Exception {
        String send = elementAround(composer(), "home_send");
        assertTrue("the send control is an icon button, not a text button",
                send.startsWith("<ImageButton"));
        assertTrue("it carries the circular green pill", send.contains("@drawable/bg_send_fab"));
        assertTrue("with the prototype's send glyph", send.contains("@drawable/ic_chat_send"));
        assertTrue("and no text of its own", !send.contains("android:text="));
        // An icon-only control loses its name unless it is described; the label moves from text to
        // contentDescription rather than disappearing.
        assertTrue("the icon-only button must keep the label as its content description",
                send.contains("android:contentDescription=\"@string/home_send\""));
        assertTrue("it starts disabled until there is a draft", send.contains("android:enabled=\"false\""));
        assertTrue("and it stays a 44dp target in both dimensions",
                send.contains("android:layout_width=\"@dimen/touch_min\"")
                        && send.contains("android:layout_height=\"@dimen/touch_min\""));
    }

    @Test
    public void theComposersFootLeadsWithTheModelChipAndCarriesNoKeyStatePill() throws Exception {
        String composer = composer();
        // 2026-09-28 user decision: the key-state pill was removed from the composer's foot, so the
        // settings key card is the one surface that reports a key's state. Pinned here because the
        // pill's ids were the thing a later "restore what was deleted" pass would reach for.
        assertTrue("no key-state pill id may come back",
                !composer.contains("@+id/home_key_hint")
                        && !composer.contains("@+id/home_key_icon")
                        && !composer.contains("@+id/home_key_text"));
        assertTrue("and no key glyph may linger in the composer",
                !composer.contains("@drawable/ic_key"));
        assertTrue("the model chip is what leads the foot now",
                elementAround(composer, "home_model_chip").startsWith(
                        "<com.treelearn.app.screen.ModelThinkingPicker"));
    }

    @Test
    public void bothComposerPillsAreShapedByThePillRadius() throws Exception {
        String pill = read(RES.resolve(Paths.get("drawable", "bg_pill.xml")));
        String chip = read(RES.resolve(Paths.get("drawable", "bg_chip.xml")));
        assertTrue("the key pill must be a pill",
                pill.contains("android:radius=\"@dimen/radius_full\""));
        assertTrue("so must the model chip",
                chip.contains("android:radius=\"@dimen/radius_full\""));
        assertTrue("and the model chip reads the model pill drawable",
                elementAround(composer(), "home_model_chip").startsWith(
                        "<com.treelearn.app.screen.ModelThinkingPicker"));
        assertTrue("whose own markup is the pill",
                read(RES.resolve(Paths.get("layout", "view_model_chip.xml")))
                        .contains("android:background=\"@drawable/bg_chip\""));
    }

    /**
     * The pill is small and the control is 44dp: the two must not be the same box, or the chip would be
     * either un-faithful (44dp tall) or unreachable (22dp). Pinned from both sides.
     */
    @Test
    public void theModelPillStaysSmallWhileItsHostKeepsTheTouchTarget() throws Exception {
        String pill = read(RES.resolve(Paths.get("layout", "view_model_chip.xml")));
        assertTrue("the pill sizes itself to its contents",
                pill.contains("android:layout_height=\"wrap_content\""));
        assertTrue("the pill must not claim the touch height",
                !pill.contains("android:layout_height=\"@dimen/touch_min\""));
        String picker = read(SRC.resolve(Paths.get("screen", "ModelThinkingPicker.java")));
        assertTrue("the host carries the touch height instead",
                picker.contains("R.dimen.touch_min"));
        assertTrue("and it is the host that takes the click",
                picker.contains("setOnClickListener(view -> togglePopover())"));
        assertTrue("so the node TalkBack reads is the host, described once",
                picker.contains("setContentDescription(chipLabel.getText())"));
    }

    /**
     * The one-entry-point invariant this slice is most likely to lose: the page texture and the writing
     * texture are two inks decided in one class. A later painter that re-assembles the composite by hand
     * would pass every pixel test and still be the drift this repository has already been burned by, so
     * the painters are pinned to the named judgement.
     */
    @Test
    public void bothPaperTexturesReadTheirColourFromTheOneJudgement() throws Exception {
        String field = read(SRC.resolve(Paths.get("screen", "RuleEditText.java")));
        String backdrop = read(SRC.resolve(Paths.get("screen", "PaperRuleBackdrop.java")));
        assertTrue("the writing field must read PaperRules#writingRuleColour",
                field.contains("PaperRules.writingRuleColour("));
        assertTrue("the page backdrop must read PaperRules#pageRuleColour",
                backdrop.contains("PaperRules.pageRuleColour("));
        assertTrue("and neither may inline a composite of its own",
                !field.contains("PaperRules.composite(") && !backdrop.contains("PaperRules.composite("));
        assertTrue("both must take their offsets from the same schedule",
                field.contains("PaperRules.lineOffsets(") && backdrop.contains("PaperRules.lineOffsets("));
    }
}
