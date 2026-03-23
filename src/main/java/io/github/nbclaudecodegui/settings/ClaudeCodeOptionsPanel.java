package io.github.nbclaudecodegui.settings;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * Settings panel displayed inside Tools → Options → Claude Code.
 *
 * <p>Contains two tabs:
 * <ul>
 *   <li><b>General</b> — Claude CLI path, MCP port, send/newline key bindings, debug mode</li>
 *   <li><b>Profiles</b> — named connection profiles with auth, proxy, and extra env vars
 *       (see {@link ClaudeProfilesPanel})</li>
 * </ul>
 *
 * <p>{@link #load()} and {@link #store()} delegate to both tabs.
 */
public final class ClaudeCodeOptionsPanel extends JPanel {

    private static final String[] KEY_VALUES = {
        ClaudeCodePreferences.ENTER,
        ClaudeCodePreferences.SHIFT_ENTER,
        ClaudeCodePreferences.CTRL_ENTER,
        ClaudeCodePreferences.ALT_ENTER
    };
    private static final String[] KEY_LABELS = {
        "Enter", "Shift+Enter", "Ctrl+Enter", "Alt+Enter"
    };

    private JTextField executablePathField;
    private JSpinner mcpPortSpinner;
    private javax.swing.JCheckBox debugCheckBox;

    /** send-key radio buttons: value → button */
    private final Map<String, JRadioButton> sendRadios = new LinkedHashMap<>();
    /** newline-key radio buttons: value → button */
    private final Map<String, JRadioButton> newlineRadios = new LinkedHashMap<>();

    private final ButtonGroup sendGroup    = new ButtonGroup();
    private final ButtonGroup newlineGroup = new ButtonGroup();

    /** Profiles tab panel. */
    private final ClaudeProfilesPanel profilesPanel = new ClaudeProfilesPanel();

    /**
     * Creates the panel and initialises all UI components.
     */
    public ClaudeCodeOptionsPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("General", buildGeneralPanel());
        tabs.addTab("Profiles", profilesPanel);
        add(tabs, BorderLayout.CENTER);
    }

    /**
     * Builds the General tab containing the original settings controls.
     *
     * @return the General tab panel
     */
    private JPanel buildGeneralPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        int row = 0;

        // --- executable path ---
        form.add(new JLabel("Claude CLI path:"), gbc(0, row, false));
        executablePathField = new JTextField(30);
        executablePathField.setToolTipText("Leave empty to detect 'claude' from PATH");
        form.add(executablePathField, gbcFill(1, row));
        JButton browseButton = new JButton("Browse\u2026");
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                executablePathField.setText(
                        chooser.getSelectedFile().getAbsolutePath());
            }
        });
        form.add(browseButton, gbc(2, row, false));
        row++;

        // --- MCP port ---
        form.add(new JLabel("MCP server port:"), gbc(0, row, false));
        mcpPortSpinner = new JSpinner(new SpinnerNumberModel(
                ClaudeCodePreferences.DEFAULT_MCP_PORT, 1024, 65535, 1));
        mcpPortSpinner.setToolTipText("Port for the NetBeans MCP SSE server (restart required)");
        form.add(mcpPortSpinner, gbc(1, row, false));
        row++;

        // --- send key ---
        JPanel sendPanel = buildKeyPanel("Send prompt:", sendRadios, sendGroup);
        GridBagConstraints sendGbc = new GridBagConstraints();
        sendGbc.gridx = 0; sendGbc.gridy = row;
        sendGbc.gridwidth = 3;
        sendGbc.anchor = GridBagConstraints.WEST;
        sendGbc.insets = new Insets(12, 8, 4, 8);
        form.add(sendPanel, sendGbc);
        row++;

        // --- newline key ---
        JPanel newlinePanel = buildKeyPanel("Insert newline:", newlineRadios, newlineGroup);
        GridBagConstraints nlGbc = new GridBagConstraints();
        nlGbc.gridx = 0; nlGbc.gridy = row;
        nlGbc.gridwidth = 3;
        nlGbc.anchor = GridBagConstraints.WEST;
        nlGbc.insets = new Insets(4, 8, 4, 8);
        form.add(newlinePanel, nlGbc);
        row++;

        // mutual exclusion: selecting in one group disables that option in the other
        for (int i = 0; i < KEY_VALUES.length; i++) {
            final String val = KEY_VALUES[i];
            sendRadios.get(val).addActionListener(e -> syncExclusion(val, true));
            newlineRadios.get(val).addActionListener(e -> syncExclusion(val, false));
        }

        // --- debug mode ---
        debugCheckBox = new javax.swing.JCheckBox("Debug mode (log all claude I/O to NetBeans log and output area)");
        GridBagConstraints dbgGbc = new GridBagConstraints();
        dbgGbc.gridx = 0; dbgGbc.gridy = row;
        dbgGbc.gridwidth = 3;
        dbgGbc.anchor = GridBagConstraints.WEST;
        dbgGbc.insets = new Insets(4, 8, 4, 8);
        form.add(debugCheckBox, dbgGbc);
        row++;

        // spacer
        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridx = 0; spacer.gridy = row;
        spacer.gridwidth = 3; spacer.weighty = 1.0;
        spacer.fill = GridBagConstraints.VERTICAL;
        form.add(new JPanel(), spacer);

        return form;
    }

    private JPanel buildKeyPanel(String title,
            Map<String, JRadioButton> radios, ButtonGroup group) {
        JPanel panel = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.LEFT, 8, 0));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        for (int i = 0; i < KEY_VALUES.length; i++) {
            JRadioButton rb = new JRadioButton(KEY_LABELS[i]);
            radios.put(KEY_VALUES[i], rb);
            group.add(rb);
            panel.add(rb);
        }
        return panel;
    }

    /**
     * Called when a radio button is selected: disables that value in the
     * opposite group (and re-enables the previously-disabled one).
     *
     * @param selectedValue the just-selected key value
     * @param inSendGroup   {@code true} if the event came from the send group
     */
    private void syncExclusion(String selectedValue, boolean inSendGroup) {
        Map<String, JRadioButton> opposite =
                inSendGroup ? newlineRadios : sendRadios;

        // re-enable all in opposite group first
        opposite.values().forEach(rb -> rb.setEnabled(true));

        // disable the one that matches the selected value
        JRadioButton toDisable = opposite.get(selectedValue);
        if (toDisable != null) {
            toDisable.setEnabled(false);
            // if it was selected, move selection to the first available option
            if (toDisable.isSelected()) {
                ButtonGroup grp = inSendGroup ? newlineGroup : sendGroup;
                for (JRadioButton rb : opposite.values()) {
                    if (rb.isEnabled()) {
                        rb.setSelected(true);
                        grp.setSelected(rb.getModel(), true);
                        break;
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // load / store / valid
    // -------------------------------------------------------------------------

    /**
     * Loads current preference values into the UI controls (both tabs).
     */
    void load() {
        executablePathField.setText(
                ClaudeCodePreferences.getClaudeExecutablePath());
        mcpPortSpinner.setValue(ClaudeCodePreferences.getMcpPort());
        debugCheckBox.setSelected(ClaudeCodePreferences.isDebugMode());

        String sendVal    = ClaudeCodePreferences.getSendKey();
        String newlineVal = ClaudeCodePreferences.getNewlineKey();

        selectRadio(sendRadios,    sendGroup,    sendVal);
        selectRadio(newlineRadios, newlineGroup, newlineVal);

        // apply mutual exclusion for the loaded values
        syncExclusion(sendVal, true);
        syncExclusion(newlineVal, false);

        profilesPanel.load();
    }

    /**
     * Persists the values currently shown in the UI controls to preferences
     * (both tabs).
     */
    void store() {
        ClaudeCodePreferences.setClaudeExecutablePath(
                executablePathField.getText().trim());
        ClaudeCodePreferences.setMcpPort((Integer) mcpPortSpinner.getValue());
        ClaudeCodePreferences.setDebugMode(debugCheckBox.isSelected());
        ClaudeCodePreferences.setSendKey(selectedValue(sendRadios));
        ClaudeCodePreferences.setNewlineKey(selectedValue(newlineRadios));

        profilesPanel.store();
    }

    /**
     * Validates the current panel state.
     *
     * @return {@code true} — the panel is always valid
     */
    boolean valid() {
        return true;
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static void selectRadio(Map<String, JRadioButton> radios,
            ButtonGroup group, String value) {
        JRadioButton rb = radios.get(value);
        if (rb != null) {
            rb.setSelected(true);
            group.setSelected(rb.getModel(), true);
        }
    }

    private static String selectedValue(Map<String, JRadioButton> radios) {
        for (Map.Entry<String, JRadioButton> e : radios.entrySet()) {
            if (e.getValue().isSelected()) return e.getKey();
        }
        return ClaudeCodePreferences.DEFAULT_SEND_KEY;
    }

    private static GridBagConstraints gbc(int x, int y, boolean fill) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x; c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(8, x == 0 ? 8 : 0, 4, x == 2 ? 8 : 4);
        return c;
    }

    private static GridBagConstraints gbcFill(int x, int y) {
        GridBagConstraints c = gbc(x, y, true);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        return c;
    }
}
