package io.github.nbclaudecodegui.process;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClaudeProcess}.
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
    void testStartRejectsBlankDir() {
        ClaudeProcess cp = new ClaudeProcess(line -> {});
        assertThrows(IllegalArgumentException.class, () -> cp.start(""));
        assertThrows(IllegalArgumentException.class, () -> cp.start(null));
    }

    @Test
    void testStartRecordsWorkingDir() {
        ClaudeProcess cp = new ClaudeProcess(line -> {});
        // start() must not throw for a valid directory string
        assertDoesNotThrow(() -> cp.start(System.getProperty("java.io.tmpdir")));
        // after start(), isRunning is still false (no subprocess yet)
        assertFalse(cp.isRunning());
    }

    /**
     * Sends a one-shot command via a real subprocess ({@code echo}) to verify
     * that the output consumer receives the output lines.
     *
     * <p>We override the executable via preferences to point to a shell
     * script / command that accepts {@code --print ... text} and echoes it.
     * On Unix we use a tiny shell wrapper stored in a temp file.
     */
    @Test
    void testOutputConsumerReceivesLinesViaEcho() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"),
                "Skipping on Windows");

        // Create a tiny shell script that echoes its last argument (simulates claude --print)
        java.io.File script = java.io.File.createTempFile("fake-claude", ".sh");
        script.deleteOnExit();
        java.nio.file.Files.writeString(script.toPath(),
                "#!/bin/sh\n# echo last argument\necho \"$@\"\n");
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
            ClaudeProcess cp = new ClaudeProcess(line -> {});
            cp.setDebugConsumer(debugLines::add);
            cp.start(System.getProperty("java.io.tmpdir"));

            Thread sender = new Thread(() -> {
                try { cp.sendInput("dbg-test"); }
                catch (Exception ex) {}
            });
            sender.setDaemon(true);
            sender.start();
            sender.join(10_000);

            assertTrue(debugLines.stream().anyMatch(l -> l.startsWith("[debug]")),
                    "debugConsumer must receive [debug] lines when debug mode is on");
        } finally {
            io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                    .setClaudeExecutablePath("");
            io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                    .setDebugMode(false);
            script.delete();
        }
    }
}
