package com.treelearn.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.MarkdownRenderer;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Verifies Markdown rendering against the real Android text/span implementation. */
@RunWith(AndroidJUnit4.class)
public final class MarkdownRendererInstrumentedTest {

    @Test
    public void commonMarkdownSyntaxBecomesReadableTextAndSpans() {
        CharSequence rendered = MarkdownRenderer.render(
                "# Heading\n\n**bold** and *italic* with `code`\n\n- one\n- two");

        assertFalse(rendered.toString().contains("**"));
        assertFalse(rendered.toString().contains("`code`"));
        assertTrue(rendered.toString().contains("Heading"));
        assertTrue(rendered.toString().contains("• one"));
        assertTrue(rendered instanceof Spanned);
        Spanned spans = (Spanned) rendered;
        assertTrue(spans.getSpans(0, rendered.length(), StyleSpan.class).length > 0);
        assertTrue(spans.getSpans(0, rendered.length(), TypefaceSpan.class).length > 0);
    }

    @Test
    public void fencedCodeAndLinksStaySelectableWithoutRawFenceMarkers() {
        CharSequence rendered = MarkdownRenderer.render(
                "> note\n\n```java\nreturn 1;\n```\n\n[Treelearn](https://example.com)");

        assertFalse(rendered.toString().contains("```"));
        assertTrue(rendered.toString().contains("│ note"));
        assertTrue(rendered.toString().contains("return 1;"));
        assertTrue(((Spanned) rendered).getSpans(0, rendered.length(), URLSpan.class).length > 0);
    }
}
