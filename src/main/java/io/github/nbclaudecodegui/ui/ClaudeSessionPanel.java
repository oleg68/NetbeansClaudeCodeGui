package io.github.nbclaudecodegui.ui;

import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.pty4j.PtyProcess;
import io.github.nbclaudecodegui.process.ClaudeProcess;
import io.github.nbclaudecodegui.process.PtyTtyConnector;
import io.github.nbclaudecodegui.process.TtyPromptDetector;
import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import java.util.logging.Logger;
import org.netbeans.api.options.OptionsDisplayer;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

/**
 * Panel representing a single Claude Code session tab.
 *
 * <p>Top bar: project combo, path combo, Browse, Open, Settings.
 * After Open: an embedded JediTerm terminal runs the Claude TUI.
 */
public final class ClaudeSessionPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(ClaudeSessionPanel.class.getName());

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

    private JediTermWidget      terminalWidget;
    private JPanel              topBar;
    private JPanel              inputPanel;
    private JTextArea           inputArea;
    private JButton             sendButton;
    private JButton             cancelButton;
    private PtyTtyConnector     connector;
    private final TtyPromptDetector ttyPromptDetector = new TtyPromptDetector();
    private PromptResponsePanel promptResponsePanel;
    /** Fires tryFlush() when PTY output goes silent while menu options are being collected. */
    private final javax.swing.Timer promptFlushTimer = new javax.swing.Timer(400, e -> flushPendingPrompt());

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

        topBar = new JPanel(new BorderLayout());
        topBar.add(controlBar, BorderLayout.CENTER);
        topBar.add(errorLabel, BorderLayout.SOUTH);
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        add(topBar, BorderLayout.NORTH);

        // --- placeholder ---
        placeholderLabel = new JLabel(
                NbBundle.getMessage(ClaudeSessionPanel.class, "LBL_SelectDir"),
                javax.swing.SwingConstants.CENTER);
        placeholderLabel.setForeground(Color.GRAY);
        add(placeholderLabel, BorderLayout.CENTER);

        // --- input panel (shown after session starts) ---
        inputArea = new JTextArea(3, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        bindSendKey(inputArea);

        sendButton   = new JButton("Send");
        cancelButton = new JButton("Cancel");
        sendButton.addActionListener(e -> sendPrompt());
        cancelButton.addActionListener(e -> cancelPrompt());

        JPanel buttonCol = new JPanel();
        buttonCol.setLayout(new BoxLayout(buttonCol, BoxLayout.Y_AXIS));
        sendButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonCol.add(sendButton);
        buttonCol.add(Box.createRigidArea(new Dimension(0, 4)));
        buttonCol.add(cancelButton);
        buttonCol.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        promptResponsePanel = new PromptResponsePanel();

        JPanel southStack = new JPanel(new BorderLayout());
        inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);
        inputPanel.add(buttonCol, BorderLayout.EAST);
        inputPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        inputPanel.setVisible(false);

        southStack.add(promptResponsePanel, BorderLayout.NORTH);
        southStack.add(inputPanel, BorderLayout.CENTER);
        add(southStack, BorderLayout.SOUTH);

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
    /**
     * Returns {@code true} if this panel has no running process.
     *
     * <p>Close-time confirmation and process termination are handled by
     * {@code ClaudeSessionTopComponent} rather than here.
     *
     * @return {@code true} if no PTY process is running
     */
    public boolean canClose() {
        return claudeProcess == null || !claudeProcess.isRunning();
    }

    /**
     * Returns {@code true} if a PTY process is currently running.
     */
    public boolean hasLiveProcess() {
        return claudeProcess != null && claudeProcess.isRunning();
    }

    /**
     * Starts the session for {@code dir} programmatically without UI dialogs.
     *
     * <p>Used when a directory is known (e.g. restored from persistence or
     * opened from the context menu) and the session should start immediately.
     *
     * @param dir the working directory
     */
    public void autoStart(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        confirmedDirectory = dir;
        pathCombo.setSelectedItem(dir.getAbsolutePath());
        setControlsLocked(true);
        placeholderLabel.setVisible(false);
        if (directoryListener != null) {
            directoryListener.directorySelected(dir);
        }
        startProcess();
    }

    /**
     * Removes the terminal widget from the layout without destroying the PTY.
     *
     * <p>Called when the TopComponent window is closed/hidden but the process
     * should remain alive (e.g. ResetWindows). Use {@link #reattachTerminal()}
     * to make the widget visible again.
     */
    public void detachTerminal() {
        if (terminalWidget != null) {
            remove(terminalWidget);
            revalidate();
            repaint();
        }
    }

    /**
     * Re-adds the terminal widget to the layout after a detach.
     *
     * <p>Called when the TopComponent is reopened and the process is still alive.
     */
    public void reattachTerminal() {
        if (terminalWidget != null) {
            add(terminalWidget, BorderLayout.CENTER);
            revalidate();
            repaint();
            terminalWidget.requestFocusInWindow();
        }
    }

    /**
     * Moves keyboard focus to the prompt input area if a session is active,
     * or to the terminal widget otherwise.
     *
     * <p>Called from {@code ClaudeSessionTopComponent.componentActivated()} so
     * that the user can type immediately after switching to this window.
     */
    public void requestFocusOnInput() {
        if (inputPanel.isVisible() && inputArea != null) {
            inputArea.requestFocusInWindow();
        } else if (terminalWidget != null) {
            terminalWidget.requestFocusInWindow();
        }
    }

    /**
     * Stops the process unconditionally (called when the whole window closes).
     */
    public void stopProcess() {
        if (connector != null) {
            connector.setLineListener(null);
        }
        if (terminalWidget != null) {
            terminalWidget.close();
            terminalWidget = null;
        }
        if (claudeProcess != null) {
            claudeProcess.stop();
        }
        connector = null;
        promptResponsePanel.dismiss();
        inputPanel.setVisible(false);
        topBar.setVisible(true);
    }

    /**
     * Resolves the tab label for a directory.
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

        claudeProcess = new ClaudeProcess();

        try {
            PtyProcess process = claudeProcess.start(confirmedDirectory.getAbsolutePath());
            showChatUI(process);

            Thread waiter = new Thread(() -> {
                try {
                    process.waitFor();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                SwingUtilities.invokeLater(() -> {
                    if (terminalWidget != null) {
                        // Process ended; terminal keeps showing last output
                    }
                });
            }, "claude-waiter");
            waiter.setDaemon(true);
            waiter.start();
        } catch (IOException ex) {
            showError("Failed to start claude: " + ex.getMessage());
        }
    }

    private void showChatUI(PtyProcess process) {
        remove(placeholderLabel);

        connector = new PtyTtyConnector(process);
        connector.setLineListener(line -> {
            if (ClaudeCodePreferences.isDebugMode()) {
                LOG.info("[PTY line] " + line);
            }
            // While the prompt panel is visible, ignore PTY lines and wait for
            // the user to click a button. Spinner frames and menu redraws must
            // not auto-dismiss the panel before the user has responded.
            if (promptResponsePanel.isVisible()) {
                return;
            }

            // Restart flush timer on every line. If Claude stops sending output
            // while the detector is in COLLECTING state (menu is the last output),
            // the timer fires and calls tryFlush() to emit the pending prompt.
            SwingUtilities.invokeLater(() -> {
                promptFlushTimer.restart();
            });

            ttyPromptDetector.feed(line).ifPresent(req -> {
                LOG.info("[PTY prompt detected] text=\"" + req.text() + "\" | options=" + req.options());
                SwingUtilities.invokeLater(() -> {
                    inputPanel.setVisible(false);
                    promptResponsePanel.show(req, answer -> {
                        LOG.info("[PTY prompt answer] " + answer);
                        inputPanel.setVisible(true);
                        revalidate();
                        repaint();
                        writePtyAnswer(answer);
                    });
                    revalidate();
                    repaint();
                });
            });
        });

        JediTermWidget widget = new JediTermWidget(new DefaultSettingsProvider());
        widget.setTtyConnector(connector);
        widget.start();
        this.terminalWidget = widget;

        topBar.setVisible(false);
        inputPanel.setVisible(true);

        add(widget, BorderLayout.CENTER);
        revalidate();
        repaint();
        widget.requestFocusInWindow();
    }

    private void sendPrompt() {
        String text = inputArea.getText();
        if (text.isEmpty() || connector == null) return;
        try {
            connector.write(text + "\r");
            inputArea.setText("");
            if (terminalWidget != null) terminalWidget.requestFocusInWindow();
        } catch (IOException ex) {
            showError("Write failed: " + ex.getMessage());
        }
    }

    private void cancelPrompt() {
        if (connector == null) return;
        try {
            connector.write(new byte[]{0x03});
        } catch (IOException ex) {
            showError("Cancel failed: " + ex.getMessage());
        }
    }

    private void bindSendKey(JTextArea area) {
        area.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                String sendKey = ClaudeCodePreferences.getSendKey();
                boolean ctrl  = e.isControlDown();
                boolean shift = e.isShiftDown();
                boolean alt   = e.isAltDown();
                boolean plain = !ctrl && !shift && !alt;
                boolean isEnter = e.getKeyCode() == KeyEvent.VK_ENTER;
                if (!isEnter) return;

                boolean match = switch (sendKey) {
                    case ClaudeCodePreferences.ENTER       -> plain;
                    case ClaudeCodePreferences.CTRL_ENTER  -> ctrl && !shift && !alt;
                    case ClaudeCodePreferences.SHIFT_ENTER -> shift && !ctrl && !alt;
                    case ClaudeCodePreferences.ALT_ENTER   -> alt && !ctrl && !shift;
                    default -> false;
                };

                if (match) {
                    e.consume();
                    sendPrompt();
                }
                // non-matching Enter variants fall through and insert a newline
            }
        });
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

    /** Called by promptFlushTimer when PTY output goes silent mid-menu. */
    private void flushPendingPrompt() {
        if (promptResponsePanel.isVisible()) {
            return;
        }
        ttyPromptDetector.tryFlush().ifPresent(req -> {
            LOG.info("[PTY prompt flush] text=\"" + req.text() + "\" | options=" + req.options());
            inputPanel.setVisible(false);
            promptResponsePanel.show(req, answer -> {
                LOG.info("[PTY prompt answer] " + answer);
                inputPanel.setVisible(true);
                revalidate();
                repaint();
                writePtyAnswer(answer);
            });
            revalidate();
            repaint();
        });
    }

    /**
     * Writes the user's answer to the PTY.
     *
     * <p>Single-digit answers select a numbered Ink/inquirer menu option immediately
     * (no Enter needed). Free-form text needs a trailing {@code \r}. A {@code null}
     * answer means the user clicked Cancel — send ESC so Claude dismisses its menu.
     */
    private void writePtyAnswer(String answer) {
        try {
            if (answer == null) {
                // Cancel — send ESC to dismiss the Ink menu
                LOG.info("[PTY write] \\x1b (Cancel/ESC)");
                connector.write(new byte[]{0x1b});
            } else {
                boolean isMenuDigit = answer.matches("[0-9]");
                String toWrite = isMenuDigit ? answer : answer + "\r";
                LOG.info("[PTY write] " + toWrite.replace("\r", "\\r").replace("\n", "\\n"));
                connector.write(toWrite);
            }
        } catch (java.io.IOException ex) {
            showError("Write failed: " + ex.getMessage());
        }
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
