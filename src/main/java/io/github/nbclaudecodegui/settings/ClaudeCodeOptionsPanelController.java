package io.github.nbclaudecodegui.settings;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

/**
 * Registers and manages the Claude Code top-level options page in
 * Tools → Options.
 *
 * <p>The page is identified by the category id {@code "ClaudeCodeGUI"} and
 * can be opened programmatically via:
 * <pre>{@code
 * OptionsDisplayer.getDefault().open("ClaudeCodeGUI");
 * }</pre>
 */
@OptionsPanelController.TopLevelRegistration(
        id = "ClaudeCodeGUI",
        categoryName = "Claude Code",
        iconBase = "io/github/nbclaudecodegui/icons/claude-icon-32.png",
        keywords = "claude code ai",
        keywordsCategory = "Claude Code"
)
public final class ClaudeCodeOptionsPanelController extends OptionsPanelController {

    private ClaudeCodeOptionsPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;

    @Override
    public void update() {
        getPanel().load();
        changed = false;
    }

    @Override
    public void applyChanges() {
        SwingUtilities.invokeLater(() -> {
            getPanel().store();
            changed = false;
        });
    }

    @Override
    public void cancel() {
        // no pending state to discard
    }

    @Override
    public boolean isValid() {
        return getPanel().valid();
    }

    @Override
    public boolean isChanged() {
        return changed;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null;
    }

    @Override
    public JComponent getComponent(Lookup masterLookup) {
        return getPanel();
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    /**
     * Notifies listeners that a setting value has changed.
     * Called by the panel when a field is modified.
     */
    void changed() {
        if (!changed) {
            changed = true;
            pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
        }
        pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
    }

    private ClaudeCodeOptionsPanel getPanel() {
        if (panel == null) {
            panel = new ClaudeCodeOptionsPanel();
        }
        return panel;
    }
}
