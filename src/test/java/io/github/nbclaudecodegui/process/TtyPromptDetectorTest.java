package io.github.nbclaudecodegui.process;

import io.github.nbclaudecodegui.ui.PromptResponsePanel;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TtyPromptDetector}.
 */
class TtyPromptDetectorTest {

    private TtyPromptDetector detector;

    @BeforeEach
    void setUp() {
        detector = new TtyPromptDetector();
    }

    // -------------------------------------------------------------------------
    // Numbered menu detection
    // -------------------------------------------------------------------------

    @Test
    void testNumberedMenuCollectsOptions() {
        // First line starts with ❯1. — triggers COLLECTING
        assertTrue(detector.feed("\u276f1.Yes").isEmpty());
        assertTrue(detector.feed("2.Yes,allowalledits(shift+tab)").isEmpty());
        assertTrue(detector.feed("3.No").isEmpty());
        // Non-option lines are now ignored in COLLECTING; tryFlush() emits when PTY goes silent
        assertTrue(detector.feed("Esctocancel").isEmpty());
        Optional<PromptResponsePanel.PromptRequest> req = detector.tryFlush();
        assertTrue(req.isPresent());
        assertEquals(3, req.get().options().size());
        assertEquals("Yes", req.get().options().get(0).display());
        assertEquals("1",   req.get().options().get(0).response());
        assertEquals("Yes,allowalledits", req.get().options().get(1).display());
        assertEquals("2",   req.get().options().get(1).response());
        assertEquals("No",  req.get().options().get(2).display());
        assertEquals("3",   req.get().options().get(2).response());
        assertEquals(0, req.get().defaultOptionIndex());
    }

    @Test
    void testNumberedMenuWithSpacesAfterCursor() {
        // After ESC[1C expansion, Claude outputs "❯ 1. Yes" (spaces around number)
        assertTrue(detector.feed("\u276f 1. Yes").isEmpty());
        assertTrue(detector.feed("2. Yes, and always allow").isEmpty());
        assertTrue(detector.feed("3. No").isEmpty());
        assertTrue(detector.feed("Esc to cancel").isEmpty());
        Optional<PromptResponsePanel.PromptRequest> req = detector.tryFlush();
        assertTrue(req.isPresent());
        assertEquals(3, req.get().options().size());
        assertEquals("1", req.get().options().get(0).response());
        assertEquals("2", req.get().options().get(1).response());
        assertEquals("3", req.get().options().get(2).response());
        assertEquals(0, req.get().defaultOptionIndex());
    }

    @Test
    void testNumberedMenuWithArrowCursor() {
        // Some terminals use > instead of ❯
        assertTrue(detector.feed(">1.Option A").isEmpty());
        assertTrue(detector.feed("2.Option B").isEmpty());
        assertTrue(detector.feed("done").isEmpty());
        Optional<PromptResponsePanel.PromptRequest> req = detector.tryFlush();
        assertTrue(req.isPresent());
        assertEquals(2, req.get().options().size());
        assertEquals("Option A", req.get().options().get(0).display());
        assertEquals("1",        req.get().options().get(0).response());
        assertEquals("Option B", req.get().options().get(1).display());
        assertEquals("2",        req.get().options().get(1).response());
        assertEquals(0, req.get().defaultOptionIndex());
    }

    @Test
    void testSingleMenuOptionEmittedViaFlush() {
        detector.feed("\u276f1.Only");
        detector.feed("something else");
        Optional<PromptResponsePanel.PromptRequest> req = detector.tryFlush();
        assertTrue(req.isPresent());
        assertEquals(1, req.get().options().size());
    }

    // -------------------------------------------------------------------------
    // Inline prompts
    // -------------------------------------------------------------------------

    @Test
    void testInlineYnPrompt() {
        Optional<PromptResponsePanel.PromptRequest> req =
                detector.feed("Allow this action? (y/n)");
        assertTrue(req.isPresent());
        assertEquals(2, req.get().options().size());
        assertEquals("y", req.get().options().get(0).display());
        assertEquals("y", req.get().options().get(0).response());
        assertEquals("n", req.get().options().get(1).display());
        assertEquals("n", req.get().options().get(1).response());
        assertEquals(-1, req.get().defaultOptionIndex());
    }

    @Test
    void testInlineAlwaysPrompt() {
        Optional<PromptResponsePanel.PromptRequest> req =
                detector.feed("Allow? (y/n/always)");
        assertTrue(req.isPresent());
        assertEquals(3, req.get().options().size());
    }

    @Test
    void testInlineBracketsPrompt() {
        Optional<PromptResponsePanel.PromptRequest> req =
                detector.feed("Proceed [y/n]");
        assertTrue(req.isPresent());
        assertEquals(2, req.get().options().size());
    }

    // -------------------------------------------------------------------------
    // JSON subtype events
    // -------------------------------------------------------------------------

    @Test
    void testJsonQuestionSubtype() {
        String line = "{\"type\":\"system\",\"subtype\":\"question\",\"question\":\"Allow?\"}";
        Optional<PromptResponsePanel.PromptRequest> req = detector.feed(line);
        assertTrue(req.isPresent());
        assertEquals("Allow?", req.get().text());
        assertEquals(-1, req.get().defaultOptionIndex());
    }

