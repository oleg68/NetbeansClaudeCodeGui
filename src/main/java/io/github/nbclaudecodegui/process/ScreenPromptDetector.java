package io.github.nbclaudecodegui.process;

import io.github.nbclaudecodegui.ui.PromptResponsePanel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Detects numbered-menu prompts by scanning the rendered terminal screen
 * (JediTerm's TerminalTextBuffer), not the raw PTY stream.
 *
 * <p>Reading the rendered screen avoids false aborts caused by cursor-movement
 * sequences (e.g. {@code ESC[15A}) that Claude uses to update spinner lines
 * above the menu while the menu is still waiting for input.
 *
 * <p>This class is stateless — call {@link #detect(List)} at any time.
 */
public final class ScreenPromptDetector {

    private static final Logger LOG = Logger.getLogger(ScreenPromptDetector.class.getName());

    /**
     * Matches a numbered-menu option line in either of two forms:
     * <ul>
     *   <li>{@code ❯ 1. Yes} — selected option (cursor glyph + number + dot)</li>
     *   <li>{@code 2. Yes, allow...} — non-selected option (number + dot)</li>
     * </ul>
     * Leading whitespace is allowed (produced by ESC[1C cursor-forward expansion).
     */
    private static final Pattern OPTION_LINE =
            Pattern.compile("^\\s*(\u276F|\u25B6|>)?\\s*\\d+\\.\\s*.+");

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Scan the visible screen lines for a numbered menu prompt.
     *
     * <p>The algorithm searches from the bottom of the screen upward for a
     * contiguous block of numbered-option lines, then uses the line immediately
     * above that block as the question text.
     *
     * @param screenLines lines from {@code TerminalTextBuffer.getScreenBuffer().getLineTexts()}
     * @return a detected prompt, or empty if no numbered menu is visible
     */
    public Optional<PromptResponsePanel.PromptRequest> detect(List<String> screenLines) {
        if (screenLines == null || screenLines.isEmpty()) return Optional.empty();

        // Find the last option line (bottom-most) on screen
        int lastOptionRow = -1;
        for (int i = screenLines.size() - 1; i >= 0; i--) {
            if (OPTION_LINE.matcher(screenLines.get(i).trim()).matches()) {
                lastOptionRow = i;
                break;
            }
        }
        if (lastOptionRow < 0) return Optional.empty();

        // Walk upward to collect all contiguous option lines
        List<PromptResponsePanel.Option> options = new ArrayList<>();
        int firstOptionRow = lastOptionRow;
        for (int i = lastOptionRow; i >= 0; i--) {
            String trimmed = screenLines.get(i).trim();
            if (OPTION_LINE.matcher(trimmed).matches()) {
                options.add(0, extractOption(trimmed, options.size() + 1));
                firstOptionRow = i;
            } else if (!trimmed.isBlank()) {
                break; // first non-option, non-blank line above block → stop
            }
        }

        if (options.isEmpty()) return Optional.empty();

        // Line immediately above the first option is the question
        String question = (firstOptionRow > 0)
                ? screenLines.get(firstOptionRow - 1).trim()
                : "";

        LOG.info("[ScreenPromptDetector] detected prompt: \"" + question + "\" options=" + options);
        return Optional.of(new PromptResponsePanel.PromptRequest(question, options, 0));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extract a display Option from a menu line.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "❯ 1. Yes"} → Option("Yes", "1")</li>
     *   <li>{@code "2. Yes, allow reading from plans/ during this session"} → Option("Yes, allow...", "2")</li>
     *   <li>{@code "3. No"} → Option("No", "3")</li>
     * </ul>
     *
     * @param line   the trimmed screen line
     * @param index  1-based fallback index if number extraction fails
     */
    static PromptResponsePanel.Option extractOption(String line, int index) {
        // Skip cursor glyph if present
        String working = line;
        if (!working.isEmpty() && "\u276F\u25B6>".indexOf(working.charAt(0)) >= 0) {
            working = working.substring(1).stripLeading();
        }
        int dotPos = working.indexOf('.');
        if (dotPos < 0) {
            return new PromptResponsePanel.Option(line, String.valueOf(index));
        }
        String numStr = working.substring(0, dotPos).strip();
        int num;
        try {
            num = Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            num = index;
        }
        String afterDot = working.substring(dotPos + 1).strip();
        // Remove trailing parenthetical hint like "(shift+tab)"
        int parenPos = afterDot.lastIndexOf('(');
        if (parenPos > 0 && afterDot.endsWith(")")) {
            afterDot = afterDot.substring(0, parenPos).stripTrailing();
        }
        return new PromptResponsePanel.Option(afterDot.strip(), String.valueOf(num));
    }
}
