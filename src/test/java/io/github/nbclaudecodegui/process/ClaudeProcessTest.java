package io.github.nbclaudecodegui.process;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClaudeProcess}.
 *
 * <p>Uses tiny shell scripts as fake claude executables.  Each script accepts
 * the {@code --print <text>} invocation style and writes JSON to stdout, while
 * keeping stdin open so that {@link ClaudeProcess#sendResponse} can be tested.
 */
class ClaudeProcessTest {

    @Test
    void testConstructorRejectsNullConsumer() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClaudeProcess(null));
    }

    @Test
    void testIsRunningFalseBeforeStart() {
        ClaudeProcess cp = new ClaudeProcess(line -> {});
        assertFalse(cp.isRunning());
    }

    @Test
    void testIsBusyFalseBeforeStart() {
        ClaudeProcess cp = new ClaudeProcess(line -> {});
        assertFalse(cp.isBusy());
    }

    @Test
    void testStopWhenNotRunningIsNoop() {
        ClaudeProcess cp = new ClaudeProcess(line -> {});
        assertDoesNotThrow(cp::stop);
    }

    @Test
    void testSendInputThrowsWhenNotStarted() {
        ClaudeProcess cp = new ClaudeProcess(line -> {});
        assertThrows(IllegalStateException.class,
                () -> cp.sendInput("hello"));
    }

    @Test
    void testSendResponseThrowsWhenNoSubprocess() {
        ClaudeProcess cp = new ClaudeProcess(line -> {});
        cp.start(System.getProperty("java.io.tmpdir"));
        assertThrows(IllegalStateException.class,
                () -> cp.sendResponse("yes"));
    }

    @Test
    void testStartRejectsBlankDir() {
        ClaudeProcess cp = new ClaudeProcess(line -> {});
        assertThrows(IllegalArgumentException.class, () -> cp.start(""));
        assertThrows(IllegalArgumentException.class, () -> cp.start(null));
    }

    @Test
    void testStartRecordsWorkingDir() {
        ClaudeProcess cp = new ClaudeProcess(line -> {});
        assertDoesNotThrow(() -> cp.start(System.getProperty("java.io.tmpdir")));
        // start() only records the dir — no subprocess launched yet
        assertFalse(cp.isRunning());
    }

    /**
     * sendInput() launches a subprocess; outputConsumer receives its output.
     */
    @Test
    void testOutputConsumerReceivesLinesViaEcho() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"),
                "Skipping on Windows");

        // Script that echoes its last argument (simulates claude --print <text>).
        // The real claude uses the CLI argument for the prompt, not stdin.
        java.io.File script = java.io.File.createTempFile("fake-claude", ".sh");
        script.deleteOnExit();
        java.nio.file.Files.writeString(script.toPath(),
                "#!/bin/sh\necho \"$@\"\n");
        script.setExecutable(true);

        io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                .setClaudeExecutablePath(script.getAbsolutePath());
        try {
            List<String> received = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(1);

            ClaudeProcess cp = new ClaudeProcess(line -> {
                received.add(line);
                latch.countDown();
            });
            cp.start(System.getProperty("java.io.tmpdir"));

            Thread sender = new Thread(() -> {
                try { cp.sendInput("hello-test"); }
                catch (Exception ex) { /* ignore */ }
            });
            sender.setDaemon(true);
            sender.start();

            assertTrue(latch.await(10, TimeUnit.SECONDS),
                    "outputConsumer must receive output within 10s");
            assertTrue(received.stream().anyMatch(l -> l.contains("hello-test")),
                    "received lines must contain sent text");
        } finally {
            io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                    .setClaudeExecutablePath("");
            script.delete();
        }
    }

    /**
     * sendResponse() writes to the subprocess stdin while it is running.
     */
    @Test
    void testSendResponseWritesToStdin() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"),
                "Skipping on Windows");
        // stdin is closed immediately after process start in --print mode.
        // Interactive stdin responses require PTY (Stage 11).
        org.junit.jupiter.api.Assumptions.abort(
                "Skipped: sendResponse via stdin not supported in --print mode; requires PTY (Stage 11)");

        // Script: echoes last arg (the prompt), then reads one answer from stdin.
        // Simulates claude --print responding then asking for input.
        java.io.File script = java.io.File.createTempFile("fake-claude-resp", ".sh");
        script.deleteOnExit();
        java.nio.file.Files.writeString(script.toPath(),
                "#!/bin/sh\n"
                + "echo \"got:$@\"\n"      // echo the CLI args (prompt text)
                + "read -r answer\n"       // reads the sendResponse() text from stdin
                + "echo \"answer:$answer\"\n");
        script.setExecutable(true);

        io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                .setClaudeExecutablePath(script.getAbsolutePath());
        try {
            List<String> received = new ArrayList<>();
            CountDownLatch answerLatch = new CountDownLatch(1);

            ClaudeProcess cp = new ClaudeProcess(line -> {
                received.add(line);
                if (line.startsWith("answer:")) answerLatch.countDown();
            });
            cp.start(System.getProperty("java.io.tmpdir"));

            Thread sender = new Thread(() -> {
                try {
                    cp.sendInput("question-text");
                } catch (Exception ex) { /* ignore */ }
            });
            sender.setDaemon(true);
            sender.start();

            // Wait for process to start and output first line, then send response
            Thread.sleep(300);
            cp.sendResponse("my-answer");

            assertTrue(answerLatch.await(10, TimeUnit.SECONDS),
                    "answer must be echoed back within 10s");
            assertTrue(received.stream().anyMatch(l -> l.contains("my-answer")));
        } finally {
            io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                    .setClaudeExecutablePath("");
            script.delete();
        }
    }

    /**
     * promptConsumer is invoked when the script outputs a prompt-like line.
     */
    @Test
    void testPromptConsumerCalledForPromptLine() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"),
                "Skipping on Windows");

        // Script that emits a prompt-style line, then a result
        java.io.File script = java.io.File.createTempFile("fake-claude-prompt", ".sh");
        script.deleteOnExit();
        java.nio.file.Files.writeString(script.toPath(),
                "#!/bin/sh\n"
                + "echo 'Allow this action? (y/n/always)'\n"
                + "echo '{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"ok\"}'\n");
        script.setExecutable(true);

        io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                .setClaudeExecutablePath(script.getAbsolutePath());
        try {
            List<ClaudeProcess.PromptRequest> prompts = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(1);

            ClaudeProcess cp = new ClaudeProcess(line -> {});
            cp.setPromptConsumer(req -> {
                prompts.add(req);
                latch.countDown();
            });
            cp.start(System.getProperty("java.io.tmpdir"));

            Thread sender = new Thread(() -> {
                try { cp.sendInput("test"); }
                catch (Exception ex) {}
            });
            sender.setDaemon(true);
            sender.start();

            assertTrue(latch.await(10, TimeUnit.SECONDS),
                    "promptConsumer must be called within 10s");
            assertFalse(prompts.isEmpty());
            assertFalse(prompts.get(0).options().isEmpty(),
                    "options should be parsed from the prompt line");
        } finally {
            io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                    .setClaudeExecutablePath("");
            script.delete();
        }
    }

    /**
     * responseDoneCallback is invoked after sendInput() completes.
     */
    @Test
    void testResponseDoneCallbackCalledAfterSendInput() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"),
                "Skipping on Windows");

        java.io.File script = java.io.File.createTempFile("fake-claude-done", ".sh");
        script.deleteOnExit();
        java.nio.file.Files.writeString(script.toPath(),
                "#!/bin/sh\n"
                + "echo '{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"Done!\"}'\n");
        script.setExecutable(true);

        io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                .setClaudeExecutablePath(script.getAbsolutePath());
        try {
            CountDownLatch doneLatch = new CountDownLatch(1);
            ClaudeProcess cp = new ClaudeProcess(line -> {});
            cp.setResponseDoneCallback(doneLatch::countDown);
            cp.start(System.getProperty("java.io.tmpdir"));

            Thread sender = new Thread(() -> {
                try { cp.sendInput("go"); }
                catch (Exception ex) {}
            });
            sender.setDaemon(true);
            sender.start();

            assertTrue(doneLatch.await(10, TimeUnit.SECONDS),
                    "responseDoneCallback must be called after sendInput completes");
            assertFalse(cp.isBusy(), "should not be busy after completion");
        } finally {
            io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                    .setClaudeExecutablePath("");
            script.delete();
        }
    }

    @Test
    void testGetSessionIdNullBeforeFirstSend() {
        ClaudeProcess cp = new ClaudeProcess(line -> {});
        cp.start(System.getProperty("java.io.tmpdir"));
        assertNull(cp.getSessionId(), "sessionId must be null before any subprocess output");
    }

    @Test
    void testSessionIdExtractedFromSystemEvent() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"));

        java.io.File script = java.io.File.createTempFile("fake-claude-sess", ".sh");
        script.deleteOnExit();
        java.nio.file.Files.writeString(script.toPath(),
                "#!/bin/sh\n"
                + "echo '{\"type\":\"system\",\"subtype\":\"init\",\"sessionId\":\"test-sess-999\"}'\n"
                + "echo '{\"type\":\"assistant\",\"message\":{\"id\":\"msg_1\",\"content\":[{\"type\":\"text\",\"text\":\"Hi!\"}]}}'\n"
                + "echo '{\"type\":\"result\",\"subtype\":\"success\",\"session_id\":\"test-sess-999\"}'\n");
        script.setExecutable(true);

        io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                .setClaudeExecutablePath(script.getAbsolutePath());
        try {
            List<String> received = new ArrayList<>();
            List<String> sessionIds = new ArrayList<>();

            ClaudeProcess cp = new ClaudeProcess(received::add);
            cp.setSessionIdConsumer(sessionIds::add);
            cp.start(System.getProperty("java.io.tmpdir"));
            cp.sendInput("hello");

            assertEquals("test-sess-999", cp.getSessionId());
            assertFalse(sessionIds.isEmpty());
            assertEquals("test-sess-999", sessionIds.get(0));
            assertTrue(received.contains("Hi!"));
        } finally {
            io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                    .setClaudeExecutablePath("");
            script.delete();
        }
    }

    @Test
    void testNonJsonLinesPassedThrough() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"));

        java.io.File script = java.io.File.createTempFile("fake-claude-raw", ".sh");
        script.deleteOnExit();
        java.nio.file.Files.writeString(script.toPath(),
                "#!/bin/sh\necho 'plain text output'\n");
        script.setExecutable(true);

        io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                .setClaudeExecutablePath(script.getAbsolutePath());
        try {
            List<String> received = new ArrayList<>();
            ClaudeProcess cp = new ClaudeProcess(received::add);
            cp.start(System.getProperty("java.io.tmpdir"));
            cp.sendInput("test");

            assertTrue(received.stream().anyMatch(l -> l.contains("plain text output")));
        } finally {
            io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                    .setClaudeExecutablePath("");
            script.delete();
        }
    }

    @Test
    void testDebugConsumerReceivesLinesWhenDebugEnabled() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"));

        java.io.File script = java.io.File.createTempFile("fake-claude2", ".sh");
        script.deleteOnExit();
        java.nio.file.Files.writeString(script.toPath(),
                "#!/bin/sh\necho \"$@\"\n");
        script.setExecutable(true);

        io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                .setClaudeExecutablePath(script.getAbsolutePath());
        io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                .setDebugMode(true);
        try {
            List<String> debugLines = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(1);
            ClaudeProcess cp = new ClaudeProcess(line -> {});
            cp.setDebugConsumer(line -> {
                debugLines.add(line);
                latch.countDown();
            });
            cp.start(System.getProperty("java.io.tmpdir"));

            Thread sender = new Thread(() -> {
                try { cp.sendInput("dbg-test"); }
                catch (Exception ex) {}
            });
            sender.setDaemon(true);
            sender.start();
            sender.join(10_000);

            assertTrue(debugLines.stream().anyMatch(l -> l.startsWith("[debug]")));
        } finally {
            io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                    .setClaudeExecutablePath("");
            io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                    .setDebugMode(false);
            script.delete();
        }
    }
}
