package io.github.nbclaudecodegui.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

/**
 * Highlights {@code @path} tokens in an {@link AtHighlightTextArea} with a blue foreground.
 *
 * <p>Uses a custom {@link JTextArea} subclass that overrides {@code paintComponent()}
 * to redraw token text after the default rendering, ensuring the colour always
 * appears on top of the default black glyphs and the selection highlight.
 *
 * <p>Install with {@link #install(AtHighlightTextArea)}.
 */
public final class AtPathHighlighter {

    /** Matches @-tokens: @ followed by one or more non-whitespace characters. */
    private static final Pattern AT_TOKEN = Pattern.compile("@\\S+");

    static final Color AT_TOKEN_COLOR = new Color(0x4A, 0x9E, 0xCD);

    private final AtHighlightTextArea area;
    /** Current token ranges [start, end) — kept for {@link #highlightCount()}. */
    private final List<int[]> ranges = new ArrayList<>();

    private AtPathHighlighter(AtHighlightTextArea area) {
        this.area = area;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Installs an {@code AtPathHighlighter} on the given text area.
     *
     * @param area the {@link AtHighlightTextArea} to highlight
     * @return the installed highlighter (kept for testing)
     */
    public static AtPathHighlighter install(AtHighlightTextArea area) {
        AtPathHighlighter h = new AtPathHighlighter(area);
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { h.rehighlight(); }
            @Override public void removeUpdate(DocumentEvent e)  { h.rehighlight(); }
            @Override public void changedUpdate(DocumentEvent e) { h.rehighlight(); }
        });
        // Repaint on selection changes so selected vs unselected portions update correctly.
        area.addCaretListener(e -> area.repaint());
        return h;
    }

    /**
     * Re-computes token ranges from the current document text and triggers a repaint.
     * Called automatically by the document listener; also available for direct call.
     */
    public void rehighlight() {
        ranges.clear();
        String text;
        try {
            text = area.getDocument().getText(0, area.getDocument().getLength());
        } catch (BadLocationException ex) {
            area.setRanges(ranges);
            area.repaint();
            return;
        }
        Matcher m = AT_TOKEN.matcher(text);
        while (m.find()) {
            ranges.add(new int[]{m.start(), m.end()});
        }
        area.setRanges(new ArrayList<>(ranges));
        area.repaint();
    }

    /**
     * Returns the number of currently active token ranges. Package-private for testing.
     */
    int highlightCount() {
        return ranges.size();
    }

    // -------------------------------------------------------------------------
    // AtHighlightTextArea — custom JTextArea subclass
    // -------------------------------------------------------------------------

    /**
     * A {@link JTextArea} that redraws {@code @path} token ranges in
     * {@link AtPathHighlighter#AT_TOKEN_COLOR} after the default rendering,
     * so token text is always visible on top of both normal and selected text.
     */
    public static final class AtHighlightTextArea extends JTextArea {

        /** Current highlighted @-token ranges as {@code [start, end]} pairs. */
        private List<int[]> ranges = new ArrayList<>();

        /** Creates an empty text area. */
        public AtHighlightTextArea() { super(); }

        /**
         * Creates a text area with the given dimensions.
         *
         * @param rows number of visible rows
         * @param cols number of visible columns
         */
        public AtHighlightTextArea(int rows, int cols) { super(rows, cols); }

        /** Called by {@link AtPathHighlighter#rehighlight()} with updated ranges. */
        void setRanges(List<int[]> r) {
            this.ranges = r;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (ranges.isEmpty()) return;
            FontMetrics fm = g.getFontMetrics(getFont());
            int selStart = getSelectionStart();
            int selEnd   = getSelectionEnd();
            for (int[] range : ranges) {
                // Split token into three parts relative to the current selection:
                //   before selection — erase background, draw in AT_TOKEN_COLOR
                //   inside selection — keep selection background, draw in AT_TOKEN_COLOR
                //   after  selection — erase background, draw in AT_TOKEN_COLOR
                paintPart(g, fm, range[0],                     Math.min(range[1], selStart), false);
                paintPart(g, fm, Math.max(range[0], selStart), Math.min(range[1], selEnd),   true);
                paintPart(g, fm, Math.max(range[0], selEnd),   range[1],                     false);
            }
        }

        private void paintPart(Graphics g, FontMetrics fm, int start, int end, boolean selected) {
            if (start >= end) return;
            try {
                Rectangle r0 = modelToView(start);
                Rectangle r1 = modelToView(end);
                if (r0 == null || r1 == null) return;
                int x = r0.x, y = r0.y, w = r1.x - r0.x, h = r0.height;
                if (!selected) {
                    g.setColor(getBackground());
                    g.fillRect(x, y, w, h);
                }
                g.setColor(AT_TOKEN_COLOR);
                g.setFont(getFont());
                g.drawString(getDocument().getText(start, end - start), x, y + fm.getAscent());
            } catch (BadLocationException ex) { /* skip */ }
        }
    }
}
