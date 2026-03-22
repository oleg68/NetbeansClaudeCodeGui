package io.github.nbclaudecodegui.ui;

import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.pty4j.PtyProcess;
import io.github.nbclaudecodegui.process.ClaudeProcess;
import io.github.nbclaudecodegui.process.PtyTtyConnector;
import io.github.nbclaudecodegui.process.ScreenContentDetector;
import io.github.nbclaudecodegui.process.TtyPromptDetector;
import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
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
 * After Open: an embedded JediTerm terminal runs the Claude TUI,
 * separated from the input area by a draggable JSplitPane divider.
 */
public final class ClaudePromptPanel extends JPanel {

    /**
     * Result of {@link #parseModelDiscovery}: all model names + index of the active model
     * ({@code -1} if no active model was detected).
     */
    record ModelDiscovery(List<String> models, int currentIndex) {}

    private static final Logger LOG = Logger.getLogger(ClaudePromptPanel.class.getName());

    // -------------------------------------------------------------------------
    // history persistence
    // -------------------------------------------------------------------------

    private static final String PREF_RECENT = "recentPaths";
    private static final int HISTORY_SIZE = 10;
    private static final String HISTORY_SEP = "\n";

    private static List<String> loadHistory() {
        Preferences prefs = NbPreferences.forModule(ClaudePromptPanel.class);
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
        NbPreferences.forModule(ClaudePromptPanel.class)
                .put(PREF_RECENT, String.join(HISTORY_SEP, history));
    }

    // -------------------------------------------------------------------------
    // Icons
    // -------------------------------------------------------------------------

    private static final String ICON_SEND   = "\u25b6";  // ▶ BLACK RIGHT-POINTING TRIANGLE
    private static final String ICON_CANCEL = "\u2716";  // ✖ HEAVY MULTIPLICATION X

    // -------------------------------------------------------------------------
    // Edit mode constants
    // -------------------------------------------------------------------------

    private static final String[] EDIT_MODE_LABELS   = {"Plan Mode", "Ask on Edit", "Accept on Edit"};
    private static final String[] EDIT_MODE_VALUES   = {"plan",      "default",     "acceptEdits"};

    /** ESC byte sent to the PTY (dismiss autocomplete, close menus). */
    private static final byte[] PTY_ESC = {0x1b};

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
    private JSplitPane          splitPane;
    private JPanel              topBar;
    private JPanel              inputPanel;
    private JPanel              southStack;
    private JTextArea           inputArea;
    private JButton             sendButton;
    private JButton             cancelButton;
    private PtyTtyConnector     connector;
    private final TtyPromptDetector ttyPromptDetector = new TtyPromptDetector();
    private final ScreenContentDetector screenContentDetector = new ScreenContentDetector();
    private ChoiceMenuPanel choiceMenuPanel;
    /** Fires flushPendingPrompt() when PTY output goes silent while menu options are being collected. */
    private final javax.swing.Timer promptFlushTimer = new javax.swing.Timer(400, e -> flushPendingPrompt());

    // Status bar components
    private JPanel      statusBar;
    private JComboBox<String> editModeCombo;
    private JComboBox<String> modelCombo;
    private JLabel      stateLabel;
    private JLabel      planLabel;
    private JLabel      versionLabel;
    private javax.swing.Timer statusTimer;
    private boolean     modelComboPopulated = false;
    private volatile boolean modelDiscoveryInProgress = false;
    private int modelDiscoveryAttempts = 0;
    private static final int MAX_MODEL_DISCOVERY_ATTEMPTS = 3;
    private boolean     statusBarVisible    = false;

    /**
     * Lifecycle state of the Claude session.
     *
     * <ul>
     *   <li>{@link #STARTING} — process launched, waiting for the first {@code ❯} prompt.
     *       Send is disabled; the screen content detector drives the STARTING → READY transition.</li>
     *   <li>{@link #READY} — Claude is idle and waiting for user input.
     *       Send is enabled; modelCombo is enabled when populated.</li>
     *   <li>{@link #WORKING} — Claude is executing (prompt sent, model discovery, or
     *       edit-mode switch in progress). Send is disabled; Cancel is enabled.</li>
     * </ul>
     *
     * <p>Transitions:
     * <pre>
     *   showChatUI()                    → STARTING
     *   detectInputPromptReady() = true → READY   (one-shot, from STARTING only)
     *   sendPrompt()                    → WORKING
     *   discoverModels() start          → WORKING
     *   sendShiftTabsUntilMode() start  → WORKING
     *   onClaudeIdle()                  → READY
     * </pre>
     *
     * <p>All transitions go through {@link #applyState(SessionLifecycle)}, which
     * updates the UI atomically on the EDT.
     */
    enum SessionLifecycle { STARTING, READY, WORKING }
    private volatile SessionLifecycle lifecycle = SessionLifecycle.STARTING;

