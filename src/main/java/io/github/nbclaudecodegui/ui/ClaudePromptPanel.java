package io.github.nbclaudecodegui.ui;

import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import io.github.nbclaudecodegui.controller.ClaudeSessionController;
import io.github.nbclaudecodegui.model.ChoiceMenuModel;
import io.github.nbclaudecodegui.model.ClaudeSessionModel;
import io.github.nbclaudecodegui.model.SessionLifecycle;
import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import io.github.nbclaudecodegui.settings.ClaudeProfile;
import io.github.nbclaudecodegui.settings.ClaudeProfileStore;
import io.github.nbclaudecodegui.settings.ClaudeProjectProperties;
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
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
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
 * View for a single Claude Code session tab.
 *
 * <p>Passive View in the MVC structure: all state is owned by
 * {@link ClaudeSessionModel}; all business logic runs in
 * {@link ClaudeSessionController}. This class only:
 * <ul>
 *   <li>Builds and lays out Swing components.</li>
 *   <li>Translates user gestures into controller calls.</li>
 *   <li>Implements {@link ClaudeSessionModel.ClaudeSessionModelListener} to
 *       keep the UI in sync with the model (lifecycle buttons and choice menus).</li>
 * </ul>
 *
 * <p>The status bar (edit-mode combo, model combo, state label, etc.) lives in
 * {@link ClaudeSessionTab} so it is always visible outside the split pane.
 *
 * <p>Top bar: project combo, path combo, Browse, Open, Settings.
 * After Open: an embedded JediTerm terminal runs the Claude TUI,
 * separated from the input area by a draggable JSplitPane divider.
 */
