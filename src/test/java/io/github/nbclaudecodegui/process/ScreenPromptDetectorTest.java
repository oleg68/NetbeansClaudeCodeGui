package io.github.nbclaudecodegui.process;

import io.github.nbclaudecodegui.ui.PromptResponsePanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ScreenPromptDetector}.
 */
class ScreenPromptDetectorTest {

    private ScreenPromptDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ScreenPromptDetector();
    }

    // -------------------------------------------------------------------------
    // Basic detection
    // -------------------------------------------------------------------------

    @Test
    void testDetectsSimpleMenu() {
        List<String> screen = Arrays.asList(
                "Do you want to proceed?",
                " \u276F 1. Yes",
                "   2. Yes, allow reading from plans/ during this session",
                "   3. No",
                "Esc to cancel \u00B7 Tab to amend"
        );
        Optional<PromptResponsePanel.PromptRequest> req = detector.detect(screen);
        assertTrue(req.isPresent());
        assertEquals("Do you want to proceed?", req.get().text());
        assertEquals(3, req.get().options().size());
        assertEquals("Yes", req.get().options().get(0).display());
        assertEquals("1", req.get().options().get(0).response());
        assertEquals("No", req.get().options().get(2).display());
        assertEquals("3", req.get().options().get(2).response());
    }

    @Test
    void testIgnoresActivityLinesAboveMenu() {
        // Simulates spinner updates above the menu (from cursor-up ESC[15A)
        List<String> screen = Arrays.asList(
                "\u25cf Reading 1 file\u2026 (ctrl+o to expand)",
                "  \u29BF  tail -100 /home/oleg/.netbeans/28/var/log/messages.log",
                "Run shell command",
                "Do you want to proceed?",
                " \u276F 1. Yes",
                "   2. Yes, allow reading from log/ from this project",
                "   3. No",
                "Esc to cancel \u00B7 Tab to amend \u00B7 ctrl+e to explain"
        );
        Optional<PromptResponsePanel.PromptRequest> req = detector.detect(screen);
        assertTrue(req.isPresent());
        assertEquals("Do you want to proceed?", req.get().text());
        assertEquals(3, req.get().options().size());
        assertEquals("Yes", req.get().options().get(0).display());
        assertEquals("Yes, allow reading from log/ from this project",
                req.get().options().get(1).display());
    }

    @Test
    void testReturnsEmptyForNoMenu() {
        List<String> screen = Arrays.asList(
                "Claude is thinking...",
                "\u25cf Reading files",
                ""
        );
        assertTrue(detector.detect(screen).isEmpty());
    }

    @Test
    void testReturnsEmptyForNullInput() {
        assertTrue(detector.detect(null).isEmpty());
    }

    @Test
    void testReturnsEmptyForEmptyList() {
        assertTrue(detector.detect(List.of()).isEmpty());
    }

    @Test
    void testMenuWithLeadingSpaces() {
        // JediTerm may pad lines with trailing spaces
        List<String> screen = Arrays.asList(
                "Allow this action?                    ",
                " \u276F 1. Yes                            ",
                "   2. No                              "
        );
        Optional<PromptResponsePanel.PromptRequest> req = detector.detect(screen);
        assertTrue(req.isPresent());
        assertEquals(2, req.get().options().size());
    }

    // -------------------------------------------------------------------------
    // extractOption helper
    // -------------------------------------------------------------------------

    @Test
    void testExtractOptionWithCursorGlyph() {
        PromptResponsePanel.Option opt = ScreenPromptDetector.extractOption("\u276F 1. Yes", 1);
        assertEquals("Yes", opt.display());
        assertEquals("1", opt.response());
    }

    @Test
    void testExtractOptionWithoutCursorGlyph() {
        PromptResponsePanel.Option opt = ScreenPromptDetector.extractOption("2. Yes, allow reading", 2);
        assertEquals("Yes, allow reading", opt.display());
        assertEquals("2", opt.response());
    }

    @Test
    void testExtractOptionStripsParenthetical() {
        PromptResponsePanel.Option opt = ScreenPromptDetector.extractOption("3. No (esc)", 3);
        assertEquals("No", opt.display());
        assertEquals("3", opt.response());
    }

    @Test
    void testExtractOptionNoDot() {
        PromptResponsePanel.Option opt = ScreenPromptDetector.extractOption("some text", 5);
        assertEquals("some text", opt.display());
        assertEquals("5", opt.response());
    }
}
