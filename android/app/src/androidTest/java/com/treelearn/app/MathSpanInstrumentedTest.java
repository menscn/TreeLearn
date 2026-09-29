package com.treelearn.app;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.SpannableString;
import android.text.TextPaint;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import com.treelearn.app.ui.math.MathNode;
import com.treelearn.app.ui.math.MathParser;
import com.treelearn.app.ui.math.MathSpan;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Device proof for {@link MathSpan}: a tall formula must widen the line (LineHeightSpan duty, so it
 * is not clipped to the text font metrics) and drawing must actually paint visible strokes. The
 * drawn bitmap is also persisted to device Pictures so it can be pulled into artifacts/ as evidence.
 */
public class MathSpanInstrumentedTest {

    private static final int VIEW_WIDTH = 600;

    @Test
    public void tallFormulaIsNotClippedByLineHeight() {
        MathNode node = MathParser.parse("\\frac{\\sum_{i=1}^{n} x^2}{\\sqrt{y}}");
        assertNotNull("fixture must parse", node);
        MathSpan span = MathSpan.create(node, false);

        SpannableString text = new SpannableString("X");
        text.setSpan(span, 0, 1, SpannableString.SPAN_INCLUSIVE_EXCLUSIVE);
        TextView tv = new TextView(InstrumentationRegistry.getInstrumentation().getTargetContext());
        tv.setTextSize(16f);
        tv.setText(text);
        tv.measure(android.view.View.MeasureSpec.makeMeasureSpec(1000,
                        android.view.View.MeasureSpec.AT_MOST),
                android.view.View.MeasureSpec.makeMeasureSpec(1000,
                        android.view.View.MeasureSpec.AT_MOST));
        tv.layout(0, 0, tv.getMeasuredWidth(), tv.getMeasuredHeight());
        Layout layout = tv.getLayout();
        assertNotNull(layout);
        int lineHeight = layout.getLineBottom(0) - layout.getLineTop(0);
        float expected = span.geometry().height();
        assertTrue("span height " + expected + " must fit inside line " + lineHeight,
                lineHeight >= expected - 1f);
    }

    @Test
    public void drawProducesVisiblePixels() {
        MathNode node = MathParser.parse("\\frac{1}{2}+\\sqrt{x^2}");
        assertNotNull("fixture must parse", node);
        MathSpan span = MathSpan.create(node, false);

        SpannableString text = new SpannableString("X");
        text.setSpan(span, 0, 1, SpannableString.SPAN_INCLUSIVE_EXCLUSIVE);
        TextPaint paint = new TextPaint();
        paint.setTextSize(16f);
        paint.setAntiAlias(true);
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        span.draw(canvas, text, 0, 1, 20f, 100, 100, 100, paint);

        boolean nonWhite = false;
        for (int y = 0; y < bitmap.getHeight() && !nonWhite; y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) != Color.WHITE) {
                    nonWhite = true;
                    break;
                }
            }
        }
        assertTrue("formula must paint visible strokes", nonWhite);
        savePng(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                bitmap, "math-span-draw-evidence.png");
    }

    @Test
    public void drawScalesToHostPaintAndRestoresPaintState() {
        MathNode node = MathParser.parse("\\frac{1}{2}+x^2");
        assertNotNull("fixture must parse", node);
        MathSpan span = MathSpan.create(node, false);
        TextPaint paint = new TextPaint();
        paint.setTextSize(32f);
        paint.setColor(Color.BLUE);
        paint.setTextSkewX(0.2f);
        paint.setStrokeWidth(3f);
        float baseWidth = span.geometry().width;
        Paint.FontMetricsInt metrics = new Paint.FontMetricsInt();
        int scaledWidth = span.getSize(paint, "X", 0, 1, metrics);
        assertTrue("formula width must follow the host text size", scaledWidth > baseWidth * 1.5f);

        Bitmap bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        span.draw(canvas, "X", 0, 1, 20f, 100, 100, 100, paint);
        assertTrue("draw must restore text size", paint.getTextSize() == 32f);
        assertTrue("draw must restore color", paint.getColor() == Color.BLUE);
        assertTrue("draw must restore skew", paint.getTextSkewX() == 0.2f);
        assertTrue("draw must restore stroke width", paint.getStrokeWidth() == 3f);
    }

    /** Writes through MediaStore so the evidence outlives the post-run uninstall of the app. */
    private static void savePng(Context context, Bitmap bitmap, String name) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
        }
        Uri uri = context.getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return;
        try (OutputStream stream = context.getContentResolver().openOutputStream(uri)) {
            if (stream == null) return;
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (IOException e) {
            throw new AssertionError("cannot write evidence bitmap", e);
        }
    }
}
