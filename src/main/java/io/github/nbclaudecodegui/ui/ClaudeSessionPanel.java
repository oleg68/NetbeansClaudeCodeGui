package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.process.ClaudeProcess;
import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import org.netbeans.api.options.OptionsDisplayer;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

/**
 * Panel representing a single Claude Code session tab.
 *
 * <p>Top bar: project combo → path combo → Browse → Open → ⚙.
 * After Open: {@link ClaudeProcess} starts automatically; controls lock.
 * Chat area: output (raw JSON) above, input below with Send button.
 * Slash-commands: typing {@code /} shows a popup menu of known commands.
 */
public final class ClaudeSessionPanel extends JPanel {

    // -------------------------------------------------------------------------
    // slash-commands
    // -------------------------------------------------------------------------

    private static final String[] SLASH_COMMANDS = {
        "/help", "/clear", "/exit", "/init", "/review", "/bug",
        "/compact", "/config", "/cost", "/doctor", "/login",
        "/logout", "/memory", "/model", "/permissions", "/pr_comments",
        "/release-notes", "/status", "/terminal-setup", "/vim"
    };

    // -------------------------------------------------------------------------
    // history persistence
    // -------------------------------------------------------------------------

    private static final String PREF_RECENT = "recentPaths";
    private static final int HISTORY_SIZE = 10;
    private static final String HISTORY_SEP = "\n";

    private static List<String> loadHistory() {
        Preferences prefs = NbPreferences.forModule(ClaudeSessionPanel.class);
        String raw = prefs.get(PREF_RECENT, "");
        List<String> list = new ArrayList<>();
        for (String p : raw.split(HISTORY_SEP, -1)) {
            if (!p.isBlank()) list.add(p);
        }
        return list;
    }

    private static void saveToHistory(String path) {
        List<String> history = loadHistory();
        history.remove(path);
        history.add(0, path);
        if (history.size() > HISTORY_SIZE) {
            history = history.subList(0, HISTORY_SIZE);
        }
        NbPreferences.forModule(ClaudeSessionPanel.class)
                .put(PREF_RECENT, String.join(HISTORY_SEP, history));
    }

    // -------------------------------------------------------------------------
    // fields
    // -------------------------------------------------------------------------

    private final JComboBox<ProjectItem> projectCombo;
    private final JComboBox<String>      pathCombo;
    private final JButton                browseButton;
    private final JButton                openButton;
    private final JLabel                 errorLabel;
    private final JLabel                 placeholderLabel;

    private final JTextArea outputArea;
    private final JTextArea inputArea;
    private final JButton   sendButton;

    private boolean suppressProjectListener;
    private File    confirmedDirectory;
    private ClaudeProcess claudeProcess;

    /** Listener notified when the confirmed working directory changes. */
    public interface DirectoryListener {
        /**
         * Called when the user confirms a valid directory.
         *
         * @param dir the confirmed directory
         */
        void directorySelected(File dir);
    }

    private DirectoryListener directoryListener;

    // -------------------------------------------------------------------------
    // constructors
    // -------------------------------------------------------------------------

    /** Creates a session panel with no pre-set directory (selector active). */
    public ClaudeSessionPanel() {
        this(null, false);
    }

