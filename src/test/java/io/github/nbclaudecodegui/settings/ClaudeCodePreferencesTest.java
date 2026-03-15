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
        ClaudeCodePreferences.setClaudeExecutablePath(ClaudeCodePreferences.DEFAULT_CLAUDE_EXECUTABLE_PATH);
    }

    @Test
    void defaultPathIsEmpty() {
        assertEquals(ClaudeCodePreferences.DEFAULT_CLAUDE_EXECUTABLE_PATH,
                ClaudeCodePreferences.getClaudeExecutablePath());
    }

    @Test
    void storeAndRetrievePath() {
        ClaudeCodePreferences.setClaudeExecutablePath("/usr/local/bin/claude");
        assertEquals("/usr/local/bin/claude", ClaudeCodePreferences.getClaudeExecutablePath());
    }

    @Test
    void nullPathStoredAsDefault() {
        ClaudeCodePreferences.setClaudeExecutablePath(null);
        assertEquals(ClaudeCodePreferences.DEFAULT_CLAUDE_EXECUTABLE_PATH,
                ClaudeCodePreferences.getClaudeExecutablePath());
    }
}
