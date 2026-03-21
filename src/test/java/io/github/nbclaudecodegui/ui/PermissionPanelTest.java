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
 * Unit tests for {@link PermissionPanel}.
 */
class PermissionPanelTest {

    /** Finds the first JButton whose text contains the given label (case-insensitive). */
    private static JButton findButton(PermissionPanel panel, String label) {
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

    private static JTextField findTextField(PermissionPanel panel) {
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

    @Test
    void declineButtonLabelNotReject() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PermissionPanel panel = new PermissionPanel(() -> {}, reason -> {}, () -> {});
            assertNull(findButton(panel, "Reject"), "button labelled 'Reject' should not exist");
            assertNotNull(findButton(panel, "Decline"), "button labelled 'Decline' should exist");
        });
    }

    @Test
    void declineWithReasonCallsCallback() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PermissionPanel panel = new PermissionPanel(() -> {}, received::set, () -> {});
            JTextField tf = findTextField(panel);
            assertNotNull(tf);
            tf.setText("wrong file");
            JButton decline = findButton(panel, "Decline");
            assertNotNull(decline);
            decline.doClick();
        });
        assertEquals("wrong file", received.get());
    }

    @Test
    void acceptWithEmptyReasonCallsCallbackDirectly() throws Exception {
        List<String> accepted = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            PermissionPanel panel = new PermissionPanel(() -> accepted.add("ok"), reason -> {}, () -> {});
            // reason field is empty — no warning expected
            JButton accept = findButton(panel, "Accept");
            assertNotNull(accept);
            accept.doClick();
        });
        assertEquals(List.of("ok"), accepted, "onAccept should be called when reason is empty");
    }
}
