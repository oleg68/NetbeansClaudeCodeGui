package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.model.AttachedFilesModel;
import io.github.nbclaudecodegui.model.FavoriteEntry;
import io.github.nbclaudecodegui.model.PromptFavoritesStore;
import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

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
 *
 * <p>Stage 15: file attachments are prepended as {@code @/absolute/path\n} lines
 * before the prompt text when sent to Claude.
 */
public final class ClaudePromptPanel extends JPanel {

    private static final String ICON_SEND      = "\u25b6";  // ▶
    private static final String ICON_CANCEL    = "\u2716";  // ✖
    private static final String ICON_FAVORITES = "\u2605";  // ★
    private static final String ICON_HISTORY   = "\u2630";  // ☰
    private final JTextArea inputArea;
    private final JButton   sendButton;
    private final JButton   cancelButton;

    private final Consumer<String>       onSend;
    private final Runnable               onCancel;
    private final Runnable               onShiftTab;
    private final Supplier<List<String>> promptHistorySupplier;
    private final Supplier<String>       workingDirSupplier;

    /** Current position in the history list; {@code -1} = newest (empty field). */
    private int historyIndex = -1;

    /** Shortcut matcher; lazily initialised once a working directory is available. */
    private ShortcutMatcher shortcutMatcher;

    // Stage 15 — file attachment components
    private final AttachedFilesModel  attachedModel;
    private final AttachedFilesPanel  attachedFilesPanel;
    private final FileDropHandler     dropHandler;
    @SuppressWarnings("FieldCanBeLocal")
    private final AtPathHighlighter   pathHighlighter;
    @SuppressWarnings("FieldCanBeLocal")
    private final AtCompletionPopup   completionPopup;

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
     * @param workingDirSupplier     returns the current working directory path; used for
     *                               the history/favorites popup and file completion
     */
    public ClaudePromptPanel(Consumer<String> onSend,
                             Runnable onCancel,
                             Runnable onShiftTab,
                             Supplier<List<String>> promptHistorySupplier,
                             Supplier<String> workingDirSupplier) {
        super(new BorderLayout());
        this.onSend                = onSend;
        this.onCancel              = onCancel;
        this.onShiftTab            = onShiftTab;
        this.promptHistorySupplier = promptHistorySupplier;
        this.workingDirSupplier    = workingDirSupplier;

        // --- Stage 15: attachment model ---
        attachedModel      = new AttachedFilesModel();
        attachedFilesPanel = new AttachedFilesPanel(attachedModel, () -> { /* chip removed callback */ });

        inputArea = new JTextArea(3, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFocusTraversalKeysEnabled(false);
        bindKeys(inputArea);
        attachContextMenu(inputArea);

        // Stage 15: highlighter and completion popup
        pathHighlighter  = AtPathHighlighter.install(inputArea);
        completionPopup  = AtCompletionPopup.install(inputArea, workingDirSupplier);

        // Stage 15: drop handler (DnD + clipboard)
        dropHandler = new FileDropHandler(attachedModel, attachedFilesPanel, workingDirSupplier);
        inputArea.setTransferHandler(dropHandler);

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

        JButton historyButton   = new JButton("<html>" + ICON_HISTORY + " History</html>");
        JButton favoritesButton = new JButton("<html><font color='#CC6600'>" + ICON_FAVORITES + "</font> Favorites</html>");
        historyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        favoritesButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        historyButton.setToolTipText("Browse prompt history");
        favoritesButton.setToolTipText("Browse favorites");

        historyButton.addActionListener(e -> showHistoryDialog(historyButton));
        favoritesButton.addActionListener(e -> showFavoritesDialog(favoritesButton));

        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.add(favoritesButton);
        leftCol.add(Box.createRigidArea(new Dimension(0, 4)));
        leftCol.add(historyButton);
        leftCol.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Equalize all button widths and heights.
        // Done after the component is shown so HTML-rendered labels are measured correctly.
        JButton[] allButtons = {sendButton, cancelButton, historyButton, favoritesButton};
        addAncestorListener(new javax.swing.event.AncestorListener() {
            @Override public void ancestorAdded(javax.swing.event.AncestorEvent e) {
                equalizeButtons(allButtons);
                revalidate();
                removeAncestorListener(this);
            }
            @Override public void ancestorRemoved(javax.swing.event.AncestorEvent e) {}
            @Override public void ancestorMoved(javax.swing.event.AncestorEvent e) {}
        });

        // Center column: chips panel above textarea
        JPanel centerCol = new JPanel(new BorderLayout());
        centerCol.add(attachedFilesPanel, BorderLayout.NORTH);
        centerCol.add(new JScrollPane(inputArea), BorderLayout.CENTER);

        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        add(centerCol,   BorderLayout.CENTER);
        add(buttonCol,   BorderLayout.EAST);
        add(leftCol,     BorderLayout.WEST);

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
     * Also clears file attachments and cleans up temp files.
     * Called by the owning tab when the session stops.
     */
    public void reset() {
        inputArea.setText("");
        historyIndex = -1;
        attachedModel.clearFiles();
        attachedModel.cleanup();
        attachedFilesPanel.refresh();
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

                // Ctrl+V — intercept for file/image/text paste
                if (e.getKeyCode() == KeyEvent.VK_V && e.isControlDown()
                        && !e.isShiftDown() && !e.isAltDown()) {
                    e.consume();
                    if (!dropHandler.importFromClipboard(area)) {
                        area.paste();
                    }
                    return;
                }

                // ShortcutMatcher — try to match favorites shortcuts
                ShortcutMatcher sm = getOrCreateShortcutMatcher();
                if (sm != null && sm.keyPressed(e)) {
                    e.consume();
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

    private ShortcutMatcher getOrCreateShortcutMatcher() {
        if (workingDirSupplier == null) return null;
        String wd = workingDirSupplier.get();
        if (wd == null) return null;
        if (shortcutMatcher == null) {
            shortcutMatcher = new ShortcutMatcher(inputArea,
                    PromptFavoritesStore.getInstance(Path.of(wd)));
        }
        return shortcutMatcher;
    }

    private void doSend() {
        String text = inputArea.getText();
        List<File> files = attachedModel.getFiles();
        if (text.isEmpty() && files.isEmpty()) return;

        // Build prompt: attached file chips first as @/absolute/path, then user text
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            sb.append("@").append(f.getAbsolutePath()).append("\n");
        }
        sb.append(text);

        // Clean up attachments before sending
        attachedModel.clearFiles();
        attachedModel.cleanup();
        attachedFilesPanel.refresh();

        inputArea.setText("");
        inputArea.requestFocusInWindow();
        historyIndex = -1;
        onSend.accept(sb.toString());
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

        JMenuItem addToFav = new JMenuItem("Add to Favorites");
        addToFav.addActionListener(e -> {
            String text = area.getText().trim();
            if (!text.isEmpty() && workingDirSupplier != null) {
                String wd = workingDirSupplier.get();
                if (wd != null) {
                    PromptFavoritesStore.getInstance(Path.of(wd))
                            .addProject(FavoriteEntry.ofProject(text));
                }
            }
        });

        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                List<String> h = promptHistorySupplier.get();
                prevPrompt.setEnabled(!h.isEmpty() && historyIndex < h.size() - 1);
                nextPrompt.setEnabled(historyIndex > -1);
                addToFav.setEnabled(!area.getText().trim().isEmpty());
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });

        menu.addSeparator();
        menu.add(prevPrompt);
        menu.add(nextPrompt);
        menu.addSeparator();
        menu.add(addToFav);

        TextContextMenu.attach(area, menu);
    }

    // -------------------------------------------------------------------------
    // History / Favorites dialogs
    // -------------------------------------------------------------------------

    private void showHistoryDialog(JButton anchor) {
        String wd = workingDirSupplier != null ? workingDirSupplier.get() : null;
        if (wd == null) return;
        Window owner = SwingUtilities.getWindowAncestor(anchor);
        HistoryDialog dlg = new HistoryDialog(owner, Path.of(wd), text -> inputArea.setText(text));
        dlg.setVisible(true);
    }

    private void showFavoritesDialog(JButton anchor) {
        String wd = workingDirSupplier != null ? workingDirSupplier.get() : null;
        if (wd == null) return;
        Window owner = SwingUtilities.getWindowAncestor(anchor);
        FavoritesDialog dlg = new FavoritesDialog(owner, Path.of(wd), text -> inputArea.setText(text));
        dlg.setVisible(true);
    }

    // -------------------------------------------------------------------------
    // File chooser (Attach button)
    // -------------------------------------------------------------------------

    private void showFileChooser(JButton anchor) {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Attach files to prompt");
        Window owner = SwingUtilities.getWindowAncestor(anchor);
        int result = chooser.showOpenDialog(owner);
        if (result == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                // Check if inside workingDir
                String wdStr = workingDirSupplier != null ? workingDirSupplier.get() : null;
                if (wdStr != null) {
                    Path workDir = Path.of(wdStr).normalize();
                    Path filePath = f.toPath().toAbsolutePath().normalize();
                    if (filePath.startsWith(workDir)) {
                        // Inside: insert relative path into text area
                        Path rel = workDir.relativize(filePath);
                        int pos = inputArea.getCaretPosition();
                        String toInsert = rel.toString().replace(java.io.File.separatorChar, '/');
                        try {
                            String before = inputArea.getText(0, pos);
                            String prefix = (!before.isEmpty() && !before.endsWith(" ") && !before.endsWith("\n")) ? " " : "";
                            inputArea.getDocument().insertString(pos, prefix + toInsert, null);
                        } catch (javax.swing.text.BadLocationException ex) {
                            inputArea.append(" " + toInsert);
                        }
                        continue;
                    }
                }
                // Outside: add as chip
                attachedModel.addFile(f);
            }
            attachedFilesPanel.refresh();
        }
    }

    /**
     * Sets preferredSize, minimumSize, and maximumSize on all buttons to the same
     * value (the maximum preferred width/height across all buttons). This prevents
     * layout managers from shrinking buttons below their natural size and causing
     * HTML-rendered icon+text to wrap onto separate lines.
     *
     * <p>Package-private for testability.
     */
    static void equalizeButtons(JButton[] buttons) {
        int maxW = 0, maxH = 0;
        for (JButton b : buttons) {
            maxW = Math.max(maxW, b.getPreferredSize().width);
            maxH = Math.max(maxH, b.getPreferredSize().height);
        }
        Dimension eq = new Dimension(maxW, maxH);
        for (JButton b : buttons) {
            b.setPreferredSize(eq);
            b.setMinimumSize(eq);
            b.setMaximumSize(eq);
        }
    }
}
