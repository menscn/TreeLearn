package com.treelearn.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Single-activity host for TreeLearn.
 *
 * <p>Screen-level controllers are attached in later tasks. This task only proves
 * that the five-module skeleton builds, installs and renders a first screen
 * without crashing.
 */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView status = findViewById(R.id.main_status);
        status.setText(getString(R.string.main_skeleton_status));
    }
}
