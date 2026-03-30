package io.github.nbclaudecodegui.ui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
        assertDoesNotThrow(() -> panel.attachRawDiffSync(new JPanel()));
    }

    @Test
    void editorPanesHaveSelectAllAndCopyContextMenu() {
        MarkdownDiffPanel panel = new MarkdownDiffPanel("# Before", "# After");
        List<JEditorPane> panes = new ArrayList<>();
        collectEditorPanes(panel, panes);
        assertEquals(2, panes.size(), "Expected two JEditorPane instances");
        for (JEditorPane pane : panes) {
            JPopupMenu popup = pane.getComponentPopupMenu();
            assertNotNull(popup, "JEditorPane should have a component popup menu");
            List<String> labels = menuItemLabels(popup);
            assertTrue(labels.contains("Select All"), "Context menu should contain 'Select All'");
            assertTrue(labels.contains("Copy"), "Context menu should contain 'Copy'");
        }
    }

    private static List<String> menuItemLabels(JPopupMenu menu) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < menu.getComponentCount(); i++) {
            Component c = menu.getComponent(i);
            if (c instanceof JMenuItem) labels.add(((JMenuItem) c).getText());
        }
        return labels;
    }

    private static void collectEditorPanes(Component c, List<JEditorPane> result) {
        if (c instanceof JEditorPane) result.add((JEditorPane) c);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collectEditorPanes(child, result);
            }
        }
    }
}
