package com.treelearn.app;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.TreeCanvasScreen;
import com.treelearn.app.ui.PopupPlacement;
import com.treelearn.app.ui.TreeCanvasViewState;
import com.treelearn.treelayout.Density;
import com.treelearn.treelayout.LayoutNode;
import com.treelearn.treelayout.LayoutResult;
import com.treelearn.treelayout.Pt;
import com.treelearn.treelayout.TreeLayout;
import com.treelearn.treelayout.TreeSnapshot;
import com.treelearn.treelayout.ViewTransform;
import com.treelearn.treelayout.ViewportConstraints;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class TreeCanvasVisualEvidenceInstrumentedTest {

    @Test
    public void writesIsolatedCanvasStatesForVisualComparison() throws Exception {
        android.app.Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Context target = instrumentation.getTargetContext();
        Activity host = instrumentation.startActivitySync(
                new Intent(target, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            FrameLayout root = host.findViewById(android.R.id.content);
            TreeCanvasScreen phone = new TreeCanvasScreen(host);
            instrumentation.runOnMainSync(() -> root.addView(phone, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)));
            instrumentation.waitForIdleSync();

            View phoneCanvas = phone.findViewById(R.id.tree_canvas);
            ViewportConstraints phoneViewport = ViewportConstraints.of(phoneCanvas.getWidth(),
                    phoneCanvas.getHeight());
            TreeCanvasViewState ready = ready(phoneViewport);
            instrumentation.runOnMainSync(() -> phone.render(ready));
            instrumentation.waitForIdleSync();
            writePng(target, "tree-canvas-phone-single.png", draw(phone));

            float density = target.getResources().getDisplayMetrics().density;
            PopupPlacement popup = new PopupPlacement("root", 120f, 220f,
                    244f * density, 168f * density, false, false);
            instrumentation.runOnMainSync(() -> phone.render(ready.withPopup(popup)));
            instrumentation.waitForIdleSync();
            writePng(target, "tree-canvas-phone-popup.png",
                    overlayPopup(target, phone, draw(phone), popup));

            instrumentation.runOnMainSync(() -> phone.render(
                    TreeCanvasViewState.initial(null, null, phoneViewport)
                            .withPhase(TreeCanvasViewState.Phase.EMPTY)));
            instrumentation.waitForIdleSync();
            writePng(target, "tree-canvas-phone-empty.png", draw(phone));

            Configuration tabletConfiguration = new Configuration(
                    target.getResources().getConfiguration());
            tabletConfiguration.smallestScreenWidthDp = 600;
            Context tablet = target.createConfigurationContext(tabletConfiguration);
            TreeCanvasScreen tabletScreen = new TreeCanvasScreen(tablet);
            int tabletWidth = 1200;
            int tabletHeight = 800;
            instrumentation.runOnMainSync(() -> {
                tabletScreen.measure(View.MeasureSpec.makeMeasureSpec(tabletWidth,
                                View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(tabletHeight, View.MeasureSpec.EXACTLY));
                tabletScreen.layout(0, 0, tabletWidth, tabletHeight);
                View tabletCanvas = tabletScreen.findViewById(R.id.tree_canvas);
                ViewportConstraints tabletViewport = ViewportConstraints.of(tabletCanvas.getWidth(),
                        tabletCanvas.getHeight());
                tabletScreen.render(ready(tabletViewport));
            });
            instrumentation.waitForIdleSync();
            writePng(target, "tree-canvas-sw600-landscape.png", draw(tabletScreen));
        } finally {
            host.finish();
        }
    }

    private TreeCanvasViewState ready(ViewportConstraints viewport) {
        TreeSnapshot snapshot = TreeSnapshot.of("root", Arrays.asList(
                new LayoutNode("root", null, "从问题开始", "先把好奇写下来", 1, 1, false, false),
                new LayoutNode("child", "root", "第一条分支", "沿着问题继续追问", 2, 2, false, false),
                new LayoutNode("bud", "root", "探索更多", "", 1, 0, true, false),
                new LayoutNode("hidden", "bud", "隐藏节点", "", 0, 3, false, false)));
        LayoutResult layout = TreeLayout.compute(snapshot, "root", Density.forScale(1f), viewport);
        Pt anchor = layout.anchorOf("root");
        float scale = 0.92f;
        ViewTransform transform = new ViewTransform(scale, viewport.width / 2f - anchor.x * scale,
                viewport.height - 180f - anchor.y * scale);
        return TreeCanvasViewState.initial("visual-evidence-topic", "root", viewport)
                .withPhase(TreeCanvasViewState.Phase.READY)
                .withFrame(layout, transform)
                .withSnapshot(snapshot);
    }

    private Bitmap draw(View view) {
        Bitmap[] holder = new Bitmap[1];
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            int width = Math.max(1, view.getWidth());
            int height = Math.max(1, view.getHeight());
            holder[0] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(holder[0]));
        });
        return holder[0];
    }

    private Bitmap overlayPopup(Context target, TreeCanvasScreen screen, Bitmap base,
            PopupPlacement placement) {
        View popup = LayoutInflater.from(target).inflate(R.layout.view_node_popup, null, false);
        ((TextView) popup.findViewById(R.id.node_popup_title)).setText("从问题开始");
        ((TextView) popup.findViewById(R.id.node_popup_summary)).setText("先把好奇写下来");
        ((TextView) popup.findViewById(R.id.node_popup_unread)).setText("未读 1");
        popup.measure(View.MeasureSpec.makeMeasureSpec(Math.round(placement.width),
                        View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        popup.layout(0, 0, popup.getMeasuredWidth(), popup.getMeasuredHeight());
        View canvas = screen.findViewById(R.id.tree_canvas);
        Canvas output = new Canvas(base);
        output.save();
        output.translate(placement.x, canvas.getTop() + placement.y);
        popup.draw(output);
        output.restore();
        return base;
    }

    private void writePng(Context target, String name, Bitmap bitmap) throws IOException {
        File file = new File(target.getCacheDir(), name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue("PNG must be writable: " + file, bitmap.compress(Bitmap.CompressFormat.PNG,
                    100, output));
        }
        assertTrue("PNG must exist: " + file, file.isFile() && file.length() > 0L);
    }
}
