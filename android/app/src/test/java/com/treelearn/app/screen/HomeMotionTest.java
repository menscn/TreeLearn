package com.treelearn.app.screen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * T-2026-09-27-007: the home's entrance motion - the prototype's two animations and the stagger.
 *
 * <p>The numbers are the prototype's own: {@code .screen} lines 117-118 give the screen entrance
 * (280ms, {@code translateX(14px)}, opacity 0 to 1), {@code .topic-card} lines 167-168 give the card
 * entrance (350ms, {@code translateY(10px)}, opacity 0 to 1), and line 1730 gives each card's delay
 * as {@code i * 0.06s}. They live in {@link HomeMotion} and nowhere else, the same way
 * {@code ChatMotion} holds the chat's - a screen that hardcodes a duration is the thing this test
 * exists to catch.
 *
 * <p>The last two cases are structural, not numeric, and they are the ones that keep a later edit
 * honest: the app reads the system animator scale in exactly one class ({@code ChatMotion}), so the
 * home must not grow a second copy of that judgement; and the shell must be the one that starts the
 * home's entrance, which is how "the home enters, the library and the settings do not" stays true.
 */
public final class HomeMotionTest {

    private static final Path MAIN = Paths.get("src", "main", "java").toAbsolutePath();

    private static String source(String relative) throws Exception {
        Path path = MAIN.resolve(relative);
        assertTrue("missing file: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String homeMotion() throws Exception {
        return source("com/treelearn/app/screen/HomeMotion.java");
    }

    private static String homeScreen() throws Exception {
        return source("com/treelearn/app/screen/HomeScreen.java");
    }

    // ------------------------------------------------------------------
    // The numbers, and the delay they feed.
    // ------------------------------------------------------------------

    @Test
    public void theEntranceNumbersAreThePrototypes() throws Exception {
        assertEquals(280L, HomeMotion.SCREEN_ENTER_MS);
        assertEquals(14, HomeMotion.SCREEN_SHIFT_DP);
        assertEquals(350L, HomeMotion.CARD_ENTER_MS);
        assertEquals(10, HomeMotion.CARD_SHIFT_DP);
        assertEquals(60L, HomeMotion.CARD_STAGGER_MS);
    }

    @Test
    public void eachCardWaitsOneStepBehindTheOneBeforeIt() {
        assertEquals("the first card starts at once", 0L, HomeMotion.cardDelayMs(0));
        assertEquals(60L, HomeMotion.cardDelayMs(1));
        assertEquals(120L, HomeMotion.cardDelayMs(2));
        assertEquals(180L, HomeMotion.cardDelayMs(3));
    }

    @Test
    public void theWaitStopsGrowingOnceItReachesTheCap() {
        long capped = HomeMotion.CARD_STAGGER_STEPS_MAX * HomeMotion.CARD_STAGGER_MS;
        assertEquals(capped, HomeMotion.cardDelayMs(HomeMotion.CARD_STAGGER_STEPS_MAX));
        assertEquals("past the cap every card waits the same", capped,
                HomeMotion.cardDelayMs(HomeMotion.CARD_STAGGER_STEPS_MAX + 1));
        assertEquals(capped, HomeMotion.cardDelayMs(10_000));
    }

    /**
     * The home stopped trimming its history list on 2026-10-23: the section head counts the whole
     * read, so a cap made the page announce six topics and draw three, and left the column shorter
     * than the scroll view - which is the one shape a scroll view answers "cannot scroll" to. The
     * stagger cap above is now the only thing that bounds the entrance, and it is a wait limit, not
     * a list limit.
     */
    @Test
    public void theHomeDrawsEveryTopicTheReadReturned() throws Exception {
        String screen = homeScreen();
        assertTrue("the history section must iterate the read, not a window of it",
                screen.contains("index < state.topics.size()"));
        assertTrue("no second list cap may come back: the head's count and the cards must be "
                        + "the same list",
                !screen.contains("MAX_RECENT") && !screen.contains("visibleCount"));
    }

    @Test
    public void aPositionBeforeTheFirstCardNeverWaitsNegatively() {
        assertEquals("an order below the first is not a wait at all",
                0L, HomeMotion.cardDelayMs(-1));
        assertEquals(0L, HomeMotion.cardDelayMs(Integer.MIN_VALUE));
    }

    // ------------------------------------------------------------------
    // Where the values live, and who is allowed to judge the low-motion switch.
    // ------------------------------------------------------------------

    @Test
    public void theScreenReadsTheMotionInsteadOfHardcodingIt() throws Exception {
        assertTrue("the screen must read HomeMotion, never a literal duration",
                homeScreen().contains("HomeMotion."));
    }

    @Test
    public void theLowMotionSwitchStaysInItsOnePlace() throws Exception {
        assertFalse("the app reads the system animator scale in exactly one class, and it is not this one",
                homeMotion().contains("ANIMATOR_DURATION_SCALE"));
        assertTrue("the home must consult that one switch before animating anything",
                homeScreen().contains("ChatMotion."));
    }

    @Test
    public void theShellIsWhatStartsTheHomesEntrance() throws Exception {
        assertTrue("the swap is where a screen becomes the visible one, so the home's entrance "
                        + "belongs to it - and to the home's path alone",
                source("com/treelearn/app/MainActivity.java").contains("playEntrance"));
    }
}
