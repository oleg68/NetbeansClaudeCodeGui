package io.github.nbclaudecodegui.model;

import java.time.Instant;

/**
 * A single entry in the persistent prompt history.
 */
public final class HistoryEntry extends PromptEntry {

    private Instant time;

    public HistoryEntry(String text, Instant time) {
        super(text);
        this.time = time;
    }

    public Instant getTime() { return time; }
}
