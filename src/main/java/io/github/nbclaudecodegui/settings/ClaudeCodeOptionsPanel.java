package io.github.nbclaudecodegui.settings;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Settings panel displayed inside Tools → Options → Claude Code.
 *
 * <p>Currently exposes a single field: the path to the {@code claude} CLI
 * executable. Additional fields will be added in subsequent stages.
 */
public final class ClaudeCodeOptionsPanel extends JPanel {

    private JTextField executablePathField;

    /**
     * Creates the panel and initialises all UI components.
     */
    public ClaudeCodeOptionsPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = 0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(8, 8, 4, 8);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = 0;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.insets = new Insets(8, 0, 4, 4);

        GridBagConstraints browseConstraints = new GridBagConstraints();
        browseConstraints.gridx = 2;
        browseConstraints.gridy = 0;
        browseConstraints.insets = new Insets(8, 0, 4, 8);

        form.add(new JLabel("Claude CLI path:"), labelConstraints);

        executablePathField = new JTextField(30);
        executablePathField.setToolTipText("Leave empty to use 'claude' from PATH");
        form.add(executablePathField, fieldConstraints);

        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                executablePathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        form.add(browseButton, browseConstraints);

        // spacer row to push form to the top
        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridx = 0;
        spacer.gridy = 1;
        spacer.gridwidth = 3;
        spacer.weighty = 1.0;
        spacer.fill = GridBagConstraints.VERTICAL;
        form.add(new JPanel(), spacer);

        add(form, BorderLayout.CENTER);
    }

    /**
     * Loads current preference values into the UI controls.
     */
    void load() {
        executablePathField.setText(ClaudeCodePreferences.getClaudeExecutablePath());
    }

    /**
     * Persists the values currently shown in the UI controls to preferences.
     */
    void store() {
        ClaudeCodePreferences.setClaudeExecutablePath(executablePathField.getText().trim());
    }

    /**
     * Validates the current panel state.
     *
     * @return {@code true} — the panel is always considered valid at this stage
     */
    boolean valid() {
        return true;
    }
}
