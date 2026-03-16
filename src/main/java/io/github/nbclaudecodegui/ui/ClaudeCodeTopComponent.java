package io.github.nbclaudecodegui.ui;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * Claude Code chat window — the main UI container for the plugin.
 *
 * <p>Currently shows a placeholder message; tabs and real chat will be
 * implemented in Stage 4 and beyond.
 */
@TopComponent.Description(
    preferredID = "ClaudeCodeTopComponent",
    iconBase = "io/github/nbclaudecodegui/icons/claude-icon.png",
    persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(
    mode = "properties",
    openAtStartup = false
)
@ActionID(
    category = "Window",
    id = "io.github.nbclaudecodegui.ui.ClaudeCodeTopComponent"
)
@ActionReference(path = "Menu/Window", position = 500)
@TopComponent.OpenActionRegistration(
    displayName = "#CTL_ClaudeCodeTopComponent",
    preferredID = "ClaudeCodeTopComponent"
)
@Messages("CTL_ClaudeCodeTopComponent=Claude Code")
public final class ClaudeCodeTopComponent extends TopComponent {

    /** Creates the top component and initialises the placeholder UI. */
    public ClaudeCodeTopComponent() {
        initComponents();
        setName("Claude Code");
        setToolTipText("Claude Code chat window");
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        JPanel placeholder = new JPanel(new BorderLayout());
        JLabel label = new JLabel("Claude Code — coming in Stage 4", SwingConstants.CENTER);
        placeholder.add(label, BorderLayout.CENTER);
        add(placeholder, BorderLayout.CENTER);
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_ALWAYS;
    }

}
