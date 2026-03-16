package io.github.nbclaudecodegui.process;

import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages Claude Code CLI invocations for a single session.
 *
 * <p>Each call to {@link #sendInput(String)} launches a new subprocess:
 * <ul>
 *   <li>First message: {@code claude --print <text> --output-format stream-json --verbose}</li>
 *   <li>Subsequent messages: same with {@code --continue} to resume the session</li>
 * </ul>
 *
 * <p>{@link #start(String)} records the working directory without starting a
 * subprocess. {@link #isRunning()} returns {@code true} while a subprocess is
 * processing a message.
 *
 * <p>All subprocess I/O is logged via {@link Logger} at {@link Level#FINE}.
 * When debug mode is enabled in preferences, each log line is also forwarded
 * to the {@code debugConsumer} (prefixed with {@code [debug]}) so the user
 * can see raw exchange in the output area.
 */
public final class ClaudeProcess {

    private static final Logger LOG =
            Logger.getLogger(ClaudeProcess.class.getName());

    private final Consumer<String> outputConsumer;
    /** Receives debug lines when debug mode is on; may be null. */
    private Consumer<String> debugConsumer;

    private String workingDir;
    private volatile Process currentProcess;
    private boolean firstMessage = true;

    /**
     * Creates a new {@code ClaudeProcess}.
     *
     * @param outputConsumer receives each stdout line; invoked on a background
     *                       thread
     */
    public ClaudeProcess(Consumer<String> outputConsumer) {
        if (outputConsumer == null) {
            throw new IllegalArgumentException("outputConsumer must not be null");
        }
        this.outputConsumer = outputConsumer;
    }

    /**
     * Sets the consumer that receives debug trace lines (only used when debug
     * mode is enabled in preferences).
     *
     * @param debugConsumer consumer for {@code [debug] ...} lines, or
     *                      {@code null} to disable in-UI debug output
     */
    public void setDebugConsumer(Consumer<String> debugConsumer) {
        this.debugConsumer = debugConsumer;
    }

    // -------------------------------------------------------------------------
    // public API
    // -------------------------------------------------------------------------

    /**
     * Records the working directory for this session.
     *
     * @param workingDir absolute path to the working directory
     */
    public void start(String workingDir) {
        if (workingDir == null || workingDir.isBlank()) {
            throw new IllegalArgumentException("workingDir must not be blank");
        }
        this.workingDir = workingDir;
        this.firstMessage = true;
        debug("session started in " + workingDir);
    }

    /**
     * Sends a prompt to claude and streams the response to the output consumer.
     *
     * <p>Launches {@code claude --print [--continue] --output-format
     * stream-json --verbose <text>} in the recorded working directory.
     * Blocks until the subprocess exits.
     *
     * @param text the prompt text
     * @throws IOException           if the subprocess cannot be started
     * @throws IllegalStateException if {@link #start} was not called first
     */
    public void sendInput(String text) throws IOException {
        if (workingDir == null) {
            throw new IllegalStateException("Call start() before sendInput()");
        }

        String executable = ClaudeCodePreferences.resolveClaudeExecutable();

        List<String> cmd = new ArrayList<>();
        cmd.add(executable);
        cmd.add("--print");
        if (!firstMessage) {
            cmd.add("--continue");
        }
        cmd.add("--output-format");
        cmd.add("stream-json");
        cmd.add("--verbose");
        cmd.add(text);

        firstMessage = false;

        debug(">>> " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(false);

        Process p = pb.start();
        currentProcess = p;

        // close unused stdin immediately
        try { p.getOutputStream().close(); } catch (IOException ignored) {}

        Thread stdoutThread = new Thread(
                () -> drainStream(p, false), "claude-stdout");
        Thread stderrThread = new Thread(
                () -> drainStream(p, true),  "claude-stderr");
        stdoutThread.setDaemon(true);
        stderrThread.setDaemon(true);
        stdoutThread.start();
        stderrThread.start();

        try {
            stdoutThread.join();
            stderrThread.join();
            int code = p.waitFor();
            debug("process exited with code " + code);
            if (code != 0 || ClaudeCodePreferences.isDebugMode()) {
                outputConsumer.accept("[exited with code " + code + "]");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        } finally {
            currentProcess = null;
        }
    }

    /**
     * Interrupts the currently running subprocess, if any.
     */
    public void stop() {
        Process p = currentProcess;
        if (p != null && p.isAlive()) {
            debug("stopping process");
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
        currentProcess = null;
    }

    /**
     * Returns {@code true} while a subprocess is processing a message.
     *
     * @return {@code true} if running
     */
    public boolean isRunning() {
        Process p = currentProcess;
        return p != null && p.isAlive();
    }

    // -------------------------------------------------------------------------
    // private
    // -------------------------------------------------------------------------

    private void drainStream(Process p, boolean isStderr) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                isStderr ? p.getErrorStream() : p.getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isStderr) {
                    debug("[stderr] " + line);
                    outputConsumer.accept("[stderr] " + line);
                } else {
                    debug("[stdout] " + line);
                    outputConsumer.accept(line);
                }
            }
        } catch (IOException ex) {
            // stream closed — ignore
        }
    }

    /**
     * Logs a debug trace line to the NB logger (always at FINE) and, when
     * debug mode is enabled in preferences, also to the UI via
     * {@link #debugConsumer}.
     */
    private void debug(String message) {
        LOG.fine(message);
        if (ClaudeCodePreferences.isDebugMode()) {
            LOG.info("[claude-debug] " + message);
            Consumer<String> dc = debugConsumer;
            if (dc != null) {
                dc.accept("[debug] " + message);
            }
        }
    }
}
