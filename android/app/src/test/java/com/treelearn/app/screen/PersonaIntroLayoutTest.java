package com.treelearn.app.screen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * T-2026-10-23-067: the answer-style introduction is one static screen reached from the picker's
 * last row and from the settings list, and it can do nothing but be read.
 */
public final class PersonaIntroLayoutTest {

    private static final Path RES = Paths.get("src", "main", "res").toAbsolutePath();
    private static final Path SRC = Paths.get("src", "main", "java", "com", "treelearn", "app")
            .toAbsolutePath();

    private static String read(Path path) throws Exception {
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void theIntroPageIsAReadOnlyScrollWithABackKeyAndSevenCards() throws Exception {
        String screen = read(RES.resolve(Paths.get("layout", "screen_persona_intro.xml")));
        String java = read(SRC.resolve(Paths.get("screen", "PersonaIntroScreen.java")));

        assertTrue("the title names the page",
                screen.contains("android:text=\"@string/persona_intro_title\""));
        assertTrue("the subtitle says all seven share one floor",
                screen.contains("android:text=\"@string/persona_intro_subtitle\""));
        assertTrue("the back key keeps the 44dp target",
                screen.contains("android:id=\"@+id/persona_intro_back\"")
                        && screen.contains("android:layout_width=\"@dimen/touch_min\"")
                        && screen.contains("android:layout_height=\"@dimen/touch_min\""));
        assertTrue("the page scrolls", screen.contains("<ScrollView"));
        assertTrue("the cards are generated in enum order",
                java.contains("for (Persona persona : Persona.values())"));
        assertTrue("every card uses the shared layout",
                java.contains("R.layout.item_persona_intro_card"));
        assertFalse("reading must not reach a store", java.contains("ChatStore"));
        assertFalse("reading must not send a request", java.contains("ChatCommands"));
        assertFalse("reading must not write a style", java.contains("setPersona"));
    }

    @Test
    public void everyCardCarriesTheNamePositioningAndOneStaticDemo() throws Exception {
        String card = read(RES.resolve(Paths.get("layout", "item_persona_intro_card.xml")));

        assertTrue("the name is carried", card.contains("android:id=\"@+id/persona_intro_name\""));
        assertTrue("the tag is carried", card.contains("android:id=\"@+id/persona_intro_tag\""));
        assertTrue("the positioning line is carried",
                card.contains("android:id=\"@+id/persona_intro_sub\""));
        assertTrue("the demo question is carried",
                card.contains("android:id=\"@+id/persona_intro_question\""));
        assertTrue("the demo answer is carried",
                card.contains("android:id=\"@+id/persona_intro_answer\""));
        assertTrue("the demo block uses the faint green ground",
                card.contains("android:background=\"@drawable/bg_persona_demo\""));
        assertTrue("the demo ground is the token color",
                read(RES.resolve(Paths.get("drawable", "bg_persona_demo.xml")))
                        .contains("@color/tl_green_faint"));
    }

    @Test
    public void thePopoverEndsWithTheRoadToTheIntroPage() throws Exception {
        String more = read(RES.resolve(Paths.get("layout", "view_persona_more.xml")));
        String picker = read(SRC.resolve(Paths.get("screen", "PersonaPicker.java")));

        assertTrue("the row is a 44dp target",
                more.contains("android:minHeight=\"@dimen/touch_min\""));
        assertTrue("the row names the road",
                more.contains("android:text=\"@string/persona_intro_more\""));
        assertTrue("the row ends in a chevron",
                more.contains("android:src=\"@drawable/ic_chevron_right\""));
        assertTrue("the picker builds the row after the seven options",
                picker.contains("R.layout.view_persona_more"));
        assertTrue("the road is reported, not handled, by the picker",
                picker.contains("onIntroRequested"));
        assertTrue("the road is separated from the choices",
                picker.contains("R.color.tl_line"));
    }

    @Test
    public void theSettingsListCarriesTheSameRoad() throws Exception {
        String settings = read(RES.resolve(Paths.get("layout", "screen_settings_body.xml")));
        String java = read(SRC.resolve(Paths.get("screen", "SettingsScreen.java")));

        assertTrue("the entry row exists",
                settings.contains("android:id=\"@+id/settings_persona_intro_entry\""));
        assertTrue("the section is named",
                settings.contains("android:text=\"@string/persona_intro_title\""));
        assertTrue("the row explains itself",
                settings.contains("android:text=\"@string/settings_persona_intro_sub\""));
        assertTrue("the row ends in a chevron",
                settings.contains("android:src=\"@drawable/ic_chevron_right\""));
        assertTrue("the screen reports the tap",
                java.contains("setOnPersonaIntroPressed"));
    }

    @Test
    public void everyStyleHasAPositioningAndADemoInResources() throws Exception {
        String strings = read(RES.resolve(Paths.get("values", "strings.xml")));

        String[] keys = {
            "persona_default", "persona_hand_holding", "persona_strict_mentor",
            "persona_study_buddy", "persona_sarcastic_friend", "persona_late_night",
            "persona_learning_repair"
        };
        for (String key : keys) {
            assertTrue("missing positioning " + key + "_sub",
                    strings.contains("<string name=\"" + key + "_sub\">"));
            assertTrue("missing demo " + key + "_demo",
                    strings.contains("<string name=\"" + key + "_demo\">"));
        }
        assertTrue("the shared question is one string",
                strings.contains("<string name=\"persona_intro_question\">"));
        assertTrue("the footer names the future home of custom styles",
                strings.contains("<string name=\"persona_intro_footer\">"));
        assertTrue("the picker's last row has its label",
                strings.contains("<string name=\"persona_intro_more\">"));
    }
}
