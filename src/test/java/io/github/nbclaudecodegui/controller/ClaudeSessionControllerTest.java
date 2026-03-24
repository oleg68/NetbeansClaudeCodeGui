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
