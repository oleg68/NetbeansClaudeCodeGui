package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.ui.PromptResponsePanel.Option;
import io.github.nbclaudecodegui.ui.PromptResponsePanel.PromptRequest;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
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
            PromptRequest req = new PromptRequest("Allow? (y/n)",
                    List.of(new Option("y", "y"), new Option("n", "n")));
            panel.show(req, answer -> {});

            assertTrue(panel.isVisible(), "panel should become visible after show()");

            // Buttons are labelled by display text
            List<String> btnLabels = collectButtonLabels(panel);
            assertTrue(btnLabels.contains("y"), "should have 'y' button");
            assertTrue(btnLabels.contains("n"), "should have 'n' button");
        });
    }

    @Test
    void testButtonClickInvokesCallbackWithResponse() throws Exception {
        List<String> received = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> {
            PromptResponsePanel panel = new PromptResponsePanel();
            PromptRequest req = new PromptRequest("Allow?",
                    List.of(new Option("Yes", "1"), new Option("No", "2")));
            panel.show(req, received::add);

            // Button label is the display text; clicking sends response "1"
            JButton yesBtn = findButton(panel, "Yes");
            assertNotNull(yesBtn, "Yes button must exist");
            yesBtn.doClick();
        });

        assertEquals(1, received.size(), "callback must be called exactly once");
        assertEquals("1", received.get(0), "response should be the option response value");
    }

    @Test
    void testHideAfterButtonClick() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PromptResponsePanel panel = new PromptResponsePanel();
            PromptRequest req = new PromptRequest("Go?", List.of(new Option("ok", "1")));
            panel.show(req, answer -> {});

            JButton btn = findButton(panel, "ok");
            assertNotNull(btn);
            btn.doClick();

            assertFalse(panel.isVisible(), "panel should hide after button click");
        });
    }

    @Test
    void testCancelInvokesCallbackWithNull() throws Exception {
        List<String> received = new ArrayList<>();
        List<Boolean> nullReceived = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> {
            PromptResponsePanel panel = new PromptResponsePanel();
            PromptRequest req = new PromptRequest("Q?", List.of(new Option("a", "1")));
            panel.show(req, answer -> {
                if (answer == null) nullReceived.add(true);
                else received.add(answer);
            });

            JButton cancelBtn = findButton(panel, "Cancel");
            assertNotNull(cancelBtn, "Cancel button must exist");
            cancelBtn.doClick();

            assertFalse(panel.isVisible(), "panel should hide after Cancel");
        });

        assertTrue(nullReceived.size() == 1, "Cancel must invoke callback with null");
        assertTrue(received.isEmpty(), "Cancel must not pass a non-null answer");
    }

    @Test
    void testHideMethodHidesPanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PromptResponsePanel panel = new PromptResponsePanel();
            PromptRequest req = new PromptRequest("Q?", List.of(new Option("a", "1")));
            panel.show(req, answer -> {});
            assertTrue(panel.isVisible());

            panel.dismiss();
            assertFalse(panel.isVisible(), "dismiss() should hide the panel");
        });
    }

    @Test
    void testShowWithNoOptionsShowsSendAndCancelButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PromptResponsePanel panel = new PromptResponsePanel();
            PromptRequest req = new PromptRequest("Enter your response:", List.of());
            panel.show(req, answer -> {});
            assertTrue(panel.isVisible());

            List<String> btnLabels = collectButtonLabels(panel);
            assertTrue(btnLabels.contains("Send"), "Send button should be present for free-form input");
            assertTrue(btnLabels.contains("Cancel"), "Cancel button should always be present");
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
