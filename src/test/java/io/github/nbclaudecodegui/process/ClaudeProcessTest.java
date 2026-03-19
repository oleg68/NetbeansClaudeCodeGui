package io.github.nbclaudecodegui.process;

import com.pty4j.PtyProcess;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClaudeProcess}.
 */
class ClaudeProcessTest {

    @Test
    void testStartRejectsBlankDir() {
        ClaudeProcess cp = new ClaudeProcess();
        assertThrows(IllegalArgumentException.class, () -> cp.start(""));
        assertThrows(IllegalArgumentException.class, () -> cp.start(null));
    }

    @Test
    void testStopWhenNotRunningIsNoop() {
        ClaudeProcess cp = new ClaudeProcess();
        assertDoesNotThrow(cp::stop);
    }

    @Test
    void testIsRunningFalseBeforeStart() {
        ClaudeProcess cp = new ClaudeProcess();
        assertFalse(cp.isRunning());
    }

    @Test
    void testStartReturnsPtyProcess() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"),
                "Skipping on Windows");

        File script = makeFakeClaude("sleep 60\n");
        try {
            ClaudeProcess cp = new ClaudeProcess();
            PtyProcess p = cp.start(System.getProperty("java.io.tmpdir"));
            assertNotNull(p);
            assertTrue(p.isAlive());
            cp.stop();
        } finally {
            resetPrefs(script);
        }
    }

    @Test
    void testStartedProcessIsRunning() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"),
                "Skipping on Windows");

        File script = makeFakeClaude("sleep 60\n");
        try {
            ClaudeProcess cp = new ClaudeProcess();
            cp.start(System.getProperty("java.io.tmpdir"));
            assertTrue(cp.isRunning());
            cp.stop();
            assertFalse(cp.isRunning());
        } finally {
            resetPrefs(script);
        }
    }

    @Test
    void testStopKillsProcess() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"),
                "Skipping on Windows");

        File script = makeFakeClaude("sleep 60\n");
        try {
            ClaudeProcess cp = new ClaudeProcess();
            PtyProcess p = cp.start(System.getProperty("java.io.tmpdir"));
            assertTrue(p.isAlive());
            cp.stop();
            p.waitFor();
            assertFalse(p.isAlive());
        } finally {
            resetPrefs(script);
        }
    }

    @Test
    void testMcpConfigJsonUsesSseTransport() {
        String json = ClaudeProcess.buildMcpConfigJson(8990);
        assertTrue(json.contains("\"type\":\"sse\""),
                "MCP config must use SSE transport, got: " + json);
        assertTrue(json.contains("http://localhost:8990"),
                "MCP config must use http:// URL for SSE, got: " + json);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static File makeFakeClaude(String body) throws Exception {
        File script = File.createTempFile("fake-claude", ".sh");
        script.deleteOnExit();
        Files.writeString(script.toPath(), "#!/bin/sh\n" + body);
        script.setExecutable(true);
        io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                .setClaudeExecutablePath(script.getAbsolutePath());
        return script;
    }

    private static void resetPrefs(File script) {
        io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                .setClaudeExecutablePath("");
        if (script != null) script.delete();
    }
}
