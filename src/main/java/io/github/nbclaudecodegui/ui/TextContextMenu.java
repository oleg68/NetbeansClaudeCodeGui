package io.github.nbclaudecodegui.ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.text.JTextComponent;

/**
 * Builds a standard Cut / Copy / Paste / Select All / Clear context menu
 * for any {@link JTextComponent}.
 *
 * <p>Usage — simple (no extra items):
 * <pre>
 *   TextContextMenu.attach(myTextField);
 * </pre>
 *
 * <p>Usage — with extra items (e.g. prompt history):
 * <pre>
 *   JPopupMenu menu = TextContextMenu.create(myTextArea);
 *   menu.addSeparator();
 *   menu.add(extraItem);
 *   TextContextMenu.attach(myTextArea, menu);
 * </pre>
 */
public final class TextContextMenu {

    private TextContextMenu() {}

    /**
     * Creates a JPopupMenu with Cut, Copy, Paste, Select All, [separator], Clear.
     * Does NOT attach it — call {@link #attach(JTextComponent, JPopupMenu)} afterwards
     * if you need to add more items first.
     *
     * @param tc the text component whose actions the menu items delegate to
     * @return the configured popup menu
     */
    public static JPopupMenu create(JTextComponent tc) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem cut = new JMenuItem("Cut");
        cut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));
        cut.addActionListener(e -> tc.cut());

        JMenuItem copy = new JMenuItem("Copy");
        copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copy.addActionListener(e -> tc.copy());

        JMenuItem paste = new JMenuItem("Paste");
        paste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        paste.addActionListener(e -> tc.paste());

        JMenuItem selectAll = new JMenuItem("Select All");
        selectAll.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK));
        selectAll.addActionListener(e -> tc.selectAll());

        JMenuItem clear = new JMenuItem("Clear");
        clear.addActionListener(e -> tc.setText(""));

        menu.add(cut);
        menu.add(copy);
        menu.add(paste);
        menu.add(selectAll);
        menu.addSeparator();
        menu.add(clear);
        return menu;
    }

    /**
     * Attaches a pre-built menu to a text component via mousePressed + mouseReleased
     * popup triggers (handles both Windows and macOS conventions).
     *
     * @param tc   the text component to attach the menu to
     * @param menu the popup menu to show on right-click
     */
    public static void attach(JTextComponent tc, JPopupMenu menu) {
        tc.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) menu.show(tc, e.getX(), e.getY());
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) menu.show(tc, e.getX(), e.getY());
            }
        });
    }

    /**
     * Convenience: create and attach in one call when no extra items are needed.
     *
     * @param tc the text component to attach a default context menu to
     */
    public static void attach(JTextComponent tc) {
        attach(tc, create(tc));
    }
}
