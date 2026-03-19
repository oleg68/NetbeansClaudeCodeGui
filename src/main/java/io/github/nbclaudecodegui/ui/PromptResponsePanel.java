package io.github.nbclaudecodegui.ui;

import java.awt.Color;
import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

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
                JButton btn = new JButton(display);
                if (display.equalsIgnoreCase("Yes")) {
                    btn.setBackground(new Color(34, 139, 34));
                    btn.setForeground(Color.WHITE);
                } else {
                    btn.setBackground(new Color(178, 34, 34));
                    btn.setForeground(Color.WHITE);
                }
                btn.setOpaque(true);
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

        // Radio buttons for other options
        ButtonGroup group = null;
        if (!otherOptions.isEmpty()) {
            group = new ButtonGroup();
            for (int i = 0; i < otherOptions.size(); i++) {
                Option opt = otherOptions.get(i);
                JRadioButton rb = new JRadioButton(opt.display().trim());
                rb.setAlignmentX(Component.LEFT_ALIGNMENT);
                group.add(rb);
                leftCol.add(rb);
                radioButtons[i] = rb;

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
                for (JRadioButton rb : finalRadios) {
                    if (rb != null && rb.isSelected()) {
                        String text = rb.getText();
                        for (Option opt : otherOptions) {
                            if (opt.display().trim().equals(text)) {
                                submitAnswer(opt.response());
                                return;
                            }
                        }
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

        setVisible(true);
        revalidate();
        repaint();

        // --- Default focus ---
        if (freeField != null) {
            freeField.requestFocusInWindow();
        } else if (defaultYesNoBtn != null) {
            defaultYesNoBtn.requestFocusInWindow();
        } else if (sendBtn != null && otherOptions.size() > 0) {
            sendBtn.requestFocusInWindow();
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

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
