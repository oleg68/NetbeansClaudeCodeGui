package io.github.nbclaudecodegui.ui;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.netbeans.api.diff.Diff;
import org.netbeans.api.diff.DiffView;
import org.netbeans.api.diff.Difference;
import org.netbeans.api.diff.StreamSource;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Opens a NetBeans editor tab showing a diff of two file versions together with
 * a {@link PermissionPanel} at the bottom.
 *
 * <p>Used by both the PreToolUse HTTP hook handler and the {@code permission_prompt}
 * MCP tool so that both paths share identical UI.
 */
public final class FileDiffTab {

    private static final Logger LOGGER = Logger.getLogger(FileDiffTab.class.getName());

    private FileDiffTab() {}

    /**
     * Returns {@code true} if {@code filePath} is located inside {@code dirPath}
     * (or equals it).
     */
    public static boolean isFileUnderDirectory(String filePath, String dirPath) {
        if (filePath == null || dirPath == null) return false;
        String absFile = new java.io.File(filePath).getAbsolutePath();
        String absDir  = new java.io.File(dirPath).getAbsolutePath();
        return absFile.equals(absDir)
                || absFile.startsWith(absDir + java.io.File.separator);
    }

    /**
     * Opens the diff tab on the EDT.  All callbacks are invoked on the EDT.
     *
     * <p>After any callback (including {@code onClose}) the Claude session tab
     * whose working directory is an ancestor of {@code filePath} is activated
     * automatically.
     *
     * @param filePath     path of the file being changed (used for tab title and session lookup)
     * @param before       file content before the change
     * @param after        file content after the change
     * @param tabName      display name of the diff tab
     * @param confirmedDir if non-null, the session's working directory (hook case); the warning
     *                     is shown when the file is outside this directory. If null (MCP case),
     *                     the warning is shown when the file is outside all open sessions.
     * @param onAccept     called when the user clicks Accept
     * @param onReject     called with the (possibly empty) reason when the user clicks Reject
     * @param onCancel     called when the user clicks Cancel (caller should also send Ctrl+C)
     * @param onClose      called when the tab is closed via the × button without a decision
     */
    public static void open(
            String filePath, String before, String after, String tabName,
            String confirmedDir,
            Runnable onAccept,
            Consumer<String> onReject,
            Runnable onCancel,
            Runnable onClose) {

        SwingUtilities.invokeLater(() -> {
            // Determine if the file is outside the relevant project directory.
            // confirmedDir != null (hook): check against that specific directory.
            // confirmedDir == null (MCP): check against all open sessions.
            final boolean outsideProject;
            final String outsideWarning;
            if (confirmedDir != null) {
                outsideProject = !isFileUnderDirectory(filePath, confirmedDir);
                outsideWarning = "⚠ File is outside current project";
            } else {
                boolean insideAny = false;
                for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
                    if (tc instanceof ClaudeSessionTopComponent stc) {
                        java.io.File dir = stc.getConfirmedDirectory();
                        if (dir != null && isFileUnderDirectory(filePath, dir.getAbsolutePath())) {
                            insideAny = true;
                            break;
                        }
                    }
                }
                outsideProject = !insideAny;
                outsideWarning = "⚠ File is outside any open project";
            }

            Diff diffService = Lookup.getDefault().lookup(Diff.class);
            if (diffService == null) {
                LOGGER.warning("Diff service not available; falling back to onClose for: " + tabName);
                onClose.run();
                activateSessionForFile(filePath);
                return;
            }

            String fileName = new java.io.File(filePath).getName();
            StreamSource oldSource = textSource(fileName + " (before)", filePath, before);
            StreamSource newSource = textSource(fileName + " (after)",  filePath, after);

            final DiffView diffView;
            try {
                diffView = diffService.createDiff(oldSource, newSource);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error creating diff view for: " + tabName, e);
                onClose.run();
                activateSessionForFile(filePath);
                return;
            }

            if (diffView == null) {
                LOGGER.warning("createDiff returned null; falling back to onClose for: " + tabName);
                onClose.run();
                activateSessionForFile(filePath);
                return;
            }

            // Flag set to true once a button is clicked — prevents onClose from
            // firing again when diffTC.close() triggers componentClosed().
            AtomicBoolean decided = new AtomicBoolean(false);

            TopComponent diffTC = new TopComponent() {
                @Override
                public void componentClosed() {
                    super.componentClosed();
                    if (!decided.get()) {
                        onClose.run();
                        activateSessionForFile(filePath);
                    }
                }
            };
            diffTC.setDisplayName(tabName);
            diffTC.setLayout(new java.awt.BorderLayout());

            PermissionPanel permPanel = new PermissionPanel(
                () -> {
                    decided.set(true);
                    onAccept.run();
                    diffTC.close();
                    activateSessionForFile(filePath);
                },
                reason -> {
                    decided.set(true);
                    onReject.accept(reason);
                    diffTC.close();
                    activateSessionForFile(filePath);
                },
                () -> {
                    decided.set(true);
                    onCancel.run();
                    diffTC.close();
                    activateSessionForFile(filePath);
                }
            );

            // Keep the DiffView's own navigation toolbar (prev/next difference buttons)
            javax.swing.JToolBar toolbar = diffView.getToolBar();
            if (toolbar == null) {
                toolbar = new javax.swing.JToolBar();
            }
            toolbar.setFloatable(false);

            javax.swing.JPanel northPanel = new javax.swing.JPanel(new java.awt.BorderLayout());
            northPanel.add(toolbar, java.awt.BorderLayout.NORTH);
            if (outsideProject) {
                javax.swing.JLabel warningLabel = new javax.swing.JLabel(outsideWarning);
                warningLabel.setForeground(java.awt.Color.RED);
                warningLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 8, 2, 8));
                northPanel.add(warningLabel, java.awt.BorderLayout.SOUTH);
            }

