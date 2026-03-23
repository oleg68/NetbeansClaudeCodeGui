package io.github.nbclaudecodegui.ui;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import javax.swing.SwingUtilities;
import org.openide.util.ImageUtilities;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * TopComponent representing a single Claude Code session.
 *
 * <p>Closing the window (× button or Reset Windows) stops the PTY process and
 * saves the working directory to {@link #pathToRestore}. When the TC is
 * reopened (e.g. by Reset Windows or via Window menu) a new session starts
 * automatically in the same directory.
 *
 * <p>On IDE restart the directory is restored via {@link #readExternal}.
 */
@TopComponent.Description(
    preferredID = "ClaudeSessionTopComponent",
    iconBase = "io/github/nbclaudecodegui/icons/claude-icon.png",
    persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(
    mode = "editor",
    openAtStartup = false
)
public final class ClaudeSessionTopComponent extends TopComponent {

    private static final String ICON =
            "io/github/nbclaudecodegui/icons/claude-icon.png";

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

    private final ClaudePromptPanel panel;

    /** Creates a new empty session (no directory selected). */
    public ClaudeSessionTopComponent() {
        this(null);
    }

    /**
     * Creates a session pre-configured for the given directory.
     *
     * @param dir working directory, or {@code null} for none
     */
    public ClaudeSessionTopComponent(File dir) {
        panel = new ClaudePromptPanel(dir, false);
        setLayout(new java.awt.BorderLayout());
        add(panel, java.awt.BorderLayout.CENTER);
        setIcon(ImageUtilities.loadImage(ICON, true));
        updateDisplayName(dir);
        panel.setDirectoryListener(this::updateDisplayName);
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
                if (tc instanceof ClaudeSessionTopComponent stc && !stc.panel.isLocked()) {
                    stc.requestActive();
                    return;
                }
            }
            ClaudeSessionTopComponent tc = new ClaudeSessionTopComponent();
            tc.open();
            tc.requestActive();
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
                if (tc instanceof ClaudeSessionTopComponent stc
                        && dir.equals(stc.panel.getConfirmedDirectory())) {
                    stc.requestActive();
                    return;
                }
            }
            ClaudeSessionTopComponent tc = new ClaudeSessionTopComponent(dir);
            tc.open();
            tc.requestActive();
            tc.panel.autoStart(dir, profileName);
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
        File dir = panel.getConfirmedDirectory();
        pathToRestore = (dir != null) ? dir.getAbsolutePath() : null;
        panel.stopProcess();
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
     * Saves the confirmed working directory path for IDE restart.
     *
     * @param out the output stream
     * @throws IOException on I/O error
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        File dir = panel.getConfirmedDirectory();
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
    // helpers
    // -------------------------------------------------------------------------

    /**
     * Sends Ctrl+C (0x03) to the running Claude process to interrupt the current prompt.
     * No-op if no process is running.
     */
    public void cancelCurrentPrompt() {
        panel.cancelPrompt();
    }

    /** Returns the working directory of this session, or {@code null} if not yet selected. */
    public File getConfirmedDirectory() {
        return panel.getConfirmedDirectory();
    }

    /** Called by Stop hook — Claude finished its turn, re-enables Send button. */
    public void onClaudeIdle() { panel.onClaudeIdle(); }

    /** Called by PermissionRequest hook — triggers screen scan for upcoming Ink menu. */
    public void triggerPromptScan() { panel.triggerPromptScan(); }

    /** Returns the current edit mode: {@code "default"}, {@code "acceptEdits"}, or {@code "plan"}. */
    public String getEditMode() { return panel.getEditMode(); }

    private void updateDisplayName(File dir) {
        String name = ClaudePromptPanel.resolveTabLabel(dir);
        setDisplayName(name);
        setToolTipText(dir != null ? dir.getAbsolutePath() : "New Claude Code session");
    }
}