public final class ClaudePromptPanel extends JPanel
        implements ClaudeSessionModel.ClaudeSessionModelListener {

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

    private static final String ICON_SEND   = "\u25b6";  // ▶
    private static final String ICON_CANCEL = "\u2716";  // ✖

    // -------------------------------------------------------------------------
    // MVC wiring
    // -------------------------------------------------------------------------

    private final ClaudeSessionModel model = new ClaudeSessionModel();
    private final ClaudeSessionController controller =
            new ClaudeSessionController(model, this::getScreenLines);

    // -------------------------------------------------------------------------
    // UI components — top bar
    // -------------------------------------------------------------------------

    private final JComboBox<ProjectItem> projectCombo;
    private final JComboBox<String>      pathCombo;
    private final JButton                browseButton;
    private final JComboBox<String>      profileCombo;
    private final JButton                openButton;
    private final JLabel                 errorLabel;
    private final JLabel                 placeholderLabel;

    // -------------------------------------------------------------------------
    // UI components — chat area
    // -------------------------------------------------------------------------

    private JediTermWidget      terminalWidget;
    private JSplitPane          splitPane;
    private JPanel              topBar;
    private JPanel              inputPanel;
    private JPanel              southStack;
    private JTextArea           inputArea;
    private JButton             sendButton;
    private JButton             cancelButton;
    private ChoiceMenuPanel     choiceMenuPanel;

    // -------------------------------------------------------------------------
    // View-local state
    // -------------------------------------------------------------------------

    /** Logging/debugging tag, e.g. {@code "[my-project] "}. */
    private String sessionTag = "";

    /** Current position in prompt history for keyboard navigation; {@code -1} = newest. */
    private int historyIndex = -1;

    private boolean suppressProjectListener;

    // -------------------------------------------------------------------------
    // Constructors
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

        if (directory != null) {
            model.setWorkingDirectory(directory);
        }

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

        // --- profile combo ---
        profileCombo = new JComboBox<>();
        profileCombo.setToolTipText("Connection profile");
        populateProfileCombo();

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
        controlBar.add(profileCombo);
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
                SwingConstants.CENTER);
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

        // --- south stack (choice menu + input area) ---
        southStack = new JPanel(new BorderLayout());
        inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);
        inputPanel.add(buttonCol, BorderLayout.EAST);
        inputPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        inputPanel.setVisible(false);

        southStack.add(choiceMenuPanel, BorderLayout.NORTH);
        southStack.add(inputPanel, BorderLayout.CENTER);

        add(southStack, BorderLayout.SOUTH);

        // Register model listener (Panel handles lifecycle buttons + choice menu)
        model.addListener(this);

        if (locked) {
            setControlsLocked(true);
            startProcess();
        }
    }

    // -------------------------------------------------------------------------
    // ClaudeSessionModelListener implementation
    // -------------------------------------------------------------------------

    /**
     * Updates the Send/Cancel enable state and requests focus when READY.
     */
    @Override
    public void onLifecycleChanged(SessionLifecycle s) {
        boolean ready = s == SessionLifecycle.READY;
        sendButton.setEnabled(ready);
        cancelButton.setEnabled(s == SessionLifecycle.WORKING);
        if (s == SessionLifecycle.READY) {
            inputArea.requestFocusInWindow();
        }
    }

    /**
     * Shows or hides the choice-menu panel based on the new model state.
     */
    @Override
    public void onChoiceMenuChanged(ChoiceMenuModel menu) {
        if (menu != null) {
            if (ClaudeCodePreferences.isDebugMode()) {
                LOG.info(sessionTag + "[screen prompt flush] text=\"" + menu.text()
                        + "\" | options=" + menu.options());
            }
            inputPanel.setVisible(false);
            choiceMenuPanel.show(menu, answer -> {
                if (ClaudeCodePreferences.isDebugMode()) {
                    LOG.info(sessionTag + "[PTY prompt answer] " + answer);
                }
                inputPanel.setVisible(true);
                revalidate();
                repaint();
                controller.writePtyAnswer(answer);
            });
            revalidate();
            repaint();
        } else {
            if (choiceMenuPanel.isVisible()) {
                choiceMenuPanel.dismiss();
                inputPanel.setVisible(true);
                revalidate();
                repaint();
            }
        }
    }

    // -------------------------------------------------------------------------
    // public API
    // -------------------------------------------------------------------------

    /**
     * Registers an additional model listener (e.g. {@link ClaudeSessionTab}
     * listening for status bar updates).
     *
     * @param l the listener to add
     */
    public void addModelListener(ClaudeSessionModel.ClaudeSessionModelListener l) {
        model.addListener(l);
    }

    /** Returns the working directory, or {@code null} if none. */
    public File getWorkingDirectory() {
        return model.getWorkingDirectory();
    }

    /** Returns {@code true} if the directory controls are locked. */
    public boolean isLocked() {
        return !openButton.isEnabled();
    }

    /**
     * Returns {@code true} if a PTY process is currently running.
     */
    public boolean hasLiveProcess() {
        return controller.hasLiveProcess();
    }

    /**
     * Returns the current edit mode: {@code "default"}, {@code "acceptEdits"}, or {@code "plan"}.
     */
    public String getEditMode() {
        return model.getEditMode();
    }

    /**
     * Starts the session for {@code dir} programmatically without UI dialogs,
     * using the Default profile.
     *
     * @param dir working directory
     */
    public void autoStart(File dir) {
        autoStart(dir, null);
    }

    /**
     * Starts the session for {@code dir} with the named profile selected.
     *
     * @param dir         working directory
     * @param profileName profile name, or {@code null}/{@code ""} for Default
     */
    public void autoStart(File dir, String profileName) {
        if (dir == null || !dir.isDirectory()) return;
        model.setWorkingDirectory(dir);
        pathCombo.setSelectedItem(dir.getAbsolutePath());
        if (profileName != null && !profileName.isBlank()) {
            profileCombo.setSelectedItem(profileName);
        } else {
            profileCombo.setSelectedIndex(0);
        }
        setControlsLocked(true);
        placeholderLabel.setVisible(false);
        startProcess();
    }

    /**
     * Returns the name of the currently selected profile.
     *
     * @return selected profile name
     */
    public String getSelectedProfileName() {
        Object sel = profileCombo.getSelectedItem();
        return sel != null ? sel.toString() : ClaudeProfile.DEFAULT_NAME;
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
        controller.stopProcess();
        if (terminalWidget != null) {
            terminalWidget.close();
            terminalWidget = null;
        }
        choiceMenuPanel.dismiss();
        inputPanel.setVisible(false);
        topBar.setVisible(true);
        if (splitPane != null) {
            remove(splitPane);
            splitPane = null;
            add(southStack, BorderLayout.SOUTH);
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

    /** Returns the Claude CLI version string (blocks until the subprocess exits). */
    public String readVersion() {
        return controller.readVersion();
    }

    /** Forwards an edit-mode change from the status bar combo to the controller. */
    public void sendEditModeChange(String mode) {
        controller.onEditModeComboChanged(mode);
    }

    /** Switches the active model by index (called from the status bar combo). */
    public void switchModel(int idx) {
        controller.switchModel(idx);
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
                String assignedProfile = ClaudeProjectProperties.getProfileName(dir);
                if (!assignedProfile.isBlank()) {
                    profileCombo.setSelectedItem(assignedProfile);
                } else {
                    profileCombo.setSelectedIndex(0);
                }
            }
        }
    }

    private void populateProfileCombo() {
        DefaultComboBoxModel<String> cbModel = new DefaultComboBoxModel<>();
        for (ClaudeProfile p : ClaudeProfileStore.getProfiles()) {
            cbModel.addElement(p.getName());
        }
        profileCombo.setModel(cbModel);
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
        model.setWorkingDirectory(dir);
        setControlsLocked(true);
        placeholderLabel.setVisible(false);

        startProcess();
    }

    // -------------------------------------------------------------------------
    // process management
    // -------------------------------------------------------------------------

    private void startProcess() {
        File dir = model.getWorkingDirectory();
        if (dir == null) return;

        sessionTag = "[" + dir.getName() + "] ";

        // Build terminal widget and layout on EDT
        JediTermWidget widget = new JediTermWidget(new DefaultSettingsProvider());
        showChatLayout(widget);

        // Delegate PTY start to controller
        String profileName = (String) profileCombo.getSelectedItem();
        try {
            controller.startProcess(dir, profileName, widget);
        } catch (IOException ex) {
            showError("Failed to start claude: " + ex.getMessage());
        }
    }

    /**
     * Sets up the split-pane layout for the chat session and installs the
     * terminal widget. Called from {@link #startProcess()} before
     * {@link ClaudeSessionController#startProcess} is invoked.
     */
    private void showChatLayout(JediTermWidget widget) {
        remove(placeholderLabel);
        remove(southStack);

        this.terminalWidget = widget;

        topBar.setVisible(false);
        inputPanel.setVisible(true);

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, widget, southStack);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(5);

        final int savedBottomHeight = NbPreferences.forModule(ClaudePromptPanel.class)
                .getInt("bottomHeight", -1);
        final boolean[] savingEnabled = {false};

        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int total = splitPane.getHeight();
                if (total <= 0) return;
                if (!savingEnabled[0]) {
                    int bottom = savedBottomHeight > 0 ? savedBottomHeight
                                                       : southStack.getPreferredSize().height;
                    splitPane.setDividerLocation(total - splitPane.getDividerSize() - bottom);
                    savingEnabled[0] = true;
                } else {
                    int bottom = NbPreferences.forModule(ClaudePromptPanel.class)
                            .getInt("bottomHeight", southStack.getPreferredSize().height);
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
                if (bottom > 0) NbPreferences.forModule(ClaudePromptPanel.class)
                        .putInt("bottomHeight", bottom);
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
    }

    private void lockDividerForChoiceMenu() {
        if (splitPane == null) return;
        splitPane.setEnabled(false);
        int total = splitPane.getHeight();
        if (total <= 0) return;
        int natural = choiceMenuPanel.getPreferredSize().height;
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

    // -------------------------------------------------------------------------
    // input handling
    // -------------------------------------------------------------------------

    private void sendPrompt() {
        String text = inputArea.getText();
        if (text.isEmpty()) return;
        inputArea.setText("");
        inputArea.requestFocusInWindow();
        historyIndex = -1;
        controller.sendPrompt(text);
    }

    void cancelPrompt() {
        controller.cancelPrompt();
    }

    private void bindSendKey(JTextArea area) {
        area.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Shift+Tab → advance editModeCombo to next item (cycle) — handled by Tab
                if (e.getKeyCode() == KeyEvent.VK_TAB && e.isShiftDown()) {
                    e.consume();
                    // bubble up to parent (ClaudeSessionTab) — no direct reference needed;
                    // Tab registers its own key binding on its editModeCombo.
                    return;
                }
                // Esc → Cancel
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    if (cancelButton.isEnabled()) cancelButton.doClick();
                    return;
                }
                // Ctrl+Up — navigate prompt history backward (older)
                if (e.getKeyCode() == KeyEvent.VK_UP && e.isControlDown()
                        && !e.isShiftDown() && !e.isAltDown()) {
                    e.consume();
                    navigateHistory(1);
                    return;
                }
                // Ctrl+Down — navigate prompt history forward (newer)
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
        List<String> history = model.getPromptHistory();
        if (history.isEmpty()) return;
        historyIndex = Math.max(-1, Math.min(history.size() - 1, historyIndex + delta));
        if (historyIndex < 0) {
            inputArea.setText("");
        } else {
            inputArea.setText(history.get(historyIndex));
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

        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                List<String> h = model.getPromptHistory();
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

    // -------------------------------------------------------------------------
    // controller delegation
    // -------------------------------------------------------------------------

    /** Called when Claude finishes its turn (Stop hook) — re-enables Send, disables Cancel. */
    public void onClaudeIdle() {
        controller.onClaudeIdle();
    }

    /** Called before a PTY permission dialog appears (PermissionRequest hook) — triggers screen scan. */
    public void triggerPromptScan() {
        controller.triggerPromptScan();
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private List<String> getScreenLines() {
        if (terminalWidget == null) return java.util.Collections.emptyList();
        TerminalTextBuffer buf = terminalWidget.getTerminalTextBuffer();
        return buf.getScreenBuffer().getLineTexts();
    }

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
        DefaultComboBoxModel<ProjectItem> cbModel = new DefaultComboBoxModel<>();
        cbModel.addElement(new ProjectItem(null,
                NbBundle.getMessage(ClaudePromptPanel.class, "LBL_SelectProject")));
        for (Project p : OpenProjects.getDefault().getOpenProjects()) {
            cbModel.addElement(new ProjectItem(p,
                    ProjectUtils.getInformation(p).getDisplayName()));
        }
        projectCombo.setModel(cbModel);
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
