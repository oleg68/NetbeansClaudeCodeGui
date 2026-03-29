package io.github.nbclaudecodegui.ui;

import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownDiffPanelTest {

    @Test
    void constructsWithoutError() {
        MarkdownDiffPanel panel = new MarkdownDiffPanel("# Hello", "# World");
        assertNotNull(panel);
    }

    @Test
    void constructsWithNullContent() {
        MarkdownDiffPanel panel = new MarkdownDiffPanel(null, null);
        assertNotNull(panel);
    }

    @Test
    void attachRawDiffSyncFindsNoScrollPaneGracefully() {
        MarkdownDiffPanel panel = new MarkdownDiffPanel("before", "after");
        // Should not throw even without JScrollPane in component tree
        assertDoesNotThrow(() -> panel.attachRawDiffSync(new JPanel()));
    }
}
