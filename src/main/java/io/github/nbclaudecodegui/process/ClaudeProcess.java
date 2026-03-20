package io.github.nbclaudecodegui.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.openbeans.claude.netbeans.ClaudeCodeStatusService;
import org.openide.util.Lookup;

/**
 * Manages a Claude Code CLI session as an interactive PTY process.
 *
 * <p>The process is launched without {@code --print} so that the full Claude
 * TUI (ink/React) runs inside the PTY.  The caller embeds a JediTerm terminal
 * widget that renders the TUI natively.
 *
 * <p><b>settings.local.json lifecycle</b>
 * <p>Before starting the PTY, the plugin writes
 * {@code {workingDir}/.claude/settings.local.json} to register itself as an
 * MCP server and to install a {@code PreToolUse} hook.  The write is a
 * <em>merge</em>: existing user content (other MCP servers, other hooks) is
 * preserved; only the plugin's own keys are added or updated.
 *
 * <p>When the session stops, the plugin removes its keys from the file.  If
 * the file becomes empty after the cleanup it is deleted entirely (this is the
 * normal case when the file did not exist before the session started).  If the
 * file contained user content that content is left intact.
 */
public final class ClaudeProcess {

    private static final Logger LOG =
            Logger.getLogger(ClaudeProcess.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Matcher string the plugin registers in PreToolUse hooks. */
    static final String OUR_HOOK_MATCHER = "Edit|Write|MultiEdit";

    /** Key the plugin registers under {@code mcpServers}. */
    static final String OUR_MCP_KEY = "netbeans";

    private volatile PtyProcess ptyProcess;

    /** Working directory of the current session; {@code null} when stopped. */
    private volatile String workingDir;

    /**
     * Starts a Claude CLI PTY process in the given working directory.
     *
     * @param workingDir absolute path to the session working directory
     * @return the started {@link PtyProcess}
     * @throws IllegalArgumentException if {@code workingDir} is blank
     * @throws IOException              if the process cannot be launched
     */
    public PtyProcess start(String workingDir) throws IOException {
        if (workingDir == null || workingDir.isBlank()) {
            throw new IllegalArgumentException("workingDir must not be blank");
        }

        // stop() cleans up the previous session's settings.local.json (if any)
        stop();
        this.workingDir = workingDir;

        String executable = ClaudeCodePreferences.resolveClaudeExecutable();

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");

        List<String> cmd = new ArrayList<>();
        cmd.add(executable);
        ClaudeCodeStatusService mcp = Lookup.getDefault().lookup(ClaudeCodeStatusService.class);
        LOG.info("MCP service lookup: " + (mcp == null ? "null" : mcp.getClass().getName())
                + ", running=" + (mcp != null && mcp.isServerRunning())
                + ", port=" + (mcp != null ? mcp.getServerPort() : -1));
        if (mcp != null && mcp.isServerRunning()) {
            try {
                writeSettingsLocalJson(workingDir, mcp.getServerPort());
            } catch (IOException e) {
                LOG.warning("Could not write .claude/settings.local.json: " + e.getMessage());
            }
        }

        PtyProcessBuilder builder = new PtyProcessBuilder(cmd.toArray(new String[0]))
                .setEnvironment(env)
                .setDirectory(workingDir)
                .setInitialColumns(120)
                .setInitialRows(40)
                .setConsole(false)
                .setRedirectErrorStream(true);

        PtyProcess p = builder.start();
        ptyProcess = p;
        LOG.fine("Claude PTY started, pid=" + p.pid());
        return p;
    }

    /**
     * Stops the current PTY process and cleans up {@code settings.local.json}.
     *
     * <p>Cleanup removes only the plugin's own keys ({@code mcpServers.netbeans}
     * and the {@code PreToolUse} hook entry with matcher
     * {@value #OUR_HOOK_MATCHER}).  User-provided keys are left untouched.
     * If the file becomes empty after cleanup it is deleted.
     */
    public void stop() {
        PtyProcess p = ptyProcess;
        if (p != null && p.isAlive()) {
            p.destroy();
            try {
                if (!p.waitFor(3, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }
        }
        ptyProcess = null;

        String dir = workingDir;
        workingDir = null;
        if (dir != null) {
            try {
                cleanupSettingsLocalJson(dir);
            } catch (IOException e) {
                LOG.warning("Could not clean up .claude/settings.local.json: " + e.getMessage());
            }
        }
    }

    /**
     * Returns {@code true} while a PTY process is alive.
     *
     * @return {@code true} if the process is running
     */
    public boolean isRunning() {
        PtyProcess p = ptyProcess;
        return p != null && p.isAlive();
    }

    // -------------------------------------------------------------------------
    // settings.local.json — write (merge)
    // -------------------------------------------------------------------------

    /**
     * Merges the plugin's MCP server and PreToolUse hook into
     * {@code {workingDir}/.claude/settings.local.json}.
     *
     * <p>If the file already exists its content is preserved: only
     * {@code mcpServers.netbeans} and the {@code PreToolUse} entry with matcher
     * {@value #OUR_HOOK_MATCHER} are added or updated; all other keys remain
     * unchanged.  If the file does not exist it is created.
     *
     * @param workingDir the session working directory
     * @param port       the port the MCP SSE server is listening on
     * @throws IOException if the file cannot be written
     */
    static void writeSettingsLocalJson(String workingDir, int port) throws IOException {
        Path claudeDir = Path.of(workingDir, ".claude");
        Files.createDirectories(claudeDir);
        Path cfg = claudeDir.resolve("settings.local.json");

        String existing = Files.exists(cfg)
                ? Files.readString(cfg, StandardCharsets.UTF_8)
                : "{}";

        String merged = mergeSettingsJson(existing, port);
        Files.writeString(cfg, merged, StandardCharsets.UTF_8);
        LOG.info("settings.local.json written (merged): " + cfg.toAbsolutePath());
    }

    /**
     * Merges the plugin's entries into {@code existingJson} and returns the
     * resulting JSON string.
     *
     * <p>Specifically:
     * <ul>
     *   <li>{@code mcpServers.netbeans} is set to the SSE URL for {@code port}.</li>
     *   <li>Any existing {@code hooks.PreToolUse} entry whose {@code matcher}
     *       equals {@value #OUR_HOOK_MATCHER} is replaced with a fresh entry
     *       pointing to the hook URL for {@code port}.  Other entries are kept.</li>
     * </ul>
     *
     * <p>Falls back to a fresh minimal JSON if {@code existingJson} cannot be
     * parsed.
     *
     * @param existingJson current file content (may be {@code "{}"} for a new file)
     * @param port         the MCP/hook server port
     * @return merged JSON string
     */
    static String mergeSettingsJson(String existingJson, int port) {
        try {
            ObjectNode root = existingJson == null || existingJson.isBlank()
                    ? MAPPER.createObjectNode()
                    : (ObjectNode) MAPPER.readTree(existingJson);

            // --- mcpServers.netbeans ---
            ObjectNode mcpServers = root.has("mcpServers")
                    ? (ObjectNode) root.get("mcpServers")
                    : MAPPER.createObjectNode();
            ObjectNode netbeans = MAPPER.createObjectNode();
            netbeans.put("type", "sse");
            netbeans.put("url", "http://localhost:" + port + "/sse");
            mcpServers.set(OUR_MCP_KEY, netbeans);
            root.set("mcpServers", mcpServers);

            // --- hooks.PreToolUse ---
            ObjectNode hooks = root.has("hooks")
                    ? (ObjectNode) root.get("hooks")
                    : MAPPER.createObjectNode();
            ArrayNode preToolUse = hooks.has("PreToolUse")
                    ? (ArrayNode) hooks.get("PreToolUse")
                    : MAPPER.createArrayNode();

            // Remove our old entry, keep others
            ArrayNode filtered = MAPPER.createArrayNode();
            for (JsonNode entry : preToolUse) {
                String matcher = entry.has("matcher") ? entry.get("matcher").asText() : "";
                if (!OUR_HOOK_MATCHER.equals(matcher)) {
                    filtered.add(entry);
                }
            }

            // Add fresh entry for this port
            ObjectNode ourEntry = MAPPER.createObjectNode();
            ourEntry.put("matcher", OUR_HOOK_MATCHER);
            ArrayNode hooksArr = MAPPER.createArrayNode();
            ObjectNode httpHook = MAPPER.createObjectNode();
            httpHook.put("type", "http");
            httpHook.put("url", "http://localhost:" + port + "/hook");
            hooksArr.add(httpHook);
            ourEntry.set("hooks", hooksArr);
            filtered.add(ourEntry);

            hooks.set("PreToolUse", filtered);
            root.set("hooks", hooks);

            return MAPPER.writeValueAsString(root);

        } catch (Exception e) {
            LOG.warning("Could not merge settings JSON, using fresh: " + e.getMessage());
            return buildSettingsLocalJson(port);
        }
    }

    // -------------------------------------------------------------------------
    // settings.local.json — cleanup
    // -------------------------------------------------------------------------

    /**
     * Removes the plugin's keys from {@code {workingDir}/.claude/settings.local.json}.
     *
     * <p>If the file does not exist this method does nothing.  Otherwise:
     * <ul>
     *   <li>If the file is empty after removing our keys it is deleted (the
     *       normal case when it was created entirely by the plugin).</li>
     *   <li>If the file still contains user-provided content it is written back
     *       without our keys.</li>
     * </ul>
     *
     * @param workingDir the session working directory
     * @throws IOException if the file cannot be read, written, or deleted
     */
    static void cleanupSettingsLocalJson(String workingDir) throws IOException {
        Path cfg = Path.of(workingDir, ".claude", "settings.local.json");
        if (!Files.exists(cfg)) {
            return;
        }
        String existing = Files.readString(cfg, StandardCharsets.UTF_8);
        String cleaned = cleanedSettingsJson(existing);
        if (cleaned == null) {
            Files.delete(cfg);
            LOG.info("settings.local.json deleted (was plugin-only): " + cfg.toAbsolutePath());
        } else {
            Files.writeString(cfg, cleaned, StandardCharsets.UTF_8);
            LOG.info("settings.local.json cleaned (user content preserved): " + cfg.toAbsolutePath());
        }
    }

    /**
     * Returns a copy of {@code existingJson} with the plugin's keys removed,
     * or {@code null} if nothing remains after the removal.
     *
     * <p>The plugin's keys are:
     * <ul>
     *   <li>{@code mcpServers.netbeans}</li>
     *   <li>The {@code hooks.PreToolUse} array entry whose {@code matcher}
     *       equals {@value #OUR_HOOK_MATCHER}</li>
     * </ul>
     *
     * <p>A {@code null} return value signals that the file should be deleted
     * (i.e., it contained only the plugin's entries and is now effectively
     * {@code {}}).
     *
     * @param existingJson current file content
     * @return cleaned JSON string, or {@code null} to indicate «delete the file»
     */
    static String cleanedSettingsJson(String existingJson) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(existingJson);

            // Remove mcpServers.netbeans
            if (root.has("mcpServers")) {
                ObjectNode mcpServers = (ObjectNode) root.get("mcpServers");
                mcpServers.remove(OUR_MCP_KEY);
                if (mcpServers.isEmpty()) {
                    root.remove("mcpServers");
                }
            }

            // Remove our PreToolUse hook entry
            if (root.has("hooks")) {
                ObjectNode hooks = (ObjectNode) root.get("hooks");
                if (hooks.has("PreToolUse")) {
                    ArrayNode preToolUse = (ArrayNode) hooks.get("PreToolUse");
                    ArrayNode filtered = MAPPER.createArrayNode();
                    for (JsonNode entry : preToolUse) {
                        String matcher = entry.has("matcher") ? entry.get("matcher").asText() : "";
                        if (!OUR_HOOK_MATCHER.equals(matcher)) {
                            filtered.add(entry);
                        }
                    }
                    if (filtered.isEmpty()) {
                        hooks.remove("PreToolUse");
                    } else {
                        hooks.set("PreToolUse", filtered);
                    }
                }
                if (hooks.isEmpty()) {
                    root.remove("hooks");
                }
            }

            return root.isEmpty() ? null : MAPPER.writeValueAsString(root);

        } catch (Exception e) {
            LOG.warning("Could not parse settings JSON for cleanup, deleting: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal {@code settings.local.json} from scratch for the given port.
     * Used as a fallback when the existing file cannot be parsed.
     */
    private static String buildSettingsLocalJson(int port) {
        return "{"
                + "\"mcpServers\":{\"netbeans\":{\"type\":\"sse\",\"url\":\"http://localhost:" + port + "/sse\"}},"
                + "\"hooks\":{"
                + "\"PreToolUse\":["
                + "{\"matcher\":\"Edit|Write|MultiEdit\","
                + "\"hooks\":[{\"type\":\"http\",\"url\":\"http://localhost:" + port + "/hook\"}]}"
                + "]}"
                + "}";
    }
}
