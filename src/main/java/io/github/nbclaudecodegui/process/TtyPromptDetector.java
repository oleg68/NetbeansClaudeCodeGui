package io.github.nbclaudecodegui.process;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Stateful detector for interactive prompts in Claude PTY output.
 *
 * <p>Claude renders numbered menus across several lines without spaces (PTY rendering
 * collapses whitespace). This class accumulates lines in a state machine and emits a
 * {@link io.github.nbclaudecodegui.ui.PromptResponsePanel.PromptRequest} only when the
 * full menu has been collected.
 *
 * <p>Patterns are loaded from {@code tty-patterns.properties} on the classpath.
 * If a version-specific file {@code tty-patterns-{version}.properties} exists it is
 * preferred.
 */
public final class TtyPromptDetector {

    private static final Logger LOG = Logger.getLogger(TtyPromptDetector.class.getName());

    private enum State { IDLE, COLLECTING }

    private final Pattern menuTrigger;
    private final Pattern menuOption;
    private final List<String> inlineTriggers;
    private final List<String> jsonSubtypes;

    private State state = State.IDLE;
    private String questionLine = "";
    private String previousLine = "";
    private final List<io.github.nbclaudecodegui.ui.PromptResponsePanel.Option> collectedOptions = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public TtyPromptDetector() {
        this((String) null);
    }

    public TtyPromptDetector(String version) {
        this(loadProperties(version));
    }

    /** Package-private constructor for testing with injected properties. */
    TtyPromptDetector(Properties props) {
        validate(props);
        menuTrigger    = Pattern.compile(props.getProperty("menu.trigger.pattern"));
        menuOption     = Pattern.compile(props.getProperty("menu.option.pattern"));
        inlineTriggers = splitCsv(props.getProperty("inline.trigger.patterns"));
        jsonSubtypes   = splitCsv(props.getProperty("json.subtypes"));
    }

    private static final List<String> REQUIRED_KEYS = List.of(
            "menu.trigger.pattern",
            "menu.option.pattern",
            "inline.trigger.patterns",
            "json.subtypes"
    );

