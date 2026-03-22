package io.github.nbclaudecodegui.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/**
 * One-line permission bar shown at the bottom of a diff view:
 *
 * <pre>
 *   [✓ Accept]   [___Decline reason (Optional)___]  [✗ Decline]   [Cancel]
 * </pre>
 *
 * <ul>
 *   <li><b>Accept</b> — allow the proposed change; warns if a Decline reason is already typed</li>
 *   <li><b>Decline</b> — deny the change, with an optional reason sent to Claude</li>
 *   <li><b>Cancel</b> — deny the change <em>and</em> interrupt Claude's running prompt (Ctrl+C)</li>
 * </ul>
 */
public final class FileDiffPermissionPanel extends JPanel {

    private final JButton acceptBtn;
    private final JLabel warningLabel;

    /** Placeholder text shown in the decline reason field when it is empty. */
    static final String REASON_HINT = "Decline reason (Optional)";

    /** \u2713 CHECK MARK — used on confirmation buttons (Accept, Yes) */
    public static final String ICON_ACCEPT = "\u2713";

    /** \u2717 BALLOT X — used on decline buttons (Decline, No) */
    public static final String ICON_DECLINE = "\u2717";

    /**
     * Creates the panel.
     *
     * @param onAccept called when the user clicks Accept
     * @param onDecline called with the (possibly empty) reason text when the user clicks Decline
     * @param onCancel called when the user clicks Cancel (caller should also send Ctrl+C)
     */
    public FileDiffPermissionPanel(Runnable onAccept, Consumer<String> onDecline, Runnable onCancel) {
        super(new BorderLayout(4, 0));

        // Accept button (green)
        JButton acceptBtn = new JButton(ICON_ACCEPT + " Accept");
        acceptBtn.setToolTipText("Accept this change");
        acceptBtn.setOpaque(true);
        acceptBtn.setBackground(new Color(34, 139, 34));
        acceptBtn.setForeground(Color.WHITE);
        acceptBtn.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                acceptBtn.setBackground(new Color(34, 139, 34).brighter());
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                acceptBtn.setBackground(new Color(34, 139, 34));
            }
        });

        // Decline button (red)
        JButton declineBtn = new JButton(ICON_DECLINE + " Decline");
        declineBtn.setToolTipText("Decline this change");
        declineBtn.setOpaque(true);
        declineBtn.setBackground(new Color(178, 34, 34));
        declineBtn.setForeground(Color.WHITE);
        declineBtn.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                declineBtn.setBackground(new Color(178, 34, 34).brighter());
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                declineBtn.setBackground(new Color(178, 34, 34));
            }
        });

        // Reason field
        JTextField reasonField = new JTextField();
        reasonField.putClientProperty("JTextField.placeholderText", REASON_HINT);
        reasonField.setToolTipText("Optional reason sent to Claude when declining");
        TextContextMenu.attach(reasonField);

        // Cancel button — far right
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setToolTipText("Cancel — reject and interrupt Claude's current task");

        this.acceptBtn = acceptBtn;
        this.warningLabel = new JLabel();
        this.warningLabel.setForeground(Color.RED);
        this.warningLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        this.warningLabel.setVisible(false);

        // Wire actions
        acceptBtn.addActionListener(e -> {
            String reason = reasonField.getText().trim();
            if (!reason.isEmpty()) {
                int choice = JOptionPane.showConfirmDialog(
                        acceptBtn,
                        "You have typed a decline reason: \"" + reason + "\"\n"
                        + "It will NOT be sent. Accept the change anyway?",
                        "Accept with decline reason",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) return;
            }
            onAccept.run();
        });
        declineBtn.addActionListener(e -> onDecline.accept(reasonField.getText().trim()));
        cancelBtn.addActionListener(e -> onCancel.run());

        // Enter in reason field: if non-empty (and not just the hint) → click Decline; if empty → do nothing
        reasonField.addActionListener(e -> {
            String reason = reasonField.getText().trim();
            if (!reason.isEmpty() && !reason.equals(REASON_HINT)) {
                declineBtn.doClick();
            }
        });

        // Layout: WEST=[Accept]  CENTER=[reasonField][Decline]  EAST=[Cancel]
        JPanel acceptWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        acceptWrap.add(acceptBtn);

        JPanel centerGroup = new JPanel(new BorderLayout(4, 0));
        centerGroup.add(reasonField, BorderLayout.CENTER);
        JPanel rejectWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        rejectWrap.add(declineBtn);
        centerGroup.add(rejectWrap, BorderLayout.EAST);

        JPanel cancelWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        cancelWrap.add(cancelBtn);

        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, getForeground().darker()));
        add(warningLabel, BorderLayout.NORTH);
        add(acceptWrap, BorderLayout.WEST);
        add(centerGroup, BorderLayout.CENTER);
        add(cancelWrap, BorderLayout.EAST);

        // Cyclic Tab/Shift+Tab focus: Accept → reason field → Decline → Cancel → Accept
        setFocusCycleRoot(true);
        java.awt.Component[] cycle = {acceptBtn, reasonField, declineBtn, cancelBtn};
        setFocusTraversalPolicy(new java.awt.FocusTraversalPolicy() {
            @Override public java.awt.Component getComponentAfter(java.awt.Container c, java.awt.Component comp) {
                for (int i = 0; i < cycle.length; i++) if (cycle[i] == comp) return cycle[(i + 1) % cycle.length];
                return cycle[0];
            }
            @Override public java.awt.Component getComponentBefore(java.awt.Container c, java.awt.Component comp) {
                for (int i = 0; i < cycle.length; i++) if (cycle[i] == comp) return cycle[(i + cycle.length - 1) % cycle.length];
                return cycle[cycle.length - 1];
            }
            @Override public java.awt.Component getFirstComponent(java.awt.Container c) { return cycle[0]; }
            @Override public java.awt.Component getLastComponent(java.awt.Container c)  { return cycle[cycle.length - 1]; }
            @Override public java.awt.Component getDefaultComponent(java.awt.Container c) { return cycle[0]; }
        });

        // Escape from any focused child → Cancel
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "cancel");
        getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { cancelBtn.doClick(); }
        });
    }

    /**
     * Shows a warning message above the button row.
     * Pass {@code null} or an empty string to hide the warning.
     */
    public void showWarning(String message) {
        if (message == null || message.isBlank()) {
            warningLabel.setVisible(false);
        } else {
            warningLabel.setText(message);
            warningLabel.setVisible(true);
        }
    }

    /** Moves keyboard focus to the Accept button. */
    public void requestAcceptFocus() {
        acceptBtn.requestFocusInWindow();
    }
}