    // Prompt history (in-session)
    private final List<String> promptHistory = new ArrayList<>();
    private int historyIndex = -1;
    private static final int PROMPT_HISTORY_MAX = 100;

    // Edit mode (current session state)
    private volatile String editMode = "default";

    /** Timestamp (ms) when the last ChoiceMenu answer was sent; used to suppress re-detection. */
    private volatile long choiceMenuAnsweredAt = 0L;

    /**
     * Thread-safe registry of editMode per working directory path.
     * Updated whenever editMode changes; read by NetBeansMCPHandler on servlet threads.
     */
    public static final java.util.concurrent.ConcurrentHashMap<String, String> EDIT_MODE_REGISTRY =
            new java.util.concurrent.ConcurrentHashMap<>();

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
    public ClaudePromptPanel() {
        this(null, false);
    }

    /**
     * Creates a session panel.
     *
     * @param directory pre-set directory, or {@code null} for none
     * @param locked    {@code true} to lock the directory control immediately
     *                  and start the process
     */
    public ClaudePromptPanel(File directory, boolean locked) {
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
        browseButton = new JButton(NbBundle.getMessage(ClaudePromptPanel.class, "BTN_Browse"));
        browseButton.addActionListener(e -> onBrowse());

        openButton = new JButton(NbBundle.getMessage(ClaudePromptPanel.class, "BTN_Open"));
        openButton.addActionListener(e -> onOpen());

        JButton settingsButton = new JButton("\u2699");
        settingsButton.setToolTipText(NbBundle.getMessage(ClaudePromptPanel.class, "TIP_Settings"));
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
                NbBundle.getMessage(ClaudePromptPanel.class, "LBL_SelectDir"),
                javax.swing.SwingConstants.CENTER);
        placeholderLabel.setForeground(Color.GRAY);
        add(placeholderLabel, BorderLayout.CENTER);

        // --- input area ---
        inputArea = new JTextArea(3, 40);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFocusTraversalKeysEnabled(false);
        bindSendKey(inputArea);
        attachContextMenu(inputArea);

        // --- send / cancel buttons with colored icons ---
        sendButton   = new JButton("<html><font color='#228B22'>" + ICON_SEND   + "</font> Send</html>");
        cancelButton = new JButton("<html><font color='#B22222'>" + ICON_CANCEL + "</font> Cancel</html>");
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

        choiceMenuPanel = new ChoiceMenuPanel();

        // --- status bar ---
        statusBar = buildStatusBar();

