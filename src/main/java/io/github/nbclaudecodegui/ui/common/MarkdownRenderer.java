package io.github.nbclaudecodegui.ui.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JEditorPane;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/**
 * Converts markdown-formatted text to HTML and appends it into a
 * {@link JEditorPane} backed by an {@link HTMLEditorKit}.
 *
 * <p>Supported markdown constructs:
 * <ul>
 *   <li>Fenced code blocks ({@code ```...```})</li>
 *   <li>Inline code ({@code `...`})</li>
 *   <li>Bold ({@code **...**}), italic ({@code *...*}),
 *       bold-italic ({@code ***...***})</li>
 *   <li>ATX headings ({@code #}, {@code ##}, {@code ###})</li>
 *   <li>Unordered list items ({@code - item}, {@code * item})</li>
 *   <li>Ordered list items ({@code 1. item})</li>
 *   <li>Blockquotes ({@code > text})</li>
 *   <li>Pipe tables ({@code | Col | Col |})</li>
 * </ul>
 *
 * <p>All public methods must be called on the Event Dispatch Thread.
 */
public final class MarkdownRenderer {

    // -------------------------------------------------------------------------
    // patterns for inline formatting
    // -------------------------------------------------------------------------

    private static final Pattern INLINE = Pattern.compile(
            "\\*\\*\\*(.+?)\\*\\*\\*"    // group 1: bold-italic
            + "|\\*\\*(.+?)\\*\\*"       // group 2: bold
            + "|\\*([^*\\n]+)\\*"        // group 3: italic
            + "|`([^`]+)`",              // group 4: inline code
            Pattern.DOTALL);

    // -------------------------------------------------------------------------
    // factory
    // -------------------------------------------------------------------------

    /**
     * Creates and configures a {@link JEditorPane} for rendering chat output.
     *
     * <p>The pane is non-editable, uses {@link HTMLEditorKit}, and has a
     * stylesheet suitable for markdown-rendered content.
     *
     * @return a ready-to-use output pane
     */
    public static JEditorPane createOutputPane() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet ss = kit.getStyleSheet();
        ss.addRule("body   { font-family: sans-serif; font-size: 13pt;"
                + "         margin: 6px; padding: 0; }");
        ss.addRule("p      { margin-top: 2px; margin-bottom: 4px; }");
        ss.addRule("pre    { background-color: #ebebeb; padding: 6px;"
                + "         font-family: monospace; font-size: 12pt;"
                + "         white-space: pre-wrap; margin: 4px 0; }");
        ss.addRule("code   { background-color: #f0f0f0;"
                + "         font-family: monospace; font-size: 12pt; }");
        ss.addRule("table  { border-collapse: collapse; margin: 4px 0; }");
        ss.addRule("th, td { border: 1px solid #bbb; padding: 3px 8px; }");
        ss.addRule("th     { background-color: #e0e0e0; font-weight: bold; }");
        ss.addRule("blockquote { color: #555; font-style: italic;"
                + "             border-left: 3px solid #ccc;"
                + "             margin-left: 8px; padding-left: 8px; }");
        ss.addRule("h1 { font-size: 16pt; margin: 6px 0 2px; }");
        ss.addRule("h2 { font-size: 14pt; margin: 6px 0 2px; }");
        ss.addRule("h3 { font-size: 13pt; margin: 4px 0 2px; }");
        ss.addRule(".user-label { color: #0064b4; font-weight: bold; }");
        ss.addRule(".info  { color: #888; font-size: 11pt; }");

