package io.github.nbclaudecodegui.ui;

import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import io.github.nbclaudecodegui.controller.ClaudeSessionController;
import io.github.nbclaudecodegui.model.ChoiceMenuModel;
import io.github.nbclaudecodegui.model.ClaudeSessionModel;
import io.github.nbclaudecodegui.model.SessionLifecycle;
import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * TopComponent representing a single Claude Code session.
 *
 * <p>Owns the full session lifecycle: the MVC triple
 * ({@link ClaudeSessionModel}, {@link ClaudeSessionController}),
 * the directory/profile selector ({@link ClaudeSessionSelectorPanel}),
 * the embedded JediTerm terminal, the interactive-choice menu
 * ({@link ChoiceMenuPanel}), the prompt input ({@link ClaudePromptPanel}),
 * and the status bar.
 *
 * <p>Layout while idle (no session):
 * <pre>
 *   NORTH  — ClaudeSessionSelectorPanel
 *   CENTER — placeholder label
 *   SOUTH  — status bar (hidden)
 * </pre>
 *
 * Layout while a session is active:
 * <pre>
 *   NORTH  — (selector hidden)
 *   CENTER — JSplitPane
 *               top    — JediTermWidget
 *               bottom — southStack
 *                          NORTH — ChoiceMenuPanel (visible only during prompts)
 *                          CENTER — ClaudePromptPanel (input area)
 *   SOUTH  — status bar
 * </pre>
 *
 * <p>On IDE restart the working directory and profile name are restored via
 * {@link #readExternal} / {@link #writeExternal}.
 */
@TopComponent.Description(
    preferredID = "ClaudeSessionTopComponent",  // kept for persistence compatibility
    iconBase = "io/github/nbclaudecodegui/icons/claude-icon.png",
    persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(
    mode = "editor",
    openAtStartup = false
)
public class ClaudeSessionTab extends TopComponent
        implements ClaudeSessionModel.ClaudeSessionModelListener {

    private static final Logger LOG = Logger.getLogger(ClaudeSessionTab.class.getName());

    private static final String ICON =
            "io/github/nbclaudecodegui/icons/claude-icon.png";

    // -------------------------------------------------------------------------
    // Edit mode constants
    // -------------------------------------------------------------------------

    static final String[] EDIT_MODE_LABELS = {"Plan Mode", "Ask on Edit", "Accept on Edit"};
    static final String[] EDIT_MODE_VALUES = {"plan",      "default",     "acceptEdits"};

    // -------------------------------------------------------------------------
    // Persistence fields
    // -------------------------------------------------------------------------

    /** Set by {@link #componentClosed()} so {@link #componentOpened()} can restart in same dir. */
    private String pathToRestore;

    /** Restored from serialized state after an IDE restart. */
    private String savedPath;

    /** Profile name restored from serialized state; {@code null} = Default. */
    private String savedProfileName;

    // -------------------------------------------------------------------------
    // MVC
    // -------------------------------------------------------------------------

    private final ClaudeSessionModel      model      = new ClaudeSessionModel();
    private final ClaudeSessionController controller =
            new ClaudeSessionController(model, this::getScreenLines);

    /** Logging/debugging tag, e.g. {@code "[my-project] "}. */
    private String sessionTag = "";

    // -------------------------------------------------------------------------
    // UI — selector + placeholder
    // -------------------------------------------------------------------------

    private final ClaudeSessionSelectorPanel selectorPanel;
    private final JLabel                     placeholderLabel;

    // -------------------------------------------------------------------------
    // UI — session (created once a session starts, nulled on stop)
    // -------------------------------------------------------------------------

    private JediTermWidget  terminalWidget;
    private JSplitPane      splitPane;
    private JPanel          southStack;

    // -------------------------------------------------------------------------
    // UI — always present after construction
    // -------------------------------------------------------------------------

    private final ChoiceMenuPanel  choiceMenuPanel;
    private final ClaudePromptPanel promptPanel;

    // -------------------------------------------------------------------------
    // UI — status bar
    // -------------------------------------------------------------------------

    private final JPanel            statusBar;
    private final JComboBox<String> editModeCombo;
    private final JComboBox<String> modelCombo;
    private final JLabel            stateLabel;
    private final JLabel            planLabel;
    private final JLabel            versionLabel;

    /** Cached lifecycle so {@link #onModelListChanged} can correctly enable the combo. */
    private volatile SessionLifecycle currentLifecycle;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Creates a new empty session (no directory selected). */
    public ClaudeSessionTab() {
        this(null);
    }

    /**
     * Creates a session pre-configured for the given directory.
     *
     * @param dir working directory, or {@code null} for none
     */
    public ClaudeSessionTab(File dir) {

        // --- status bar ---
        editModeCombo = new JComboBox<>(EDIT_MODE_LABELS);
        editModeCombo.setMaximumSize(new Dimension(130, 24));
        editModeCombo.setToolTipText("Edit mode");
        editModeCombo.addActionListener(e -> onEditModeComboChanged());

        modelCombo = new JComboBox<>();
        modelCombo.setMaximumSize(new Dimension(200, 24));
        modelCombo.setToolTipText("Active model");
        modelCombo.setEnabled(false);
        modelCombo.addActionListener(e -> onModelComboChanged());

        stateLabel   = new JLabel("Ready");
        planLabel    = new JLabel("");
        versionLabel = new JLabel("");
        stateLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        planLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        versionLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        stateLabel.setToolTipText("Session state: Ready or Working");
        planLabel.setToolTipText("Active plan file (if any)");
        versionLabel.setToolTipText("Claude CLI version");

        statusBar = new JPanel();
        statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        statusBar.add(Box.createRigidArea(new Dimension(4, 0)));
        statusBar.add(editModeCombo);
        statusBar.add(Box.createRigidArea(new Dimension(4, 0)));
        statusBar.add(makeSep());
        statusBar.add(Box.createRigidArea(new Dimension(4, 0)));
        statusBar.add(modelCombo);
        statusBar.add(Box.createHorizontalGlue());
        statusBar.add(stateLabel);
        statusBar.add(makeSep());
        statusBar.add(planLabel);
        statusBar.add(makeSep());
        statusBar.add(versionLabel);
        statusBar.add(Box.createRigidArea(new Dimension(4, 0)));
        statusBar.setVisible(false);

        // --- selector + placeholder ---
        selectorPanel    = new ClaudeSessionSelectorPanel(dir, this::onDirectoryOpened);
        placeholderLabel = new JLabel(
                NbBundle.getMessage(ClaudeSessionSelectorPanel.class, "LBL_SelectDir"),
                SwingConstants.CENTER);
        placeholderLabel.setForeground(Color.GRAY);

        // --- chat UI ---
        choiceMenuPanel = new ChoiceMenuPanel(() -> {
            java.io.File wd = model.getWorkingDirectory();
            return wd != null ? wd.getAbsolutePath() : null;
        });
        promptPanel     = new ClaudePromptPanel(
                this::sendPrompt,
                this::cancelPrompt,
                controller::sendShiftTab,
                model::getPromptHistory,
                () -> {
                    File wd = model.getWorkingDirectory();
                    return wd != null ? wd.getAbsolutePath() : null;
                });

        // --- layout ---
        setLayout(new BorderLayout());
        add(selectorPanel,    BorderLayout.NORTH);
        add(placeholderLabel, BorderLayout.CENTER);
        add(statusBar,        BorderLayout.SOUTH);

        setIcon(ImageUtilities.loadImage(ICON, true));
        updateDisplayName(dir);
        model.addListener(this);
    }

    // -------------------------------------------------------------------------
    // Static factory helpers
    // -------------------------------------------------------------------------

    /**
     * Opens an empty session (toolbar action).
     *
     * <p>Focuses an existing idle session if one is open; otherwise opens a new one.
     */
    public static void openNewOrFocus() {
        SwingUtilities.invokeLater(() -> {
            for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
                if (tc instanceof ClaudeSessionTab tab && !tab.isSessionActive()) {
                    tab.requestActive();
                    return;
                }
            }
            ClaudeSessionTab tab = new ClaudeSessionTab();
            tab.open();
            tab.requestActive();
        });
    }

    /**
     * Opens a session for {@code dir} using the Default profile.
     *
     * @param dir working directory
     */
    public static void openForDirectory(File dir) {
        openForDirectory(dir, null);
    }

    /**
     * Opens a session for {@code dir} with the specified profile.
     *
     * <p>Focuses an existing session for {@code dir} if one is already open.
     *
     * @param dir         working directory
     * @param profileName profile name, or {@code null} for Default
     */
    public static void openForDirectory(File dir, String profileName) {
        SwingUtilities.invokeLater(() -> {
            for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
                if (tc instanceof ClaudeSessionTab tab
                        && dir.equals(tab.getWorkingDirectory())) {
                    tab.requestActive();
                    return;
                }
            }
            ClaudeSessionTab tab = new ClaudeSessionTab(dir);
            tab.open();
            tab.requestActive();
            tab.autoStart(dir, profileName);
        });
    }

    // -------------------------------------------------------------------------
    // TopComponent lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void componentOpened() {
        super.componentOpened();
        String path        = savedPath != null ? savedPath : pathToRestore;
        String profileName = savedProfileName;
        savedPath        = null;
        pathToRestore    = null;
        savedProfileName = null;

        if (path != null && !path.isBlank()) {
            File dir = new File(path);
            if (dir.isDirectory()) {
                autoStart(dir, profileName);
                return;
            }
        }
        updateDisplayName(null);
    }

    @Override
    protected void componentClosed() {
        super.componentClosed();
        File dir = model.getWorkingDirectory();
        pathToRestore = (dir != null) ? dir.getAbsolutePath() : null;
        stopProcess();
    }

    @Override
    protected void componentActivated() {
        super.componentActivated();
        requestFocusOnInput();
    }

    @Override
    public boolean canClose() {
        return true;
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_ALWAYS;
    }

    // -------------------------------------------------------------------------
    // Persistence (IDE restart)
    // -------------------------------------------------------------------------

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        File dir = model.getWorkingDirectory();
        out.writeUTF(dir != null ? dir.getAbsolutePath() : "");
        out.writeUTF(selectorPanel.getSelectedProfileName());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        savedPath = in.readUTF();
        try {
            savedProfileName = in.readUTF();
        } catch (java.io.EOFException ignored) {
            savedProfileName = null;
        }
    }

    // -------------------------------------------------------------------------
    // ClaudeSessionModelListener
    // -------------------------------------------------------------------------

    /** Updates status bar and prompt panel on lifecycle transitions. */
    @Override
    public void onLifecycleChanged(SessionLifecycle s) {
        currentLifecycle = s;
        if (!statusBar.isVisible()) {
            statusBar.setVisible(true);
            Thread vt = new Thread(() -> {
                String ver = controller.readVersion();
                SwingUtilities.invokeLater(() -> versionLabel.setText(ver));
            }, "claude-version");
            vt.setDaemon(true);
            vt.start();
        }
        stateLabel.setText(switch (s) {
            case STARTING -> "Starting";
            case READY    -> "Ready";
            case WORKING  -> "Working";
        });
        boolean ready = s == SessionLifecycle.READY;
        modelCombo.setEnabled(ready && modelCombo.getItemCount() > 0);
        promptPanel.setReadyState(ready);
        if (ready) promptPanel.requestFocusOnInputArea();
    }

    /** Shows / hides the choice-menu and locks the split-pane divider. */
    @Override
    public void onChoiceMenuChanged(ChoiceMenuModel menu) {
        if (menu != null) {
            if (ClaudeCodePreferences.isDebugMode()) {
                LOG.info(sessionTag + "[onChoiceMenuChanged] showing menu: \"" + menu.text() + "\"");
            }
            promptPanel.setVisible(false);
            choiceMenuPanel.show(menu, answer -> {
                if (ClaudeCodePreferences.isDebugMode()) {
                    LOG.info(sessionTag + "[PTY prompt answer] " + answer);
                }
                promptPanel.setVisible(true);
                SwingUtilities.invokeLater(this::unlockDividerFromChoiceMenu);
                revalidate();
                repaint();
                controller.writePtyAnswer(answer);
            });
            SwingUtilities.invokeLater(this::lockDividerForChoiceMenu);
            revalidate();
            repaint();
        } else {
            if (choiceMenuPanel.isVisible()) {
                choiceMenuPanel.dismiss();
                promptPanel.setVisible(true);
                SwingUtilities.invokeLater(this::unlockDividerFromChoiceMenu);
                revalidate();
                repaint();
            }
        }
    }

    /** Syncs the edit-mode combo without re-triggering the action listener. */
    @Override
    public void onEditModeChanged(String mode) {
        if (mode == null) return;
        int idx = editModeIndexOf(mode);
        if (idx >= 0 && editModeCombo.getSelectedIndex() != idx) {
            editModeCombo.removeActionListener(editModeCombo.getActionListeners()[0]);
            editModeCombo.setSelectedIndex(idx);
            editModeCombo.addActionListener(ae -> onEditModeComboChanged());
        }
    }

    /** Repopulates the model combo. */
    @Override
    public void onModelListChanged(List<String> models, int selectedIdx) {
        DefaultComboBoxModel<String> cbModel = new DefaultComboBoxModel<>();
        for (String m : models) cbModel.addElement(m);
        modelCombo.removeActionListener(modelCombo.getActionListeners().length > 0
                ? modelCombo.getActionListeners()[0] : null);
        modelCombo.setModel(cbModel);
        if (selectedIdx >= 0 && selectedIdx < models.size()) {
            modelCombo.setSelectedIndex(selectedIdx);
        }
        modelCombo.addActionListener(ae -> onModelComboChanged());
        boolean ready = currentLifecycle == SessionLifecycle.READY;
        modelCombo.setEnabled(ready && !models.isEmpty());
    }

    /** Updates the tab title when the working directory is confirmed. */
    @Override
    public void onWorkingDirectoryChanged(File dir) {
        updateDisplayName(dir);
    }

    /** Updates the plan-file label in the status bar. */
    @Override
    public void onPlanNameChanged(String planName) {
        planLabel.setText(planName != null ? planName : "");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the working directory of this session, or {@code null} if not yet selected.
     *
     * @return working directory or {@code null}
     */
    public File getWorkingDirectory() {
        return model.getWorkingDirectory();
    }

    /**
     * Programmatically sets the session edit mode (e.g. {@code "acceptEdits"}).
     * No-op if the value is not a known mode or is already selected.
     *
     * @param value the edit mode value (one of {@code EDIT_MODE_VALUES})
     */
    public void setEditMode(String value) {
        int idx = editModeIndexOf(value);
        if (idx >= 0 && editModeCombo.getSelectedIndex() != idx) {
            editModeCombo.removeActionListener(editModeCombo.getActionListeners()[0]);
            editModeCombo.setSelectedIndex(idx);
            editModeCombo.addActionListener(ae -> onEditModeComboChanged());
            controller.onEditModeComboChanged(value);
        }
    }

    /**
     * Returns {@code true} if a session is currently active (controls locked).
     *
     * @return {@code true} when a process is running
     */
    public boolean isSessionActive() {
        return selectorPanel.isLocked();
    }

    /**
     * Starts a session for {@code dir} with {@code profileName} without showing any dialogs.
     *
     * @param dir         working directory
     * @param profileName profile name, or {@code null} for Default
     */
    public void autoStart(File dir, String profileName) {
        if (dir == null || !dir.isDirectory()) return;
        selectorPanel.setPath(dir.getAbsolutePath());
        selectorPanel.setProfile(profileName);
        selectorPanel.preselectForDirectory(dir);
        selectorPanel.lock();
        startSession(dir, profileName);
    }

    /**
     * Sends Ctrl+C to the PTY to interrupt the running prompt.
     */
    public void cancelCurrentPrompt() {
        cancelPrompt();
    }

    /** Called by Stop hook — Claude finished its turn. */
    public void onClaudeIdle() {
        controller.onClaudeIdle();
    }

    /** Called by PermissionRequest hook — triggers screen scan. */
    public void triggerPromptScan() {
        controller.triggerPromptScan();
    }

    /**
     * Returns the current edit mode string.
     *
     * @return edit mode string, or {@code null} if not set
     */
    public String getEditMode() {
        return model.getEditMode();
    }

    // -------------------------------------------------------------------------
    // Session management
    // -------------------------------------------------------------------------

    /** Fired by the selector panel when the user confirms a valid directory. */
    private void onDirectoryOpened(File dir, String profileName) {
        selectorPanel.lock();
        startSession(dir, profileName);
    }

    private void startSession(File dir, String profileName) {
        sessionTag = "[" + dir.getName() + "] ";
        JediTermWidget widget = new JediTermWidget(new DefaultSettingsProvider());
        showChatLayout(widget);
        try {
            controller.startProcess(dir, profileName, widget);
        } catch (IOException ex) {
            selectorPanel.showError("Failed to start claude: " + ex.getMessage());
            selectorPanel.unlock();
        }
    }

    private void showChatLayout(JediTermWidget widget) {
        remove(placeholderLabel);
        selectorPanel.setVisible(false);
        terminalWidget = widget;

        southStack = new JPanel(new BorderLayout());
        southStack.add(choiceMenuPanel, BorderLayout.NORTH);
        southStack.add(promptPanel,     BorderLayout.CENTER);

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, widget, southStack);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(5);

        final int savedBottomHeight = NbPreferences.forModule(ClaudeSessionTab.class)
                .getInt("bottomHeight", -1);
        final boolean[] savingEnabled = {false};

        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int total = splitPane.getHeight();
                if (total <= 0) return;
                int bottom;
                if (!savingEnabled[0]) {
                    bottom = savedBottomHeight > 0 ? savedBottomHeight
                                                   : southStack.getPreferredSize().height;
                    savingEnabled[0] = true;
                } else {
                    bottom = NbPreferences.forModule(ClaudeSessionTab.class)
                            .getInt("bottomHeight", southStack.getPreferredSize().height);
                }
                int divLoc = total - splitPane.getDividerSize() - bottom;
                if (ClaudeCodePreferences.isDebugMode()) {
                    LOG.info(sessionTag + "[splitPane resize] total=" + total
                            + " bottom=" + bottom + " divLoc=" + divLoc);
                }
                splitPane.setDividerLocation(divLoc);
            }
        });
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (!savingEnabled[0] || !splitPane.isEnabled()) return;
            int total = splitPane.getHeight();
            int loc   = (int) e.getNewValue();
            if (total > 0 && loc > 0) {
                int bottom = total - splitPane.getDividerSize() - loc;
                if (bottom > 0) NbPreferences.forModule(ClaudeSessionTab.class)
                        .putInt("bottomHeight", bottom);
            }
        });

        add(splitPane, BorderLayout.CENTER);
        revalidate();
        repaint();
        widget.requestFocusInWindow();
    }

    private void stopProcess() {
        controller.stopProcess();
        if (terminalWidget != null) {
            terminalWidget.close();
            terminalWidget = null;
        }
        choiceMenuPanel.dismiss();
        promptPanel.reset();
        if (splitPane != null) {
            remove(splitPane);
            splitPane  = null;
            southStack = null;
        }
        selectorPanel.setVisible(true);
        selectorPanel.unlock();
        add(placeholderLabel, BorderLayout.CENTER);
        statusBar.setVisible(false);
        currentLifecycle = null;
        stateLabel.setText("Ready");
        versionLabel.setText("");
        planLabel.setText("");
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Input delegation
    // -------------------------------------------------------------------------

    private void sendPrompt(String text) {
        model.addPromptToHistory(text);
        controller.sendPrompt(text);
    }

    private void cancelPrompt() {
        controller.cancelPrompt();
    }

    private void requestFocusOnInput() {
        if (choiceMenuPanel.isVisible()) return;
        if (promptPanel.isVisible()) {
            promptPanel.requestFocusOnInputArea();
        } else if (terminalWidget != null) {
            terminalWidget.requestFocusInWindow();
        }
    }

    // -------------------------------------------------------------------------
    // Split-pane divider helpers
    // -------------------------------------------------------------------------

    private void lockDividerForChoiceMenu() {
        if (splitPane == null) return;
        splitPane.setEnabled(false);
        splitPane.validate(); // force layout so HTML labels compute height at actual width
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
        int bottom = NbPreferences.forModule(ClaudeSessionTab.class)
                .getInt("bottomHeight", southStack.getPreferredSize().height);
        splitPane.setDividerLocation(total - splitPane.getDividerSize() - bottom);
    }

    // -------------------------------------------------------------------------
    // Status bar helpers
    // -------------------------------------------------------------------------

    private void onEditModeComboChanged() {
        int idx = editModeCombo.getSelectedIndex();
        if (idx < 0 || idx >= EDIT_MODE_VALUES.length) return;
        controller.onEditModeComboChanged(EDIT_MODE_VALUES[idx]);
    }

    private void onModelComboChanged() {
        int idx = modelCombo.getSelectedIndex();
        if (idx < 0) return;
        controller.switchModel(idx);
    }

    private int editModeIndexOf(String value) {
        for (int i = 0; i < EDIT_MODE_VALUES.length; i++) {
            if (EDIT_MODE_VALUES[i].equals(value)) return i;
        }
        return -1;
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<String> getScreenLines() {
        if (terminalWidget == null) return java.util.Collections.emptyList();
        TerminalTextBuffer buf = terminalWidget.getTerminalTextBuffer();
        return buf.getScreenBuffer().getLineTexts();
    }

    private void updateDisplayName(File dir) {
        String name = ClaudeSessionSelectorPanel.resolveTabLabel(dir);
        setDisplayName(name);
        setToolTipText(dir != null ? dir.getAbsolutePath() : "New Claude Code session");
    }
}
