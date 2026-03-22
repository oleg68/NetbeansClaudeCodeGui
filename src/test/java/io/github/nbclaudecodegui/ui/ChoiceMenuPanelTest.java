package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.model.ChoiceMenuModel;
import io.github.nbclaudecodegui.model.ChoiceMenuModel.Option;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChoiceMenuPanel}.
 *
 * <p>All Swing operations are performed on the EDT.
 */
class ChoiceMenuPanelTest {

    @Test
    void testInitiallyHidden() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            assertFalse(panel.isVisible(), "panel should be hidden initially");
        });
    }

    @Test
    void testShowWithOptionsCreatesButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Allow?",
                    List.of(new Option("Yes", "1"), new Option("No", "2")), -1);
            panel.show(model, answer -> {});

            assertTrue(panel.isVisible(), "panel should become visible after show()");

            List<String> btnLabels = collectButtonLabels(panel);
            String yesLabel = FileDiffPermissionPanel.ICON_ACCEPT + " Yes";
            String noLabel  = FileDiffPermissionPanel.ICON_DECLINE + " No";
            assertTrue(btnLabels.contains(yesLabel), "should have '" + yesLabel + "' button");
            assertTrue(btnLabels.contains(noLabel),  "should have '" + noLabel + "' button");
        });
    }

    @Test
    void testYesButtonIsGreen() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Q?",
                    List.of(new Option("Yes", "1"), new Option("No", "2")), -1);
            panel.show(model, answer -> {});

            JButton btn = findButton(panel, FileDiffPermissionPanel.ICON_ACCEPT + " Yes");
            assertNotNull(btn, "Yes button must exist");
            assertEquals(new Color(34, 139, 34), btn.getBackground(), "Yes button should be green");
        });
    }

    @Test
    void testNoButtonIsRed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Q?",
                    List.of(new Option("Yes", "1"), new Option("No", "2")), -1);
            panel.show(model, answer -> {});

            JButton btn = findButton(panel, FileDiffPermissionPanel.ICON_DECLINE + " No");
            assertNotNull(btn, "No button must exist");
            assertEquals(new Color(178, 34, 34), btn.getBackground(), "No button should be red");
        });
    }

    @Test
    void testRadioButtonForNonYesNoOption() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Q?",
                    List.of(new Option("Maybe", "3")), -1);
            panel.show(model, answer -> {});

            JRadioButton rb = findRadioButton(panel, "Maybe");
            assertNotNull(rb, "Non-Yes/No option should render as JRadioButton");
        });
    }

    @Test
    void testCancelAlwaysPresent() throws Exception {
        // With Yes/No only
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            panel.show(new ChoiceMenuModel("Q?", List.of(new Option("Yes", "1"), new Option("No", "2")), -1), a -> {});
            assertNotNull(findButton(panel, "Cancel"), "Cancel must be present with Yes/No options");
        });

        // With radio options
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            panel.show(new ChoiceMenuModel("Q?", List.of(new Option("Maybe", "3")), -1), a -> {});
            assertNotNull(findButton(panel, "Cancel"), "Cancel must be present with radio options");
        });

        // Free-form
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            panel.show(new ChoiceMenuModel("Q?", List.of(), -1), a -> {});
            assertNotNull(findButton(panel, "Cancel"), "Cancel must be present for free-form input");
        });
    }

    @Test
    void testSendAbsentWhenOnlyYesNo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            panel.show(new ChoiceMenuModel("Q?", List.of(new Option("Yes", "1"), new Option("No", "2")), -1), a -> {});
            assertNull(findButton(panel, "Send"), "Send button must NOT be present when only Yes/No options");
        });
    }

    @Test
    void testSendPresentWhenOtherOptionsExist() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            panel.show(new ChoiceMenuModel("Q?",
                    List.of(new Option("Yes", "1"), new Option("Maybe", "3")), -1), a -> {});
            assertNotNull(findButton(panel, "Send"), "Send button must be present when other options exist");
        });
    }

    @Test
    void testSendDisabledUntilRadioSelected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            panel.show(new ChoiceMenuModel("Q?", List.of(new Option("Maybe", "3")), -1), a -> {});
            JButton send = findButton(panel, "Send");
            assertNotNull(send, "Send must exist");
            assertFalse(send.isEnabled(), "Send must be disabled when no radio is selected");

            JRadioButton rb = findRadioButton(panel, "Maybe");
            assertNotNull(rb);
            rb.setSelected(true);
            assertTrue(send.isEnabled(), "Send must be enabled after radio selection");
        });
    }

    @Test
    void testSendEnabledWhenDefaultRadioSelected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            panel.show(new ChoiceMenuModel("Q?", List.of(new Option("Maybe", "1")), 0), a -> {});
            JButton send = findButton(panel, "Send");
            assertNotNull(send, "Send must exist");
            assertTrue(send.isEnabled(), "Send must be enabled when default radio is pre-selected");
        });
    }

    @Test
    void testDefaultRadioSelected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            panel.show(new ChoiceMenuModel("Q?", List.of(new Option("Maybe", "1")), 0), a -> {});
            JRadioButton rb = findRadioButton(panel, "Maybe");
            assertNotNull(rb, "JRadioButton for Maybe must exist");
            assertTrue(rb.isSelected(), "Radio button for default option should be selected");
        });
    }

    @Test
    void testButtonClickInvokesCallbackWithResponse() throws Exception {
        List<String> received = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Allow?",
                    List.of(new Option("Yes", "1"), new Option("No", "2")), -1);
            panel.show(model, received::add);

            JButton yesBtn = findButton(panel, FileDiffPermissionPanel.ICON_ACCEPT + " Yes");
            assertNotNull(yesBtn, "Yes button must exist");
            yesBtn.doClick();
        });

        assertEquals(1, received.size(), "callback must be called exactly once");
        assertEquals("1", received.get(0), "response should be the option response value");
    }

    @Test
    void testHideAfterButtonClick() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Go?", List.of(new Option("Yes", "1")), -1);
            panel.show(model, answer -> {});

            JButton btn = findButton(panel, FileDiffPermissionPanel.ICON_ACCEPT + " Yes");
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
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Q?", List.of(new Option("Yes", "1")), -1);
            panel.show(model, answer -> {
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
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Q?", List.of(new Option("Yes", "1")), -1);
            panel.show(model, answer -> {});
            assertTrue(panel.isVisible());

            panel.dismiss();
            assertFalse(panel.isVisible(), "dismiss() should hide the panel");
        });
    }

    @Test
    void testShowWithNoOptionsShowsSendAndCancelButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Enter your response:", List.of(), -1);
            panel.show(model, answer -> {});
            assertTrue(panel.isVisible());

            List<String> btnLabels = collectButtonLabels(panel);
            assertTrue(btnLabels.contains("Send"), "Send button should be present for free-form input");
            assertTrue(btnLabels.contains("Cancel"), "Cancel button should always be present");
        });
    }

    @Test
    void testCancelAndSendAreInRightCol() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            panel.show(new ChoiceMenuModel("Q?", List.of(new Option("Maybe", "3")), -1), a -> {});

            JPanel rightCol = findPanelByName(panel, "rightCol");
            assertNotNull(rightCol, "rightCol panel must exist");
            assertNotNull(findButton(rightCol, "Cancel"), "Cancel must be in rightCol");
            assertNotNull(findButton(rightCol, "Send"), "Send must be in rightCol");
        });
    }

    @Test
    void testCancelInRightColWhenOnlyYesNo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            panel.show(new ChoiceMenuModel("Q?", List.of(new Option("Yes", "1"), new Option("No", "2")), -1), a -> {});

            JPanel rightCol = findPanelByName(panel, "rightCol");
            assertNotNull(rightCol, "rightCol panel must exist");
            assertNotNull(findButton(rightCol, "Cancel"), "Cancel must be in rightCol");
            assertNull(findButton(rightCol, "Send"), "Send must NOT be in rightCol for Yes/No only");
        });
    }

    /**
     * Regression: PTY spinner lines arrived while the panel was visible and the old
     * ClaudeSessionPanel listener called dismissIfActive() on every such line.
     */
    @Test
    void dismissIfActiveFiresNullCallbackImmediately() throws Exception {
        List<String> received = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Do you want to proceed?",
                    List.of(new Option(" Yes", "1"), new Option(" No", "3")), 0);
            panel.show(model, answer -> received.add(String.valueOf(answer)));

            assertTrue(panel.isVisible(), "panel must be visible after show()");
            assertTrue(received.isEmpty(), "callback must not fire before user action");

            panel.dismissIfActive();

            assertFalse(panel.isVisible(), "dismissIfActive() hides the panel");
            assertEquals(1, received.size(), "dismissIfActive() fires callback once");
            assertEquals("null", received.get(0),
                    "dismissIfActive() fires callback with null (no user selection)");
        });
    }

    // -------------------------------------------------------------------------
    // Type-input option tests
    // -------------------------------------------------------------------------

    @Test
    void testTypeInputOptionRendersTextField() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Choose:",
                    List.of(new Option("Option A", "1"),
                            new Option("Type something.", "2")), 0);
            panel.show(model, answer -> {});

            JTextField tf = findTextField(panel);
            assertNotNull(tf, "panel must contain a JTextField for 'Type something.' option");
        });
    }

    @Test
    void testTypeInputFieldDisabledByDefault() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Choose:",
                    List.of(new Option("Option A", "1"),
                            new Option("Type something.", "2")), 0);
            panel.show(model, answer -> {});

            JTextField tf = findTextField(panel);
            assertNotNull(tf);
            assertFalse(tf.isEnabled(),
                    "type-input text field must be disabled until its radio button is selected");
        });
    }

    @Test
    void testTypeInputFieldEnabledWhenRadioSelected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Choose:",
                    List.of(new Option("Option A", "1"),
                            new Option("Type something.", "2")), 0);
            panel.show(model, answer -> {});

            JRadioButton typeRb = findRadioButton(panel, "Type something.");
            assertNotNull(typeRb);
            typeRb.setSelected(true);

            JTextField tf = findTextField(panel);
            assertNotNull(tf);
            assertTrue(tf.isEnabled(),
                    "type-input text field must be enabled when its radio button is selected");
        });
    }

    @Test
    void testTypeInputOptionSendsTypedText() throws Exception {
        List<String> captured = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            ChoiceMenuPanel panel = new ChoiceMenuPanel();
            ChoiceMenuModel model = new ChoiceMenuModel("Choose:",
                    List.of(new Option("Option A", "1"),
                            new Option("Type something.", "2")), 0);
            panel.show(model, captured::add);

            JRadioButton typeRb = findRadioButton(panel, "Type something.");
            assertNotNull(typeRb);
            typeRb.setSelected(true);

            JTextField tf = findTextField(panel);
            assertNotNull(tf);
            tf.setText("my custom text");

            // Click Send
            JButton sendBtn = null;
            for (Component c : panel.getComponents()) {
                if (c instanceof JPanel p) {
                    for (Component inner : p.getComponents()) {
                        if (inner instanceof JPanel right && "rightCol".equals(right.getName())) {
                            for (Component btn : right.getComponents()) {
                                if (btn instanceof JButton b && "Send".equals(b.getText())) {
                                    sendBtn = b;
                                }
                            }
                        }
                    }
                }
            }
            assertNotNull(sendBtn, "Send button must exist");
            sendBtn.doClick();
        });

        assertEquals(1, captured.size(), "callback must be called once");
        assertEquals("my custom text", captured.get(0),
                "callback must receive typed text, not option number");
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

    private static JPanel findPanelByName(java.awt.Container container, String name) {
        for (Component c : container.getComponents()) {
            if (c instanceof JPanel p && name.equals(p.getName())) {
                return p;
            } else if (c instanceof java.awt.Container sub) {
                JPanel found = findPanelByName(sub, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JRadioButton findRadioButton(java.awt.Container container, String label) {
        for (Component c : container.getComponents()) {
            if (c instanceof JRadioButton rb && label.equals(rb.getText())) {
                return rb;
            } else if (c instanceof java.awt.Container sub) {
                JRadioButton found = findRadioButton(sub, label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JTextField findTextField(java.awt.Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JTextField tf) return tf;
            if (c instanceof java.awt.Container sub) {
                JTextField found = findTextField(sub);
                if (found != null) return found;
            }
        }
        return null;
    }
}
