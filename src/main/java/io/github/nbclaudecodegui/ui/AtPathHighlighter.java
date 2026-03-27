package io.github.nbclaudecodegui.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;
import javax.swing.text.Position;
import javax.swing.text.View;

/**
 * Highlights {@code @path} tokens in a {@link JTextArea} with a violet foreground.
 *
 * <p>Attaches a {@link DocumentListener} to the text area's document.
 * On every document change, all previous highlights are cleared and
 * new highlights are applied to every {@code @\S+} token found in the text.
 *
 * <p>Install with {@link #install(JTextArea)}.
 */
public final class AtPathHighlighter {

    /** Matches @-tokens: @ followed by one or more non-whitespace characters. */
    private static final Pattern AT_TOKEN = Pattern.compile("@\\S+");

    private static final Color HIGHLIGHT_COLOR = new Color(0x99, 0x33, 0xCC);

    private final JTextArea area;
    private final Highlighter.HighlightPainter painter;
    /** Tags returned by {@code Highlighter.addHighlight} — kept to remove later. */
    private final List<Object> highlights = new ArrayList<>();

    private AtPathHighlighter(JTextArea area) {
        this.area = area;
        this.painter = new ForegroundPainter(HIGHLIGHT_COLOR);
    }

    private static final class ForegroundPainter extends LayeredHighlighter.LayerPainter {
        private final Color color;
        ForegroundPainter(Color c) { this.color = c; }

        @Override
        public Shape paintLayer(Graphics g, int p0, int p1, Shape bounds,
                                JTextComponent c, View view) {
            try {
                Shape s = view.modelToView(p0, Position.Bias.Forward,
                                           p1, Position.Bias.Backward, bounds);
                Rectangle r = s instanceof Rectangle rect ? rect : s.getBounds();
                g.setColor(c.getBackground());
                g.fillRect(r.x, r.y, r.width, r.height);
                g.setColor(color);
                g.setFont(c.getFont());
                FontMetrics fm = g.getFontMetrics(c.getFont());
                String text = c.getDocument().getText(p0, p1 - p0);
                g.drawString(text, r.x, r.y + fm.getAscent());
                return r;
            } catch (BadLocationException ex) {
                return bounds;
            }
        }

        @Override
        public void paint(Graphics g, int p0, int p1, Shape b, JTextComponent c) {}
    }

    /**
     * Creates and installs an {@code AtPathHighlighter} on the given text area.
     *
     * @param area the text area to highlight
     * @return the installed highlighter (kept for testing)
     */
    public static AtPathHighlighter install(JTextArea area) {
        AtPathHighlighter h = new AtPathHighlighter(area);
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { h.rehighlight(); }
            @Override public void removeUpdate(DocumentEvent e)  { h.rehighlight(); }
            @Override public void changedUpdate(DocumentEvent e) { h.rehighlight(); }
        });
        return h;
    }

    /**
     * Re-applies highlights to all {@code @\S+} tokens in the current document text.
     * Called automatically by the document listener; also available for direct call.
     */
    public void rehighlight() {
        Highlighter hl = area.getHighlighter();
        // Remove previous @-path highlights
        for (Object tag : highlights) {
            hl.removeHighlight(tag);
        }
        highlights.clear();

        String text;
        try {
            text = area.getDocument().getText(0, area.getDocument().getLength());
        } catch (BadLocationException ex) {
            return;
        }

        Matcher m = AT_TOKEN.matcher(text);
        while (m.find()) {
            try {
                Object tag = hl.addHighlight(m.start(), m.end(), painter);
                highlights.add(tag);
            } catch (BadLocationException ex) {
                // Skip this token
            }
        }
    }

    /**
     * Returns the number of currently active highlights. Package-private for testing.
     */
    int highlightCount() {
        return highlights.size();
    }
}
