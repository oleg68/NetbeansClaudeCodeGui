package io.github.nbclaudecodegui.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * One-line permission bar shown at the bottom of a diff view:
 *
 * <pre>
 *   [✓ Accept]  [✗ Decline]  [___Decline reason (Optional)___________]  [Cancel]
 * </pre>
 *
 * <ul>
 *   <li><b>Accept</b> — allow the proposed change; warns if a Decline reason is already typed</li>
 *   <li><b>Decline</b> — deny the change, with an optional reason sent to Claude</li>
 *   <li><b>Cancel</b> — deny the change <em>and</em> interrupt Claude's running prompt (Ctrl+C)</li>
 * </ul>
 */
public final class PermissionPanel extends JPanel {

    private final JButton acceptBtn;

    /** Placeholder text shown in the decline reason field when it is empty. */
    static final String REASON_HINT = "Decline reason (Optional)";

    /** \u2713 CHECK MARK — used on confirmation buttons (Accept, Yes) */
    public static final String ICON_ACCEPT = "\u2713";

    /** \u2717 BALLOT X — used on rejection buttons (Reject, No) */
    public static final String ICON_REJECT = "\u2717";

    /**
     * Creates the panel.
     *
     * @param onAccept called when the user clicks Accept
     * @param onReject called with the (possibly empty) reason text when the user clicks Reject
     * @param onCancel called when the user clicks Cancel (caller should also send Ctrl+C)
     */
    public PermissionPanel(Runnable onAccept, Consumer<String> onReject, Runnable onCancel) {
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
        JButton rejectBtn = new JButton(ICON_REJECT + " Decline");
        rejectBtn.setToolTipText("Decline this change");
        rejectBtn.setOpaque(true);
        rejectBtn.setBackground(new Color(178, 34, 34));
        rejectBtn.setForeground(Color.WHITE);
        rejectBtn.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                rejectBtn.setBackground(new Color(178, 34, 34).brighter());
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                rejectBtn.setBackground(new Color(178, 34, 34));
            }
        });

        // Reason field — filled width between Decline and Cancel
        JTextField reasonField = new JTextField();
        reasonField.putClientProperty("JTextField.placeholderText", REASON_HINT);
        reasonField.setToolTipText("Optional reason sent to Claude when declining");

        // Cancel button — far right
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setToolTipText("Cancel — reject and interrupt Claude's current task");

        this.acceptBtn = acceptBtn;

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
        rejectBtn.addActionListener(e -> onReject.accept(reasonField.getText().trim()));
        cancelBtn.addActionListener(e -> onCancel.run());

        // Enter in reason field: if non-empty (and not just the hint) → Decline; if empty → do nothing
        reasonField.addActionListener(e -> {
            String reason = reasonField.getText().trim();
            if (!reason.isEmpty() && !reason.equals(REASON_HINT)) {
                onReject.accept(reason);
            }
        });

        // Layout: WEST=[Accept][Reject]  CENTER=reasonField  EAST=[Cancel]
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        buttons.add(acceptBtn);
        buttons.add(rejectBtn);

        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, getForeground().darker()));
        add(buttons, BorderLayout.WEST);
        add(reasonField, BorderLayout.CENTER);
        add(cancelBtn, BorderLayout.EAST);
    }

    /** Moves keyboard focus to the Accept button. */
    public void requestAcceptFocus() {
        acceptBtn.requestFocusInWindow();
    }
}
