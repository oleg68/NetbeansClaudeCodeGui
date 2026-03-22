package io.github.nbclaudecodegui.process;

import io.github.nbclaudecodegui.model.ChoiceMenuModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects numbered-menu prompts by scanning the rendered terminal screen
 * (JediTerm's TerminalTextBuffer), not the raw PTY stream.
 *
 * <p>Reading the rendered screen avoids false aborts caused by cursor-movement
 * sequences (e.g. {@code ESC[15A}) that Claude uses to update spinner lines
 * above the menu while the menu is still waiting for input.
 *
 * <p>This class is stateless — call {@link #detectChoiceMenu(List)} at any time.
 */
public final class ScreenContentDetector {

    private static final Logger LOG = Logger.getLogger(ScreenContentDetector.class.getName());

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

    /** Matches an option line that carries the Ink cursor glyph (selected item). */
    private static final Pattern CURSOR_LINE =
            Pattern.compile("^\\s*[\u276F\u25B6>]\\s*\\d+\\.\\s*.+");

    /**
     * Spinner characters Claude shows in the footer when a tool is running.
     * Braille spinner: ⠋ ⠙ ⠹ ⠸ ⠼ ⠴ ⠦ ⠧ ⠇ ⠏
     */
    private static final String SPINNER_CHARS = "\u280B\u2819\u2839\u2838\u283C\u2834\u2826\u2827\u2807\u280F";

    /** Pattern matching a plan file name in the status bar (e.g. "PLAN.md" or "plan.md"). */
    private static final Pattern PLAN_NAME_PATTERN =
            Pattern.compile("\\b([\\w\\-]+\\.md)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern matching a plan name in Claude's separator line, e.g.
     * "──────── create-hello-world-script ──" (U+2500 box-drawing dashes).
     */
    private static final Pattern PLAN_NAME_DASHES_PATTERN =
            Pattern.compile("\u2500{2,}\\s+([\\w][\\w\\-]*)\\s+\u2500+");

    // -------------------------------------------------------------------------
    // Session state
    // -------------------------------------------------------------------------

    /** Represents Claude's current activity state. */
    public enum SessionState { READY, WORKING }

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
     * @return a detected {@link ChoiceMenuModel}, or empty if no numbered menu is visible
     */
    public Optional<ChoiceMenuModel> detectChoiceMenu(List<String> screenLines) {
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
        List<ChoiceMenuModel.Option> options = new ArrayList<>();
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

        // Guard: Claude Code Ink menus always show a cursor glyph (❯ / ▶ / >) on the
        // currently-selected option. Numbered lists in Claude's own output never have
        // a cursor, so requiring one eliminates false positives entirely.
        boolean hasCursor = false;
        for (int i = firstOptionRow; i <= lastOptionRow; i++) {
            if (CURSOR_LINE.matcher(screenLines.get(i).trim()).matches()) {
                hasCursor = true;
                break;
            }
        }
        if (!hasCursor) return Optional.empty();

        LOG.info("[ScreenContentDetector] detected prompt: \"" + question + "\" options=" + options);
        return Optional.of(new ChoiceMenuModel(question, options, 0));
    }

    // -------------------------------------------------------------------------
    // New detection methods (Stage 12)
    // -------------------------------------------------------------------------

    /**
     * Detect the Claude CLI edit mode from the rendered screen footer.
     *
     * <p>Returns {@code "plan"} if "plan mode" is visible in the bottom 3 lines,
     * or {@code "default"} otherwise. {@code "acceptEdits"} is our own overlay and
     * cannot be detected from the screen; it is never returned here.
     *
     * @param lines rendered screen lines
     * @return {@code Optional} of {@code "plan"} or {@code "default"}
     */
    public Optional<String> detectEditMode(List<String> lines) {
        if (lines == null || lines.isEmpty()) return Optional.empty();
        for (String line : bottomNonBlankLines(lines, 3)) {
            String lower = line.toLowerCase();
            if (lower.contains("plan mode") || lower.contains("plan-mode")) {
                return Optional.of("plan");
            }
            if (lower.contains("accept edits")) {
                return Optional.of("acceptEdits");
            }
        }
        return Optional.of("default");
    }

    /**
     * Detect whether Claude is currently working (spinner visible) or ready.
     *
     * @param lines rendered screen lines
     * @return {@link SessionState#WORKING} if a spinner char is visible in the bottom 3 lines,
     *         otherwise {@link SessionState#READY}
     */
    public SessionState detectSessionState(List<String> lines) {
        if (lines == null || lines.isEmpty()) return SessionState.READY;
        for (String line : bottomNonBlankLines(lines, 3)) {
            for (char c : SPINNER_CHARS.toCharArray()) {
                if (line.indexOf(c) >= 0) return SessionState.WORKING;
            }
        }
        return SessionState.READY;
    }

    /**
     * Matches a line whose first non-whitespace character is the CC input cursor glyph.
     * After {@code trim()}, any line starting with ❯ / > / ▶ qualifies — the idle
     * prompt may carry placeholder text (e.g. {@code ❯ Ask anything...}) so we only
     * require the glyph at the start, not an empty tail.
     */
    private static final Pattern INPUT_PROMPT =
            Pattern.compile("^[\u276F>\u25B6]");

    /**
     * Returns true when the CC input prompt ({@code ❯}) is visible in the bottom
     * portion of the screen — meaning CC is truly idle and ready for keyboard input
     * (as opposed to still rendering the startup splash screen).
     *
     * <p>The splash/welcome screen shows a welcome box with no {@code ❯} line at the
     * bottom. The genuine idle state always has {@code ❯} (with or without placeholder
     * text) in the bottom few lines.
     *
     * @param lines rendered screen lines
     * @return {@code true} if the input prompt is found in the bottom 5 non-blank lines
     */
    public boolean detectInputPromptReady(List<String> lines) {
        if (lines == null || lines.isEmpty()) return false;
        for (String line : bottomNonBlankLines(lines, 5)) {
            if (INPUT_PROMPT.matcher(line.trim()).find()) return true;
        }
        return false;
    }

    /**
     * Detect the active plan file name from the screen footer.
     *
     * @param lines rendered screen lines
     * @return the plan file name, or empty if none detected
     */
    public Optional<String> detectPlanName(List<String> lines) {
        if (lines == null || lines.isEmpty()) return Optional.empty();
        for (String line : bottomNonBlankLines(lines, 5)) {
            Matcher m = PLAN_NAME_DASHES_PATTERN.matcher(line);
            if (m.find()) {
                return Optional.of(m.group(1));
            }
            m = PLAN_NAME_PATTERN.matcher(line);
            if (m.find()) {
                String name = m.group(1);
                // Skip common non-plan occurrences like "README.md"
                if (!name.equalsIgnoreCase("README.md")) {
                    return Optional.of(name);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns up to {@code n} non-blank lines from the bottom of the screen buffer,
     * skipping trailing empty rows that JediTerm adds as padding.
     * Lines are returned in bottom-up order (last visible line first).
     */
    static List<String> bottomNonBlankLines(List<String> lines, int n) {
        List<String> result = new ArrayList<>(n);
        for (int i = lines.size() - 1; i >= 0 && result.size() < n; i--) {
            String line = lines.get(i);
            if (!line.isBlank()) result.add(line);
        }
        return result;
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
    static ChoiceMenuModel.Option extractOption(String line, int index) {
        // Skip cursor glyph if present
        String working = line;
        if (!working.isEmpty() && "\u276F\u25B6>".indexOf(working.charAt(0)) >= 0) {
            working = working.substring(1).stripLeading();
        }
        int dotPos = working.indexOf('.');
        if (dotPos < 0) {
            return new ChoiceMenuModel.Option(line, String.valueOf(index));
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
        return new ChoiceMenuModel.Option(afterDot.strip(), String.valueOf(num));
    }
}
