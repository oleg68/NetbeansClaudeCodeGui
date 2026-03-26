package io.github.nbclaudecodegui.controller;

import io.github.nbclaudecodegui.model.ClaudeSessionModel;
import io.github.nbclaudecodegui.model.SessionLifecycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClaudeSessionController} logic that does not require
 * a live PTY process.
 *
 * <p>All tests use a stub {@code screenLines} supplier and verify state
 * changes via {@link ClaudeSessionModel}.
 */
class ClaudeSessionControllerTest {

    private ClaudeSessionModel model;
    private ClaudeSessionController controller;

    @BeforeEach
    void setUp() {
        model = new ClaudeSessionModel();
        controller = new ClaudeSessionController(model, Collections::emptyList);
    }

    // -------------------------------------------------------------------------
    // onClaudeIdle
    // -------------------------------------------------------------------------

    @Test
    void onClaudeIdleSetsLifecycleToReady() {
        model.setLifecycle(SessionLifecycle.WORKING);
        controller.onClaudeIdle();
        assertEquals(SessionLifecycle.READY, model.getLifecycle());
    }

    @Test
    void onClaudeIdleFromStartingAlsoSetsReady() {
        assertEquals(SessionLifecycle.STARTING, model.getLifecycle());
        controller.onClaudeIdle();
        assertEquals(SessionLifecycle.READY, model.getLifecycle());
    }

    // -------------------------------------------------------------------------
    // cancelPrompt (no connector — should be a no-op)
    // -------------------------------------------------------------------------

    @Test
    void cancelPromptWithNoConnectorDoesNotCrash() {
        assertDoesNotThrow(() -> controller.cancelPrompt());
    }

    // -------------------------------------------------------------------------
    // onEditModeComboChanged
    // -------------------------------------------------------------------------

    @Test
    void onEditModeComboChangedUpdatesModel() {
        // Set up directory so registry write works
        model.setWorkingDirectory(new java.io.File("/tmp/test"));
        controller.onEditModeComboChanged("acceptEdits");
        assertEquals("acceptEdits", model.getEditMode());
    }

    @Test
    void onEditModeComboChangedNoOpWhenSameMode() {
        model.setWorkingDirectory(new java.io.File("/tmp/test"));
        model.setEditMode("default");

        AtomicReference<String> captured = new AtomicReference<>(null);
        model.addListener(new NoOpListener() {
            @Override public void onEditModeChanged(String mode) {
                captured.set(mode);
            }
        });

        controller.onEditModeComboChanged("default");  // same as current
        // Listener must NOT have been called (no change)
        assertNull(captured.get(), "No notification expected when mode is unchanged");
    }

    @Test
    void onEditModeComboChangedNullIsIgnored() {
        assertDoesNotThrow(() -> controller.onEditModeComboChanged(null));
    }

    // -------------------------------------------------------------------------
    // triggerPromptScan (no connector — screen is empty)
    // -------------------------------------------------------------------------

    @Test
    void triggerPromptScanWithEmptyScreenLeavesChoiceMenuNull() throws Exception {
        // triggerPromptScan schedules on EDT; since we're already on the test thread
        // (not necessarily EDT), we just verify it doesn't crash and model stays clean
        assertDoesNotThrow(() -> controller.triggerPromptScan());
        // Give invokeLater time if needed (test thread is not EDT)
        Thread.sleep(50);
        assertNull(model.getActiveChoiceMenu());
    }

    // -------------------------------------------------------------------------
    // hasLiveProcess
    // -------------------------------------------------------------------------

    @Test
    void hasLiveProcessReturnsFalseWithoutProcess() {
        assertFalse(controller.hasLiveProcess());
    }

    // -------------------------------------------------------------------------
    // acceptEdits mode must not be overwritten by screen poll during WORKING
    // -------------------------------------------------------------------------