        // --- south stack ---
        southStack = new JPanel(new BorderLayout());
        inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);
        inputPanel.add(buttonCol, BorderLayout.EAST);
        inputPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        inputPanel.setVisible(false);

        southStack.add(choiceMenuPanel, BorderLayout.NORTH);
        southStack.add(inputPanel, BorderLayout.CENTER);
        southStack.add(statusBar, BorderLayout.SOUTH);

        // southStack added in showChatUI (via splitPane); for initial state add as SOUTH
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
     * Returns {@code true} if this panel has no running process.
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
     * Returns the current edit mode: {@code "default"}, {@code "acceptEdits"}, or {@code "plan"}.
     */
    public String getEditMode() {
        return editMode;
    }

    /**
     * Starts the session for {@code dir} programmatically without UI dialogs.
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
     */
    public void detachTerminal() {
        if (splitPane != null) {
            remove(splitPane);
            revalidate();
            repaint();
        } else if (terminalWidget != null) {
            remove(terminalWidget);
            revalidate();
            repaint();
        }
    }

    /**
     * Re-adds the terminal widget to the layout after a detach.
     */
    public void reattachTerminal() {
        if (splitPane != null) {
            add(splitPane, BorderLayout.CENTER);
            revalidate();
            repaint();
            terminalWidget.requestFocusInWindow();
        } else if (terminalWidget != null) {
            add(terminalWidget, BorderLayout.CENTER);
            revalidate();
            repaint();
            terminalWidget.requestFocusInWindow();
        }
    }

    /**
     * Moves keyboard focus to the prompt input area if a session is active,
     * or to the terminal widget otherwise.
     */
    public void requestFocusOnInput() {
        if (choiceMenuPanel.isVisible()) {
            return;
        }
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
        if (statusTimer != null) {
            statusTimer.stop();
            statusTimer = null;
        }
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
        choiceMenuPanel.dismiss();
        inputPanel.setVisible(false);
        topBar.setVisible(true);
        if (splitPane != null) {
            remove(splitPane);
            splitPane = null;
            add(southStack, BorderLayout.SOUTH);
        }
        statusBar.setVisible(false);
        modelComboPopulated = false;
        lifecycle = SessionLifecycle.STARTING;
        if (confirmedDirectory != null) {
            EDIT_MODE_REGISTRY.remove(confirmedDirectory.getAbsolutePath());
        }
        revalidate();
        repaint();
    }

    /**
     * Resolves the tab label for a directory.
     */
    public static String resolveTabLabel(File dir) {
        if (dir == null) {
            return NbBundle.getMessage(ClaudePromptPanel.class, "TAB_NewSession");
        }
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
        LOG.info("resolveTabLabel: dir=" + dir.getAbsolutePath()
                + " openProjects=" + openProjects.length);
        for (Project p : openProjects) {
            File projDir = FileUtil.toFile(p.getProjectDirectory());
            String projName = ProjectUtils.getInformation(p).getDisplayName();
            LOG.info("  comparing with project '" + projName
                    + "' dir=" + (projDir != null ? projDir.getAbsolutePath() : "null")
                    + " equal=" + dir.equals(projDir));
        }
        // Try canonical path comparison as fallback
        for (Project p : openProjects) {
            File projDir = FileUtil.toFile(p.getProjectDirectory());
            if (projDir == null) continue;
            if (dir.equals(projDir)) {
                return ProjectUtils.getInformation(p).getDisplayName();
            }
            try {
                if (dir.getCanonicalPath().equals(projDir.getCanonicalPath())) {
                    LOG.info("  canonical match found for project '"
                            + ProjectUtils.getInformation(p).getDisplayName() + "'");
                    return ProjectUtils.getInformation(p).getDisplayName();
                }
            } catch (IOException e) {
                LOG.fine("canonical path comparison failed: " + e.getMessage());
            }
        }
        LOG.warning("resolveTabLabel: no project match for " + dir.getAbsolutePath());
        return "Claude Code";
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
            showError(NbBundle.getMessage(ClaudePromptPanel.class, "ERR_PathEmpty"));
            return;
        }
        String text = item.toString().trim();
        File dir = new File(text);
        if (!dir.exists() || !dir.isDirectory()) {
            showError(NbBundle.getMessage(ClaudePromptPanel.class, "ERR_PathNotFound"));
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
        // Remove the pre-session layout pieces
        remove(placeholderLabel);
        remove(southStack);  // was added as SOUTH in constructor

        modelDiscoveryAttempts = 0;
        connector = new PtyTtyConnector(process);
        connector.setLineListener(line -> {
            if (ClaudeCodePreferences.isDebugMode()) {
                LOG.info("[PTY line] " + line);
            }
            if (choiceMenuPanel.isVisible()) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                promptFlushTimer.restart();
            });

            ttyPromptDetector.feed(line).ifPresent(req -> {
                LOG.info("[PTY prompt detected] text=\"" + req.text() + "\" | options=" + req.options());
                SwingUtilities.invokeLater(() -> {
                    inputPanel.setVisible(false);
                    choiceMenuPanel.show(req, answer -> {
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
        statusBar.setVisible(true);
        applyState(SessionLifecycle.STARTING);

        // Build split pane: terminal on top, southStack on bottom
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, widget, southStack);
        // resizeWeight=1.0: all extra space goes to PTY (top); bottom height stays fixed
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(5);

        // Saved value is the bottom panel height in pixels; -1 means "use preferred size"
        final int savedBottomHeight = NbPreferences.forModule(ClaudePromptPanel.class).getInt("bottomHeight", -1);
        final boolean[] savingEnabled = {false};

        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int total = splitPane.getHeight();
                if (total <= 0) return;
                if (!savingEnabled[0]) {
                    // First resize: set initial divider position
                    int bottom = savedBottomHeight > 0 ? savedBottomHeight
                                                       : southStack.getPreferredSize().height;
                    splitPane.setDividerLocation(total - splitPane.getDividerSize() - bottom);
                    savingEnabled[0] = true;
                } else {
                    // Subsequent resizes (output area shown/hidden): restore saved bottom height
                    int bottom = NbPreferences.forModule(ClaudePromptPanel.class).getInt("bottomHeight", southStack.getPreferredSize().height);
                    splitPane.setDividerLocation(total - splitPane.getDividerSize() - bottom);
                }
            }
        });
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (!savingEnabled[0]) return;
            if (!splitPane.isEnabled()) return;
            int total = splitPane.getHeight();
            int loc = (int) e.getNewValue();
            if (total > 0 && loc > 0) {
                int bottom = total - splitPane.getDividerSize() - loc;
                if (bottom > 0) NbPreferences.forModule(ClaudePromptPanel.class).putInt("bottomHeight", bottom);
            }
        });

        choiceMenuPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> lockDividerForChoiceMenu());
            }
            @Override
            public void componentHidden(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> unlockDividerFromChoiceMenu());
            }
        });

        add(splitPane, BorderLayout.CENTER);
        revalidate();
        repaint();
        widget.requestFocusInWindow();

        // Register default edit mode immediately so hook can read it
        registerEditMode(editMode);

        // Start background tasks after session is set up
        startVersionDiscovery();
        startStatusPollTimer();
    }

    private void lockDividerForChoiceMenu() {
        if (splitPane == null) return;
        splitPane.setEnabled(false);
        int total = splitPane.getHeight();
        if (total <= 0) return;
        int natural = choiceMenuPanel.getPreferredSize().height
                    + statusBar.getPreferredSize().height;
        splitPane.setDividerLocation(total - splitPane.getDividerSize() - natural);
    }

    private void unlockDividerFromChoiceMenu() {
        if (splitPane == null) return;
        splitPane.setEnabled(true);
        int total = splitPane.getHeight();
        if (total <= 0) return;
        int bottom = NbPreferences.forModule(ClaudePromptPanel.class)
            .getInt("bottomHeight", southStack.getPreferredSize().height);
        splitPane.setDividerLocation(total - splitPane.getDividerSize() - bottom);
    }

    private void sendPrompt() {
        String text = inputArea.getText();
        if (text.isEmpty() || connector == null) return;
        applyState(SessionLifecycle.WORKING);

        // Add to history (prepend; ignore blank; cap at max)
        if (!text.isBlank()) {
            promptHistory.remove(text);
            promptHistory.add(0, text);
            if (promptHistory.size() > PROMPT_HISTORY_MAX) {
                promptHistory.remove(promptHistory.size() - 1);
            }
            historyIndex = -1;
        }

        inputArea.setText("");
        inputArea.requestFocusInWindow();

        // Send text and \r separately with a small pause so CC processes the
        // text before seeing Enter (avoids multiline-prompt / type-input timing issues).
        Thread t = new Thread(() -> {
            try {
                connector.write(text);
                Thread.sleep(200);
                connector.write("\r");
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> showError("Write failed: " + ex.getMessage()));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }, "pty-send-prompt");
        t.setDaemon(true);
        t.start();
    }

    void cancelPrompt() {
        if (connector == null) return;
        try {
            sendCancelToPty(new byte[]{0x03});
        } catch (IOException ex) {
            showError("Cancel failed: " + ex.getMessage());
        }
    }

    /** Sends cancel bytes to the PTY and resets the session to idle state. Single place for all cancel paths. */
    private void sendCancelToPty(byte[] bytes) throws IOException {
        connector.write(bytes);
        onClaudeIdle();
    }

    private void bindSendKey(JTextArea area) {
        area.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Shift+Tab → advance editModeCombo to next item (cycle)
                if (e.getKeyCode() == KeyEvent.VK_TAB && e.isShiftDown()) {
                    e.consume();
                    int next = (editModeCombo.getSelectedIndex() + 1) % editModeCombo.getItemCount();
                    editModeCombo.setSelectedIndex(next);
                    return;
                }
                // Bug 1: Esc → Cancel
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    if (cancelButton.isEnabled()) cancelButton.doClick();
                    return;
                }
                // Ctrl+Up — navigate prompt history backward (older)
                if (e.getKeyCode() == KeyEvent.VK_UP && e.isControlDown() && !e.isShiftDown() && !e.isAltDown()) {
                    e.consume();
                    navigateHistory(1);
                    return;
                }
                // Ctrl+Down — navigate prompt history forward (newer)
                if (e.getKeyCode() == KeyEvent.VK_DOWN && e.isControlDown() && !e.isShiftDown() && !e.isAltDown()) {
                    e.consume();
                    navigateHistory(-1);
                    return;
                }

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
            }
        });
    }

    /** Navigate prompt history. delta=1 means older (up), delta=-1 means newer (down). */
    private void navigateHistory(int delta) {
        if (promptHistory.isEmpty()) return;
        historyIndex = Math.max(-1, Math.min(promptHistory.size() - 1, historyIndex + delta));
        if (historyIndex < 0) {
            inputArea.setText("");
        } else {
            inputArea.setText(promptHistory.get(historyIndex));
            inputArea.setCaretPosition(inputArea.getDocument().getLength());
        }
    }

    /** Attaches a right-click context menu to the input area. */
    private void attachContextMenu(JTextArea area) {
        JPopupMenu menu = TextContextMenu.create(area);

        JMenuItem prevPrompt = new JMenuItem("Previous prompt  (Ctrl+\u2191)");
        prevPrompt.addActionListener(e -> navigateHistory(1));

        JMenuItem nextPrompt = new JMenuItem("Next prompt  (Ctrl+\u2193)");
        nextPrompt.addActionListener(e -> navigateHistory(-1));

        // Bug 6: enable/disable Prev/Next based on history state
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                prevPrompt.setEnabled(!promptHistory.isEmpty() && historyIndex < promptHistory.size() - 1);
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

    // -------------------------------------------------------------------------
    // status bar
    // -------------------------------------------------------------------------

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

        // Edit mode combo
        editModeCombo = new JComboBox<>(EDIT_MODE_LABELS);
        editModeCombo.setMaximumSize(new Dimension(130, 24));
        editModeCombo.setToolTipText("Edit mode");
        editModeCombo.addActionListener(e -> onEditModeComboChanged());

        // Model combo (populated after session start)
        modelCombo = new JComboBox<>();
        modelCombo.setMaximumSize(new Dimension(200, 24));
        modelCombo.setToolTipText("Active model");
        modelCombo.setEnabled(false);
        modelCombo.addActionListener(e -> onModelComboChanged());

        stateLabel  = new JLabel("Ready");
        planLabel   = new JLabel("");
        versionLabel = new JLabel("");

        stateLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        planLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        versionLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

        stateLabel.setToolTipText("Session state: Ready or Working");
        planLabel.setToolTipText("Active plan file (if any)");
        versionLabel.setToolTipText("Claude CLI version");

        bar.add(Box.createRigidArea(new Dimension(4, 0)));
        bar.add(editModeCombo);
        bar.add(Box.createRigidArea(new Dimension(4, 0)));
        bar.add(makeSep());
        bar.add(Box.createRigidArea(new Dimension(4, 0)));
        bar.add(modelCombo);
        bar.add(Box.createHorizontalGlue());
        bar.add(stateLabel);
        bar.add(makeSep());
        bar.add(planLabel);
        bar.add(makeSep());
        bar.add(versionLabel);
        bar.add(Box.createRigidArea(new Dimension(4, 0)));

        bar.setVisible(false);
        return bar;
    }

    private static JPanel makeSep() {
        JPanel sep = new JPanel();
        sep.setLayout(new BoxLayout(sep, BoxLayout.X_AXIS));
        sep.setOpaque(false);
        JPanel dark = new JPanel();
        dark.setOpaque(true);
        dark.setBackground(UIManager.getColor("controlShadow"));
        dark.setMaximumSize(new Dimension(1, 16));
        dark.setPreferredSize(new Dimension(1, 16));
        JPanel light = new JPanel();
        light.setOpaque(true);
        light.setBackground(UIManager.getColor("controlHighlight"));
        light.setMaximumSize(new Dimension(1, 16));
        light.setPreferredSize(new Dimension(1, 16));
        sep.add(dark);
        sep.add(light);
        sep.setMaximumSize(new Dimension(2, 16));
        sep.setPreferredSize(new Dimension(2, 16));
        return sep;
    }

    /** True while a shift-tab thread is actively switching the CC edit mode. */
    private volatile boolean modeSwitchInProgress = false;

    private void onEditModeComboChanged() {
        int idx = editModeCombo.getSelectedIndex();
        if (idx < 0 || idx >= EDIT_MODE_VALUES.length) return;
        String newMode = EDIT_MODE_VALUES[idx];
        if (newMode.equals(editMode)) return;

        editMode = newMode;
        registerEditMode(newMode);
        sendShiftTabsUntilMode(newMode);
    }

    private void registerEditMode(String mode) {
        if (confirmedDirectory != null) {
            EDIT_MODE_REGISTRY.put(confirmedDirectory.getAbsolutePath(), mode);
        }
    }

    /**
     * Sends Shift+Tab (ESC[Z) presses until the screen shows {@code targetMode},
     * or until 3 attempts are exhausted.  Runs on a daemon thread.
     */
    private void sendShiftTabsUntilMode(String targetMode) {
        if (connector == null) return;
        applyState(SessionLifecycle.WORKING);
        modeSwitchInProgress = true;
        Thread t = new Thread(() -> {
            try {
                for (int attempt = 0; attempt < 3; attempt++) {
                    connector.write(new byte[]{0x1b, '[', 'Z'});
                    Thread.sleep(200);
                    java.util.Optional<String> detected =
                            screenContentDetector.detectEditMode(getScreenLines());
                    if (detected.isPresent() && detected.get().equals(targetMode)) {
                        return;
                    }
                }
                LOG.warning("sendShiftTabsUntilMode: did not reach " + targetMode + " after 3 attempts");
            } catch (IOException | InterruptedException ex) {
                LOG.warning("sendShiftTabsUntilMode failed: " + ex.getMessage());
            } finally {
                modeSwitchInProgress = false;
                onClaudeIdle();
            }
        }, "shift-tab-edit-mode");
        t.setDaemon(true);
        t.start();
    }

    private List<String> getScreenLines() {
        if (terminalWidget == null) return java.util.Collections.emptyList();
        TerminalTextBuffer buf = terminalWidget.getTerminalTextBuffer();
        return buf.getScreenBuffer().getLineTexts();
    }

    private void onModelComboChanged() {
        if (!modelComboPopulated || modelDiscoveryInProgress) return;
        int idx = modelCombo.getSelectedIndex();
        if (idx < 0 || connector == null) return;

        // Only switch when Claude is idle
        if (lifecycle != SessionLifecycle.READY) return;

        modelDiscoveryInProgress = true;
        Thread t = new Thread(() -> {
            try {
                openModelMenu();
                // Send the 1-based number key (no Enter — menu responds to number keys)
                connector.write(String.valueOf(idx + 1));
                Thread.sleep(500);
            } catch (IOException | InterruptedException ex) {
                LOG.warning("model switch failed: " + ex.getMessage());
            } finally {
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                SwingUtilities.invokeLater(() -> modelDiscoveryInProgress = false);
            }
        }, "claude-model-switch");
        t.setDaemon(true);
        t.start();
    }

    /** Starts background version discovery. */
    private void startVersionDiscovery() {
        ClaudeProcess cp = claudeProcess;
        if (cp == null) return;
        Thread t = new Thread(() -> {
            String ver = cp.readVersion();
            SwingUtilities.invokeLater(() -> versionLabel.setText(ver));
        }, "claude-version");
        t.setDaemon(true);
        t.start();
    }

    /** Polls screen state every 500ms; populates model combo once on first Ready. */
    private void startStatusPollTimer() {
        statusTimer = new javax.swing.Timer(500, e -> pollScreenState());
        statusTimer.start();
    }

    private void pollScreenState() {
        if (terminalWidget == null) return;
        TerminalTextBuffer buf = terminalWidget.getTerminalTextBuffer();
        List<String> lines = buf.getScreenBuffer().getLineTexts();

        java.util.Optional<String> plan = screenContentDetector.detectPlanName(lines);
        planLabel.setText(plan.orElse(""));

        // STARTING → READY: one-shot transition when ❯ prompt appears
        if (lifecycle == SessionLifecycle.STARTING
                && screenContentDetector.detectInputPromptReady(lines)) {
            applyState(SessionLifecycle.READY);
        }

        // On first READY: discover models and detect initial edit mode
        if (lifecycle == SessionLifecycle.READY && !modelComboPopulated && !modelDiscoveryInProgress) {
            discoverModels();
            detectAndApplyInitialEditMode(lines);
        }

        // Continuously sync CC screen mode → combo (skip while switch or discovery is in progress)
        if (lifecycle == SessionLifecycle.READY
                && modelComboPopulated && !modeSwitchInProgress && !modelDiscoveryInProgress) {
            screenContentDetector.detectEditMode(lines).ifPresent(detected -> {
                if (!detected.equals(editMode)) {
                    editMode = detected;
                    registerEditMode(detected);
                    int idx = editModeIndexOf(detected);
                    if (idx >= 0) {
                        editModeCombo.removeActionListener(editModeCombo.getActionListeners()[0]);
                        editModeCombo.setSelectedIndex(idx);
                        editModeCombo.addActionListener(ae -> onEditModeComboChanged());
                    }
                }
            });
        }
    }

    private void detectAndApplyInitialEditMode(List<String> lines) {
        java.util.Optional<String> mode = screenContentDetector.detectEditMode(lines);
        mode.ifPresent(m -> {
            editMode = m;
            registerEditMode(m);
            int idx = editModeIndexOf(m);
            if (idx >= 0 && editModeCombo.getActionListeners().length > 0) {
                editModeCombo.removeActionListener(editModeCombo.getActionListeners()[0]);
                editModeCombo.setSelectedIndex(idx);
                editModeCombo.addActionListener(ae -> onEditModeComboChanged());
            }
        });
    }

    private int editModeIndexOf(String value) {
        for (int i = 0; i < EDIT_MODE_VALUES.length; i++) {
            if (EDIT_MODE_VALUES[i].equals(value)) return i;
        }
        return -1;
    }

    /**
     * Opens the {@code /model} selection menu via a safe three-step sequence:
     * <ol>
     *   <li>Type {@code /model} — autocomplete popup appears</li>
     *   <li>200 ms</li>
     *   <li>ESC — dismiss autocomplete; {@code /model} stays in the input field</li>
     *   <li>200 ms</li>
     *   <li>{@code \r} — execute the command (no autocomplete active)</li>
     *   <li>200 ms — wait for the numbered menu to render</li>
     * </ol>
     */
    private void openModelMenu() throws IOException, InterruptedException {
        connector.write("/model");
        Thread.sleep(200);
        connector.write(PTY_ESC);   // dismiss autocomplete; "/model" stays in input
        Thread.sleep(200);
        connector.write("\r");       // execute now that autocomplete is gone
        Thread.sleep(200);
    }

    /** Sends /model to PTY and parses the screen after the menu renders to get model list. */
    private void discoverModels() {
        if (connector == null || modelComboPopulated || modelDiscoveryInProgress) return;
        if (modelDiscoveryAttempts >= MAX_MODEL_DISCOVERY_ATTEMPTS) return;
        modelComboPopulated = true;  // set early to avoid re-entry
        modelDiscoveryInProgress = true;
        modelDiscoveryAttempts++;
        applyState(SessionLifecycle.WORKING);

        Thread t = new Thread(() -> {
            try {
                openModelMenu();

                if (terminalWidget == null) return;
                TerminalTextBuffer buf = terminalWidget.getTerminalTextBuffer();
                List<String> lines = buf.getScreenBuffer().getLineTexts();
                ModelDiscovery discovery = parseModelDiscovery(lines);
                List<String> models = discovery.models();
                int selIdx = discovery.currentIndex();

                // Dismiss the model menu
                connector.write(new byte[]{0x1b});  // Esc

                if (!models.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
                        for (String model : models) m.addElement(model);
                        modelCombo.setModel(m);
                        if (selIdx >= 0) modelCombo.setSelectedIndex(selIdx);
                    });
                    onClaudeIdle();
                } else {
                    // Allow retry (up to MAX_MODEL_DISCOVERY_ATTEMPTS)
                    SwingUtilities.invokeLater(() -> modelComboPopulated = false);
                    onClaudeIdle();
                }
            } catch (IOException | InterruptedException ex) {
                LOG.warning("discoverModels failed: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> modelComboPopulated = false);
                onClaudeIdle();
            } finally {
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                SwingUtilities.invokeLater(() -> modelDiscoveryInProgress = false);
            }
        }, "claude-model-discovery");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Parses model names from the rendered screen after {@code /model} was sent, and
     * identifies the currently selected model by the {@code ✔} check mark Claude renders
     * on the active entry (new format) or the cursor glyph (legacy format).
     *
     * <p>Supports two formats:
     * <ul>
     *   <li>New numbered menu: {@code ❯ 1. Default (recommended) ✔  Sonnet 4.6 · Best for everyday tasks}
     *       → extracts version string {@code Sonnet 4.6}; entry with {@code ✔} is current</li>
     *   <li>Legacy: {@code claude-opus-4-5} — lines starting with {@code claude-};
     *       entry prefixed with cursor glyph (❯ ▶ >) is current</li>
     * </ul>
     *
     * @return {@link ModelDiscovery} with all model names and the index of the active model
     *         ({@code -1} if not detected)
     */
    static ModelDiscovery parseModelDiscovery(List<String> lines) {
        List<String> models = new ArrayList<>();
        int currentIndex = -1;
        java.util.regex.Pattern versionTailPat =
                java.util.regex.Pattern.compile("([A-Z][a-z]+\\s+\\d+\\.\\d+)\\s*$");
        for (String line : lines) {
            boolean hasCursor = line.trim().matches("^[❯▶>].*");
            boolean hasCheck  = line.contains("\u2714");  // ✔ marks the active model
            // Strip Ink cursor glyphs (❯ ▶ >) that prefix the selected item
            String trimmed = line.trim().replaceFirst("^[❯▶>]\\s*", "").trim();
            // New numbered format: "N. DisplayName ✔  VersionStr · Description"
            if (trimmed.matches("^\\d+\\..*")) {
                // Version is always at the tail of the part before ·; ✔ appears before the version
                String leftPart = trimmed.split("[·\u00b7]", 2)[0];
                java.util.regex.Matcher m = versionTailPat.matcher(leftPart);
                if (m.find()) {
                    if (hasCheck) currentIndex = models.size();
                    models.add(m.group(1).trim());
                    continue;
                }
            }
            // Legacy format: claude-xxx (cursor glyph marks the active entry)
            if (trimmed.startsWith("claude-") && !trimmed.contains(" ")) {
                if (hasCursor) currentIndex = models.size();
                models.add(trimmed);
            } else if (trimmed.matches("(?i)claude[\\-/][\\w\\-\\.]+")) {
                if (hasCursor) currentIndex = models.size();
                models.add(trimmed);
            }
        }
        return new ModelDiscovery(models, currentIndex);
    }

    /** Convenience wrapper — returns only the model list (used by tests). */
    static List<String> parseModelList(List<String> lines) {
        return parseModelDiscovery(lines).models();
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

    /** Called by promptFlushTimer when PTY output goes silent. Reads the rendered screen. */
    private void flushPendingPrompt() {
        if (modelDiscoveryInProgress) {
            return;
        }

        java.util.Optional<io.github.nbclaudecodegui.model.ChoiceMenuModel> req = java.util.Optional.empty();
        if (terminalWidget != null) {
            TerminalTextBuffer buf = terminalWidget.getTerminalTextBuffer();
            java.util.List<String> lines = buf.getScreenBuffer().getLineTexts();
            req = screenContentDetector.detectChoiceMenu(lines);
        }

        if (choiceMenuPanel.isVisible()) {
            // Panel already shown — dismiss it if Claude has cleared the menu from screen
            if (req.isEmpty()) {
                LOG.info("[screen prompt] menu gone from screen, dismissing panel");
                choiceMenuPanel.dismiss();
                inputPanel.setVisible(true);
                revalidate();
                repaint();
            }
            return;
        }

        req.ifPresent(r -> {
            LOG.info("[screen prompt flush] text=\"" + r.text() + "\" | options=" + r.options());
            inputPanel.setVisible(false);
            choiceMenuPanel.show(r, answer -> {
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
     */
    private void writePtyAnswer(String answer) {
        try {
            if (answer == null) {
                LOG.info("[PTY write] \\x1b (Cancel/ESC)");
                sendCancelToPty(new byte[]{0x1b});
            } else if (answer.startsWith("TYPE:")) {
                // Bug 4: type-input option — send the option digit first to activate
                // Claude's text-entry mode, then the typed text + \r.
                int sep = answer.indexOf(':', 5);
                String digit = answer.substring(5, sep);
                String text = answer.substring(sep + 1);
                LOG.info("[PTY write] type-input digit=" + digit + " text=" + text);
                Thread t = new Thread(() -> {
                    try {
                        connector.write(digit);
                        Thread.sleep(200);
                        connector.write(text);
                        Thread.sleep(200);
                        connector.write("\r");
                    } catch (java.io.IOException ex) {
                        SwingUtilities.invokeLater(() -> showError("Write failed: " + ex.getMessage()));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }, "pty-typeinput");
                t.setDaemon(true);
                t.start();
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

    /**
     * Applies {@code s} as the new lifecycle state and updates UI components accordingly.
     * Safe to call from any thread; dispatches to EDT internally.
     */
    void applyState(SessionLifecycle s) {
        SwingUtilities.invokeLater(() -> {
            lifecycle = s;
            stateLabel.setText(switch (s) {
                case STARTING -> "Starting";
                case READY    -> "Ready";
                case WORKING  -> "Working";
            });
            boolean ready = s == SessionLifecycle.READY;
            sendButton.setEnabled(ready);
            cancelButton.setEnabled(s == SessionLifecycle.WORKING);
            modelCombo.setEnabled(ready && modelComboPopulated);
        });
    }

    /** Called when Claude finishes its turn (Stop hook) — re-enables Send, disables Cancel. */
    public void onClaudeIdle() {
        applyState(SessionLifecycle.READY);
        SwingUtilities.invokeLater(() -> inputArea.requestFocusInWindow());
    }

    /** Called before a PTY permission dialog appears (PermissionRequest hook) — triggers screen scan. */
    public void triggerPromptScan() {
        SwingUtilities.invokeLater(this::flushPendingPrompt);
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
                NbBundle.getMessage(ClaudePromptPanel.class, "LBL_SelectProject")));
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
