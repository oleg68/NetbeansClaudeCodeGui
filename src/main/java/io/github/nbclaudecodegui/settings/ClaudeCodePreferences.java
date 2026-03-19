package io.github.nbclaudecodegui.settings;

import java.io.File;
import java.io.IOException;
import org.openide.util.NbPreferences;

/**
 * Typed access to Claude Code GUI plugin preferences stored via
 * {@link NbPreferences}.
 */
public final class ClaudeCodePreferences {

    /** Preference key: path to the {@code claude} CLI executable. */
    public static final String KEY_CLAUDE_EXECUTABLE_PATH = "claudeExecutablePath";
    /** Default: empty — resolved from {@code PATH} at runtime. */
    public static final String DEFAULT_CLAUDE_EXECUTABLE_PATH = "";

    /** Preference key: key combination that sends the prompt. */
    public static final String KEY_SEND_KEY = "sendKey";
    /** Preference key: key combination that inserts a newline. */
    public static final String KEY_NEWLINE_KEY = "newlineKey";

    /** Allowed key-combo values. */
    public static final String ENTER        = "ENTER";
    public static final String SHIFT_ENTER  = "SHIFT_ENTER";
    public static final String CTRL_ENTER   = "CTRL_ENTER";
    public static final String ALT_ENTER    = "ALT_ENTER";

    public static final String DEFAULT_SEND_KEY    = CTRL_ENTER;
    public static final String DEFAULT_NEWLINE_KEY = ENTER;

    private ClaudeCodePreferences() {}

    // -------------------------------------------------------------------------
    // claudeExecutablePath
    // -------------------------------------------------------------------------

    /**
     * Returns the stored path to the {@code claude} CLI, or an empty string.
     *
     * @return stored path or {@link #DEFAULT_CLAUDE_EXECUTABLE_PATH}
     */
    public static String getClaudeExecutablePath() {
        return NbPreferences.forModule(ClaudeCodePreferences.class)
                .get(KEY_CLAUDE_EXECUTABLE_PATH, DEFAULT_CLAUDE_EXECUTABLE_PATH);
    }

    /**
     * Persists the path to the {@code claude} CLI executable.
     *
     * @param path absolute path, or empty string to use {@code PATH}
     */
    public static void setClaudeExecutablePath(String path) {
        NbPreferences.forModule(ClaudeCodePreferences.class)
                .put(KEY_CLAUDE_EXECUTABLE_PATH,
                        path == null ? DEFAULT_CLAUDE_EXECUTABLE_PATH : path);
    }

    /**
     * Returns the {@code claude} executable to use.
     *
     * <p>Uses the stored path when non-empty; otherwise searches {@code PATH},
     * stores the result for future calls, and falls back to {@code "claude"}.
     *
     * @return resolved executable path or {@code "claude"}
     */
    public static String resolveClaudeExecutable() {
        String stored = getClaudeExecutablePath();
        if (!stored.isBlank()) {
            return stored;
        }
        String found = findOnPath();
        if (found != null) {
            setClaudeExecutablePath(found);
            return found;
        }
        return "claude";
    }

    /**
     * Searches the system {@code PATH} for the {@code claude} executable.
     *
     * @return absolute path, or {@code null} if not found
     */
    static String findOnPath() {
        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase().contains("win");
        String[] candidates = isWindows
                ? new String[]{"claude.cmd", "claude.exe", "claude"}
                : new String[]{"claude"};
        String locator = isWindows ? "where" : "which";
        for (String candidate : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(locator, candidate);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String output = new String(p.getInputStream().readAllBytes()).trim();
                p.waitFor();
                String first = output.lines().findFirst().orElse("").trim();
                if (!first.isBlank() && new File(first).canExecute()) {
                    return first;
                }
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // sendKey / newlineKey
    // -------------------------------------------------------------------------

    /**
     * Returns the key combination used to send the prompt.
     *
     * @return one of {@link #ENTER}, {@link #SHIFT_ENTER}, {@link #CTRL_ENTER},
     *         {@link #ALT_ENTER}
     */
    public static String getSendKey() {
        return NbPreferences.forModule(ClaudeCodePreferences.class)
                .get(KEY_SEND_KEY, DEFAULT_SEND_KEY);
    }

    /**
     * Persists the send-prompt key combination.
     *
     * @param value one of the {@code *_ENTER} constants
     */
    public static void setSendKey(String value) {
        NbPreferences.forModule(ClaudeCodePreferences.class)
                .put(KEY_SEND_KEY, validated(value, DEFAULT_SEND_KEY));
    }

    /**
     * Returns the key combination used to insert a newline.
     *
     * @return one of {@link #ENTER}, {@link #SHIFT_ENTER}, {@link #CTRL_ENTER},
     *         {@link #ALT_ENTER}
     */
    public static String getNewlineKey() {
        return NbPreferences.forModule(ClaudeCodePreferences.class)
                .get(KEY_NEWLINE_KEY, DEFAULT_NEWLINE_KEY);
    }

    /**
     * Persists the insert-newline key combination.
     *
     * @param value one of the {@code *_ENTER} constants
     */
    public static void setNewlineKey(String value) {
        NbPreferences.forModule(ClaudeCodePreferences.class)
                .put(KEY_NEWLINE_KEY, validated(value, DEFAULT_NEWLINE_KEY));
    }

    // -------------------------------------------------------------------------
    // debugMode
    // -------------------------------------------------------------------------

    /** Preference key: enable debug/trace logging. */
    public static final String KEY_DEBUG_MODE = "debugMode";
    /** Default: debug logging off. */
    public static final boolean DEFAULT_DEBUG_MODE = false;

    /**
     * Returns whether debug mode is enabled.
     *
     * @return {@code true} if debug logging is on
     */
    public static boolean isDebugMode() {
        return NbPreferences.forModule(ClaudeCodePreferences.class)
                .getBoolean(KEY_DEBUG_MODE, DEFAULT_DEBUG_MODE);
    }

    /**
     * Persists the debug mode flag.
     *
     * @param enabled {@code true} to enable debug logging
     */
    public static void setDebugMode(boolean enabled) {
        NbPreferences.forModule(ClaudeCodePreferences.class)
                .putBoolean(KEY_DEBUG_MODE, enabled);
    }

    // -------------------------------------------------------------------------
    // mcpPort
    // -------------------------------------------------------------------------

    /** Preference key: MCP SSE server port. */
    public static final String KEY_MCP_PORT = "mcpPort";
    /** Default port for the NetBeans MCP SSE server. */
    public static final int DEFAULT_MCP_PORT = 28991;

    /**
     * Returns the configured MCP server port.
     */
    public static int getMcpPort() {
        return NbPreferences.forModule(ClaudeCodePreferences.class)
                .getInt(KEY_MCP_PORT, DEFAULT_MCP_PORT);
    }

    /**
     * Persists the MCP server port.
     */
    public static void setMcpPort(int port) {
        NbPreferences.forModule(ClaudeCodePreferences.class)
                .putInt(KEY_MCP_PORT, port);
    }

    private static String validated(String value, String fallback) {
        return ENTER.equals(value) || SHIFT_ENTER.equals(value)
                || CTRL_ENTER.equals(value) || ALT_ENTER.equals(value)
                ? value : fallback;
    }
}
