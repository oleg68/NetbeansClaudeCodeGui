package io.github.nbclaudecodegui.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FileDiffPermissionPanel}.
 */
class FileDiffPermissionPanelTest {

    /** Finds the first JButton whose text contains the given label (case-insensitive). */
    private static JButton findButton(FileDiffPermissionPanel panel, String label) {
        for (java.awt.Component c : panel.getComponents()) {
            JButton btn = findButtonIn(c, label);
            if (btn != null) return btn;
        }
        return null;
    }

    private static JButton findButtonIn(java.awt.Component root, String label) {
        if (root instanceof JButton btn && btn.getText().toLowerCase().contains(label.toLowerCase())) {
            return btn;
        }
        if (root instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                JButton found = findButtonIn(child, label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JTextField findTextField(FileDiffPermissionPanel panel) {
        return findTextFieldIn(panel);
    }

    private static JTextField findTextFieldIn(java.awt.Component root) {
        if (root instanceof JTextField tf) return tf;
        if (root instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                JTextField found = findTextFieldIn(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void fireEscape(FileDiffPermissionPanel panel) {
        javax.swing.KeyStroke esc = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0);
        Object key = panel.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(esc);
        assertNotNull(key, "Escape key binding should be registered");
        panel.getActionMap().get(key).actionPerformed(
                new java.awt.event.ActionEvent(panel, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void declineButtonLabelNotReject() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FileDiffPermissionPanel panel = new FileDiffPermissionPanel(() -> {}, reason -> {}, () -> {});
            assertNull(findButton(panel, "Reject"), "button labelled 'Reject' should not exist");
            assertNotNull(findButton(panel, "Decline"), "button labelled 'Decline' should exist");
        });
    }

    @Test
    void declineWithReasonCallsCallback() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            FileDiffPermissionPanel panel = new FileDiffPermissionPanel(() -> {}, received::set, () -> {});
            JTextField tf = findTextField(panel);
            assertNotNull(tf);
            tf.setText("wrong file");
            JButton decline = findButton(panel, "Decline");
            assertNotNull(decline);
            decline.doClick();
        });
        assertEquals("wrong file", received.get());
    }

    private static void fireEnter(JTextField tf) {
        for (java.awt.event.ActionListener al : tf.getActionListeners()) {
            al.actionPerformed(new java.awt.event.ActionEvent(tf, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
        }
    }

    @Test
    void enterOnEmptyReasonFieldDoesNothing() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            FileDiffPermissionPanel panel = new FileDiffPermissionPanel(() -> {}, received::set, () -> {});
            JTextField tf = findTextField(panel);
            assertNotNull(tf);
            tf.setText("");
            fireEnter(tf);
        });
        assertNull(received.get(), "onReject should NOT be called when reason field is empty");
    }

    @Test
    void enterOnHintTextDoesNothing() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            FileDiffPermissionPanel panel = new FileDiffPermissionPanel(() -> {}, received::set, () -> {});
            JTextField tf = findTextField(panel);
            assertNotNull(tf);
            tf.setText(FileDiffPermissionPanel.REASON_HINT);
            fireEnter(tf);
        });
        assertNull(received.get(), "onReject should NOT be called when field contains only the hint text");
    }

    @Test
    void enterOnNonEmptyReasonFieldCallsDecline() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            FileDiffPermissionPanel panel = new FileDiffPermissionPanel(() -> {}, received::set, () -> {});
            JTextField tf = findTextField(panel);
            assertNotNull(tf);
            tf.setText("wrong path");
            fireEnter(tf);
        });
        assertEquals("wrong path", received.get(), "onReject should be called with the typed reason");
    }

    @Test
    void acceptWithEmptyReasonCallsCallbackDirectly() throws Exception {
        List<String> accepted = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            FileDiffPermissionPanel panel = new FileDiffPermissionPanel(() -> accepted.add("ok"), reason -> {}, () -> {});
            JButton accept = findButton(panel, "Accept");
            assertNotNull(accept);
            accept.doClick();
        });
        assertEquals(List.of("ok"), accepted, "onAccept should be called when reason is empty");
    }

    @Test
    void escapeOnAcceptButtonTriggersCancelCallback() throws Exception {
        List<String> cancelled = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            FileDiffPermissionPanel panel = new FileDiffPermissionPanel(() -> {}, reason -> {}, () -> cancelled.add("cancel"));
            fireEscape(panel);
        });
        assertEquals(List.of("cancel"), cancelled, "onCancel should be called when Escape is pressed");
    }

    @Test
    void escapeOnReasonFieldTriggersCancelCallback() throws Exception {
        List<String> cancelled = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            FileDiffPermissionPanel panel = new FileDiffPermissionPanel(() -> {}, reason -> {}, () -> cancelled.add("cancel"));
            fireEscape(panel);
        });
        assertEquals(List.of("cancel"), cancelled, "onCancel should be called when Escape is pressed from reason field");
    }

    @Test
    void escapeOnDeclineButtonTriggersCancelCallback() throws Exception {
        List<String> cancelled = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            FileDiffPermissionPanel panel = new FileDiffPermissionPanel(() -> {}, reason -> {}, () -> cancelled.add("cancel"));
            fireEscape(panel);
        });
        assertEquals(List.of("cancel"), cancelled, "onCancel should be called when Escape is pressed from decline button");
    }
}
