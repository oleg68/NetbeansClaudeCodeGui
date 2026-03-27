package org.openbeans.claude.netbeans.tools;

import javax.swing.text.JTextComponent;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.editor.EditorRegistry;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.GetCurrentSelectionParams;

/**
 * Tool to get the current text selection in the active editor.
 */
public class GetCurrentSelection implements Tool<GetCurrentSelectionParams, NbUtils.SelectionData> {

    /** Creates a new instance of this tool. */
    public GetCurrentSelection() {}

    private static final Logger LOGGER = Logger.getLogger(GetCurrentSelection.class.getName());
    
    @Override
    public String getName() {
        return "getCurrentSelection";
    }
    
    @Override
    public String getDescription() {
        return "Get the current text selection in the active editor";
    }
    
    @Override
    public Class<GetCurrentSelectionParams> getParameterClass() {
        return GetCurrentSelectionParams.class;
    }

    /**
     * Returns the last focused editor component; overridable in tests.
     *
     * @return last focused {@link JTextComponent}, or {@code null} if none
     */
    protected JTextComponent getLastFocusedEditor() {
        return EditorRegistry.lastFocusedComponent();
    }

    @Override
    public NbUtils.SelectionData run(GetCurrentSelectionParams params) throws Exception {
        try {
            NbUtils.SelectionData selectionData = NbUtils.getCurrentSelectionData(getLastFocusedEditor());
            if (selectionData != null) {
                return selectionData;
            } else {
                return new NbUtils.SelectionData("", "", 0, 0, 0, 0);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting current selection", e);
            return new NbUtils.SelectionData("", "", 0, 0, 0, 0);
        }
    }
}