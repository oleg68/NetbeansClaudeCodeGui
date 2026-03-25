package io.github.nbclaudecodegui.model;

import java.util.UUID;

/**
 * A saved favorite prompt with optional keyboard shortcut and scope.
 */
public final class FavoriteEntry extends PromptEntry {

    public enum Scope { GLOBAL, PROJECT }

    private final UUID   id;
    private String shortcut; // nullable
    private Scope  scope;

    public FavoriteEntry(String text, UUID id, String shortcut, Scope scope) {
        super(text);
        this.id       = id;
        this.shortcut = shortcut;
        this.scope    = scope;
    }

    /** Creates a new PROJECT-scoped favorite with a random UUID. */
    public static FavoriteEntry ofProject(String text) {
        return new FavoriteEntry(text, UUID.randomUUID(), null, Scope.PROJECT);
    }

    /** Creates a new GLOBAL-scoped favorite with a random UUID. */
    public static FavoriteEntry ofGlobal(String text) {
        return new FavoriteEntry(text, UUID.randomUUID(), null, Scope.GLOBAL);
    }

    public UUID   getId()       { return id; }
    public String getShortcut() { return shortcut; }
    public Scope  getScope()    { return scope; }

    public void setShortcut(String shortcut) { this.shortcut = shortcut; }
    public void setScope(Scope scope)        { this.scope    = scope;    }
}
