package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.model.AttachedFilesModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * View panel that shows attached files as dismissible chips.
 *
 * <p>Hidden when the attachment list is empty. Each chip shows the filename
 * and has a tooltip with the full absolute path and an "×" dismiss button.
 *
 * <p>The panel is driven by an {@link AttachedFilesModel}; callers must call
 * {@link #refresh()} after modifying the model.
 */
public final class AttachedFilesPanel extends JPanel {

    private static final Color CHIP_BG     = new Color(0xE8, 0xF4, 0xFF);
    private static final Color CHIP_BORDER = new Color(0x99, 0xCC, 0xFF);
    private static final Color CLOSE_FG    = new Color(0x66, 0x66, 0x66);

    private final AttachedFilesModel model;
    private final Runnable onChanged;

    /**
     * Creates the chip panel.
     *
     * @param model     the model to read files from
     * @param onChanged callback invoked after any chip is removed
     */
    public AttachedFilesPanel(AttachedFilesModel model, Runnable onChanged) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 2));
        this.model     = model;
        this.onChanged = onChanged;
        setVisible(false);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }

    /**
     * Rebuilds the chip list from the current model state.
     * Must be called on the EDT.
     */
    public void refresh() {
        removeAll();
        List<File> files = model.getFiles();
        for (File f : files) {
            add(buildChip(f));
        }
        setVisible(!files.isEmpty());
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private JPanel buildChip(File file) {
        JPanel chip = new JPanel();
        chip.setLayout(new BoxLayout(chip, BoxLayout.X_AXIS));
        chip.setBackground(CHIP_BG);
        chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CHIP_BORDER, 1, true),
                BorderFactory.createEmptyBorder(1, 5, 1, 2)));
        chip.setOpaque(true);

        JLabel nameLabel = new JLabel(file.getName());
        nameLabel.setToolTipText(file.getAbsolutePath());
        nameLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        nameLabel.setFont(nameLabel.getFont().deriveFont(nameLabel.getFont().getSize2D() - 1f));

        JButton closeBtn = new JButton("×");
        closeBtn.setToolTipText("Remove " + file.getName());
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setForeground(CLOSE_FG);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.setFont(closeBtn.getFont().deriveFont(closeBtn.getFont().getSize2D() - 1f));
        closeBtn.setAlignmentY(Component.CENTER_ALIGNMENT);
        closeBtn.setPreferredSize(new Dimension(16, 16));
        closeBtn.setMinimumSize(new Dimension(16, 16));
        closeBtn.setMaximumSize(new Dimension(16, 16));
        closeBtn.addActionListener(e -> {
            model.removeFile(file);
            refresh();
            onChanged.run();
        });

        chip.add(nameLabel);
        chip.add(Box.createHorizontalStrut(2));
        chip.add(closeBtn);
        return chip;
    }
}
