package org.openbeans.claude.netbeans.tools;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.GetCurrentSelectionParams;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TC-06 regression: getCurrentSelection used TopComponent.getRegistry().getActivated()
 * to find the editor. When Claude sends the MCP request, its own terminal is the
 * activated TopComponent, not the editor — so the selection was always reported empty.
 *
 * Fix: use EditorRegistry.lastFocusedComponent() which returns the last focused
 * JTextComponent regardless of which TopComponent currently has focus.
 */
public class GetCurrentSelectionTest {

    @Test
    public void testUsesLastFocusedEditorNotActivatedTopComponent() throws Exception {
        AtomicBoolean lastFocusedWasCalled = new AtomicBoolean(false);

        GetCurrentSelection tool = new GetCurrentSelection() {
            @Override
            protected JTextComponent getLastFocusedEditor() {
                lastFocusedWasCalled.set(true);
                return null; // no editor open — normal empty-selection path
            }
        };

        NbUtils.SelectionData result = tool.run(new GetCurrentSelectionParams());

        assertTrue(lastFocusedWasCalled.get(),
                "getCurrentSelection must use EditorRegistry.lastFocusedComponent(), "
                + "not TopComponent.getRegistry().getActivated(). TC-06 regression: "
                + "when Claude's terminal is focused, getActivated() returns the terminal "
                + "instead of the editor, so selection is always reported empty.");
        assertNotNull(result);
        assertTrue(result.isEmpty);
    }
}
