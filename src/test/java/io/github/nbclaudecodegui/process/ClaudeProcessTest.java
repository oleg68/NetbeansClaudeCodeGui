package io.github.nbclaudecodegui.process;

import com.pty4j.PtyProcess;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    // -------------------------------------------------------------------------
    // mergeSettingsJson
    // -------------------------------------------------------------------------

    @Test
    void testMergeEmptyFileAddsOurEntries() {
        String result = ClaudeProcess.mergeSettingsJson("{}", 8990);
        assertTrue(result.contains("\"netbeans\""), "should add mcpServers.netbeans");
        assertTrue(result.contains("http://localhost:8990/sse"), "should contain SSE URL");
        assertTrue(result.contains("PreToolUse"), "should add PreToolUse hook");
        assertTrue(result.contains("http://localhost:8990/hook"), "should contain hook URL");
        assertTrue(result.contains("Edit|Write|MultiEdit"), "should contain matcher");
    }

    @Test
    void testMergePreservesOtherMcpServers() {
        String existing = "{\"mcpServers\":{\"other-server\":{\"type\":\"stdio\",\"command\":\"foo\"}}}";
        String result = ClaudeProcess.mergeSettingsJson(existing, 9000);
        assertTrue(result.contains("\"other-server\""), "should preserve other MCP server");
        assertTrue(result.contains("\"netbeans\""), "should add netbeans server");
    }

    @Test
    void testMergeUpdatesPortWhenAlreadyPresent() {
        String existing = ClaudeProcess.mergeSettingsJson("{}", 8888);
        String result = ClaudeProcess.mergeSettingsJson(existing, 9999);
        assertTrue(result.contains("localhost:9999"), "should update to new port");
        assertFalse(result.contains("localhost:8888"), "should not keep old port");
        // Only one netbeans entry
        assertEquals(1, countOccurrences(result, "\"netbeans\""));
    }

    @Test
    void testMergePreservesOtherPreToolUseHooks() {
        String existing = "{"
                + "\"hooks\":{\"PreToolUse\":["
                + "{\"matcher\":\"Read\",\"hooks\":[{\"type\":\"http\",\"url\":\"http://other/hook\"}]}"
                + "]}}";
        String result = ClaudeProcess.mergeSettingsJson(existing, 9000);
        assertTrue(result.contains("\"Read\""), "should preserve other PreToolUse hooks");
        assertTrue(result.contains("Edit|Write|MultiEdit"), "should add our hook");
        // Our matcher appears exactly once
        assertEquals(1, countOccurrences(result, "Edit|Write|MultiEdit"));
    }

    @Test
    void testMergeReplacesOurHookWithUpdatedPort() {
        String existing = ClaudeProcess.mergeSettingsJson("{}", 8888);
        String result = ClaudeProcess.mergeSettingsJson(existing, 9999);
        assertEquals(1, countOccurrences(result, "Edit|Write|MultiEdit"),
                "our hook matcher should appear exactly once");
        assertTrue(result.contains("localhost:9999/hook"), "hook URL should be updated");
    }

    // -------------------------------------------------------------------------
    // cleanedSettingsJson
    // -------------------------------------------------------------------------

    @Test
    void testCleanupReturnsNullWhenOnlyOurEntries() {
        String merged = ClaudeProcess.mergeSettingsJson("{}", 9000);
        String cleaned = ClaudeProcess.cleanedSettingsJson(merged);
        assertNull(cleaned, "file containing only plugin entries should yield null (→ delete)");
    }

    @Test
    void testCleanupRemovesOurEntriesButKeepsOthers() {
        String existing = "{"
                + "\"mcpServers\":{\"other-server\":{\"type\":\"stdio\",\"command\":\"foo\"}},"
                + "\"hooks\":{\"PreToolUse\":["
                + "{\"matcher\":\"Read\",\"hooks\":[{\"type\":\"http\",\"url\":\"http://other/hook\"}]}"
                + "]}}";
        String merged = ClaudeProcess.mergeSettingsJson(existing, 9000);
        String cleaned = ClaudeProcess.cleanedSettingsJson(merged);

        assertNotNull(cleaned, "should not delete file when user content remains");
        assertFalse(cleaned.contains("\"netbeans\""), "should remove netbeans MCP server");
        assertFalse(cleaned.contains("Edit|Write|MultiEdit"), "should remove our PreToolUse hook");
        assertTrue(cleaned.contains("\"other-server\""), "should preserve other MCP server");
        assertTrue(cleaned.contains("\"Read\""), "should preserve other PreToolUse hooks");
    }

    @Test
    void testCleanupPreservesNonPreToolUseHooks() {
        String existing = "{\"hooks\":{\"PostToolUse\":[{\"matcher\":\"*\",\"hooks\":[]}]}}";
        String merged = ClaudeProcess.mergeSettingsJson(existing, 9000);
        String cleaned = ClaudeProcess.cleanedSettingsJson(merged);

        assertNotNull(cleaned, "should not delete when PostToolUse hook remains");
        assertTrue(cleaned.contains("PostToolUse"), "should preserve PostToolUse hook");
    }

    // -------------------------------------------------------------------------
    // cleanupSettingsLocalJson (I/O)
    // -------------------------------------------------------------------------

    @Test
    void testCleanupDeletesFileWhenOnlyPluginContent(@TempDir Path tmpDir) throws Exception {
        Path claudeDir = tmpDir.resolve(".claude");
        Files.createDirectories(claudeDir);
        Path cfg = claudeDir.resolve("settings.local.json");
        Files.writeString(cfg, ClaudeProcess.mergeSettingsJson("{}", 9000), StandardCharsets.UTF_8);

        ClaudeProcess.cleanupSettingsLocalJson(tmpDir.toString());

        assertFalse(Files.exists(cfg), "file should be deleted when only plugin content was present");
    }

    @Test
    void testCleanupUpdatesFileWhenUserContentPresent(@TempDir Path tmpDir) throws Exception {
        Path claudeDir = tmpDir.resolve(".claude");
        Files.createDirectories(claudeDir);
        Path cfg = claudeDir.resolve("settings.local.json");
        String userContent = "{\"mcpServers\":{\"my-server\":{\"type\":\"stdio\",\"command\":\"bar\"}}}";
        Files.writeString(cfg, ClaudeProcess.mergeSettingsJson(userContent, 9000), StandardCharsets.UTF_8);

        ClaudeProcess.cleanupSettingsLocalJson(tmpDir.toString());

        assertTrue(Files.exists(cfg), "file should remain when user content was present");
        String remaining = Files.readString(cfg);
        assertTrue(remaining.contains("my-server"), "user content should be preserved");
        assertFalse(remaining.contains("netbeans"), "plugin entry should be removed");
    }

    @Test
    void testCleanupIsNoopWhenFileAbsent(@TempDir Path tmpDir) {
        assertDoesNotThrow(() -> ClaudeProcess.cleanupSettingsLocalJson(tmpDir.toString()));
    }

    // -------------------------------------------------------------------------
    // readVersion
    // -------------------------------------------------------------------------

    @Test
    void testReadVersionReturnsStringOnSuccess() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"),
                "Skipping on Windows");

        // Create a fake claude that prints a version line
        File script = makeFakeClaude("echo '1.2.3 (Claude Code)'\n");
        try {
            ClaudeProcess cp = new ClaudeProcess();
            String version = cp.readVersion();
            assertEquals("1.2.3 (Claude Code)", version);
        } finally {
            resetPrefs(script);
        }
    }

    @Test
    void testReadVersionReturnsEmptyStringOnFailure() {
        // Point to a non-existent executable
        io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                .setClaudeExecutablePath("/nonexistent/claude-xyz");
        try {
            ClaudeProcess cp = new ClaudeProcess();
            String version = cp.readVersion();
            assertEquals("", version);
        } finally {
            io.github.nbclaudecodegui.settings.ClaudeCodePreferences
                    .setClaudeExecutablePath("");
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

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
