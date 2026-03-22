package io.github.nbclaudecodegui.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link ClaudePromptPanel.SessionLifecycle} enum.
 *
 * <p>Tests verify that the enum has the expected states and that state
 * properties (ready vs. working) are correctly identified.
 */
class SessionLifecycleTest {

    @Test
    void enumHasExpectedStates() {
        ClaudePromptPanel.SessionLifecycle[] values = ClaudePromptPanel.SessionLifecycle.values();
        assertEquals(3, values.length, "SessionLifecycle must have exactly 3 states");
        assertEquals(ClaudePromptPanel.SessionLifecycle.STARTING, values[0]);
        assertEquals(ClaudePromptPanel.SessionLifecycle.READY,    values[1]);
        assertEquals(ClaudePromptPanel.SessionLifecycle.WORKING,  values[2]);
    }

    @Test
    void startingIsNotReadyOrWorking() {
        ClaudePromptPanel.SessionLifecycle s = ClaudePromptPanel.SessionLifecycle.STARTING;
        assertNotEquals(ClaudePromptPanel.SessionLifecycle.READY,   s);
        assertNotEquals(ClaudePromptPanel.SessionLifecycle.WORKING, s);
    }

    @Test
    void readyIsDistinctFromOthers() {
        ClaudePromptPanel.SessionLifecycle s = ClaudePromptPanel.SessionLifecycle.READY;
        assertNotEquals(ClaudePromptPanel.SessionLifecycle.STARTING, s);
        assertNotEquals(ClaudePromptPanel.SessionLifecycle.WORKING,  s);
    }

    @Test
    void workingIsDistinctFromOthers() {
        ClaudePromptPanel.SessionLifecycle s = ClaudePromptPanel.SessionLifecycle.WORKING;
        assertNotEquals(ClaudePromptPanel.SessionLifecycle.STARTING, s);
        assertNotEquals(ClaudePromptPanel.SessionLifecycle.READY,    s);
    }

    @Test
    void valueOfByName() {
        assertEquals(ClaudePromptPanel.SessionLifecycle.STARTING,
                ClaudePromptPanel.SessionLifecycle.valueOf("STARTING"));
        assertEquals(ClaudePromptPanel.SessionLifecycle.READY,
                ClaudePromptPanel.SessionLifecycle.valueOf("READY"));
        assertEquals(ClaudePromptPanel.SessionLifecycle.WORKING,
                ClaudePromptPanel.SessionLifecycle.valueOf("WORKING"));
    }
}
