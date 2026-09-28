package com.treelearn.app;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.HomeDrawerScreen;
import com.treelearn.app.ui.HomeDrawerPresenter;
import com.treelearn.app.ui.HomeStats;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class HomeDrawerScreenInstrumentedTest {

    @Test
    public void emptyStatsKeepTheHeatCardButHideCalendarCells() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        HomeDrawerScreen screen = new HomeDrawerScreen(context);

        screen.render(new HomeDrawerPresenter.State(HomeDrawerPresenter.Phase.OPEN,
                Collections.emptyList(), HomeStats.empty(LocalDate.of(2026, 9, 27))));

        assertEquals(View.VISIBLE, screen.findViewById(R.id.drawer_heatmap_card).getVisibility());
        assertEquals(View.VISIBLE, screen.findViewById(R.id.drawer_heatmap_empty).getVisibility());
        assertEquals(View.GONE, screen.findViewById(R.id.drawer_heatmap_cells).getVisibility());
    }
}
