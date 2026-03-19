package io.github.nbclaudecodegui.process;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.io.IOException;
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
 */
public final class ClaudeProcess {

    private static final Logger LOG =
            Logger.getLogger(ClaudeProcess.class.getName());

    private volatile PtyProcess ptyProcess;

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

        stop();

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
                String json = buildMcpConfigJson(mcp.getServerPort());
                Path cfg = Path.of(System.getProperty("java.io.tmpdir"),
                        "netbeans-mcp-" + mcp.getServerPort() + ".json");
                Files.writeString(cfg, json);
                cmd.add("--mcp-config");
                cmd.add(cfg.toAbsolutePath().toString());
                LOG.info("MCP config written: " + cfg.toAbsolutePath() + " content: " + json);
            } catch (IOException e) {
                LOG.warning("Could not write MCP config file: " + e.getMessage());
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
     * Stops the current PTY process, if any.
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

    /**
     * Builds the {@code --mcp-config} JSON for the given SSE server port.
     * Uses SSE transport ({@code "type":"sse"}) which is supported by Claude Code.
     *
     * @param port the port the MCP SSE server is listening on
     * @return JSON string suitable for writing to a {@code --mcp-config} file
     */
    static String buildMcpConfigJson(int port) {
        return "{\"mcpServers\":{\"netbeans\":{\"type\":\"sse\",\"url\":\"http://localhost:" + port + "/sse\"}}}";
    }
}
