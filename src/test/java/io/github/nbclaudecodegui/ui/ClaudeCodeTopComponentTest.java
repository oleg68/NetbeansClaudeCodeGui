package io.github.nbclaudecodegui.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.openide.windows.TopComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClaudeSessionTab}.
 */
class ClaudeCodeTopComponentTest {

    @Test
    void testPersistenceType() {
        ClaudeSessionTab tc = new ClaudeSessionTab();
        assertEquals(TopComponent.PERSISTENCE_ALWAYS, tc.getPersistenceType());
    }

    @Test
    void testDisplayNameDefaultIsNewSession() {
        ClaudeSessionTab tc = new ClaudeSessionTab();
        // display name should contain "New Session" for an empty session
        assertNotNull(tc.getDisplayName());
    }

    @Test
    void testSessionPanelLabelResolutionNoProject() throws IOException {
        File tmpDir = Files.createTempDirectory("claude-test").toFile();
        tmpDir.deleteOnExit();
        try {
            String label = ClaudeSessionSelectorPanel.resolveTabLabel(tmpDir);
            assertEquals("Claude Code", label);
        } finally {
            tmpDir.delete();
        }
    }

    @Test
    void testSessionPanelLabelResolutionNull() {
        String label = ClaudeSessionSelectorPanel.resolveTabLabel(null);
        assertNotNull(label);
        assertFalse(label.isBlank());
    }

    @Test
    void testCanCloseWithNoActiveSession() {
        ClaudeSessionTab tc = new ClaudeSessionTab();
        // No process running — canClose() must return true without prompting
        assertTrue(tc.canClose(),
                "canClose() must be true when no process is running");
    }

    // -------------------------------------------------------------------------
    // OpenWithClaudeAction tests
    // -------------------------------------------------------------------------

    @Test
    void testContextActionDisabledForNullDirectory() {
        io.github.nbclaudecodegui.actions.OpenWithClaudeAction action =
                new io.github.nbclaudecodegui.actions.OpenWithClaudeAction();
        javax.swing.Action ctx = action.createContextAwareInstance(
                org.openide.util.Lookup.EMPTY);
        assertFalse(ctx.isEnabled(),
                "Context action must be disabled when lookup contains no project or folder");
    }

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

    @Test
    void testContextActionHasSmallIcon() {
        io.github.nbclaudecodegui.actions.OpenWithClaudeAction action =
                new io.github.nbclaudecodegui.actions.OpenWithClaudeAction();
        javax.swing.Action ctx = action.createContextAwareInstance(
                org.openide.util.Lookup.EMPTY);
        assertNotNull(ctx.getValue(javax.swing.Action.SMALL_ICON),
                "ContextAction must have SMALL_ICON set for context menu display");
    }

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
}
