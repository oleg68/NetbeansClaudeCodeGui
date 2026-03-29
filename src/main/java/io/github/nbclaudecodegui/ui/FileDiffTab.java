package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JToolBar;
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
 * a {@link FileDiffPermissionPanel} at the bottom.
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
     *
     * @param filePath path of the file to check
     * @param dirPath  path of the directory to check against
     * @return {@code true} if the file is inside the directory or equals it
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
     * @param onDecline    called with the (possibly empty) reason when the user clicks Decline
     * @param onCancel     called when the user clicks Cancel (caller should also send Ctrl+C)
     * @param onClose      called when the tab is closed via the × button without a decision
     */
    public static void open(
            String filePath, String before, String after, String tabName,
            String confirmedDir,
            Runnable onAccept,
            Consumer<String> onDecline,
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
                outsideWarning = "⚠ This file is outside the current project";
            } else {
                boolean insideAny = false;
                for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
                    if (tc instanceof ClaudeSessionTab stc) {
                        java.io.File dir = stc.getWorkingDirectory();
                        if (dir != null && isFileUnderDirectory(filePath, dir.getAbsolutePath())) {
                            insideAny = true;
                            break;
                        }
                    }
                }
                outsideProject = !insideAny;
                outsideWarning = "⚠ This file is outside any open project";
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
            // firing again when the diff is closed.
            AtomicBoolean decided = new AtomicBoolean(false);

            // Look up the session whose working directory matches the file being edited.
            // confirmedDir != null (hook case): use exact directory match.
            // confirmedDir == null (MCP case): fall back to isFileUnderDirectory.
            final ClaudeSessionTab sessionTab = WindowManager.getDefault().getRegistry().getOpened().stream()
                    .map(tc -> {
                        if (!(tc instanceof ClaudeSessionTab s)) return null;
                        File dir = s.getWorkingDirectory();
                        if (dir == null) return null;
                        String abs = dir.getAbsolutePath();
                        return (confirmedDir != null ? abs.equals(confirmedDir)
                                : isFileUnderDirectory(filePath, abs)) ? s : null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            String workingDir = sessionTab != null && sessionTab.getWorkingDirectory() != null
                    ? sessionTab.getWorkingDirectory().getAbsolutePath()
                    : null;

            // Forward reference for the close action (resolved below for each branch).
            Runnable[] closeDiffRef = { null };

            FileDiffPermissionPanel permPanel = new FileDiffPermissionPanel(
                () -> {
                    decided.set(true);
                    onAccept.run();
                    closeDiffRef[0].run();
                    activateSessionForFile(filePath);
                },
                reason -> {
                    decided.set(true);
                    onDecline.accept(reason);
                    closeDiffRef[0].run();
                    activateSessionForFile(filePath);
                },
                sessionTab == null ? null : () -> {
                    sessionTab.setEditMode("acceptEdits");
                    decided.set(true);
                    onAccept.run();
                    closeDiffRef[0].run();
                    activateSessionForFile(filePath);
                },
                sessionTab == null ? null : () -> {
                    decided.set(true);
                    onCancel.run();
                    closeDiffRef[0].run();
                    activateSessionForFile(filePath);
                },
                workingDir
            );

            boolean isMdFile = filePath.toLowerCase().endsWith(".md")
                    || filePath.toLowerCase().endsWith(".markdown");

            // Keep the DiffView's own navigation toolbar (prev/next difference buttons)
            // Null means this DiffView provider offers no toolbar — don't add an empty one.
            JToolBar toolbar = diffView.getToolBar();

            // Build the main content component: for .md files with preview enabled,
            // embed a MarkdownDiffPanel above the diff in a vertical split pane.
            java.awt.Component mainComponent = diffView.getComponent();
            if (isMdFile && ClaudeCodePreferences.isMdPreviewInDiff()) {
                MarkdownDiffPanel mdPanel = new MarkdownDiffPanel(before, after);
                mdPanel.attachRawDiffSync(diffView.getComponent());
                javax.swing.JSplitPane mdSplit = new javax.swing.JSplitPane(
                        javax.swing.JSplitPane.VERTICAL_SPLIT, mdPanel, diffView.getComponent());
                mdSplit.setResizeWeight(0.5);
                mainComponent = mdSplit;
            }

            // --- SESSION branch ---
            if (!ClaudeCodePreferences.isOpenDiffInSeparateTab()
                    && sessionTab != null) {
                closeDiffRef[0] = sessionTab::hideEmbeddedDiff;
                boolean shown = sessionTab.showEmbeddedDiff(
                        outsideProject, outsideWarning,
                        toolbar, mainComponent, permPanel,
                        () -> { if (!decided.get()) { onClose.run(); activateSessionForFile(filePath); } });
                if (shown) return;
            }

            // --- TOPLEVEL branch ---
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
            closeDiffRef[0] = diffTC::close;
            diffTC.setDisplayName(tabName);
            diffTC.setLayout(new java.awt.BorderLayout());

            if (toolbar != null) {
                toolbar.setFloatable(false);
                diffTC.add(toolbar, java.awt.BorderLayout.NORTH);
            }
            diffTC.add(mainComponent, java.awt.BorderLayout.CENTER);
            if (outsideProject) {
                permPanel.showWarning(outsideWarning);
            }
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
            if (tc instanceof ClaudeSessionTab stc) {
                java.io.File dir = stc.getWorkingDirectory();
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
     *
     * @param filePath absolute path of the file whose session should be interrupted
     */
    public static void cancelCurrentPromptForFile(String filePath) {
        java.io.File file = new java.io.File(filePath);
        for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
            if (tc instanceof ClaudeSessionTab stc) {
                java.io.File dir = stc.getWorkingDirectory();
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
