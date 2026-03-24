package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

/**
 * Pure-UI input panel for a Claude Code session.
 *
 * <p>Renders a multi-line text area, a Send button, and a Cancel button.
 * Contains no references to {@link io.github.nbclaudecodegui.model.ClaudeSessionModel}
 * or {@link io.github.nbclaudecodegui.controller.ClaudeSessionController}; all
 * behaviour is driven through constructor callbacks:
 * <ul>
 *   <li>{@code onSend} — fired with the trimmed input text when the user sends</li>
 *   <li>{@code onCancel} — fired when the user presses Cancel or Esc</li>
 *   <li>{@code promptHistorySupplier} — queried on demand for Ctrl+Up/Down history navigation</li>
 * </ul>
 *
 * <p>The send key (Enter / Ctrl+Enter / Shift+Enter / Alt+Enter) is read from
 * {@link ClaudeCodePreferences#getSendKey()} at the time of each key event.
 */
public final class ClaudePromptPanel extends JPanel {

    private static final String ICON_SEND   = "\u25b6";  // ▶
    private static final String ICON_CANCEL = "\u2716";  // ✖

    private final JTextArea inputArea;
    private final JButton   sendButton;
    private final JButton   cancelButton;

    private final Consumer<String>      onSend;
    private final Runnable              onCancel;
    private final Runnable              onShiftTab;
    private final Supplier<List<String>> promptHistorySupplier;

