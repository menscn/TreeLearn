package com.treelearn.app.screen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * T-2026-10-23-066: the persona chip is the prototype's green-soft pill inside a 44dp control, the
 * popover rows are generated from the seven-value enum, and the dock keeps the tools on a line of
 * their own so the draft never has to share a single row with two chips.
 */
public final class PersonaPickerLayoutTest {

    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app")
            .toAbsolutePath();

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
    public void theChipIsTheGreenSoftPillFromThePrototype() throws Exception {
        String chip = read(RES.resolve(Paths.get("layout", "view_persona_chip.xml")));

        assertTrue("the pill is the shared capsule",
                chip.contains("android:background=\"@drawable/bg_pill\""));
        assertTrue("filled with the identity soft tone",
                chip.contains("android:backgroundTint=\"@color/tl_green_soft\""));
        assertTrue("the name is the identity deep green and bold",
                chip.contains("android:textColor=\"@color/tl_green_deep\"")
                        && chip.contains("android:textStyle=\"bold\""));
        assertTrue("the name is the chip type scale",
                chip.contains("android:textSize=\"@dimen/text_cap\""));
        assertTrue("the pill keeps the model chip's height",
                chip.contains("android:minHeight=\"@dimen/model_chip_height\""));
        assertTrue("the chevron advertises the popover",
                elementAround(chip, "persona_chip_chevron")
                        .contains("android:src=\"@drawable/ic_chevron_down\""));
    }

    @Test
    public void thePopoverRowIsAFullTapTargetWithANameTagAndCheck() throws Exception {
        String row = read(RES.resolve(Paths.get("layout", "view_persona_option.xml")));

        assertTrue("every row is a 44dp target",
                row.contains("android:minHeight=\"@dimen/touch_min\""));
        assertTrue("the row keeps the platform ripple",
                row.contains("android:foreground=\"?android:attr/selectableItemBackground\""));
        assertTrue("the name is ink and semibold",
                row.contains("android:textSize=\"@dimen/text_sm\"")
                        && row.contains("android:textStyle=\"bold\""));
        assertTrue("the tag is a small pill",
                elementAround(row, "persona_option_tag")
                        .contains("android:background=\"@drawable/bg_pill\""));
        assertTrue("the current mark is the check glyph",
                elementAround(row, "persona_option_check")
                        .contains("android:src=\"@drawable/ic_check\""));
    }

    @Test
    public void thePickerBuildsSevenRowsFromTheEnumAndKeepsTheTarget() throws Exception {
        String picker = read(SRC.resolve(Paths.get("screen", "PersonaPicker.java")));

        assertTrue("the rows follow the enum order", picker.contains("for (Persona persona : Persona.values())"));
        assertTrue("each row is the shared option layout",
                picker.contains("R.layout.view_persona_option"));
        assertTrue("the container is the touch target",
                picker.contains("R.dimen.touch_min"));
        assertTrue("the chip and the popover report through the listener",
                picker.contains("onPersonaSelected"));
    }

    @Test
    public void theDockGivesTheTwoChipsTheirOwnToolsLine() throws Exception {
        String chat = read(RES.resolve(Paths.get("layout", "screen_chat_body.xml")));

        String card = elementAround(chat, "chat_composer_card");
        assertTrue("the dock card stacks its two lines", card.contains("android:orientation=\"vertical\""));
        assertTrue("the input line is horizontal",
                elementAround(chat, "chat_composer_row").contains("android:orientation=\"horizontal\""));
        assertTrue("the tools line is horizontal",
                elementAround(chat, "chat_composer_tools").contains("android:orientation=\"horizontal\""));
        assertTrue("the persona chip sits beside the model chip",
                elementAround(chat, "chat_persona_chip")
                        .contains("com.treelearn.app.screen.PersonaPicker"));
        assertFalse("the persona chip is not hidden",
                elementAround(chat, "chat_persona_chip").contains("android:visibility=\"gone\""));
    }

    @Test
    public void everyEnumValueHasANameAndATag() throws Exception {
        String strings = read(RES.resolve(Paths.get("values", "strings.xml")));

        String[] keys = {
            "persona_default", "persona_hand_holding", "persona_strict_mentor",
            "persona_study_buddy", "persona_sarcastic_friend", "persona_late_night",
            "persona_learning_repair"
        };
        for (String key : keys) {
            assertTrue("missing name " + key,
                    strings.contains("<string name=\"" + key + "\">"));
            assertTrue("missing tag " + key + "_tag",
                    strings.contains("<string name=\"" + key + "_tag\">"));
        }
    }
}
