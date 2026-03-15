package io.github.nbclaudecodegui.settings;

import org.openide.util.NbPreferences;

/**
 * Provides typed access to Claude Code GUI plugin preferences stored via
 * {@link NbPreferences}.
 *
 * <p>Settings are persisted in the NetBeans user directory:
 * <ul>
 *   <li>Linux/macOS: {@code ~/.netbeans/23/config/Preferences/io/github/nbclaudecodegui.properties}</li>
 *   <li>Windows: {@code %APPDATA%\NetBeans\23\config\Preferences\io\github\nbclaudecodegui.properties}</li>
 * </ul>
 */
public final class ClaudeCodePreferences {

    /**
     * Preference key for the path to the {@code claude} CLI executable.
     * When empty, the plugin searches for {@code claude} on the system {@code PATH}.
     */
    public static final String KEY_CLAUDE_EXECUTABLE_PATH = "claudeExecutablePath";

    /**
     * Default value for {@link #KEY_CLAUDE_EXECUTABLE_PATH}: empty string,
     * meaning the executable is looked up on {@code PATH}.
     */
    public static final String DEFAULT_CLAUDE_EXECUTABLE_PATH = "";

    private ClaudeCodePreferences() {
    }

    /**
     * Returns the path to the {@code claude} CLI executable as stored in preferences.
     *
     * @return stored path, or {@link #DEFAULT_CLAUDE_EXECUTABLE_PATH} if not set
     */
    public static String getClaudeExecutablePath() {
        return NbPreferences.forModule(ClaudeCodePreferences.class)
                .get(KEY_CLAUDE_EXECUTABLE_PATH, DEFAULT_CLAUDE_EXECUTABLE_PATH);
    }

    /**
     * Persists the path to the {@code claude} CLI executable.
     *
     * @param path absolute path to the executable, or empty string to use {@code PATH}
     */
    public static void setClaudeExecutablePath(String path) {
        NbPreferences.forModule(ClaudeCodePreferences.class)
                .put(KEY_CLAUDE_EXECUTABLE_PATH, path == null ? DEFAULT_CLAUDE_EXECUTABLE_PATH : path);
    }
}