    /** Current position in the history list; {@code -1} = newest (empty field). */
    private int historyIndex = -1;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates the input panel.
     *
     * @param onSend                 called with the text when the user sends a prompt;
     *                               the text has already been cleared from the field
     * @param onCancel               called when the user presses Cancel or Esc
     * @param onShiftTab             called when Shift+Tab is pressed on any control in
     *                               the panel; used to cycle the Claude Code edit mode
     * @param promptHistorySupplier  returns the current in-session prompt history
     *                               (index 0 = most recent); queried on demand
     */
    public ClaudePromptPanel(Consumer<String> onSend,
                             Runnable onCancel,
                             Runnable onShiftTab,
                             Supplier<List<String>> promptHistorySupplier) {
        super(new BorderLayout());
        this.onSend                = onSend;
        this.onCancel              = onCancel;
        this.onShiftTab            = onShiftTab;
        this.promptHistorySupplier = promptHistorySupplier;

        inputArea = new JTextArea(3, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFocusTraversalKeysEnabled(false);
        bindKeys(inputArea);
        attachContextMenu(inputArea);

        sendButton   = new JButton("<html><font color='#228B22'>" + ICON_SEND   + "</font> Send</html>");
        cancelButton = new JButton("<html><font color='#B22222'>" + ICON_CANCEL + "</font> Cancel</html>");
        sendButton.setEnabled(false);
        cancelButton.setEnabled(false);
        sendButton.addActionListener(e -> doSend());
        cancelButton.addActionListener(e -> onCancel.run());

        JPanel buttonCol = new JPanel();
        buttonCol.setLayout(new BoxLayout(buttonCol, BoxLayout.Y_AXIS));
        sendButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonCol.add(sendButton);
        buttonCol.add(Box.createRigidArea(new Dimension(0, 4)));
        buttonCol.add(cancelButton);
        buttonCol.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        add(new JScrollPane(inputArea), BorderLayout.CENTER);
        add(buttonCol, BorderLayout.EAST);

        // Shift+Tab on buttons (and any other non-textarea child) → cycle edit mode.
        // The inputArea handles its own Shift+Tab in bindKeys because
        // setFocusTraversalKeysEnabled(false) prevents the InputMap from firing.
        KeyStroke shiftTab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(shiftTab, "shiftTab");
        getActionMap().put("shiftTab", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                onShiftTab.run();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Synchronises button state with the session lifecycle.
     *
     * @param ready {@code true} when Claude is idle and ready to accept input
     *              (Send enabled, Cancel disabled); {@code false} when working
     *              (Send disabled, Cancel enabled)
     */
    public void setReadyState(boolean ready) {
        sendButton.setEnabled(ready);
        cancelButton.setEnabled(!ready);
    }

    /**
     * Requests keyboard focus on the text input area.
     */
    public void requestFocusOnInputArea() {
        inputArea.requestFocusInWindow();
    }

    /**
     * Clears the input area and resets history navigation.
     * Called by the owning tab when the session stops.
     */
    public void reset() {
        inputArea.setText("");
        historyIndex = -1;
    }

    // -------------------------------------------------------------------------
    // Key bindings
    // -------------------------------------------------------------------------

    private void bindKeys(JTextArea area) {
        area.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Shift+Tab → cycle edit mode
                if (e.getKeyCode() == KeyEvent.VK_TAB && e.isShiftDown()) {
                    e.consume();
                    onShiftTab.run();
                    return;
                }
                // Esc → Cancel
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    if (cancelButton.isEnabled()) onCancel.run();
                    return;
                }
                // Ctrl+Up — navigate history backward (older)
                if (e.getKeyCode() == KeyEvent.VK_UP && e.isControlDown()
                        && !e.isShiftDown() && !e.isAltDown()) {
                    e.consume();
                    navigateHistory(1);
                    return;
                }
                // Ctrl+Down — navigate history forward (newer)
                if (e.getKeyCode() == KeyEvent.VK_DOWN && e.isControlDown()
                        && !e.isShiftDown() && !e.isAltDown()) {
                    e.consume();
                    navigateHistory(-1);
                    return;
                }

                String sendKey = ClaudeCodePreferences.getSendKey();
                boolean ctrl  = e.isControlDown();
                boolean shift = e.isShiftDown();
                boolean alt   = e.isAltDown();
                boolean plain = !ctrl && !shift && !alt;
                if (e.getKeyCode() != KeyEvent.VK_ENTER) return;

                boolean match = switch (sendKey) {
                    case ClaudeCodePreferences.ENTER       -> plain;
                    case ClaudeCodePreferences.CTRL_ENTER  -> ctrl && !shift && !alt;
                    case ClaudeCodePreferences.SHIFT_ENTER -> shift && !ctrl && !alt;
                    case ClaudeCodePreferences.ALT_ENTER   -> alt && !ctrl && !shift;
                    default -> false;
                };
                if (match) {
                    e.consume();
                    doSend();
                }
            }
        });
    }

    private void doSend() {
        String text = inputArea.getText();
        if (text.isEmpty()) return;
        inputArea.setText("");
        inputArea.requestFocusInWindow();
        historyIndex = -1;
        onSend.accept(text);
    }

    /** delta=+1 → older, delta=-1 → newer. */
    private void navigateHistory(int delta) {
        List<String> history = promptHistorySupplier.get();
        if (history.isEmpty()) return;
        historyIndex = Math.max(-1, Math.min(history.size() - 1, historyIndex + delta));
        if (historyIndex < 0) {
            inputArea.setText("");
        } else {
            inputArea.setText(history.get(historyIndex));
            inputArea.setCaretPosition(inputArea.getDocument().getLength());
        }
    }

    // -------------------------------------------------------------------------
    // Context menu
    // -------------------------------------------------------------------------

    private void attachContextMenu(JTextArea area) {
        JPopupMenu menu = TextContextMenu.create(area);

        JMenuItem prevPrompt = new JMenuItem("Previous prompt  (Ctrl+\u2191)");
        prevPrompt.addActionListener(e -> navigateHistory(1));

        JMenuItem nextPrompt = new JMenuItem("Next prompt  (Ctrl+\u2193)");
        nextPrompt.addActionListener(e -> navigateHistory(-1));

        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                List<String> h = promptHistorySupplier.get();
                prevPrompt.setEnabled(!h.isEmpty() && historyIndex < h.size() - 1);
                nextPrompt.setEnabled(historyIndex > -1);
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });

        menu.addSeparator();
        menu.add(prevPrompt);
        menu.add(nextPrompt);

        TextContextMenu.attach(area, menu);
    }
}
