package io.github.nbclaudecodegui.controller;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClaudeSessionController#parseModelDiscovery(List)} and the
 * {@link ClaudeSessionController#parseModelList(List)} convenience wrapper.
 *
 * Bug: model entries prefixed with ❯ cursor glyph (e.g. "❯ claude-opus-4-5") were not
 * recognized, causing an empty result and infinite discoverModels() loop.
 */
class ClaudeSessionControllerParseModelTest {

    // -------------------------------------------------------------------------
    // parseModelDiscovery — currentIndex detection
    // -------------------------------------------------------------------------

    @Test
    void currentIndexDetectedByCheckMark() {
        List<String> lines = Arrays.asList(
                "❯ 1. Default (recommended) \u2714  Sonnet 4.6 \u00b7 Best for everyday tasks",
                "  2. Opus                     Opus 4.6 \u00b7 Most capable for complex work",
                "  3. Haiku                    Haiku 4.5 \u00b7 Fastest for quick answers"
        );
        ClaudeSessionController.ModelDiscovery d = ClaudeSessionController.parseModelDiscovery(lines);
        assertEquals(List.of("Sonnet 4.6", "Opus 4.6", "Haiku 4.5"), d.models());
        assertEquals(0, d.currentIndex(), "First item has ✔ so index must be 0");
    }

    @Test
    void currentIndexDetectedOnSecondItem() {
        List<String> lines = Arrays.asList(
                "  1. Default (recommended)    Sonnet 4.6 \u00b7 Best for everyday tasks",
                "❯ 2. Opus                  \u2714  Opus 4.6 \u00b7 Most capable for complex work",
                "  3. Haiku                    Haiku 4.5 \u00b7 Fastest for quick answers"
        );
        ClaudeSessionController.ModelDiscovery d = ClaudeSessionController.parseModelDiscovery(lines);
        assertEquals(1, d.currentIndex(), "Second item has ✔ so index must be 1");
    }

    @Test
    void currentIndexMinusOneWhenNoCheckMark() {
        List<String> lines = Arrays.asList(
                "  1. Default (recommended)    Sonnet 4.6 \u00b7 Best for everyday tasks",
                "  2. Opus                     Opus 4.6 \u00b7 Most capable for complex work"
        );
        ClaudeSessionController.ModelDiscovery d = ClaudeSessionController.parseModelDiscovery(lines);
        assertEquals(-1, d.currentIndex(), "No ✔ present, currentIndex must be -1");
    }

    @Test
    void currentIndexForLegacyFormatDetectedByCursor() {
        List<String> lines = Arrays.asList(
                "  claude-sonnet-4-6",
                "❯ claude-opus-4-5"
        );
        ClaudeSessionController.ModelDiscovery d = ClaudeSessionController.parseModelDiscovery(lines);
        assertEquals(List.of("claude-sonnet-4-6", "claude-opus-4-5"), d.models());
        assertEquals(1, d.currentIndex(), "Cursor glyph marks second legacy entry as current");
    }

    // -------------------------------------------------------------------------
    // parseModelList — backward-compat wrapper
    // -------------------------------------------------------------------------

    @Test
    void plainModelName() {
        List<String> lines = Arrays.asList("  claude-opus-4-5", "  claude-sonnet-4-6");
        List<String> models = ClaudeSessionController.parseModelList(lines);
        assertEquals(List.of("claude-opus-4-5", "claude-sonnet-4-6"), models);
    }

    @Test
    void modelWithCursorGlyphHeavyArrow() {
        List<String> lines = Arrays.asList("  ❯ claude-opus-4-5", "  claude-sonnet-4-6");
        List<String> models = ClaudeSessionController.parseModelList(lines);
        assertFalse(models.isEmpty(), "Should recognize model names prefixed with ❯");
        assertTrue(models.contains("claude-opus-4-5"));
        assertTrue(models.contains("claude-sonnet-4-6"));
    }

    @Test
    void modelWithTriangleGlyph() {
        List<String> lines = Arrays.asList("  ▶ claude-haiku-4-5", "  claude-opus-4-5");
        List<String> models = ClaudeSessionController.parseModelList(lines);
        assertTrue(models.contains("claude-haiku-4-5"));
        assertTrue(models.contains("claude-opus-4-5"));
    }

    @Test
    void modelWithGreaterThanGlyph() {
        List<String> lines = Arrays.asList("  > claude-sonnet-4-6");
        List<String> models = ClaudeSessionController.parseModelList(lines);
        assertTrue(models.contains("claude-sonnet-4-6"));
    }

    @Test
    void nonModelLinesIgnored() {
        List<String> lines = Arrays.asList(
                "  Select a model",
                "  ❯ claude-opus-4-5",
                "  Use arrow keys to select",
                "  Press Enter to confirm"
        );
        List<String> models = ClaudeSessionController.parseModelList(lines);
        assertEquals(List.of("claude-opus-4-5"), models);
    }

    @Test
    void emptyListWhenNoModels() {
        List<String> lines = Arrays.asList("  No models found", "  Press q to quit");
        List<String> models = ClaudeSessionController.parseModelList(lines);
        assertTrue(models.isEmpty());
    }

    @Test
    void newNumberedMenuFormat() {
        List<String> lines = Arrays.asList(
                "❯ 1. Default (recommended) \u2714  Sonnet 4.6 \u00b7 Best for everyday tasks",
                "  2. Opus                     Opus 4.6 \u00b7 Most capable for complex work",
                "  3. Haiku                    Haiku 4.5 \u00b7 Fastest for quick answers"
        );
        List<String> models = ClaudeSessionController.parseModelList(lines);
        assertEquals(List.of("Sonnet 4.6", "Opus 4.6", "Haiku 4.5"), models);
    }

    @Test
    void newNumberedMenuFormatWithCursorOnSecondItem() {
        List<String> lines = Arrays.asList(
                "  1. Default (recommended)    Sonnet 4.6 \u00b7 Best for everyday tasks",
                "❯ 2. Opus                  \u2714  Opus 4.6 \u00b7 Most capable for complex work",
                "  3. Haiku                    Haiku 4.5 \u00b7 Fastest for quick answers"
        );
        List<String> models = ClaudeSessionController.parseModelList(lines);
        assertEquals(List.of("Sonnet 4.6", "Opus 4.6", "Haiku 4.5"), models);
    }
}
