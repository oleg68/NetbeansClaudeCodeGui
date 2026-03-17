package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.process.ClaudeProcess.PromptRequest;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PromptResponsePanel}.
 *
 * <p>All Swing operations are performed on the EDT.
 */
class PromptResponsePanelTest {

    @Test
    void testInitiallyHidden() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PromptResponsePanel panel = new PromptResponsePanel();
            assertFalse(panel.isVisible(), "panel should be hidden initially");
        });
    }

    @Test
    void testShowWithOptionsCreatesButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PromptResponsePanel panel = new PromptResponsePanel();
            PromptRequest req = new PromptRequest("Allow? (y/n)", List.of("y", "n"));
            panel.show(req, answer -> {});

            assertTrue(panel.isVisible(), "panel should become visible after show()");

            // Count buttons matching option labels
            List<String> btnLabels = collectButtonLabels(panel);
            assertTrue(btnLabels.contains("y"), "should have 'y' button");
            assertTrue(btnLabels.contains("n"), "should have 'n' button");
        });
    }

    @Test
    void testButtonClickInvokesCallback() throws Exception {
        List<String> received = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> {
            PromptResponsePanel panel = new PromptResponsePanel();
            PromptRequest req = new PromptRequest("Allow?", List.of("yes", "no"));
            panel.show(req, received::add);

            // Find and click the "yes" button
            JButton yesBtn = findButton(panel, "yes");
            assertNotNull(yesBtn, "yes button must exist");
            yesBtn.doClick();
        });

        assertEquals(1, received.size(), "callback must be called exactly once");
        assertEquals("yes", received.get(0));
    }

    @Test
    void testHideAfterButtonClick() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PromptResponsePanel panel = new PromptResponsePanel();
            PromptRequest req = new PromptRequest("Go?", List.of("ok"));
            panel.show(req, answer -> {});

            JButton btn = findButton(panel, "ok");
            assertNotNull(btn);
            btn.doClick();

            assertFalse(panel.isVisible(), "panel should hide after button click");
        });
    }

    @Test
    void testHideMethodHidesPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PromptResponsePanel panel = new PromptResponsePanel();
            PromptRequest req = new PromptRequest("Q?", List.of("a"));
            panel.show(req, answer -> {});
            assertTrue(panel.isVisible());

            panel.dismiss();
            assertFalse(panel.isVisible(), "hide() should hide the panel");
        });
    }

    @Test
    void testShowWithNoOptionsShowsNoOptionButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PromptResponsePanel panel = new PromptResponsePanel();
            PromptRequest req = new PromptRequest("Enter your response:", List.of());
            panel.show(req, answer -> {});
            assertTrue(panel.isVisible());

            // There should be no option buttons (only the fixed Send button)
            List<String> btnLabels = collectButtonLabels(panel);
            // "Send" is always present, no option buttons
            assertEquals(List.of("Send"), btnLabels,
                    "with empty options, only Send button should be present");
        });
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static List<String> collectButtonLabels(java.awt.Container container) {
        List<String> labels = new ArrayList<>();
        for (Component c : container.getComponents()) {
            if (c instanceof JButton btn) {
                labels.add(btn.getText());
            } else if (c instanceof java.awt.Container sub) {
                labels.addAll(collectButtonLabels(sub));
            }
        }
        return labels;
    }

    private static JButton findButton(java.awt.Container container, String label) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton btn && label.equals(btn.getText())) {
                return btn;
            } else if (c instanceof java.awt.Container sub) {
                JButton found = findButton(sub, label);
                if (found != null) return found;
            }
        }
        return null;
    }
}
