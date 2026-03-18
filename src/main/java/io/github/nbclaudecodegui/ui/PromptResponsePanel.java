package io.github.nbclaudecodegui.ui;

import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A panel that appears when claude asks an interactive question.
 *
 * <p>Displays a question label, option buttons (for numbered menus),
 * a free-form text field (only when there are no parsed options),
 * and a Cancel button pinned to the right.
 */
public final class PromptResponsePanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(PromptResponsePanel.class.getName());

    /** A single selectable option in an interactive prompt. */
    public record Option(String display, String response) {}

    /** Represents an interactive question from claude. */
    public record PromptRequest(String text, List<Option> options) {}

    private Consumer<String> callback;

    public PromptResponsePanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, java.awt.Color.LIGHT_GRAY),
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

        // Question label
        String questionText = req.text();
        LOG.info("[PromptResponsePanel] question: " + questionText);
        if (questionText != null && !questionText.isBlank()) {
            JLabel questionLabel = new JLabel("<html>" + escapeHtml(questionText) + "</html>");
            questionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(questionLabel);
            add(Box.createVerticalStrut(4));
        }

        // Button/input row — Cancel pinned to the right edge via glue
        JPanel actionRow = new JPanel();
        actionRow.setLayout(new BoxLayout(actionRow, BoxLayout.X_AXIS));
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (req.options().isEmpty()) {
            // Free-form input
            JTextField field = new JTextField(30);
            field.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
            field.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                        submitAnswer(field.getText().trim());
                    }
                }
            });

            JButton sendBtn = new JButton("Send");
            sendBtn.addActionListener(e -> submitAnswer(field.getText().trim()));

            actionRow.add(field);
            actionRow.add(Box.createHorizontalStrut(4));
            actionRow.add(sendBtn);
        } else {
            // Numbered option buttons
            for (Option opt : req.options()) {
                String label = opt.display();
                String response = opt.response();
                LOG.info("[PromptResponsePanel] button: \"" + label + "\" → response: \"" + response + "\"");
                JButton btn = new JButton(label);
                btn.addActionListener(e -> {
                    LOG.info("[PromptResponsePanel] button clicked: \"" + label + "\" → \"" + response + "\"");
                    submitAnswer(response);
                });
                actionRow.add(btn);
                actionRow.add(Box.createHorizontalStrut(4));
            }
        }

        // Glue pushes Cancel to the right edge
        actionRow.add(Box.createHorizontalGlue());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> {
            LOG.info("[PromptResponsePanel] Cancel clicked");
            cancel();
        });
        actionRow.add(cancelBtn);

        add(actionRow);

        setVisible(true);
        revalidate();
        repaint();
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
