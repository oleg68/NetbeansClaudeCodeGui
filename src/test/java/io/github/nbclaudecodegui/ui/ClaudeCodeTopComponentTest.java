package io.github.nbclaudecodegui.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClaudeCodeTopComponent}.
 */
class ClaudeCodeTopComponentTest {

    @Test
    void testDisplayName() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        assertEquals("Claude Code", tc.getName());
    }

    @Test
    void testComponentNotNull() {
        assertNotNull(new ClaudeCodeTopComponent());
    }

    @Test
    void testPersistenceType() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        assertEquals(ClaudeCodeTopComponent.PERSISTENCE_ALWAYS, tc.getPersistenceType());
    }
}
