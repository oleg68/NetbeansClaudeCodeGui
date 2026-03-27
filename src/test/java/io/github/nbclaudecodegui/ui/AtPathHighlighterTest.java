package io.github.nbclaudecodegui.ui;

import java.awt.Color;
import java.lang.reflect.Field;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AtPathHighlighter}.
 *
 * <p>Verifies that {@code @path} tokens in the text area are highlighted and
 * that highlights are cleared when tokens are removed.
 */
class AtPathHighlighterTest {

    @Test
    void singleAtTokenProducesOneHighlight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = new JTextArea();
            AtPathHighlighter h = AtPathHighlighter.install(area);
            area.setText("@src/Foo.java");
            h.rehighlight();
            assertEquals(1, h.highlightCount(), "One @-token must produce one highlight");
        });
    }

    @Test
    void multipleAtTokensProduceMultipleHighlights() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = new JTextArea();
            AtPathHighlighter h = AtPathHighlighter.install(area);
            area.setText("See @src/A.java and @src/B.java for details");
            h.rehighlight();
            assertEquals(2, h.highlightCount(), "Two @-tokens must produce two highlights");
        });
    }

    @Test
    void noAtTokenProducesNoHighlight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = new JTextArea();
            AtPathHighlighter h = AtPathHighlighter.install(area);
            area.setText("No at-tokens here");
            h.rehighlight();
            assertEquals(0, h.highlightCount(), "No @-tokens must produce no highlights");
        });
    }

    @Test
    void removingTokenClearsHighlight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = new JTextArea();
            AtPathHighlighter h = AtPathHighlighter.install(area);
            area.setText("@src/Foo.java");
            h.rehighlight();
            assertEquals(1, h.highlightCount(), "One highlight before token removal");

            area.setText("plain text");
            h.rehighlight();
            assertEquals(0, h.highlightCount(), "Zero highlights after token removed");
        });
    }

    @Test
    void documentListenerTriggersRehighlight() throws Exception {
        // Document listener is attached; typing text should auto-trigger rehighlight
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = new JTextArea();
            AtPathHighlighter h = AtPathHighlighter.install(area);

            // Simulate user typing: insertions trigger DocumentListener
            area.setText("@foo/bar.txt");
            // The DocumentListener fires after setText; highlight count should be 1
            assertEquals(1, h.highlightCount(), "Highlight must be applied via document listener");
        });
    }

    @Test
    void atTokenAtStartOfLine() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = new JTextArea();
            AtPathHighlighter h = AtPathHighlighter.install(area);
            area.setText("@/absolute/path/File.java\nsome other text");
            h.rehighlight();
            assertEquals(1, h.highlightCount());
        });
    }

    @Test
    void painterIsNotDefaultBackgroundPainter() throws Exception {
        // Verify that the highlight painter is NOT DefaultHighlightPainter (background),
        // i.e. that we switched to foreground painting.
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = new JTextArea();
            AtPathHighlighter.install(area);
            area.setText("@src/Foo.java");
            Highlighter hl = area.getHighlighter();
            for (Highlighter.Highlight h : hl.getHighlights()) {
                assertFalse(
                    h.getPainter() instanceof DefaultHighlighter.DefaultHighlightPainter,
                    "Painter must NOT be DefaultHighlightPainter (background); expected foreground painter"
                );
            }
        });
    }

    @Test
    void highlightColorIsViolet() throws Exception {
        Field f = AtPathHighlighter.class.getDeclaredField("HIGHLIGHT_COLOR");
        f.setAccessible(true);
        Color c = (Color) f.get(null);
        assertTrue(c.getRed()   >= 0x80, "Red component must be >= 0x80 for violet/purple");
        assertTrue(c.getBlue()  >= 0x80, "Blue component must be >= 0x80 for violet/purple");
        assertTrue(c.getGreen() <  0x80, "Green component must be < 0x80 for violet/purple");
    }

    @Test
    void multilineTextWithMultipleTokens() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = new JTextArea();
            AtPathHighlighter h = AtPathHighlighter.install(area);
            area.setText("@/path/A.java\n@/path/B.java\nPlease fix these files.");
            h.rehighlight();
            assertEquals(2, h.highlightCount(), "Two @-tokens on separate lines must produce two highlights");
        });
    }
}