            diffTC.add(northPanel, java.awt.BorderLayout.NORTH);
            diffTC.add(diffView.getComponent(), java.awt.BorderLayout.CENTER);
            diffTC.add(permPanel, java.awt.BorderLayout.SOUTH);

            diffTC.open();
            diffTC.requestActive();
            SwingUtilities.invokeLater(permPanel::requestAcceptFocus);
        });
    }

    /**
     * Activates the Claude Code session TC whose working directory is an
     * ancestor of {@code filePath}.  Must be called on the EDT.
     */
    static void activateSessionForFile(String filePath) {
        java.io.File file = new java.io.File(filePath);
        for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
            if (tc instanceof ClaudeSessionTopComponent stc) {
                java.io.File dir = stc.getConfirmedDirectory();
                if (dir != null && file.getAbsolutePath().startsWith(dir.getAbsolutePath())) {
                    stc.requestActive();
                    return;
                }
            }
        }
    }

    /**
     * Sends Ctrl+C (0x03) to the Claude PTY session associated with {@code filePath}.
     * Must be called on the EDT.
     */
    public static void cancelCurrentPromptForFile(String filePath) {
        java.io.File file = new java.io.File(filePath);
        for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
            if (tc instanceof ClaudeSessionTopComponent stc) {
                java.io.File dir = stc.getConfirmedDirectory();
                if (dir != null && file.getAbsolutePath().startsWith(dir.getAbsolutePath())) {
                    stc.cancelCurrentPrompt();
                    return;
                }
            }
        }
    }

    private static StreamSource textSource(String name, String filePath, String content) {
        return new StreamSource() {
            @Override public String getName()     { return name; }
            @Override public String getTitle()    { return filePath; }
            @Override public String getMIMEType() { return "text/plain"; }
            @Override public Reader createReader()  { return new StringReader(content); }
            @Override public Writer createWriter(Difference[] conflicts) throws IOException {
                throw new IOException("Writing not supported");
            }
        };
    }
}
