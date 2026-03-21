package io.github.nbclaudecodegui.ui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClaudeSessionPanel#parseModelList(List)}.
 *
 * Bug: model entries prefixed with ❯ cursor glyph (e.g. "❯ claude-opus-4-5") were not
 * recognized, causing an empty result and infinite discoverModels() loop.
 */
class ClaudeSessionPanelParseModelTest {

    @Test
    void plainModelName() {
        List<String> lines = Arrays.asList("  claude-opus-4-5", "  claude-sonnet-4-6");
        List<String> models = ClaudeSessionPanel.parseModelList(lines);
        assertEquals(List.of("claude-opus-4-5", "claude-sonnet-4-6"), models);
    }

    @Test
    void modelWithCursorGlyphHeavyArrow() {
        // ❯ is the Ink cursor glyph used in the selected item
        List<String> lines = Arrays.asList("  ❯ claude-opus-4-5", "  claude-sonnet-4-6");
        List<String> models = ClaudeSessionPanel.parseModelList(lines);
        assertFalse(models.isEmpty(), "Should recognize model names prefixed with ❯");
        assertTrue(models.contains("claude-opus-4-5"));
        assertTrue(models.contains("claude-sonnet-4-6"));
    }

    @Test
    void modelWithTriangleGlyph() {
        List<String> lines = Arrays.asList("  ▶ claude-haiku-4-5", "  claude-opus-4-5");
        List<String> models = ClaudeSessionPanel.parseModelList(lines);
        assertTrue(models.contains("claude-haiku-4-5"));
        assertTrue(models.contains("claude-opus-4-5"));
    }

    @Test
    void modelWithGreaterThanGlyph() {
        List<String> lines = Arrays.asList("  > claude-sonnet-4-6");
        List<String> models = ClaudeSessionPanel.parseModelList(lines);
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
        List<String> models = ClaudeSessionPanel.parseModelList(lines);
        assertEquals(List.of("claude-opus-4-5"), models);
    }

    @Test
    void emptyListWhenNoModels() {
        List<String> lines = Arrays.asList("  No models found", "  Press q to quit");
        List<String> models = ClaudeSessionPanel.parseModelList(lines);
        assertTrue(models.isEmpty());
    }

    @Test
    void newNumberedMenuFormat() {
        // Actual menu format observed in Claude CLI
        List<String> lines = Arrays.asList(
                "❯ 1. Default (recommended) \u2714  Sonnet 4.6 \u00b7 Best for everyday tasks",
                "  2. Opus                     Opus 4.6 \u00b7 Most capable for complex work",
                "  3. Haiku                    Haiku 4.5 \u00b7 Fastest for quick answers"
        );
        List<String> models = ClaudeSessionPanel.parseModelList(lines);
        assertEquals(List.of("Sonnet 4.6", "Opus 4.6", "Haiku 4.5"), models);
    }

    @Test
    void newNumberedMenuFormatWithCursorOnSecondItem() {
        List<String> lines = Arrays.asList(
                "  1. Default (recommended)    Sonnet 4.6 \u00b7 Best for everyday tasks",
                "❯ 2. Opus                  \u2714  Opus 4.6 \u00b7 Most capable for complex work",
                "  3. Haiku                    Haiku 4.5 \u00b7 Fastest for quick answers"
        );
        List<String> models = ClaudeSessionPanel.parseModelList(lines);
        assertEquals(List.of("Sonnet 4.6", "Opus 4.6", "Haiku 4.5"), models);
    }
}