        JEditorPane pane = new JEditorPane();
        pane.setEditorKit(kit);
        pane.setDocument(kit.createDefaultDocument());
        pane.setEditable(false);
        pane.setCursor(java.awt.Cursor.getPredefinedCursor(
                java.awt.Cursor.TEXT_CURSOR));
        BasicTextContextMenu.attach(pane, BasicTextContextMenu.createReadOnly(pane));
        return pane;
    }

    // -------------------------------------------------------------------------
    // public append API
    // -------------------------------------------------------------------------

    /**
     * Appends a user message with a styled {@code "You: "} prefix.
     *
     * @param pane the target pane (must be called on EDT)
     * @param text the user's message (plain text, not markdown)
     */
    public static void appendUserMessage(JEditorPane pane, String text) {
        appendHtml(pane,
                "<p><span class=\"user-label\">You:</span>&nbsp;"
                + esc(text) + "</p>");
    }

    /**
     * Appends a markdown-formatted assistant response.
     *
     * @param pane     the target pane (must be called on EDT)
     * @param markdown the markdown text returned by claude
     */
    public static void appendAssistantResponse(JEditorPane pane, String markdown) {
        appendHtml(pane, "<div>" + toHtml(markdown) + "</div>");
    }

    /**
     * Appends a plain informational or status line.
     *
     * @param pane the target pane (must be called on EDT)
     * @param line the text to append
     */
    public static void appendInfo(JEditorPane pane, String line) {
        appendHtml(pane, "<p class=\"info\">" + esc(line) + "</p>");
    }

    // -------------------------------------------------------------------------
    // markdown → HTML conversion (package-private for testing)
    // -------------------------------------------------------------------------

    /**
     * Converts a markdown string to an HTML fragment suitable for insertion
     * into an {@link HTMLDocument}.
     *
     * @param markdown the markdown source
     * @return the corresponding HTML fragment (no {@code <html>/<body>} wrapper)
     */
    public static String toHtml(String markdown) {
        StringBuilder html = new StringBuilder();
        // Split on fenced code blocks; odd-indexed segments are code content.
        String[] parts = markdown.split("```[^\\n]*\\n", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 0) {
                // regular text segment
                // strip trailing ``` from previous code block end
                String seg = parts[i];
                if (i > 0 && seg.startsWith("```")) {
                    seg = seg.substring(3);
                }
                html.append(convertSegment(seg));
            } else {
                // code block content (strip trailing ```)
                String code = parts[i];
                if (code.endsWith("```")) {
                    code = code.substring(0, code.length() - 3);
                }
                if (code.endsWith("\n")) {
                    code = code.substring(0, code.length() - 1);
                }
                html.append("<pre>").append(esc(code)).append("</pre>");
            }
        }
        return html.toString();
    }

    // -------------------------------------------------------------------------
    // block-level conversion
    // -------------------------------------------------------------------------

    private static String convertSegment(String text) {
        StringBuilder html = new StringBuilder();
        String[] lines = text.split("\n", -1);
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];

            if (line.isBlank()) {
                i++;
                continue;
            }

            // --- Headings ---
            if (line.startsWith("# ")) {
                html.append("<h1>").append(inlineToHtml(line.substring(2).trim()))
                        .append("</h1>");
                i++;
                continue;
            }
            if (line.startsWith("## ")) {
                html.append("<h2>").append(inlineToHtml(line.substring(3).trim()))
                        .append("</h2>");
                i++;
                continue;
            }
            if (line.startsWith("### ")) {
                html.append("<h3>").append(inlineToHtml(line.substring(4).trim()))
                        .append("</h3>");
                i++;
                continue;
            }

            // --- Table ---
            if (isTableRow(line) && i + 1 < lines.length
                    && isSeparatorRow(lines[i + 1])) {
                i = renderTable(lines, i, html);
                continue;
            }

            // --- Unordered list ---
            if (isUnordered(line)) {
                html.append("<ul>");
                while (i < lines.length && isUnordered(lines[i])) {
                    html.append("<li>")
                            .append(inlineToHtml(lines[i]
                                    .replaceFirst("^\\s*[-*]\\s+", "")))
                            .append("</li>");
                    i++;
                }
                html.append("</ul>");
                continue;
            }

            // --- Ordered list ---
            if (isOrdered(line)) {
                html.append("<ol>");
                while (i < lines.length && isOrdered(lines[i])) {
                    html.append("<li>")
                            .append(inlineToHtml(lines[i]
                                    .replaceFirst("^\\s*\\d+\\.\\s+", "")))
                            .append("</li>");
                    i++;
                }
                html.append("</ol>");
                continue;
            }

            // --- Blockquote ---
            if (line.startsWith("> ")) {
                html.append("<blockquote>");
                while (i < lines.length && lines[i].startsWith("> ")) {
                    html.append(inlineToHtml(lines[i].substring(2)));
                    i++;
                    if (i < lines.length && lines[i].startsWith("> ")) {
                        html.append("<br>");
                    }
                }
                html.append("</blockquote>");
                continue;
            }

            // --- Paragraph ---
            html.append("<p>");
            while (i < lines.length && !lines[i].isBlank()
                    && !isBlockStart(lines[i])
                    && !(isTableRow(lines[i]) && i + 1 < lines.length
                            && isSeparatorRow(lines[i + 1]))) {
                html.append(inlineToHtml(lines[i]));
                i++;
                if (i < lines.length && !lines[i].isBlank()
                        && !isBlockStart(lines[i])) {
                    html.append(" ");
                }
            }
            html.append("</p>");
        }

        return html.toString();
    }

    // -------------------------------------------------------------------------
    // table rendering
    // -------------------------------------------------------------------------

    /**
     * Renders a table starting at {@code lines[start]}.
     *
     * <p>{@code lines[start]} is the header row, {@code lines[start+1]} is the
     * separator row (e.g., {@code |---|---|}).  Subsequent {@code |…|} rows are
     * data rows.
     *
     * @param lines the full line array
     * @param start index of the header row
     * @param out   output buffer
     * @return index of the first line after the table
     */
    private static int renderTable(String[] lines, int start, StringBuilder out) {
        out.append("<table>");

        // Header row
        out.append("<tr>");
        for (String cell : parseCells(lines[start])) {
            out.append("<th>").append(inlineToHtml(cell.trim())).append("</th>");
        }
        out.append("</tr>");

        // Skip separator row
        int i = start + 2;

        // Data rows
        while (i < lines.length && isTableRow(lines[i])) {
            out.append("<tr>");
            for (String cell : parseCells(lines[i])) {
                out.append("<td>").append(inlineToHtml(cell.trim())).append("</td>");
            }
            out.append("</tr>");
            i++;
        }

        out.append("</table>");
        return i;
    }

    // -------------------------------------------------------------------------
    // inline formatting
    // -------------------------------------------------------------------------

    /**
     * Converts inline markdown formatting within a single line to HTML.
     *
     * <p>Handles bold ({@code **}), italic ({@code *}), bold-italic
     * ({@code ***}), and inline code ({@code `}).  All other text is
     * HTML-escaped.
     *
     * @param text the inline markdown text
     * @return the corresponding HTML fragment
     */
    static String inlineToHtml(String text) {
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        Matcher m = INLINE.matcher(text);
        while (m.find()) {
            if (m.start() > lastEnd) {
                sb.append(esc(text.substring(lastEnd, m.start())));
            }
            if (m.group(1) != null) {
                sb.append("<b><em>").append(esc(m.group(1))).append("</em></b>");
            } else if (m.group(2) != null) {
                sb.append("<b>").append(esc(m.group(2))).append("</b>");
            } else if (m.group(3) != null) {
                sb.append("<em>").append(esc(m.group(3))).append("</em>");
            } else if (m.group(4) != null) {
                sb.append("<code>").append(esc(m.group(4))).append("</code>");
            }
            lastEnd = m.end();
        }
        if (lastEnd < text.length()) {
            sb.append(esc(text.substring(lastEnd)));
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static boolean isTableRow(String line) {
        String t = line.trim();
        return t.startsWith("|") && t.endsWith("|") && t.length() > 2;
    }

    private static boolean isSeparatorRow(String line) {
        if (!isTableRow(line)) return false;
        for (String cell : parseCells(line)) {
            if (!cell.trim().matches("[:\\- ]+")) return false;
        }
        return true;
    }

    private static List<String> parseCells(String row) {
        String t = row.trim();
        if (t.startsWith("|")) t = t.substring(1);
        if (t.endsWith("|")) t = t.substring(0, t.length() - 1);
        return Arrays.asList(t.split("\\|", -1));
    }

    private static boolean isUnordered(String line) {
        return line.matches("^\\s*[-*]\\s+.*");
    }

    private static boolean isOrdered(String line) {
        return line.matches("^\\s*\\d+\\.\\s+.*");
    }

    private static boolean isBlockStart(String line) {
        return line.startsWith("# ")
                || line.startsWith("## ")
                || line.startsWith("### ")
                || line.startsWith("> ")
                || isUnordered(line)
                || isOrdered(line)
                || isTableRow(line);
    }

    /**
     * Escapes HTML special characters in plain text.
     *
     * @param text the raw text
     * @return the HTML-escaped text
     */
    static String esc(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    // -------------------------------------------------------------------------
    // HTML insertion
    // -------------------------------------------------------------------------

    private static void appendHtml(JEditorPane pane, String html) {
        try {
            HTMLDocument doc = (HTMLDocument) pane.getDocument();
            HTMLEditorKit kit = (HTMLEditorKit) pane.getEditorKit();
            kit.insertHTML(doc, doc.getLength(), html, 0, 0, null);
            pane.setCaretPosition(doc.getLength());
        } catch (Exception ex) {
            // Should not happen under normal usage
        }
    }

    private MarkdownRenderer() {}
}
