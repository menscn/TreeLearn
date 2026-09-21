package com.treelearn.app;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * T-002 acceptance evidence: the launcher Activity starts on a real device /
 * emulator and renders its first screen without crashing.
 *
 * <p>Deliberately written against the platform {@code Instrumentation} API so the
 * androidTest classpath stays free of Kotlin (no Espresso, no AndroidX core).
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
        final String[] statusHolder = new String[1];
        instrumentation.runOnMainSync(() -> {
            rootHolder[0] = activity.findViewById(R.id.main_root);
            TextView status = activity.findViewById(R.id.main_status);
            statusHolder[0] = status == null ? null : status.getText().toString();
        });

        assertNotNull("activity_main root view was not inflated", rootHolder[0]);
        assertNotNull("main_status TextView is missing from the first screen", statusHolder[0]);
        assertTrue("first screen did not render its skeleton status text",
                statusHolder[0].length() > 0);

        activity.finish();
    }
}
