package io.github.nbclaudecodegui.ui;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Custom tab header component that displays a label and a close (×) button.
 *
 * <p>Attach to a tab via {@link JTabbedPane#setTabComponentAt(int, java.awt.Component)}.
 */
final class TabHeader extends JPanel {

    private final JLabel label;

    /**
     * Creates a tab header for the given tabbed pane.
     *
     * @param pane    the owning tabbed pane
     * @param title   initial tab title
     * @param onClose runnable invoked when the close button is clicked;
     *                receives the tab index at click time
     */
    TabHeader(JTabbedPane pane, String title, Runnable onClose) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        label = new JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

        JButton closeBtn = new JButton("\u00d7");
        closeBtn.setFont(closeBtn.getFont().deriveFont(10f));
        closeBtn.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusable(false);
        closeBtn.setToolTipText("Close");
        closeBtn.addActionListener((ActionEvent e) -> onClose.run());

        add(label);
        add(closeBtn);
    }

    /**
     * Updates the displayed title text.
     *
     * @param title the new title
     */
    void setTitle(String title) {
        label.setText(title);
    }
}
