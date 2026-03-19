package io.github.nbclaudecodegui.settings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClaudeCodePreferences}.
 */
class ClaudeCodePreferencesTest {

    @AfterEach
    void tearDown() {
        ClaudeCodePreferences.setClaudeExecutablePath(
                ClaudeCodePreferences.DEFAULT_CLAUDE_EXECUTABLE_PATH);
        ClaudeCodePreferences.setMcpPort(ClaudeCodePreferences.DEFAULT_MCP_PORT);
    }

    @Test
    void defaultPathIsEmpty() {
        assertEquals(ClaudeCodePreferences.DEFAULT_CLAUDE_EXECUTABLE_PATH,
                ClaudeCodePreferences.getClaudeExecutablePath());
    }

    @Test
    void storeAndRetrievePath() {
        ClaudeCodePreferences.setClaudeExecutablePath("/usr/local/bin/claude");
        assertEquals("/usr/local/bin/claude",
                ClaudeCodePreferences.getClaudeExecutablePath());
    }

    @Test
    void nullPathStoredAsDefault() {
        ClaudeCodePreferences.setClaudeExecutablePath(null);
        assertEquals(ClaudeCodePreferences.DEFAULT_CLAUDE_EXECUTABLE_PATH,
                ClaudeCodePreferences.getClaudeExecutablePath());
    }

    @Test
    void testFindOnPathFindsRealExecutable() {
        String result = ClaudeCodePreferences.findOnPath();
        if (result != null) {
            assertTrue(new java.io.File(result).canExecute(),
                    "findOnPath must return an executable path");
        }
    }

    @Test
    void mcpPortDefaultIs28991() {
        assertEquals(28991, ClaudeCodePreferences.DEFAULT_MCP_PORT);
        assertEquals(28991, ClaudeCodePreferences.getMcpPort());
    }

    @Test
    void mcpPortStoreAndRetrieve() {
        ClaudeCodePreferences.setMcpPort(9000);
        assertEquals(9000, ClaudeCodePreferences.getMcpPort());
    }

    @Test
    void testResolveClaudeExecutableFallsBackToLiteral() {
        ClaudeCodePreferences.setClaudeExecutablePath("");
        String resolved = ClaudeCodePreferences.resolveClaudeExecutable();
        assertNotNull(resolved);
        assertFalse(resolved.isBlank(),
                "resolveClaudeExecutable must never return blank");
    }
}