    @Test
    void testJsonPermissionRequestSubtype() {
        String line = "{\"type\":\"system\",\"subtype\":\"permission_request\"}";
        Optional<PromptResponsePanel.PromptRequest> req = detector.feed(line);
        assertTrue(req.isPresent());
    }

    @Test
    void testJsonNonPromptSubtype() {
        String line = "{\"type\":\"result\",\"subtype\":\"success\",\"result\":\"done\"}";
        assertTrue(detector.feed(line).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Negative cases
    // -------------------------------------------------------------------------

    @Test
    void testNormalLineIgnored() {
        assertTrue(detector.feed("normal output line").isEmpty());
        assertTrue(detector.feed("building...").isEmpty());
    }

    @Test
    void testNullLine() {
        assertTrue(detector.feed(null).isEmpty());
    }

    @Test
    void testResetClearsState() {
        detector.feed("\u276f1.Yes");
        detector.reset();
        // After reset, non-option line should not emit
        assertTrue(detector.feed("something").isEmpty());
    }

    @Test
    void testTryFlushEmitsPendingMenuWhenPtyGoesIdle() {
        // Regression: Claude outputs menu options then goes silent waiting for input.
        // No terminating non-option line arrives, so feed() never emits a PromptRequest.
        // tryFlush() must emit the pending request immediately.
        assertTrue(detector.feed("❯ 1. Yes").isEmpty(), "trigger line alone should not emit");
        assertTrue(detector.feed("2. No").isEmpty(),    "second option alone should not emit");

        Optional<PromptResponsePanel.PromptRequest> flushed = detector.tryFlush();
        assertTrue(flushed.isPresent(), "tryFlush() must emit pending prompt");
        assertEquals(2, flushed.get().options().size());
        assertEquals("1", flushed.get().options().get(0).response());
        assertEquals("2", flushed.get().options().get(1).response());
    }

    @Test
    void testTryFlushReturnsEmptyWhenIdle() {
        assertTrue(detector.tryFlush().isEmpty(), "tryFlush() on idle detector must return empty");
    }

    @Test
    void testTryFlushResetsStateToIdle() {
        detector.feed("❯ 1. Yes");
        detector.tryFlush();
        // After flush, feeding another menu trigger should start fresh
        assertTrue(detector.feed("something unrelated").isEmpty());
        Optional<PromptResponsePanel.PromptRequest> req = detector.feed("❯ 1. Option");
        assertTrue(req.isEmpty(), "new trigger starts collecting again, no emit yet");
    }

    // -------------------------------------------------------------------------
    // Bug fixes: description lines, blank separators, pre-cursor options, re-renders
    // -------------------------------------------------------------------------

    @Test
    void testDescriptionLinesSkippedInCollecting() {
        // Claude renders options with sub-label text after each numbered line.
        // Description lines must not terminate COLLECTING; all options collected via tryFlush().
        assertTrue(detector.feed("❯ 1. auto / edit / diff").isEmpty());
        assertTrue(detector.feed("   Режим применения правок: auto-patch").isEmpty());
        assertTrue(detector.feed("2. Permission mode").isEmpty());
        assertTrue(detector.feed("   bypassPermissions / default / acceptEdits").isEmpty());
        assertTrue(detector.feed("3. Other").isEmpty());
        assertTrue(detector.feed("4. Type something.").isEmpty());

        Optional<PromptResponsePanel.PromptRequest> req = detector.tryFlush();
        assertTrue(req.isPresent());
        assertEquals(4, req.get().options().size());
        assertEquals("1", req.get().options().get(0).response());
        assertEquals("2", req.get().options().get(1).response());
        assertEquals("3", req.get().options().get(2).response());
        assertEquals("4", req.get().options().get(3).response());
    }

    @Test
    void testBlankLineSeparatorSkippedInCollecting() {
        // A blank line between option groups must not terminate COLLECTING.
        assertTrue(detector.feed("❯ 1. Option A").isEmpty());
        assertTrue(detector.feed("2. Option B").isEmpty());
        assertTrue(detector.feed("").isEmpty());          // blank separator
        assertTrue(detector.feed("3. Chat about this").isEmpty());
        assertTrue(detector.feed("4. Skip interview").isEmpty());

        Optional<PromptResponsePanel.PromptRequest> req = detector.tryFlush();
        assertTrue(req.isPresent());
        assertEquals(4, req.get().options().size());
        assertEquals("4", req.get().options().get(3).response());
    }

    @Test
    void testCursorOnSecondOption() {
        // Options before the cursor line are buffered in IDLE and prepended on trigger.
        assertTrue(detector.feed("1. Yes").isEmpty());    // pre-buffer
        assertTrue(detector.feed("❯2. No").isEmpty());   // trigger
        assertTrue(detector.feed("3. Cancel").isEmpty());

        Optional<PromptResponsePanel.PromptRequest> req = detector.tryFlush();
        assertTrue(req.isPresent());
        assertEquals(3, req.get().options().size());
        assertEquals("Yes",    req.get().options().get(0).display().trim());
        assertEquals("1",      req.get().options().get(0).response());
        assertEquals("No",     req.get().options().get(1).display().trim());
        assertEquals("2",      req.get().options().get(1).response());
        assertEquals("Cancel", req.get().options().get(2).display().trim());
        assertEquals("3",      req.get().options().get(2).response());
    }

    @Test
    void testLeadingSpaceBeforeCursor() {
        // PTY ESC[1C expansion produces a leading space: " ❯ 1. Yes"
        assertTrue(detector.feed(" ❯ 1. Yes").isEmpty());
        assertTrue(detector.feed("2. No").isEmpty());

        Optional<PromptResponsePanel.PromptRequest> req = detector.tryFlush();
        assertTrue(req.isPresent());
        assertEquals(2, req.get().options().size());
        assertEquals("1", req.get().options().get(0).response());
        assertEquals("2", req.get().options().get(1).response());
    }

    @Test
    void testMenuReRenderInCollecting() {
        // User presses arrow key → Claude re-renders menu with cursor on option 2.
        // The second trigger must reset collection cleanly.
        assertTrue(detector.feed("❯ 1. Yes").isEmpty());   // first render, cursor on 1
        assertTrue(detector.feed("2. No").isEmpty());
        // Re-render: cursor moved to option 2
        assertTrue(detector.feed("1. Yes").isEmpty());     // becomes pre-buffer? no — we're in COLLECTING
        // Actually in COLLECTING, "1. Yes" matches option pattern → added as option 3 (stale).
        // Then the new trigger resets.
        assertTrue(detector.feed("❯ 2. No").isEmpty());   // re-render trigger → reset
        assertTrue(detector.feed("3. Cancel").isEmpty());

        Optional<PromptResponsePanel.PromptRequest> req = detector.tryFlush();
        assertTrue(req.isPresent());
        // After re-render reset: options are those collected since the second trigger
        assertEquals("No",     req.get().options().get(0).display().trim());
        assertEquals("Cancel", req.get().options().get(1).display().trim());
    }

    @Test
    void testFalsePositiveTriggerInProseAborted() {
        // Plan/doc text may contain "❯2. No ← trigger →" which matches menu.trigger.pattern.
        // Followed by numbered list items from the doc and then prose.
        // After MAX_NON_OPTION_STREAK consecutive non-option, non-blank lines the
        // detector must silently abort — no PromptRequest should be emitted.
        assertTrue(detector.feed("❯2. No         ← trigger → questionLine = \"1. Yes\"").isEmpty());
        assertTrue(detector.feed("3. Cancel      ← COLLECTING: adds option 3").isEmpty());
        // Three prose lines → streak = 3 → abort
        assertTrue(detector.feed("This is documentation prose line one.").isEmpty());
        assertTrue(detector.feed("This is documentation prose line two.").isEmpty());
        assertTrue(detector.feed("This is documentation prose line three.").isEmpty());
        // tryFlush() must return empty — collection was aborted
        assertTrue(detector.tryFlush().isEmpty(), "false-positive trigger must not emit a prompt");
    }

    @Test
    void testPreBufferClearedByNonOptionLine() {
        // A stray numbered line not followed by a trigger should not produce a prompt.
        detector.feed("1. Some numbered list item");
        detector.feed("This is prose text, not a menu");
        assertTrue(detector.tryFlush().isEmpty(), "no prompt should have been emitted");
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Test
    void testMissingAllPropertiesThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new TtyPromptDetector(new Properties()));
        String msg = ex.getMessage();
        assertTrue(msg.contains("menu.trigger.pattern"), "should mention menu.trigger.pattern, got: " + msg);
        assertTrue(msg.contains("menu.option.pattern"),  "should mention menu.option.pattern, got: " + msg);
        assertTrue(msg.contains("inline.trigger.patterns"), "should mention inline.trigger.patterns, got: " + msg);
        assertTrue(msg.contains("json.subtypes"),        "should mention json.subtypes, got: " + msg);
    }

    @Test
    void testMissingOnePropertyThrows() {
        Properties props = new Properties();
        props.setProperty("menu.trigger.pattern", "^(>)\\s*\\d+\\.");
        props.setProperty("menu.option.pattern",  "^\\s*\\d+\\.");
        props.setProperty("json.subtypes",        "question");
        // inline.trigger.patterns is missing
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new TtyPromptDetector(props));
        String msg = ex.getMessage();
        assertTrue(msg.contains("inline.trigger.patterns"), "should mention inline.trigger.patterns, got: " + msg);
        assertFalse(msg.contains("menu.trigger.pattern"), "should NOT mention menu.trigger.pattern, got: " + msg);
    }

    @Test
    void testAllPropertiesPresentDoesNotThrow() {
        Properties props = new Properties();
        props.setProperty("menu.trigger.pattern",    "^(>)\\s*\\d+\\.");
        props.setProperty("menu.option.pattern",     "^\\s*\\d+\\.");
        props.setProperty("inline.trigger.patterns", "(y/n)");
        props.setProperty("json.subtypes",           "question");
        assertDoesNotThrow(() -> new TtyPromptDetector(props));
    }
}
