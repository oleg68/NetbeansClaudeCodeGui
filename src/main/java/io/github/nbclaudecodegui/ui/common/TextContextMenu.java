package io.github.nbclaudecodegui.ui.common;

import io.github.nbclaudecodegui.model.FavoriteEntry;
import io.github.nbclaudecodegui.model.PromptFavoritesStore;
import io.github.nbclaudecodegui.ui.FavoritesDialog;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.function.Supplier;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Builds a standard Cut / Copy / Paste / Select All / Clear context menu
 * for any {@link JTextComponent}, optionally including Add to Favorites and
 * Favorites browser items.
 *
 * <p>Usage — simple (no extra items):
 * <pre>
 *   TextContextMenu.attach(myTextField);
 * </pre>
 *
 * <p>Usage — with favorites (wdSupplier may return null; items will be disabled):
 * <pre>
 *   JPopupMenu menu = TextContextMenu.create(myTextArea, wdSupplier, windowSupplier);
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
     * Creates a JPopupMenu with standard editing actions plus Add to Favorites and
     * Favorites browser items. Both favorites items are enabled only when
     * {@code wdSupplier.get()} returns a non-null value (evaluated lazily on popup open).
     *
     * @param tc             the text component whose actions the menu items delegate to
     * @param wdSupplier     returns the current working directory path, or {@code null}
     * @param windowSupplier returns the ancestor Window for dialogs
     * @return the configured popup menu
     */
    public static JPopupMenu create(JTextComponent tc,
                                    Supplier<String> wdSupplier,
                                    Supplier<Window> windowSupplier) {
        JPopupMenu menu = create(tc);

        JMenuItem addToFav = new JMenuItem("Add to Favorites");
        addToFav.addActionListener(e -> {
            String wd = wdSupplier.get();
            if (wd == null) return;
            String text = tc.getText().trim();
            if (!text.isEmpty()) {
                PromptFavoritesStore.getInstance(Path.of(wd))
                        .addProject(FavoriteEntry.ofProject(text));
            }
        });

        JMenuItem openFav = new JMenuItem("Favorites...");
        openFav.addActionListener(e -> {
            String wd = wdSupplier.get();
            if (wd == null) return;
            Window win = windowSupplier.get();
            FavoritesDialog dlg = new FavoritesDialog(win, Path.of(wd), text -> tc.setText(text));
            dlg.setVisible(true);
        });

        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                boolean hasWd = wdSupplier.get() != null;
                addToFav.setEnabled(hasWd && !tc.getText().trim().isEmpty());
                openFav.setEnabled(hasWd);
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });

        menu.addSeparator();
        menu.add(addToFav);
        menu.add(openFav);
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
