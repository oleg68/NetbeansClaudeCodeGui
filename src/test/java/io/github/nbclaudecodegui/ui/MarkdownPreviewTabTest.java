package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.ui.common.MarkdownRenderer;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that panes created for MarkdownPreviewTab (via createOutputPane())
 * have a context menu with Select All and Copy.
 */
class MarkdownPreviewTabTest {

    @Test
    void createOutputPaneHasSelectAllAndCopyContextMenu() {
        JEditorPane pane = MarkdownRenderer.createOutputPane();
        JPopupMenu popup = pane.getComponentPopupMenu();
        assertNotNull(popup, "createOutputPane() should attach a component popup menu");
        List<String> labels = menuItemLabels(popup);
        assertTrue(labels.contains("Select All"), "Context menu should contain 'Select All'");
        assertTrue(labels.contains("Copy"), "Context menu should contain 'Copy'");
    }

    @Test
    void createOutputPaneIsNotEditable() {
        JEditorPane pane = MarkdownRenderer.createOutputPane();
        assertFalse(pane.isEditable());
    }

    private static List<String> menuItemLabels(JPopupMenu menu) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < menu.getComponentCount(); i++) {
            Component c = menu.getComponent(i);
            if (c instanceof JMenuItem) labels.add(((JMenuItem) c).getText());
        }
        return labels;
    }
}