    /**
     * Regression: when lifecycle is WORKING the screen footer shows a spinner,
     * not the mode indicator.  The fixture has "esc to interrupt" without leading
     * spaces → detectEditMode() returns Optional.empty().  pollScreenState() must
     * skip the update when WORKING + empty (screen transitioning), so "acceptEdits"
     * is preserved in the model and registry.
     */
    @Test
    void pollScreenStateDoesNotOverwriteAcceptEditsWhileWorking() throws Exception {
        // Build a controller whose screenLines supplier returns a non-empty WORKING screen
        // (spinner visible, no "accept edits" text — exactly what Claude shows while executing)
        List<String> workingScreen = Arrays.asList(
                "Writing file src/main/java/Foo.java…",
                "⠙ Thinking",
                "esc to interrupt"
        );
        ClaudeSessionModel m2 = new ClaudeSessionModel();
        ClaudeSessionController c2 = new ClaudeSessionController(m2, () -> workingScreen);

        m2.setWorkingDirectory(new java.io.File("/tmp/test2"));
        m2.setEditMode("acceptEdits");
        m2.setLifecycle(SessionLifecycle.WORKING);

        // Simulate modelComboPopulated = true so the sync branch is reached
        java.lang.reflect.Field f =
                ClaudeSessionController.class.getDeclaredField("modelComboPopulated");
        f.setAccessible(true);
        f.setBoolean(c2, true);

        c2.pollScreenState();

        assertEquals("acceptEdits", m2.getEditMode(),
                "acceptEdits must not be overwritten by screen poll while WORKING");
        assertEquals("acceptEdits",
                io.github.nbclaudecodegui.model.ClaudeSessionModel.EDIT_MODE_REGISTRY
                        .get("/tmp/test2"),
                "Registry must still hold acceptEdits");
    }

    /**
     * Regression guard: if the screen shows a concrete mode (e.g. "plan mode" disappears
     * and nothing replaces it → "default") while WORKING, the model must NOT be overwritten
     * with "default" — but if the screen explicitly shows "plan mode" text it must still update.
     */
    @Test
    void pollScreenStateDetectsPlanModeChangeWhileWorking() throws Exception {
        // Screen shows "plan mode" text still visible during WORKING
        List<String> planScreen = Arrays.asList(
                "⠙ Thinking",
                "esc to interrupt",
                "plan mode | ? for shortcuts"
        );
        ClaudeSessionModel m3 = new ClaudeSessionModel();
        ClaudeSessionController c3 = new ClaudeSessionController(m3, () -> planScreen);

        m3.setWorkingDirectory(new java.io.File("/tmp/test3"));
        m3.setEditMode("default");
        m3.setLifecycle(SessionLifecycle.WORKING);

        java.lang.reflect.Field f =
                ClaudeSessionController.class.getDeclaredField("modelComboPopulated");
        f.setAccessible(true);
        f.setBoolean(c3, true);

        c3.pollScreenState();

        assertEquals("plan", m3.getEditMode(),
                "plan mode must be detected even during WORKING when text is visible");
    }

    // -------------------------------------------------------------------------
    // parseModelDiscovery — representative cases (see full suite in
    // ClaudeSessionControllerParseModelTest)
    // -------------------------------------------------------------------------

    @Test
    void parseModelDiscoveryEmptyLinesReturnsEmpty() {
        ClaudeSessionController.ModelDiscovery d =
                ClaudeSessionController.parseModelDiscovery(Collections.emptyList());
        assertTrue(d.models().isEmpty());
        assertEquals(-1, d.currentIndex());
    }

    @Test
    void parseModelListDelegatesToParseModelDiscovery() {
        List<String> lines = Arrays.asList(
                "❯ 1. Default (recommended) \u2714  Sonnet 4.6 \u00b7 Best for everyday tasks",
                "  2. Opus                     Opus 4.6 \u00b7 Most capable for complex work"
        );
        List<String> fromList = ClaudeSessionController.parseModelList(lines);
        List<String> fromDiscovery = ClaudeSessionController.parseModelDiscovery(lines).models();
        assertEquals(fromDiscovery, fromList);
    }

    // -------------------------------------------------------------------------
    // setWorkingDirectory — propagated to listeners (required for tab title fix)
    // -------------------------------------------------------------------------

