package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.ui.PermissionPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/**
 * A panel that appears when claude asks an interactive question.
 *
 * <p>Displays a question label, Yes/No colour buttons, radio buttons for other
 * options, a free-form text field (only when there are no parsed options),
 * and a Cancel button pinned to the right.
 */
public final class PromptResponsePanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(PromptResponsePanel.class.getName());

    /** A single selectable option in an interactive prompt. */
    public record Option(String display, String response) {}

    /**
     * Represents an interactive question from claude.
     *
     * @param defaultOptionIndex 0-based index of the default option, or -1 if unknown
     */
    public record PromptRequest(String text, List<Option> options, int defaultOptionIndex) {}

    private Consumer<String> callback;

    public PromptResponsePanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        setVisible(false);
    }

    /**
     * Shows the panel for the given prompt request.
     *
     * @param req      the prompt request
     * @param callback invoked with the chosen response string, or {@code null} on Cancel
     */
    public void show(PromptRequest req, Consumer<String> callback) {
        this.callback = callback;
        removeAll();

        // Split options into Yes/No vs others
        List<Option> yesNoOptions = req.options().stream()
                .filter(o -> o.display().trim().equalsIgnoreCase("Yes")
                          || o.display().trim().equalsIgnoreCase("No"))
                .toList();
        List<Option> otherOptions = req.options().stream()
                .filter(o -> !o.display().trim().equalsIgnoreCase("Yes")
                          && !o.display().trim().equalsIgnoreCase("No"))
                .toList();

        // Track focus targets
        JButton defaultYesNoBtn = null;
        JRadioButton[] radioButtons = new JRadioButton[otherOptions.size()];
        JTextField freeField = null;
        JButton sendBtn = null;

        // --- Question label — full width ---
        String questionText = req.text();
        LOG.info("[PromptResponsePanel] question: " + questionText);
        if (questionText != null && !questionText.isBlank()) {
            JLabel questionLabel = new JLabel("<html>" + escapeHtml(questionText) + "</html>");
            questionLabel.setFont(questionLabel.getFont().deriveFont(questionLabel.getFont().getSize() * 1.5f));
            questionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(questionLabel);
            add(Box.createVerticalStrut(6));
        }

        // --- Left column (Y_AXIS): yes/no row, radios, free-form ---
        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setAlignmentY(Component.TOP_ALIGNMENT);

        // Yes / No buttons
        if (!yesNoOptions.isEmpty()) {
            JPanel yesNoRow = new JPanel();
            yesNoRow.setLayout(new BoxLayout(yesNoRow, BoxLayout.X_AXIS));
            yesNoRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            for (Option opt : yesNoOptions) {
                String display = opt.display().trim();
                String response = opt.response();
                // Prefix Yes/No with the same icons used on Accept/Reject in PermissionPanel:
                // PermissionPanel.ICON_ACCEPT = \u2713 (CHECK MARK)
                // PermissionPanel.ICON_REJECT = \u2717 (BALLOT X)
                String label = display.equalsIgnoreCase("Yes")
                        ? PermissionPanel.ICON_ACCEPT + " " + display
                        : PermissionPanel.ICON_REJECT + " " + display;
                JButton btn = new JButton(label);
                Color baseColor = display.equalsIgnoreCase("Yes")
                        ? new Color(34, 139, 34) : new Color(178, 34, 34);
                Color focusColor = baseColor.brighter();
                btn.setBackground(baseColor);
                btn.setForeground(Color.WHITE);
                btn.setOpaque(true);
                btn.addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override public void focusGained(java.awt.event.FocusEvent e) {
                        btn.setBackground(focusColor);
                    }
                    @Override public void focusLost(java.awt.event.FocusEvent e) {
                        btn.setBackground(baseColor);
                    }
                });
                btn.addActionListener(e -> {
                    LOG.info("[PromptResponsePanel] yes/no clicked: \"" + display + "\" → \"" + response + "\"");
                    submitAnswer(response);
                });
                yesNoRow.add(btn);
                yesNoRow.add(Box.createHorizontalStrut(4));

                int idx = req.options().indexOf(opt);
                if (idx == req.defaultOptionIndex()) {
                    defaultYesNoBtn = btn;
                }
            }
            leftCol.add(yesNoRow);
            leftCol.add(Box.createVerticalStrut(4));
        }

        // Radio buttons for other options (type-input options get an adjacent text field)
        ButtonGroup group = null;
        JTextField[] typeFields = new JTextField[otherOptions.size()];
        if (!otherOptions.isEmpty()) {
            group = new ButtonGroup();
            for (int i = 0; i < otherOptions.size(); i++) {
                Option opt = otherOptions.get(i);
                JRadioButton rb = new JRadioButton(opt.display().trim());
                rb.setAlignmentX(Component.LEFT_ALIGNMENT);
                group.add(rb);
                radioButtons[i] = rb;

                if (isTypeInputOption(opt)) {
                    JTextField tf = new JTextField(20);
                    tf.setEnabled(false);
                    tf.setMaximumSize(new java.awt.Dimension(
                            Integer.MAX_VALUE, tf.getPreferredSize().height));
                    rb.addItemListener(e -> tf.setEnabled(rb.isSelected()));
                    typeFields[i] = tf;

                    JPanel row = new JPanel();
                    row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.add(rb);
                    row.add(Box.createHorizontalStrut(4));
                    row.add(tf);
                    leftCol.add(row);
                } else {
                    leftCol.add(rb);
                }

                int idx = req.options().indexOf(opt);
                if (idx == req.defaultOptionIndex()) {
                    rb.setSelected(true);
                }
            }
            leftCol.add(Box.createVerticalStrut(4));
        }

        // Free-form input (no options at all)
        if (req.options().isEmpty()) {
            freeField = new JTextField(30);
            freeField.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, freeField.getPreferredSize().height));
            freeField.setAlignmentX(Component.LEFT_ALIGNMENT);
            final JTextField ff = freeField;
            freeField.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                        submitAnswer(ff.getText().trim());
                    }
                }
            });
            leftCol.add(freeField);
            leftCol.add(Box.createVerticalStrut(4));
        }

        // --- Right column (Y_AXIS): Cancel, then Send ---
        JPanel rightCol = new JPanel();
        rightCol.setName("rightCol");
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setAlignmentY(Component.TOP_ALIGNMENT);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancelBtn.addActionListener(e -> {
            LOG.info("[PromptResponsePanel] Cancel clicked");
            cancel();
        });
        rightCol.add(cancelBtn);

        boolean hasSend = !otherOptions.isEmpty() || req.options().isEmpty();
        if (hasSend) {
            final JRadioButton[] finalRadios = radioButtons;
            final JTextField[] finalTypeFields = typeFields;
            final JTextField finalField = freeField;
            sendBtn = new JButton("Send");
            sendBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            // Disabled until a radio is selected (for radio-only prompts)
            boolean anySelected = false;
            for (JRadioButton rb : radioButtons) {
                if (rb != null && rb.isSelected()) { anySelected = true; break; }
            }
            if (!otherOptions.isEmpty()) {
                sendBtn.setEnabled(anySelected);
            }
            final JButton finalSend = sendBtn;
            for (JRadioButton rb : radioButtons) {
                if (rb != null) {
                    rb.addItemListener(ev -> {
                        boolean sel = false;
                        for (JRadioButton r : finalRadios) {
                            if (r != null && r.isSelected()) { sel = true; break; }
                        }
                        finalSend.setEnabled(sel);
                    });
                }
            }
            sendBtn.addActionListener(e -> {
                for (int i = 0; i < finalRadios.length; i++) {
                    JRadioButton rb = finalRadios[i];
                    if (rb != null && rb.isSelected()) {
                        Option opt = otherOptions.get(i);
                        if (isTypeInputOption(opt) && finalTypeFields[i] != null) {
                            String typed = finalTypeFields[i].getText().trim();
                            if (!typed.isEmpty()) {
                                submitAnswer(typed);
                            }
                        } else {
                            submitAnswer(opt.response());
                        }
                        return;
                    }
                }
                if (finalField != null) {
                    submitAnswer(finalField.getText().trim());
                }
            });
            rightCol.add(Box.createVerticalStrut(4));
            rightCol.add(sendBtn);
        }

        // Pin rightCol width to its preferred size so it doesn't stretch
        rightCol.setMaximumSize(new java.awt.Dimension(
                rightCol.getPreferredSize().width, Integer.MAX_VALUE));

        // --- Main row: leftCol + strut + rightCol ---
        JPanel mainRow = new JPanel();
        mainRow.setLayout(new BoxLayout(mainRow, BoxLayout.X_AXIS));
        mainRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainRow.add(leftCol);
        mainRow.add(Box.createHorizontalGlue());
        mainRow.add(rightCol);

        add(mainRow);

        // ESC → cancel from anywhere in the panel
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { cancel(); }
        });

        // Enter on radio buttons → click Send
        final JButton finalSendRef = sendBtn;
        for (JRadioButton rb : radioButtons) {
            if (rb != null) {
                rb.getInputMap(JComponent.WHEN_FOCUSED)
                        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendOnEnter");
                rb.getActionMap().put("sendOnEnter", new AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                        if (finalSendRef != null && finalSendRef.isEnabled()) {
                            finalSendRef.doClick();
                        }
                    }
                });
            }
        }

        setVisible(true);
        revalidate();
        repaint();

        // --- Default focus (deferred so the component is fully laid out first) ---
        final java.awt.Component focusTarget;
        if (freeField != null) {
            focusTarget = freeField;
        } else if (defaultYesNoBtn != null) {
            focusTarget = defaultYesNoBtn;
        } else if (sendBtn != null && otherOptions.size() > 0) {
            focusTarget = sendBtn;
        } else {
            focusTarget = null;
        }
        if (focusTarget != null) {
            javax.swing.SwingUtilities.invokeLater(focusTarget::requestFocusInWindow);
        }
    }

    /** Hides the panel if a prompt is still pending (called when Claude accepted input via terminal). */
    public void dismissIfActive() {
        if (isVisible() && callback != null) {
            LOG.info("[PromptResponsePanel] dismissing — Claude accepted terminal input");
            Consumer<String> cb = callback;
            setVisible(false);
            callback = null;
            removeAll();
            revalidate();
            repaint();
            cb.accept(null);
        }
    }

    /** Hides the panel and clears its state. */
    public void dismiss() {
        setVisible(false);
        callback = null;
        removeAll();
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------

    private void submitAnswer(String answer) {
        Consumer<String> cb = callback;
        LOG.info("[PromptResponsePanel] submitAnswer: \"" + answer + "\", cb=" + cb);
        setVisible(false);
        callback = null;
        removeAll();
        revalidate();
        repaint();
        if (cb != null && answer != null && !answer.isBlank()) {
            cb.accept(answer);
        }
    }

    private void cancel() {
        Consumer<String> cb = callback;
        setVisible(false);
        callback = null;
        removeAll();
        revalidate();
        repaint();
        if (cb != null) {
            cb.accept(null);
        }
    }

    /**
     * Returns {@code true} if the option represents a free-text input slot
     * (display text starts with "type", case-insensitive).
     *
     * <p>Claude Code appends a "Type something." entry to AskUserQuestion menus
     * to allow the user to submit arbitrary text instead of a predefined choice.
     */
    private static boolean isTypeInputOption(Option opt) {
        return opt.display().trim().toLowerCase().startsWith("type");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
