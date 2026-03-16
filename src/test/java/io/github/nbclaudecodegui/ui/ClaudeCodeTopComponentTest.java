package io.github.nbclaudecodegui.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.swing.JTabbedPane;
import org.openide.windows.TopComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClaudeCodeTopComponent}.
 */
class ClaudeCodeTopComponentTest {

    @Test
    void testDisplayName() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        assertEquals("Claude Code", tc.getName());
    }

    @Test
    void testPersistenceType() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        assertEquals(TopComponent.PERSISTENCE_ALWAYS, tc.getPersistenceType());
    }

    @Test
    void testInitialTabCount() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        // 1 session tab + 1 "+" tab
        assertEquals(2, tc.tabbedPane.getTabCount());
    }

    @Test
    void testTabHeaderPresentOnSessionTab() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        TabHeader header = (TabHeader) tc.tabbedPane.getTabComponentAt(0);
        assertNotNull(header);
    }

    @Test
    void testSessionPanelLabelResolutionNoProject() throws IOException {
        File tmpDir = Files.createTempDirectory("claude-test").toFile();
        tmpDir.deleteOnExit();
        try {
            // No open projects — label should be directory basename
            String label = ClaudeSessionPanel.resolveTabLabel(tmpDir);
            assertEquals(tmpDir.getName(), label);
        } finally {
            tmpDir.delete();
        }
    }

    // -------------------------------------------------------------------------
    // Bug fix: guard against infinite "+" tab creation (addingTab flag)
    // -------------------------------------------------------------------------

    /**
     * Selecting the "+" tab must add exactly one new session tab, not loop.
     *
     * <p>Regression test for the bug where the ChangeListener re-entered
     * itself during tab insertion, creating unlimited "New Session" tabs.
     */
    @Test
    void testPlusTabCreatesExactlyOneNewSession() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        // initial state: [Session, +]
        assertEquals(2, tc.tabbedPane.getTabCount());

        // simulate clicking "+": select the last tab
        int plusIndex = tc.tabbedPane.getTabCount() - 1;
        tc.tabbedPane.setSelectedIndex(plusIndex);

        // must now have exactly: [Session, NewSession, +]
        assertEquals(3, tc.tabbedPane.getTabCount());
        assertEquals("+", tc.tabbedPane.getTitleAt(2));
    }

    /**
     * Clicking "+" multiple times must add exactly one tab per click.
     */
    @Test
    void testPlusTabMultipleClicksOneTabEach() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();

        for (int click = 0; click < 3; click++) {
            int beforeCount = tc.tabbedPane.getTabCount();
            tc.tabbedPane.setSelectedIndex(beforeCount - 1); // select "+"
            assertEquals(beforeCount + 1, tc.tabbedPane.getTabCount(),
                    "Click " + (click + 1) + " should add exactly one tab");
        }
        // [Session, NS1, NS2, NS3, +]
        assertEquals(5, tc.tabbedPane.getTabCount());
    }

    // -------------------------------------------------------------------------
    // Bug fix: confirmation before close — no dialog when no active sessions
    // -------------------------------------------------------------------------

    /**
     * {@code canClose()} must return {@code true} without a dialog when there
     * are no locked sessions (no active working directories).
     */
    @Test
    void testCanCloseWithNoActiveSessions() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        // The initial tab has no confirmed directory, so canClose() should be
        // true without prompting.
        assertTrue(tc.canClose(),
                "canClose() must be true when no sessions have a locked directory");
    }

    // -------------------------------------------------------------------------
    // Bug fix: context-aware action (OpenWithClaudeAction)
    // -------------------------------------------------------------------------

    /**
     * {@code OpenWithClaudeAction.ContextAction} must be disabled when the
     * resolved directory is null.
     */
    @Test
    void testContextActionDisabledForNullDirectory() {
        io.github.nbclaudecodegui.actions.OpenWithClaudeAction action =
                new io.github.nbclaudecodegui.actions.OpenWithClaudeAction();
        javax.swing.Action ctx = action.createContextAwareInstance(
                org.openide.util.Lookup.EMPTY);
        assertFalse(ctx.isEnabled(),
                "Context action must be disabled when lookup contains no project or folder");
    }

    /**
     * Context action for null directory must implement {@link org.openide.util.actions.Presenter.Popup}
     * and return a non-null, invisible {@link javax.swing.JMenuItem}.
     */
    @Test
    void testContextActionPopupPresenterInvisibleWhenDisabled() {
        io.github.nbclaudecodegui.actions.OpenWithClaudeAction action =
                new io.github.nbclaudecodegui.actions.OpenWithClaudeAction();
        javax.swing.Action ctx = action.createContextAwareInstance(
                org.openide.util.Lookup.EMPTY);
        assertInstanceOf(org.openide.util.actions.Presenter.Popup.class, ctx,
                "ContextAction must implement Presenter.Popup");
        javax.swing.JMenuItem item =
                ((org.openide.util.actions.Presenter.Popup) ctx).getPopupPresenter();
        assertNotNull(item, "getPopupPresenter() must not return null");
        assertFalse(item.isVisible(),
                "Menu item must be invisible when action is disabled");
    }

    /**
     * Context action icon must be set (SMALL_ICON value non-null) so it
     * appears in the context menu.
     */
    @Test
    void testContextActionHasSmallIcon() {
        io.github.nbclaudecodegui.actions.OpenWithClaudeAction action =
                new io.github.nbclaudecodegui.actions.OpenWithClaudeAction();
        javax.swing.Action ctx = action.createContextAwareInstance(
                org.openide.util.Lookup.EMPTY);
        assertNotNull(ctx.getValue(javax.swing.Action.SMALL_ICON),
                "ContextAction must have SMALL_ICON set for context menu display");
    }

    /**
     * {@code OpenWithClaudeAction.ContextAction} must be enabled for an
     * existing directory resolved from a {@code FileObject} in the lookup.
     */
    @Test
    void testContextActionEnabledForExistingDirectory() throws IOException {
        File tmpDir = Files.createTempDirectory("claude-ctx-test").toFile();
        tmpDir.deleteOnExit();
        try {
            org.openide.filesystems.FileObject fo =
                    org.openide.filesystems.FileUtil.toFileObject(tmpDir);
            org.junit.jupiter.api.Assumptions.assumeTrue(fo != null,
                    "FileUtil.toFileObject returned null — skipping in headless env");

            io.github.nbclaudecodegui.actions.OpenWithClaudeAction action =
                    new io.github.nbclaudecodegui.actions.OpenWithClaudeAction();
            javax.swing.Action ctx = action.createContextAwareInstance(
                    org.openide.util.lookup.Lookups.fixed(fo));
            assertTrue(ctx.isEnabled(),
                    "Context action must be enabled for an existing directory FileObject");
        } finally {
            tmpDir.delete();
        }
    }

    // -------------------------------------------------------------------------
    // Bug fix: sessions not restored after window close
    // -------------------------------------------------------------------------

    /**
     * After {@code componentClosed()} the tab count must reset to the initial
     * state: one "New Session" tab plus the "+" tab.
     */
    @Test
    void testComponentClosedResetsTabsToInitialState() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        // simulate clicking "+" twice to add 2 extra session tabs
        tc.tabbedPane.setSelectedIndex(tc.tabbedPane.getTabCount() - 1);
        tc.tabbedPane.setSelectedIndex(tc.tabbedPane.getTabCount() - 1);
        assertEquals(4, tc.tabbedPane.getTabCount(), "pre-condition: 4 tabs before close");

        tc.componentClosed();

        assertEquals(2, tc.tabbedPane.getTabCount(),
                "componentClosed() must reset to [NewSession, +]");
        assertEquals("+", tc.tabbedPane.getTitleAt(1),
                "second tab must be the '+' tab after reset");
    }

    /**
     * After {@code componentClosed()} the surviving "New Session" tab must be
     * unlocked (fresh, no confirmed directory).
     */
    @Test
    void testComponentClosedNewSessionTabIsUnlocked() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        // lock the initial tab by simulating a confirmed directory
        ClaudeSessionPanel panel =
                (ClaudeSessionPanel) tc.tabbedPane.getComponentAt(0);
        // panel is not locked initially — add a locked tab explicitly
        tc.addSessionTab(new java.io.File(System.getProperty("java.io.tmpdir")), true);
        assertEquals(3, tc.tabbedPane.getTabCount());

        tc.componentClosed();

        assertEquals(2, tc.tabbedPane.getTabCount());
        ClaudeSessionPanel fresh =
                (ClaudeSessionPanel) tc.tabbedPane.getComponentAt(0);
        assertFalse(fresh.isLocked(),
                "New Session tab after reset must not be locked");
        assertNull(fresh.getConfirmedDirectory(),
                "New Session tab after reset must have no confirmed directory");
    }

    /**
     * {@code PERSISTENCE_ALWAYS} must be returned so the window remembers
     * its docked position between opens. Session content is reset separately
     * via {@link ClaudeCodeTopComponent#componentClosed()}.
     */
    @Test
    void testPersistenceTypeIsAlways() {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        assertEquals(TopComponent.PERSISTENCE_ALWAYS, tc.getPersistenceType(),
                "Persistence must be ALWAYS so the window remembers its docked position");
    }

    // -------------------------------------------------------------------------
    // Bug fix: opening from context menu creates two tabs
    // -------------------------------------------------------------------------

    /**
     * When a project tab is added to a window that has only the initial empty
     * "New Session" tab, the empty tab must be removed — leaving exactly one
     * project tab plus the "+" tab.
     */
    @Test
    void testOpenForDirectoryReplacesInitialEmptyTab() throws java.io.IOException {
        ClaudeCodeTopComponent tc = new ClaudeCodeTopComponent();
        assertEquals(2, tc.tabbedPane.getTabCount(), "initial: [NewSession, +]");

        java.io.File tmpDir = java.nio.file.Files.createTempDirectory("claude-open-test").toFile();
        tmpDir.deleteOnExit();
        try {
            tc.removeInitialEmptyTab();
            tc.addSessionTab(tmpDir, true);

            assertEquals(2, tc.tabbedPane.getTabCount(),
                    "after opening project: must have exactly [ProjectTab, +]");
            assertEquals("+", tc.tabbedPane.getTitleAt(1));
            ClaudeSessionPanel panel = (ClaudeSessionPanel) tc.tabbedPane.getComponentAt(0);
            assertTrue(panel.isLocked(), "project tab must be locked");
        } finally {
            tmpDir.delete();
        }
    }
}
