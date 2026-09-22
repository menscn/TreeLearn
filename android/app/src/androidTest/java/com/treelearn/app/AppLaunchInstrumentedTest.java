package com.treelearn.app;

import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The launcher Activity starts on a real device / emulator and renders its first screen without
 * crashing.
 *
 * <p>T-002 wrote this against the skeleton's status line. T-012 replaced that skeleton with the real
 * first screen (PRD 6.2.16 makes the home page the only entry page), so the assertion moved from "a
 * status string is non-empty" to "the home composer exists" - the same guarantee, checked against
 * what the first screen now is. The root id is unchanged, which is why the root check is untouched.
 *
 * <p>Deliberately written against the platform {@code Instrumentation} API so the androidTest
 * classpath stays free of Kotlin (no Espresso, no AndroidX core).
 */
@RunWith(AndroidJUnit4.class)
public final class AppLaunchInstrumentedTest {

    @Test
    public void mainActivityRendersFirstScreenWithoutCrash() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Activity activity = instrumentation.startActivitySync(intent);
        assertNotNull("MainActivity failed to launch", activity);

        final View[] rootHolder = new View[1];
        final View[] composerHolder = new View[1];
        instrumentation.runOnMainSync(() -> {
            rootHolder[0] = activity.findViewById(R.id.main_root);
            composerHolder[0] = activity.findViewById(R.id.home_draft);
        });

        assertNotNull("activity_main root view was not inflated", rootHolder[0]);
        assertNotNull("the home composer is missing from the first screen", composerHolder[0]);

        activity.finish();
    }
}