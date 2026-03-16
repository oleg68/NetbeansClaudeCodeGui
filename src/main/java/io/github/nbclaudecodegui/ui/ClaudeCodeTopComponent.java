package io.github.nbclaudecodegui.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Claude Code chat window — the main UI container for the plugin.
 *
 * <p>Contains a {@link JTabbedPane} where each tab is a {@link ClaudeSessionPanel}
 * bound to a working directory. A special "+" tab at the end allows the user
 * to open new sessions.
 *
 * <p>Closing the last real tab closes the whole window. If any locked sessions
 * exist the user is asked for confirmation before closing.
 */
@TopComponent.Description(
    preferredID = "ClaudeCodeTopComponent",
    iconBase = "io/github/nbclaudecodegui/icons/claude-icon.png",
    persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(
    mode = "properties",
    openAtStartup = false
)
@ActionID(
    category = "Window",
    id = "io.github.nbclaudecodegui.ui.ClaudeCodeTopComponent"
)
@ActionReference(path = "Menu/Window", position = 500)
@TopComponent.OpenActionRegistration(
    displayName = "#CTL_ClaudeCodeTopComponent",
    preferredID = "ClaudeCodeTopComponent"
)
@Messages("CTL_ClaudeCodeTopComponent=Claude Code")
public final class ClaudeCodeTopComponent extends TopComponent {

    private static final String PLUS_TAB_TITLE = "+";

    /** Guard against re-entrant ChangeListener firing during tab insertion. */
    private boolean addingTab = false;

    JTabbedPane tabbedPane;

    /** Creates the top component and initialises the tabbed UI. */
    public ClaudeCodeTopComponent() {
        initComponents();
        setName("Claude Code");
        setToolTipText("Claude Code chat window");
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        addSessionTab(null, false);
        tabbedPane.addTab(PLUS_TAB_TITLE, null);

        tabbedPane.addChangeListener(e -> {
            if (addingTab) {
                return;
            }
            int selected = tabbedPane.getSelectedIndex();
            int plusIndex = tabbedPane.getTabCount() - 1;
            if (selected == plusIndex) {
                addingTab = true;
                try {
                    addSessionTab(null, false);
                    tabbedPane.setSelectedIndex(plusIndex - 1);
                } finally {
                    addingTab = false;
                }
            }
        });
    }

    /**
     * Opens (or focuses) the Claude Code window.
     *
     * <p>If the window is already open it is simply brought to front;
     * no new tab is created.
     */
    public static void openWindow() {
        SwingUtilities.invokeLater(() -> {
            TopComponent tc = WindowManager.getDefault()
                    .findTopComponent("ClaudeCodeTopComponent");
            if (tc == null) {
                tc = new ClaudeCodeTopComponent();
            }
            tc.open();
            tc.requestActive();
        });
    }

    /**
     * Opens the window and creates (or focuses) a tab for the given directory.
     *
     * <p>If a locked tab for {@code dir} already exists the first such tab
     * is focused. Otherwise a new locked tab is added.
     *
     * @param dir    the working directory
     * @param locked {@code true} to lock the directory control immediately
     */
    public static void openForDirectory(File dir, boolean locked) {
        SwingUtilities.invokeLater(() -> {
            TopComponent raw = WindowManager.getDefault()
                    .findTopComponent("ClaudeCodeTopComponent");
            ClaudeCodeTopComponent tc = (raw instanceof ClaudeCodeTopComponent)
                    ? (ClaudeCodeTopComponent) raw
                    : new ClaudeCodeTopComponent();
            tc.open();
            tc.requestActive();

            if (dir == null) {
                return;
            }

            // focus existing locked tab with the same path
            for (int i = 0; i < tc.tabbedPane.getTabCount() - 1; i++) {
                Component comp = tc.tabbedPane.getComponentAt(i);
                if (comp instanceof ClaudeSessionPanel) {
                    ClaudeSessionPanel panel = (ClaudeSessionPanel) comp;
                    if (panel.isLocked() && dir.equals(panel.getConfirmedDirectory())) {
                        tc.tabbedPane.setSelectedIndex(i);
                        return;
                    }
                }
            }

            tc.removeInitialEmptyTab();
            tc.addSessionTab(dir, locked);
            tc.tabbedPane.setSelectedIndex(tc.tabbedPane.getTabCount() - 2);
        });
    }

    /**
     * Asks for confirmation if there are active (locked) sessions before
     * allowing the window to close.
     *
     * @return {@code true} if the window may be closed
     */
    @Override
    public boolean canClose() {
        // Ask each panel — each may show its own confirmation if a process is running
        for (int i = 0; i < tabbedPane.getTabCount() - 1; i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof ClaudeSessionPanel panel) {
                if (!panel.canClose()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_ALWAYS;
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    /**
     * Removes the initial empty "New Session" tab when it is the only session
     * tab and has no confirmed directory. Called before adding a project tab
     * so that opening from the context menu does not leave a spurious empty tab.
     */
    void removeInitialEmptyTab() {
        if (tabbedPane.getTabCount() != 2) {
            return;
        }
        Component comp = tabbedPane.getComponentAt(0);
        if (comp instanceof ClaudeSessionPanel panel && !panel.isLocked()) {
            addingTab = true;
            try {
                tabbedPane.removeTabAt(0);
            } finally {
                addingTab = false;
            }
        }
    }

    /**
     * Inserts a new session tab before the "+" tab.
     *
     * @param dir    pre-set directory, or {@code null} for empty
     * @param locked whether to lock the directory control immediately
     */
    void addSessionTab(File dir, boolean locked) {
        ClaudeSessionPanel panel = new ClaudeSessionPanel(dir, locked);
        String title = ClaudeSessionPanel.resolveTabLabel(dir);
        int insertAt = tabbedPane.getTabCount();
        if (insertAt > 0
                && PLUS_TAB_TITLE.equals(tabbedPane.getTitleAt(insertAt - 1))) {
            insertAt--;
        }
        addingTab = true;
        try {
            tabbedPane.insertTab(title, null, panel, null, insertAt);
        } finally {
            addingTab = false;
        }

        final int tabIndex = insertAt;
        TabHeader header = new TabHeader(tabbedPane, title,
                () -> removeTab(tabbedPane.indexOfComponent(panel)));
        tabbedPane.setTabComponentAt(insertAt, header);

        panel.setDirectoryListener(confirmedDir -> {
            String newTitle = ClaudeSessionPanel.resolveTabLabel(confirmedDir);
            int idx = tabbedPane.indexOfComponent(panel);
            if (idx >= 0) {
                tabbedPane.setTitleAt(idx, newTitle);
                ((TabHeader) tabbedPane.getTabComponentAt(idx)).setTitle(newTitle);
            }
        });
    }

    /**
     * Removes the tab at the given index.
     *
     * <p>If it is the last real session tab, {@link #close()} is called,
     * which invokes {@link #canClose()} for confirmation.
     *
     * @param index the tab index to remove
     */
    private void removeTab(int index) {
        int realCount = tabbedPane.getTabCount() - 1; // exclude "+"
        if (index < 0 || index >= realCount) {
            return;
        }
        if (realCount == 1) {
            // closing the last tab — close the whole window (triggers canClose)
            close();
        } else {
            tabbedPane.removeTabAt(index);
        }
    }

    /**
     * Resets the tabbed pane to its initial state: one empty "New Session"
     * tab plus the "+" tab. Called from {@link #componentClosed()} so that
     * the next time the window is opened it starts fresh.
     */
    private void resetTabs() {
        addingTab = true;
        try {
            tabbedPane.removeAll();
            addSessionTab(null, false);
            tabbedPane.addTab(PLUS_TAB_TITLE, null);
        } finally {
            addingTab = false;
        }
    }

    /**
     * Resets session state when the window is closed so that reopening it
     * always starts with a single empty "New Session" tab.
     */
    @Override
    protected void componentClosed() {
        super.componentClosed();
        stopAllProcesses();
        resetTabs();
    }

    /** Stops all running Claude processes before the window is closed/reset. */
    private void stopAllProcesses() {
        for (int i = 0; i < tabbedPane.getTabCount() - 1; i++) {
            Component comp = tabbedPane.getComponentAt(i);
            if (comp instanceof ClaudeSessionPanel panel) {
                panel.stopProcess();
            }
        }
    }


}
