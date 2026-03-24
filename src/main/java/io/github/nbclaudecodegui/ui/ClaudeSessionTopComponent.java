package io.github.nbclaudecodegui.ui;

import java.io.File;

/**
 * @deprecated Use {@link ClaudeSessionTab} instead.
 */
@Deprecated
class ClaudeSessionTopComponent extends ClaudeSessionTab {

    ClaudeSessionTopComponent() {
        super();
    }

    ClaudeSessionTopComponent(File dir) {
        super(dir);
    }
}
