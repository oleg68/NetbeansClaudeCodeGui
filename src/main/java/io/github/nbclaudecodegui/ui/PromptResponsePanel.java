package io.github.nbclaudecodegui.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A panel that appears when claude asks an interactive question.
 *
 * <p>Displays option buttons (if the prompt has parsed options) and always
 * includes a text field for a free-form answer. Clicking an option button or
 * pressing Enter in the text field invokes the callback and hides the panel.
 *
 * <p>Usage:
 * <pre>
 *   claudeProcess.setPromptConsumer(req -&gt; SwingUtilities.invokeLater(() -&gt;
 *       promptResponsePanel.show(req, answer -&gt; claudeProcess.sendResponse(answer))
 *   ));
 * </pre>
 */
public final class PromptResponsePanel extends JPanel {

    /** Represents an interactive question from claude (kept for hybrid mode). */
    public record PromptRequest(String text, java.util.List<String> options) {}


    private final JPanel  buttonRow;
    private final JTextField answerField;
    private final JButton    sendButton;

    private Consumer<String> callback;

    public PromptResponsePanel() {
        super(new BorderLayout(4, 4));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        java.awt.Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        // Label
        JLabel label = new JLabel("Answer:");

        // Button row for option buttons
        buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonRow.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.setOpaque(false);
        top.add(label, BorderLayout.WEST);
        top.add(buttonRow, BorderLayout.CENTER);

        // Free-form text field + Send
        answerField = new JTextField(30);
        answerField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    submitAnswer(answerField.getText());
                }
            }
        });

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> submitAnswer(answerField.getText()));

        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.setOpaque(false);
        inputRow.add(answerField, BorderLayout.CENTER);
        inputRow.add(sendButton, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(inputRow, BorderLayout.CENTER);

        setVisible(false);
    }

    /**
     * Shows the panel with buttons for the given prompt request.
     *
     * @param req      the prompt request with options
     * @param callback invoked with the chosen answer; panel hides after invocation
     */
    public void show(PromptRequest req, Consumer<String> callback) {
        this.callback = callback;

        buttonRow.removeAll();
        for (String option : req.options()) {
            JButton btn = new JButton(option);
            btn.addActionListener(e -> submitAnswer(option));
            buttonRow.add(btn);
        }

        answerField.setText("");
        setVisible(true);
        revalidate();
        repaint();
        answerField.requestFocusInWindow();
    }

    /** Hides the panel and clears its state. */
    public void dismiss() {
        setVisible(false);
        callback = null;
        buttonRow.removeAll();
        answerField.setText("");
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------

    private void submitAnswer(String answer) {
        Consumer<String> cb = callback;
        dismiss();
        if (cb != null && !answer.isBlank()) {
            cb.accept(answer.trim());
        }
    }
}
