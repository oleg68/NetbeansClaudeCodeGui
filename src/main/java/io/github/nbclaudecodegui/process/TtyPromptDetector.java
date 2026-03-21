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

/**
 * Detects <em>inline</em> and <em>JSON-based</em> interactive prompts from Claude PTY output.
 *
 * <p>Numbered-menu prompts (❯ 1. Yes / 2. No / …) are <strong>not</strong> handled here —
 * they are detected by {@link ScreenContentDetector} which reads the rendered terminal screen
 * instead of the raw PTY stream.  Attempting to detect menus from the stream is unreliable
 * because Claude updates spinner lines above the menu using cursor-up sequences
 * ({@code ESC[15A}), and the stripped versions of those updates look like regular text lines
 * that pollute the stream-based state machine.
 *
 * <p>This class handles two immediate (no-wait) cases:
 * <ol>
 *   <li><strong>Inline triggers</strong> — single-line prompts containing a bracketed
 *       yes/no group, e.g. {@code "Allow? (y/n)"}.</li>
 *   <li><strong>JSON subtypes</strong> — NDJSON lines starting with <code>'{'</code> whose
 *       {@code "subtype"} field matches a configured value.</li>
 * </ol>
 *
 * <p>Patterns are loaded from {@code tty-patterns.properties} on the classpath.
 */
public final class TtyPromptDetector {

    private static final Logger LOG = Logger.getLogger(TtyPromptDetector.class.getName());

    private final List<String> inlineTriggers;
    private final List<String> jsonSubtypes;

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
            try (InputStream in = TtyPromptDetector.class.getResourceAsStream(
                    base + "-" + version + ".properties")) {
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
     * @param line stripped line from PTY (ANSI codes removed by caller)
     * @return a {@link io.github.nbclaudecodegui.model.ChoiceMenuModel}
     *         if an inline or JSON prompt is detected, otherwise empty
     */
    public Optional<io.github.nbclaudecodegui.model.ChoiceMenuModel> feed(String line) {
        if (line == null) return Optional.empty();

        // Inline single-line prompts, e.g. "Allow? (y/n)"
        String lower = line.toLowerCase();
        for (String trigger : inlineTriggers) {
            if (lower.contains(trigger.toLowerCase())) {
                return Optional.of(new io.github.nbclaudecodegui.model.ChoiceMenuModel(
                        line, extractInlineOptions(line), -1));
            }
        }

        // JSON subtype prompts, e.g. {"subtype":"question","question":"..."}
        if (line.startsWith("{")) {
            String subtype = StreamJsonParser.extractString(line, "subtype");
            if (subtype != null && jsonSubtypes.contains(subtype)) {
                String text = StreamJsonParser.extractString(line, "question");
                if (text == null) text = line;
                return Optional.of(new io.github.nbclaudecodegui.model.ChoiceMenuModel(
                        text, new ArrayList<>(), -1));
            }
        }

        return Optional.empty();
    }

    /** Reset internal state (no-op — kept for API compatibility). */
    public void reset() {
        // No state to reset; numbered-menu detection moved to ScreenContentDetector.
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extract options from a single-line prompt like "Allow? (y/n/always)".
     */
    private static List<io.github.nbclaudecodegui.model.ChoiceMenuModel.Option> extractInlineOptions(String line) {
        List<io.github.nbclaudecodegui.model.ChoiceMenuModel.Option> options = new ArrayList<>();
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
            if (!t.isEmpty()) options.add(
                    new io.github.nbclaudecodegui.model.ChoiceMenuModel.Option(t, t));
        }
        return options;
    }
}