    @Test
    void setWorkingDirectoryNotifiesListener() {
        AtomicReference<java.io.File> captured = new AtomicReference<>(null);
        model.addListener(new NoOpListener() {
            @Override public void onWorkingDirectoryChanged(java.io.File dir) {
                captured.set(dir);
            }
        });

        java.io.File dir = new java.io.File("/tmp/test-wd");
        model.setWorkingDirectory(dir);

        assertEquals(dir, captured.get(),
                "model.setWorkingDirectory must fire onWorkingDirectoryChanged so the tab title updates");
    }

    // -------------------------------------------------------------------------
    // flushPendingPrompt — Y/n trust prompt must not be dismissed on next tick
    // -------------------------------------------------------------------------

    /**
     * Regression: synthetic Y/n menu was destroyed on the very next timer tick
     * because detectChoiceMenu() returns empty for Y/n screens, which triggered
     * model.clearChoiceMenu() before the user could interact with it.
     */
    @Test
    void flushPendingPromptDoesNotDismissYnMenuWhileYnStillOnScreen() throws Exception {
        List<String> ynScreen = Arrays.asList(
                "Do you trust the files in this directory?",
                "Only proceed if you trust the source. [Y/n]"
        );
        ClaudeSessionModel m = new ClaudeSessionModel();
        ClaudeSessionController c = new ClaudeSessionController(m, () -> ynScreen);

        // First call: detects Y/n → creates synthetic menu
        c.flushPendingPrompt();
        assertNotNull(m.getActiveChoiceMenu(), "Y/n menu must be created on first flush");

        // Second call (simulates next timer tick): Y/n still on screen → must NOT clear menu
        c.flushPendingPrompt();
        assertNotNull(m.getActiveChoiceMenu(),
                "Y/n menu must survive subsequent flush while Y/n prompt is still on screen");
    }

    // -------------------------------------------------------------------------
    // flushPendingPrompt — trust prompt detected from PTY buffer when screen empty
    // -------------------------------------------------------------------------

    /**
     * Regression: when the JediTerm widget has not yet been laid out (terminal size 0×0),
     * screenLines.get() returns an empty list. The trust-directory prompt must still be
     * detected from the rolling PTY line buffer.
     */
    @Test
    void flushPendingPromptDetectsTrustPromptFromPtyBufferWhenScreenEmpty() {
        ClaudeSessionModel m = new ClaudeSessionModel();
        // screenLines always returns empty — simulates uninitialised JediTerm widget
        ClaudeSessionController c = new ClaudeSessionController(m, Collections::emptyList);

        c.simulatePtyLine("─────────────────────────────────");
        c.simulatePtyLine("Accessing workspace:");
        c.simulatePtyLine("/home/user/my-project");
        c.simulatePtyLine("Quick safety check: Is this a project you trust?");
        c.simulatePtyLine("❯ 1. Yes, I trust this folder");
        c.simulatePtyLine("2. No, exit");
        c.simulatePtyLine("Enter to confirm · Esc to cancel");

        c.flushPendingPrompt();

        assertNotNull(m.getActiveChoiceMenu(),
                "ChoiceMenuPanel must appear for trust prompt even when screen buffer is empty");
        assertEquals(2, m.getActiveChoiceMenu().options().size(),
                "Trust prompt must have exactly 2 options");
    }

    // -------------------------------------------------------------------------
    // No-op listener base class
    // -------------------------------------------------------------------------

    private static class NoOpListener
            implements ClaudeSessionModel.ClaudeSessionModelListener {
        @Override public void onLifecycleChanged(SessionLifecycle state) {}
        @Override public void onEditModeChanged(String mode) {}
        @Override public void onModelListChanged(List<String> models, int selectedIdx) {}
        @Override public void onChoiceMenuChanged(io.github.nbclaudecodegui.model.ChoiceMenuModel menu) {}
        @Override public void onWorkingDirectoryChanged(java.io.File dir) {}
    }
}
