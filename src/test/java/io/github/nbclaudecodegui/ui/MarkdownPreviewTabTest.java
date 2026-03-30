package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.ui.common.MarkdownRenderer;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that panes created for MarkdownPreviewTab (via createOutputPane())
 * have a context menu with Select All and Copy, and that live-update logic works.
 */
class MarkdownPreviewTabTest {

    @BeforeEach
    void clearOpenTabs() {
        MarkdownPreviewTab.clearOpenTabsForTest();
    }

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

    @Test
    void updateContentChangesPaneText() throws Exception {
        MarkdownPreviewTab tab = new MarkdownPreviewTab();
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setText("<html>old</html>");
        tab.setPaneForTest(pane);

        tab.updateContent("# New content");

        // updateContent schedules on EDT — flush it
        SwingUtilities.invokeAndWait(() -> {});

        String text = pane.getText();
        assertFalse(text.contains("old"), "Pane should no longer contain old content after updateContent");
    }

    @Test
    void componentClosedRemovesFromOpenTabs() {
        MarkdownPreviewTab tab = new MarkdownPreviewTab();
        tab.setFilePathForTest("/some/file.md");
        MarkdownPreviewTab.getOpenTabsForTest().put("/some/file.md", tab);

        tab.componentClosedForTest();

        assertFalse(MarkdownPreviewTab.getOpenTabsForTest().containsKey("/some/file.md"),
                "OPEN_TABS should not contain the tab after componentClosed");
    }

    @Test
    void openTabsInitiallyEmptyAfterClear() {
        assertTrue(MarkdownPreviewTab.getOpenTabsForTest().isEmpty());
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
