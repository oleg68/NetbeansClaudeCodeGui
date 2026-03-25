package io.github.nbclaudecodegui.model;

/**
 * Base class for a saved or historical prompt entry.
 */
public abstract class PromptEntry {

    private String text;

    protected PromptEntry(String text) {
        this.text = text;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