    /**
     * Creates a session panel.
     *
     * @param directory pre-set directory, or {@code null} for none
     * @param locked    {@code true} to lock the directory control immediately
     *                  and start the process
     */
    public ClaudeSessionPanel(File directory, boolean locked) {
        super(new BorderLayout());
        this.confirmedDirectory = directory;

        // --- project combo ---
        projectCombo = new JComboBox<>();
        projectCombo.setRenderer(new ProjectItemRenderer());
        populateProjectCombo();
        if (directory != null) {
            preselectProjectForDirectory(directory);
        }
        projectCombo.addActionListener(e -> onProjectSelected());

        // --- path combo ---
        pathCombo = new JComboBox<>();
        pathCombo.setEditable(true);
        pathCombo.setPrototypeDisplayValue("MMMMMMMMMMMMMMMMMMMMMMMMMMMMMM");
        populatePathHistory();
        if (directory != null) {
            pathCombo.setSelectedItem(directory.getAbsolutePath());
        }

        // --- buttons ---
        browseButton = new JButton(NbBundle.getMessage(ClaudeSessionPanel.class, "BTN_Browse"));
        browseButton.addActionListener(e -> onBrowse());

        openButton = new JButton(NbBundle.getMessage(ClaudeSessionPanel.class, "BTN_Open"));
        openButton.addActionListener(e -> onOpen());

        JButton settingsButton = new JButton("\u2699");
        settingsButton.setToolTipText(NbBundle.getMessage(ClaudeSessionPanel.class, "TIP_Settings"));
        settingsButton.addActionListener(e ->
                OptionsDisplayer.getDefault().open("ClaudeCodeGUI"));

        // --- error label ---
        errorLabel = new JLabel();
        errorLabel.setForeground(Color.RED);
        errorLabel.setVisible(false);

        // --- control bar ---
        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        controlBar.add(projectCombo);
        controlBar.add(pathCombo);
        controlBar.add(browseButton);
        controlBar.add(openButton);
        controlBar.add(settingsButton);

        JPanel top = new JPanel(new BorderLayout());
        top.add(controlBar, BorderLayout.CENTER);
        top.add(errorLabel, BorderLayout.SOUTH);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        add(top, BorderLayout.NORTH);

        // --- placeholder ---
        placeholderLabel = new JLabel(
                NbBundle.getMessage(ClaudeSessionPanel.class, "LBL_SelectDir"),
                javax.swing.SwingConstants.CENTER);
        placeholderLabel.setForeground(Color.GRAY);
        add(placeholderLabel, BorderLayout.CENTER);

        // --- output area (hidden until process starts) ---
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(false);
        outputArea.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.TEXT_CURSOR));
        installOutputContextMenu(outputArea);

        // --- input area + send button ---
        inputArea = new JTextArea(4, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { onInputChanged(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { onInputChanged(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendPrompt());

        bindSendKeys();

        if (locked) {
            setControlsLocked(true);
            startProcess();
        }
    }

    // -------------------------------------------------------------------------
    // public API
    // -------------------------------------------------------------------------

    /** Sets the listener notified when a directory is confirmed. */
    public void setDirectoryListener(DirectoryListener listener) {
        this.directoryListener = listener;
    }

    /** Returns the confirmed working directory, or {@code null} if none. */
    public File getConfirmedDirectory() {
        return confirmedDirectory;
    }

    /** Returns {@code true} if the directory controls are locked. */
    public boolean isLocked() {
        return !openButton.isEnabled();
    }

    /**
     * Asks for confirmation (if the process is running) and stops the process.
     *
     * @return {@code true} if the tab may be closed
     */
    public boolean canClose() {
        if (claudeProcess == null || !claudeProcess.isRunning()) {
            return true;
        }
        NotifyDescriptor nd = new NotifyDescriptor.Confirmation(
                "Stop the Claude Code session in '"
                        + (confirmedDirectory != null
                                ? confirmedDirectory.getName() : "?")
                        + "' and close this tab?",
                "Close Session",
                NotifyDescriptor.YES_NO_OPTION);
        if (DialogDisplayer.getDefault().notify(nd) == NotifyDescriptor.YES_OPTION) {
            claudeProcess.stop();
            return true;
        }
        return false;
    }

    /**
     * Stops the process unconditionally (called when the whole window closes).
     */
    public void stopProcess() {
        if (claudeProcess != null) {
            claudeProcess.stop();
        }
    }

    /**
     * Resolves the tab label for a directory: project display name when the
     * path matches an open project root, otherwise the directory basename.
     *
     * @param dir the working directory
     * @return the resolved label
     */
    public static String resolveTabLabel(File dir) {
        if (dir == null) {
            return NbBundle.getMessage(ClaudeSessionPanel.class, "TAB_NewSession");
        }
        for (Project p : OpenProjects.getDefault().getOpenProjects()) {
            File projDir = FileUtil.toFile(p.getProjectDirectory());
            if (dir.equals(projDir)) {
                return ProjectUtils.getInformation(p).getDisplayName();
            }
        }
        return dir.getName();
    }

    // -------------------------------------------------------------------------
    // top bar handlers
    // -------------------------------------------------------------------------

    private void onProjectSelected() {
        if (suppressProjectListener) return;
        Object sel = projectCombo.getSelectedItem();
        if (sel instanceof ProjectItem item && item.project() != null) {
            File dir = FileUtil.toFile(item.project().getProjectDirectory());
            if (dir != null) {
                pathCombo.setSelectedItem(dir.getAbsolutePath());
            }
        }
    }

    private void onBrowse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        Object cur = pathCombo.getSelectedItem();
        if (cur != null && !cur.toString().isBlank()) {
            File f = new File(cur.toString().trim());
            if (f.isDirectory()) chooser.setCurrentDirectory(f);
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathCombo.setSelectedItem(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onOpen() {
        Object item = pathCombo.getSelectedItem();
        if (item == null || item.toString().isBlank()) {
            showError(NbBundle.getMessage(ClaudeSessionPanel.class, "ERR_PathEmpty"));
            return;
        }
        String text = item.toString().trim();
        File dir = new File(text);
        if (!dir.exists() || !dir.isDirectory()) {
            showError(NbBundle.getMessage(ClaudeSessionPanel.class, "ERR_PathNotFound"));
            return;
        }

        if (!isProjectDirectory(dir)) {
            saveToHistory(dir.getAbsolutePath());
        }

        errorLabel.setVisible(false);
        confirmedDirectory = dir;
        setControlsLocked(true);
        placeholderLabel.setVisible(false);

        if (directoryListener != null) {
            directoryListener.directorySelected(dir);
        }

        startProcess();
    }

    // -------------------------------------------------------------------------
    // process management
    // -------------------------------------------------------------------------

    private void startProcess() {
        if (confirmedDirectory == null) return;

        showChatUI();

        claudeProcess = new ClaudeProcess(line ->
                SwingUtilities.invokeLater(() -> appendOutput(line)));
        claudeProcess.setDebugConsumer(line ->
                SwingUtilities.invokeLater(() -> appendOutput(line)));

        claudeProcess.start(confirmedDirectory.getAbsolutePath());
        appendOutput("ready — type a message and press Send");
    }

    private void showChatUI() {
        remove(placeholderLabel);

        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(null);

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        JPanel sendRow = new JPanel(new BorderLayout(4, 0));
        sendRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        sendRow.add(inputScroll, BorderLayout.CENTER);
        sendRow.add(sendButton, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(sendRow, BorderLayout.CENTER);

        add(outputScroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        revalidate();
        repaint();
        inputArea.requestFocusInWindow();
    }

    private void appendOutput(String line) {
        outputArea.append(line);
        outputArea.append("\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    // -------------------------------------------------------------------------
    // input / send
    // -------------------------------------------------------------------------

    private void sendPrompt() {
        String text = inputArea.getText();
        if (text.isBlank()) return;
        if (claudeProcess == null) {
            appendOutput("[not ready]");
            return;
        }
        if (claudeProcess.isRunning()) {
            appendOutput("[busy — previous message still processing]");
            return;
        }
        inputArea.setText("");
        inputArea.setBackground(Color.WHITE);
        sendButton.setEnabled(false);

        Thread t = new Thread(() -> {
            try {
                claudeProcess.sendInput(text);
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() ->
                        appendOutput("[send error: " + ex.getMessage() + "]"));
            } finally {
                SwingUtilities.invokeLater(() -> sendButton.setEnabled(true));
            }
        }, "claude-send");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Binds send/newline keys according to current preferences.
     * Reads preferences fresh each time so changes take effect on next use.
     */
    private void bindSendKeys() {
        // Remove old bindings by using named keys we control
        inputArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter-action");
        inputArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "shift-enter-action");
        inputArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "ctrl-enter-action");
        inputArea.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.ALT_DOWN_MASK), "alt-enter-action");

        inputArea.getActionMap().put("enter-action",       keyAction(ClaudeCodePreferences.ENTER));
        inputArea.getActionMap().put("shift-enter-action", keyAction(ClaudeCodePreferences.SHIFT_ENTER));
        inputArea.getActionMap().put("ctrl-enter-action",  keyAction(ClaudeCodePreferences.CTRL_ENTER));
        inputArea.getActionMap().put("alt-enter-action",   keyAction(ClaudeCodePreferences.ALT_ENTER));
    }

    private AbstractAction keyAction(final String keyValue) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String sendKey    = ClaudeCodePreferences.getSendKey();
                String newlineKey = ClaudeCodePreferences.getNewlineKey();
                if (keyValue.equals(sendKey)) {
                    sendPrompt();
                } else if (keyValue.equals(newlineKey)) {
                    inputArea.insert("\n", inputArea.getCaretPosition());
                }
            }
        };
    }

    // -------------------------------------------------------------------------
    // slash-command popup
    // -------------------------------------------------------------------------

    private void onInputChanged() {
        String text = inputArea.getText();
        boolean isSlash = text.startsWith("/");
        inputArea.setBackground(isSlash
                ? new Color(255, 255, 224)   // light yellow
                : Color.WHITE);

        if (isSlash) {
            showSlashPopup(text);
        }
    }

    private void showSlashPopup(String prefix) {
        JPopupMenu popup = new JPopupMenu();
        boolean any = false;
        for (String cmd : SLASH_COMMANDS) {
            if (cmd.startsWith(prefix) || "/".equals(prefix)) {
                JMenuItem item = new JMenuItem(cmd);
                item.addActionListener(e -> {
                    inputArea.setText(cmd + " ");
                    inputArea.setCaretPosition(inputArea.getText().length());
                    inputArea.setBackground(new Color(255, 255, 224));
                });
                popup.add(item);
                any = true;
            }
        }
        if (any) {
            popup.show(inputArea, 0, inputArea.getHeight());
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private void setControlsLocked(boolean locked) {
        projectCombo.setEnabled(!locked);
        pathCombo.setEnabled(!locked);
        browseButton.setEnabled(!locked);
        openButton.setEnabled(!locked);
    }

    private void showError(String message) {
        errorLabel.setText("  " + message);
        errorLabel.setVisible(true);
        revalidate();
        repaint();
    }

    private boolean isProjectDirectory(File dir) {
        for (Project p : OpenProjects.getDefault().getOpenProjects()) {
            if (dir.equals(FileUtil.toFile(p.getProjectDirectory()))) {
                return true;
            }
        }
        return false;
    }

    private void populateProjectCombo() {
        DefaultComboBoxModel<ProjectItem> model = new DefaultComboBoxModel<>();
        model.addElement(new ProjectItem(null,
                NbBundle.getMessage(ClaudeSessionPanel.class, "LBL_SelectProject")));
        for (Project p : OpenProjects.getDefault().getOpenProjects()) {
            model.addElement(new ProjectItem(p,
                    ProjectUtils.getInformation(p).getDisplayName()));
        }
        projectCombo.setModel(model);
    }

    private void populatePathHistory() {
        for (String p : loadHistory()) {
            pathCombo.addItem(p);
        }
    }

    private void preselectProjectForDirectory(File directory) {
        suppressProjectListener = true;
        try {
            for (int i = 0; i < projectCombo.getItemCount(); i++) {
                ProjectItem item = projectCombo.getItemAt(i);
                if (item.project() != null) {
                    File projDir = FileUtil.toFile(item.project().getProjectDirectory());
                    if (directory.equals(projDir)) {
                        projectCombo.setSelectedIndex(i);
                        return;
                    }
                }
            }
        } finally {
            suppressProjectListener = false;
        }
    }

    // -------------------------------------------------------------------------
    // output area context menu
    // -------------------------------------------------------------------------

    private static void installOutputContextMenu(JTextArea area) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem copy = new JMenuItem("Copy");
        copy.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_C, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        copy.addActionListener(e -> area.copy());

        JMenuItem selectAll = new JMenuItem("Select All");
        selectAll.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_A, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        selectAll.addActionListener(e -> area.selectAll());

        menu.add(copy);
        menu.addSeparator();
        menu.add(selectAll);

        // enable Copy only when there is a selection
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                copy.setEnabled(area.getSelectedText() != null);
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });

        area.setComponentPopupMenu(menu);
    }

    // -------------------------------------------------------------------------
    // inner types
    // -------------------------------------------------------------------------

    private record ProjectItem(Project project, String label) {
        @Override public String toString() { return label; }
    }

    private static final class ProjectItemRenderer
            extends JLabel implements ListCellRenderer<ProjectItem> {

        ProjectItemRenderer() { setOpaque(true); }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ProjectItem> list, ProjectItem value,
                int index, boolean isSelected, boolean cellHasFocus) {
            setText(value == null ? "" : value.label());
            boolean placeholder = value == null || value.project() == null;
            setForeground(placeholder
                    ? (isSelected ? list.getSelectionForeground() : Color.GRAY)
                    : (isSelected ? list.getSelectionForeground() : list.getForeground()));
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            return this;
        }
    }
}
