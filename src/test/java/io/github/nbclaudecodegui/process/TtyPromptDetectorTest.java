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
        // Non-option line ends menu
        Optional<PromptResponsePanel.PromptRequest> req = detector.feed("Esctocancel");
        assertTrue(req.isPresent());
        assertEquals(3, req.get().options().size());
        assertEquals("Yes", req.get().options().get(0).display());
        assertEquals("1",   req.get().options().get(0).response());
        assertEquals("Yes,allowalledits", req.get().options().get(1).display());
        assertEquals("2",   req.get().options().get(1).response());
        assertEquals("No",  req.get().options().get(2).display());
        assertEquals("3",   req.get().options().get(2).response());
    }

    @Test
    void testNumberedMenuWithSpacesAfterCursor() {
        // After ESC[1C expansion, Claude outputs "❯ 1. Yes" (spaces around number)
        assertTrue(detector.feed("\u276f 1. Yes").isEmpty());
        assertTrue(detector.feed("2. Yes, and always allow").isEmpty());
        assertTrue(detector.feed("3. No").isEmpty());
        Optional<PromptResponsePanel.PromptRequest> req = detector.feed("Esc to cancel");
        assertTrue(req.isPresent());
        assertEquals(3, req.get().options().size());
        assertEquals("1", req.get().options().get(0).response());
        assertEquals("2", req.get().options().get(1).response());
        assertEquals("3", req.get().options().get(2).response());
    }

    @Test
    void testNumberedMenuWithArrowCursor() {
        // Some terminals use > instead of ❯
        assertTrue(detector.feed(">1.Option A").isEmpty());
        assertTrue(detector.feed("2.Option B").isEmpty());
        Optional<PromptResponsePanel.PromptRequest> req = detector.feed("done");
        assertTrue(req.isPresent());
        assertEquals(2, req.get().options().size());
        assertEquals("Option A", req.get().options().get(0).display());
        assertEquals("1",        req.get().options().get(0).response());
        assertEquals("Option B", req.get().options().get(1).display());
        assertEquals("2",        req.get().options().get(1).response());
    }

    @Test
    void testSingleMenuOptionEmittedOnNonOption() {
        detector.feed("\u276f1.Only");
        Optional<PromptResponsePanel.PromptRequest> req = detector.feed("something else");
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
