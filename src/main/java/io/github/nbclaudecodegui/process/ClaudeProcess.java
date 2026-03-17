package io.github.nbclaudecodegui.process;

import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages Claude Code CLI invocations for a single session.
 *
 * <p>Each call to {@link #sendInput(String)} launches a new subprocess using
 * {@code --print <text> --output-format stream-json --verbose}, which is the
 * mode that produces NDJSON output over stdout.  The subprocess stdin is kept
 * open so that {@link #sendResponse(String)} can write answers to interactive
 * prompts (e.g. tool-permission requests) while the subprocess is running.
 *
 * <p>Session continuity is maintained via {@code --resume <session_id>} once
 * the session ID is extracted from the first subprocess run.
 *
 * <p>When claude emits an interactive question the optional
 * {@link #setPromptConsumer(Consumer) promptConsumer} is invoked.  After the
 * result event the optional {@link #setResponseDoneCallback(Runnable)
 * responseDoneCallback} is called and the busy flag is cleared.
 */
public final class ClaudeProcess {

    private static final Logger LOG =
            Logger.getLogger(ClaudeProcess.class.getName());

    /** Receives formatted response text; invoked on a background thread. */
    private final Consumer<String> outputConsumer;
    /** Receives debug lines when debug mode is on; may be null. */
    private Consumer<String> debugConsumer;
    /** Called once when the session ID becomes known; may be null. */
    private Consumer<String> sessionIdConsumer;
    /** Called when claude asks an interactive question; may be null. */
    private Consumer<PromptRequest> promptConsumer;
    /** Called after each result event (response done); may be null. */
    private Runnable responseDoneCallback;

    private String workingDir;
    private volatile Process currentProcess;
    /** Stdin writer for the currently running subprocess; null when idle. */
    private volatile PrintWriter stdinWriter;
    private volatile boolean busy;
    private boolean firstMessage = true;

    /** Session ID extracted from stream-json events; {@code null} until known. */
    private volatile String sessionId;

    /**
     * Message ID of the last {@code assistant} event whose text was streamed to
     * {@link #outputConsumer}.  Used to detect message boundaries and avoid
     * re-sending text that was already output.
     */
    private String lastStreamedMessageId;
    /** Number of characters already sent to {@link #outputConsumer} for {@link #lastStreamedMessageId}. */
    private int lastStreamedLength;

    // -------------------------------------------------------------------------
    // inner record
    // -------------------------------------------------------------------------

    /**
     * Represents an interactive question from claude.
     *
     * @param text    the question text as received (raw line)
     * @param options parsed answer options (may be empty — free-form only)
     */
    public record PromptRequest(String text, List<String> options) {}

    // -------------------------------------------------------------------------
    // constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code ClaudeProcess}.
     *
     * @param outputConsumer receives formatted response text; invoked on a
     *                       background thread
     */
    public ClaudeProcess(Consumer<String> outputConsumer) {
        if (outputConsumer == null) {
            throw new IllegalArgumentException("outputConsumer must not be null");
        }
        this.outputConsumer = outputConsumer;
    }

    // -------------------------------------------------------------------------
    // setters
    // -------------------------------------------------------------------------

    public void setDebugConsumer(Consumer<String> debugConsumer) {
        this.debugConsumer = debugConsumer;
    }

    public void setSessionIdConsumer(Consumer<String> consumer) {
        this.sessionIdConsumer = consumer;
    }

    /** Sets the consumer invoked when claude asks an interactive question. */
    public void setPromptConsumer(Consumer<PromptRequest> consumer) {
        this.promptConsumer = consumer;
    }

    /** Sets a callback invoked after each result event (response complete). */
    public void setResponseDoneCallback(Runnable callback) {
        this.responseDoneCallback = callback;
    }

    // -------------------------------------------------------------------------
    // public API
    // -------------------------------------------------------------------------

    /**
     * Records the working directory for this session.
     *
     * @param workingDir absolute path to the working directory
     * @throws IllegalArgumentException if {@code workingDir} is blank
     */
    public void start(String workingDir) {
        if (workingDir == null || workingDir.isBlank()) {
            throw new IllegalArgumentException("workingDir must not be blank");
        }
        this.workingDir = workingDir;
        this.firstMessage = true;
        this.sessionId = null;
        this.lastStreamedMessageId = null;
        this.lastStreamedLength = 0;
        debug("session started in " + workingDir);
    }

    /**
     * Sends a prompt to claude by launching a subprocess with
     * {@code --print <text> --output-format stream-json --verbose}.
     *
     * <p>The subprocess stdin is kept open so that {@link #sendResponse}
     * can write answers to interactive prompts.  Blocks until the subprocess
     * exits (call from a non-EDT thread).
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
            if (sessionId != null) {
                cmd.add("--resume");
                cmd.add(sessionId);
            } else {
                cmd.add("--continue");
            }
        }
        cmd.add("--output-format");
        cmd.add("stream-json");
        cmd.add("--verbose");
        cmd.add("--dangerously-skip-permissions");
        cmd.add(text);
        // --dangerously-skip-permissions: when stdin is a pipe (not a TTY),
        // claude's Node.js process buffers stdout while waiting for a terminal
        // permission prompt — causing a complete hang with no output at all.
        // Skipping permissions avoids the prompt and unblocks the output stream.
        // PTY-based interactive permission handling is planned for a later stage.

        firstMessage = false;
        busy = true;

        debug(">>> " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(false);
        Process p = pb.start();
        currentProcess = p;
        // Close stdin immediately: claude --print reads the prompt from the CLI
        // argument, not from stdin.  Keeping stdin open (no EOF) causes claude's
        // Node.js process to hang indefinitely before producing any output.
        // Interactive responses via stdin require PTY support (Stage 11).
        p.getOutputStream().close();
        debug("[stdin] closed (EOF sent), pid=" + p.pid());

        Thread stdoutThread = new Thread(() -> drainStdout(p), "claude-stdout");
        Thread stderrThread = new Thread(() -> drainStderr(p), "claude-stderr");
        stdoutThread.setDaemon(true);
        stderrThread.setDaemon(true);
        stdoutThread.start();
        stderrThread.start();

        try {
            stdoutThread.join();
            stderrThread.join();
            int code = p.waitFor();
            debug("process exited with code " + code);
            // 143 = SIGTERM (destroy), 137 = SIGKILL (destroyForcibly): user cancelled
            if (code != 0 && code != 143 && code != 137) {
                outputConsumer.accept("[exited with code " + code + "]");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        } finally {
            busy = false;
            currentProcess = null;
            stdinWriter = null;
            Runnable cb = responseDoneCallback;
            if (cb != null) cb.run();
        }
    }

    /**
     * Sends a response to an interactive prompt from claude by writing it to
     * the currently running subprocess's stdin.
     *
     * @param answer the answer text
     * @throws IllegalStateException if no subprocess is currently running
     */
    public void sendResponse(String answer) {
        PrintWriter w = stdinWriter;
        if (w == null) {
            throw new IllegalStateException("No subprocess running; cannot send response");
        }
        debug("[stdin] " + answer);
        w.println(answer);
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
        busy = false;
        currentProcess = null;
        stdinWriter = null;
    }

    /**
     * Returns {@code true} while a subprocess is active (between
     * {@link #sendInput} launching it and it exiting).
     *
     * @return {@code true} if running
     */
    public boolean isRunning() {
        Process p = currentProcess;
        return p != null && p.isAlive();
    }

    /**
     * Returns {@code true} while a subprocess is processing a prompt
     * (between {@link #sendInput} and the corresponding result event or exit).
     *
     * @return {@code true} if busy
     */
    public boolean isBusy() {
        return busy;
    }

    /**
     * Returns the session ID extracted from the last subprocess run, or
     * {@code null} if no session has been established yet.
     *
     * @return the session ID or {@code null}
     */
    public String getSessionId() {
        return sessionId;
    }

    // -------------------------------------------------------------------------
    // stream draining
    // -------------------------------------------------------------------------

    private void drainStdout(Process p) {
        debug("[stdout-thread] started");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                processStdoutLine(line);
            }
        } catch (IOException ex) {
            debug("[stdout-thread] IOException: " + ex.getMessage());
        }
        debug("[stdout-thread] EOF");
    }

    private void drainStderr(Process p) {
        debug("[stderr-thread] started");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                p.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                debug("[stderr] " + line);
            }
        } catch (IOException ex) {
            debug("[stderr-thread] IOException: " + ex.getMessage());
        }
        debug("[stderr-thread] EOF");
    }

    // -------------------------------------------------------------------------
    // JSON event processing
    // -------------------------------------------------------------------------

    private void processStdoutLine(String line) {
        debug("[stdout] " + line);

        // Check for interactive prompt before other processing
        if (StreamJsonParser.isPromptRequest(line)) {
            List<String> options = StreamJsonParser.extractPromptOptions(line);
            PromptRequest req = new PromptRequest(line, options);
            Consumer<PromptRequest> pc = promptConsumer;
            if (pc != null) {
                pc.accept(req);
            }
        }

        if (!line.startsWith("{")) {
            // Not JSON — forward as-is (e.g., during tests or plain text output)
            outputConsumer.accept(line);
            return;
        }

        String type = StreamJsonParser.extractString(line, "type");

        if ("system".equals(type)) {
            onSystemEvent(line);
        } else if ("assistant".equals(type)) {
            onAssistantEvent(line);
        } else if ("result".equals(type)) {
            onResultEvent(line);
        }
    }

    private void onSystemEvent(String line) {
        if (sessionId == null) {
            String sid = StreamJsonParser.extractSessionId(line);
            if (sid != null) {
                sessionId = sid;
                debug("session established: " + sessionId);
                notifySessionId(sessionId);
            }
        }
    }

    private void onAssistantEvent(String line) {
        String text = StreamJsonParser.extractAssistantText(line);
        if (text == null || text.isBlank()) return;

        // Each assistant event may carry the CUMULATIVE text for the message so far.
        // Track the message ID so we output only the NEW characters on each event.
        String msgId = StreamJsonParser.extractString(line, "id");
        if (!Objects.equals(msgId, lastStreamedMessageId)) {
            lastStreamedMessageId = msgId;
            lastStreamedLength = 0;
        }

        if (text.length() > lastStreamedLength) {
            outputConsumer.accept(text.substring(lastStreamedLength));
            lastStreamedLength = text.length();
        }
    }

    private void onResultEvent(String line) {
        if (sessionId == null) {
            String sid = StreamJsonParser.extractSessionId(line);
            if (sid != null) {
                sessionId = sid;
                debug("session established (from result): " + sessionId);
                notifySessionId(sessionId);
            }
        }

        String subtype = StreamJsonParser.extractString(line, "subtype");

        if ("success".equals(subtype)) {
            // Response text was already streamed via assistant events — no duplicate output.
            if (ClaudeCodePreferences.isDebugMode()) {
                String cost = StreamJsonParser.extractString(line, "total_cost_usd");
                if (cost != null) {
                    outputConsumer.accept("[cost: $" + cost + "]");
                }
            }
        } else if ("error".equals(subtype)) {
            String resultText = StreamJsonParser.extractString(line, "result");
            outputConsumer.accept("[error] "
                    + (resultText != null ? resultText : "unknown error"));
        }
    }

    private void notifySessionId(String sid) {
        Consumer<String> c = sessionIdConsumer;
        if (c != null) {
            c.accept(sid);
        }
    }

    // -------------------------------------------------------------------------
    // debug logging
    // -------------------------------------------------------------------------

    private void debug(String message) {
        LOG.fine(message);
        if (ClaudeCodePreferences.isDebugMode()) {
            LOG.log(Level.INFO, "[claude-debug] {0}", message);
            Consumer<String> dc = debugConsumer;
            if (dc != null) {
                dc.accept("[debug] " + message);
            }
        }
    }
}