    private static void validate(Properties props) {
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            if (props.getProperty(key) == null) missing.add(key);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required properties in tty-patterns.properties: " + missing);
        }
    }

    private static Properties loadProperties(String version) {
        Properties props = new Properties();
        String base = "/io/github/nbclaudecodegui/process/tty-patterns";
        if (version != null && !version.isBlank()) {
            try (InputStream in = TtyPromptDetector.class.getResourceAsStream(base + "-" + version + ".properties")) {
                if (in != null) {
                    props.load(in);
                    return props;
                }
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Could not load version-specific patterns", ex);
            }
        }
        try (InputStream in = TtyPromptDetector.class.getResourceAsStream(base + ".properties")) {
            if (in != null) props.load(in);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Could not load default tty-patterns.properties", ex);
        }
        return props;
    }

    private static List<String> splitCsv(String csv) {
        List<String> result = new ArrayList<>();
        if (csv == null) return result;
        for (String s : csv.split(",", -1)) {
            if (!s.isBlank()) result.add(s);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Feed a single PTY output line into the detector.
     *
     * @param line raw line from PTY (may contain ANSI codes stripped by caller)
     * @return a {@link io.github.nbclaudecodegui.ui.PromptResponsePanel.PromptRequest}
     *         if a complete prompt has been detected, otherwise empty
     */
    public Optional<io.github.nbclaudecodegui.ui.PromptResponsePanel.PromptRequest> feed(String line) {
        if (line == null) return Optional.empty();

        switch (state) {
            case IDLE -> {
                // Check for start of a numbered menu (❯1.Text)
                if (menuTrigger.matcher(line).find()) {
                    state = State.COLLECTING;
                    questionLine = previousLine;   // line before menu trigger is the question
                    collectedOptions.clear();
                    collectedOptions.add(extractOptionAsNumbered(line, 1));
                    previousLine = "";
                    return Optional.empty();
                }
                // Check for inline single-line prompts
                String lower = line.toLowerCase();
                for (String trigger : inlineTriggers) {
                    if (lower.contains(trigger.toLowerCase())) {
                        previousLine = line;
                        return Optional.of(new io.github.nbclaudecodegui.ui.PromptResponsePanel.PromptRequest(
                                line, extractInlineOptions(line), -1));
                    }
                }
                // Check for JSON subtype prompts
                if (line.startsWith("{")) {
                    String subtype = StreamJsonParser.extractString(line, "subtype");
                    if (subtype != null && jsonSubtypes.contains(subtype)) {
                        String text = StreamJsonParser.extractString(line, "question");
                        if (text == null) text = line;
                        previousLine = line;
                        return Optional.of(new io.github.nbclaudecodegui.ui.PromptResponsePanel.PromptRequest(
                                text, new ArrayList<>(), -1));
                    }
                }
                previousLine = line;
                return Optional.empty();
            }
            case COLLECTING -> {
                if (menuOption.matcher(line).find()) {
                    collectedOptions.add(extractOptionAsNumbered(line, collectedOptions.size() + 1));
                    return Optional.empty();
                } else {
                    // Menu ended — emit the collected prompt
                    state = State.IDLE;
                    List<io.github.nbclaudecodegui.ui.PromptResponsePanel.Option> options = new ArrayList<>(collectedOptions);
                    collectedOptions.clear();
                    String text = questionLine;
                    questionLine = "";
                    previousLine = line;
                    io.github.nbclaudecodegui.ui.PromptResponsePanel.PromptRequest req =
                            new io.github.nbclaudecodegui.ui.PromptResponsePanel.PromptRequest(text, options, 0);
                    // Re-process this line in IDLE state (it may itself be a trigger)
                    feed(line);
                    return Optional.of(req);
                }
            }
            default -> { return Optional.empty(); }
        }
    }

    /**
     * Flushes any pending COLLECTING state as a complete prompt.
     *
     * <p>Called when PTY output goes silent while a menu is partially collected —
     * Claude is waiting for input and will not send a terminating line.
     *
     * @return the pending {@link io.github.nbclaudecodegui.ui.PromptResponsePanel.PromptRequest},
     *         or empty if the detector was not in COLLECTING state
     */
    public Optional<io.github.nbclaudecodegui.ui.PromptResponsePanel.PromptRequest> tryFlush() {
        if (state == State.COLLECTING && !collectedOptions.isEmpty()) {
            state = State.IDLE;
            List<io.github.nbclaudecodegui.ui.PromptResponsePanel.Option> options = new ArrayList<>(collectedOptions);
            collectedOptions.clear();
            String text = questionLine;
            questionLine = "";
            LOG.info("[TtyPromptDetector] tryFlush emitting prompt: \"" + text + "\" options=" + options);
            return Optional.of(new io.github.nbclaudecodegui.ui.PromptResponsePanel.PromptRequest(text, options, 0));
        }
        return Optional.empty();
    }

    /** Reset internal state (e.g. when starting a new session). */
    public void reset() {
        state = State.IDLE;
        questionLine = "";
        previousLine = "";
        collectedOptions.clear();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extract a numbered Option from a menu line.
     * Input: "❯1.Yes" → Option("Yes", "1"); "2.Yes,allowalledits(shift+tab)" → Option("Yes,allowalledits", "2")
     */
    private static io.github.nbclaudecodegui.ui.PromptResponsePanel.Option extractOptionAsNumbered(String line, int index) {
        LOG.info("[TtyPromptDetector] extractOption raw line: \"" + line + "\"");
        int dotPos = line.indexOf('.');
        if (dotPos < 0) {
            LOG.info("[TtyPromptDetector] extractOption[" + index + "] (no dot): \"" + line + "\"");
            return new io.github.nbclaudecodegui.ui.PromptResponsePanel.Option(line, String.valueOf(index));
        }
        String afterDot = line.substring(dotPos + 1);
        // Remove trailing parenthetical like "(shift+tab)"
        int parenPos = afterDot.lastIndexOf('(');
        if (parenPos > 0 && afterDot.endsWith(")")) {
            afterDot = afterDot.substring(0, parenPos);
        }
        LOG.info("[TtyPromptDetector] extractOption[" + index + "] display: \"" + afterDot + "\"");
        return new io.github.nbclaudecodegui.ui.PromptResponsePanel.Option(afterDot, String.valueOf(index));
    }

    /**
     * Extract options from a single-line prompt like "Allow? (y/n/always)" → [Option("y","y"), Option("n","n"), ...].
     */
    private static List<io.github.nbclaudecodegui.ui.PromptResponsePanel.Option> extractInlineOptions(String line) {
        List<io.github.nbclaudecodegui.ui.PromptResponsePanel.Option> options = new ArrayList<>();
        // Find last bracketed group
        int lastOpen = -1;
        char closeChar = ')';
        for (int i = line.length() - 1; i >= 0; i--) {
            char c = line.charAt(i);
            if (c == ')' || c == ']') {
                closeChar = c;
                char openChar = (c == ')') ? '(' : '[';
                for (int j = i - 1; j >= 0; j--) {
                    if (line.charAt(j) == openChar) { lastOpen = j; break; }
                }
                if (lastOpen >= 0) break;
            }
        }
        if (lastOpen < 0) return options;
        int closePos = line.indexOf(closeChar, lastOpen + 1);
        if (closePos < 0) return options;
        String inner = line.substring(lastOpen + 1, closePos).trim();
        if (inner.isEmpty() || !inner.contains("/")) return options;
        for (String part : inner.split("/")) {
            String t = part.trim();
            if (!t.isEmpty()) options.add(new io.github.nbclaudecodegui.ui.PromptResponsePanel.Option(t, t));
        }
        return options;
    }
}
