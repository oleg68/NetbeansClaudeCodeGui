package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.model.ClaudeSessionModel;
import io.github.nbclaudecodegui.model.SessionLifecycle;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.openide.util.ImageUtilities;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * TopComponent representing a single Claude Code session.
 *
 * <p>Hosts a {@link ClaudePromptPanel} in the center and a status bar at the
 * bottom. The status bar (edit-mode combo, model combo, state label, plan
 * label, version label) lives here so it is always visible and is never
 * obscured by the split-pane divider inside the panel.
 *
 * <p>Closing the window (× button or Reset Windows) stops the PTY process and
 * saves the working directory to {@link #pathToRestore}. When the TC is
 * reopened (e.g. by Reset Windows or via Window menu) a new session starts
 * automatically in the same directory.
 *
 * <p>On IDE restart the directory is restored via {@link #readExternal}.
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

    /**
     * Directory path set by {@link #componentClosed()} before stopping the
     * process. Used by {@link #componentOpened()} to auto-start a new session
     * in the same directory (e.g. after Reset Windows).
     */
    private String pathToRestore;

    /** Path restored from serialized state after an IDE restart. */
    private String savedPath;

    /**
     * Profile name restored from serialized state; {@code null} means Default.
     * Set by {@link #readExternal} and consumed once by {@link #componentOpened}.
     */
    private String savedProfileName;

    // -------------------------------------------------------------------------
    // Child components
    // -------------------------------------------------------------------------

    private final ClaudePromptPanel panel;

    // -------------------------------------------------------------------------
    // Status bar components
    // -------------------------------------------------------------------------

    private final JPanel            statusBar;
    private final JComboBox<String> editModeCombo;
    private final JComboBox<String> modelCombo;
    private final JLabel            stateLabel;
    private final JLabel            planLabel;
    private final JLabel            versionLabel;

    /** Tracks the current lifecycle so model combo can be correctly enabled. */
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
        panel = new ClaudePromptPanel(dir, false);

        // build status bar
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

        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        setIcon(ImageUtilities.loadImage(ICON, true));
        updateDisplayName(dir);

        panel.addModelListener(this);
    }

    // -------------------------------------------------------------------------
    // public static factory helpers
    // -------------------------------------------------------------------------

    /**
     * Opens an empty session (toolbar action).
     *
     * <p>If an existing unlocked session TC is already open, focuses it.
     * Otherwise creates a new one.
     */
    public static void openNewOrFocus() {
        SwingUtilities.invokeLater(() -> {
            for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
                if (tc instanceof ClaudeSessionTab tab && !tab.panel.isLocked()) {
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
     * Opens a session for the given directory using the Default profile.
     *
     * @param dir working directory
     */
    public static void openForDirectory(File dir) {
        openForDirectory(dir, null);
    }

    /**
     * Opens a session for the given directory with the specified profile.
     *
     * <p>If an existing session for {@code dir} is already open, focuses it.
     * Otherwise creates and opens a new session that auto-starts in {@code dir}
     * with the given profile.
     *
     * @param dir         working directory
     * @param profileName profile name to use, or {@code null} for Default
     */
    public static void openForDirectory(File dir, String profileName) {
        SwingUtilities.invokeLater(() -> {
            for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
                if (tc instanceof ClaudeSessionTab tab
                        && dir.equals(tab.panel.getWorkingDirectory())) {
                    tab.requestActive();
                    return;
                }
            }
            ClaudeSessionTab tab = new ClaudeSessionTab(dir);
            tab.open();
            tab.requestActive();
            tab.panel.autoStart(dir, profileName);
        });
    }

    // -------------------------------------------------------------------------
    // TopComponent lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts or resumes a session when the TC is opened.
     *
     * <ul>
     *   <li>After IDE restart: {@code savedPath} is set by {@link #readExternal}
     *       → auto-starts a new session.
     *   <li>After Reset Windows (or manual reopen): {@code pathToRestore} is
     *       set by {@link #componentClosed()} → auto-starts a new session.
     *   <li>Otherwise: TC opens empty, user selects directory manually.
     * </ul>
     */
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
                panel.autoStart(dir, profileName);
                return;
            }
        }
        updateDisplayName(null);  // reset stale persisted name (e.g. "Claude Code GUI")
    }

    /**
     * Saves the current directory and stops the PTY process.
     *
     * <p>The saved path is picked up by {@link #componentOpened()} the next
     * time this TC is opened — allowing Reset Windows to transparently restart
     * the session in the same directory.
     */
    @Override
    protected void componentClosed() {
        super.componentClosed();
        File dir = panel.getWorkingDirectory();
        pathToRestore = (dir != null) ? dir.getAbsolutePath() : null;
        panel.stopProcess();
        statusBar.setVisible(false);
        currentLifecycle = null;
        stateLabel.setText("Ready");
        versionLabel.setText("");
        planLabel.setText("");
    }

    /**
     * Moves keyboard focus to the prompt input area on activation.
     */
    @Override
    protected void componentActivated() {
        super.componentActivated();
        panel.requestFocusOnInput();
    }

    /**
     * Always returns {@code true} — no confirmation dialog on close.
     *
     * @return {@code true}
     */
    @Override
    public boolean canClose() {
        return true;
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_ALWAYS;
    }

    // -------------------------------------------------------------------------
    // persistence (IDE restart)
    // -------------------------------------------------------------------------

    /**
     * Saves the working directory path for IDE restart.
     *
     * @param out the output stream
     * @throws IOException on I/O error
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        File dir = panel.getWorkingDirectory();
        out.writeUTF(dir != null ? dir.getAbsolutePath() : "");
        out.writeUTF(panel.getSelectedProfileName());
    }

    /**
     * Reads the working directory path and profile name saved by
     * {@link #writeExternal}.
     *
     * <p>The profile name is looked up in {@link
     * io.github.nbclaudecodegui.settings.ClaudeProfileStore} on next open;
     * if not found the Default profile is used.
     *
     * @param in the input stream
     * @throws IOException            on I/O error
     * @throws ClassNotFoundException if a class cannot be found
     */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        savedPath = in.readUTF();
        try {
            savedProfileName = in.readUTF();
        } catch (java.io.EOFException ignored) {
            // Old serialised state without profileName — use Default
            savedProfileName = null;
        }
    }

    // -------------------------------------------------------------------------
    // ClaudeSessionModelListener — status bar + directory title
    // -------------------------------------------------------------------------

    /**
     * Shows the status bar on the first lifecycle event (session started),
     * then updates state label and button enables.
     */
    @Override
    public void onLifecycleChanged(SessionLifecycle s) {
        currentLifecycle = s;
        if (!statusBar.isVisible()) {
            statusBar.setVisible(true);
            Thread vt = new Thread(() -> {
                String ver = panel.readVersion();
                SwingUtilities.invokeLater(() -> versionLabel.setText(ver));
            }, "claude-version");
            vt.setDaemon(true);
            vt.start();
        }
        applyState(s);
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

    /** Repopulates the model combo and enables it if non-empty and session is ready. */
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
    // public API
    // -------------------------------------------------------------------------

    /**
     * Sends Ctrl+C (0x03) to the running Claude process to interrupt the current prompt.
     * No-op if no process is running.
     */
    public void cancelCurrentPrompt() {
        panel.cancelPrompt();
    }

    /** Returns the working directory of this session, or {@code null} if not yet selected. */
    public File getWorkingDirectory() {
        return panel.getWorkingDirectory();
    }

    /** Called by Stop hook — Claude finished its turn, re-enables Send button. */
    public void onClaudeIdle() { panel.onClaudeIdle(); }

    /** Called by PermissionRequest hook — triggers screen scan for upcoming Ink menu. */
    public void triggerPromptScan() { panel.triggerPromptScan(); }

    /** Returns the current edit mode: {@code "default"}, {@code "acceptEdits"}, or {@code "plan"}. */
    public String getEditMode() { return panel.getEditMode(); }

    // -------------------------------------------------------------------------
    // status bar helpers
    // -------------------------------------------------------------------------

    private void applyState(SessionLifecycle s) {
        stateLabel.setText(switch (s) {
            case STARTING -> "Starting";
            case READY    -> "Ready";
            case WORKING  -> "Working";
        });
        boolean ready = s == SessionLifecycle.READY;
        modelCombo.setEnabled(ready && modelCombo.getItemCount() > 0);
    }

    private void onEditModeComboChanged() {
        int idx = editModeCombo.getSelectedIndex();
        if (idx < 0 || idx >= EDIT_MODE_VALUES.length) return;
        panel.sendEditModeChange(EDIT_MODE_VALUES[idx]);
    }

    private void onModelComboChanged() {
        int idx = modelCombo.getSelectedIndex();
        if (idx < 0) return;
        panel.switchModel(idx);
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

    private void updateDisplayName(File dir) {
        String name = ClaudePromptPanel.resolveTabLabel(dir);
        setDisplayName(name);
        setToolTipText(dir != null ? dir.getAbsolutePath() : "New Claude Code session");
    }
}
