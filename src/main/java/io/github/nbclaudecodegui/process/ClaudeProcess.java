package io.github.nbclaudecodegui.process;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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

        PtyProcessBuilder builder = new PtyProcessBuilder(new String[]{executable})
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
}
