package io.github.nbclaudecodegui.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

/**
 * {@code @}-completion popup for the prompt text area.
 *
 * <p>When the user types {@code @} in the text area, this popup appears and
 * offers file paths relative to the working directory as completions.
 * Additional characters after {@code @} filter the list. Up/Down navigates;
 * Enter or Tab inserts; Escape dismisses.
 *
 * <p>File scanning is performed on a background thread (max depth 5, max
 * ~1000 files) and results are pushed to the popup via {@code invokeLater}.
 *
 * <p>Install with {@link #install(JTextArea, Supplier)}.
 */
public final class AtCompletionPopup {

    private static final int MAX_FILES  = 1000;
    private static final int MAX_DEPTH  = 5;

    private final JTextArea  area;
    private final Supplier<String> workingDirSupplier;

    private final JPopupMenu           popup;
    private final DefaultListModel<String> listModel;
    private final JList<String>        list;

    /** All relative paths loaded from workingDir (background thread). */
    private volatile List<String>  allPaths = Collections.emptyList();
    /** Whether a scan is in progress. */
    private final AtomicBoolean    scanning = new AtomicBoolean(false);
    /** Executor for background scanning (single thread). */
    private final ExecutorService  executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "at-completion-scan");
        t.setDaemon(true);
        return t;
    });

    private AtCompletionPopup(JTextArea area, Supplier<String> workingDirSupplier) {
        this.area               = area;
        this.workingDirSupplier = workingDirSupplier;

        listModel = new DefaultListModel<>();
        list      = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(list.getFont().deriveFont(Font.PLAIN, list.getFont().getSize() - 1f));

        // Double-click inserts
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) insertSelected();
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(350, 180));

        popup = new JPopupMenu();
        popup.add(scroll);
        popup.setFocusable(false);   // keep focus in text area
    }

    /**
     * Installs an {@code AtCompletionPopup} on the given text area.
     *
     * @param area              the text area to monitor
     * @param workingDirSupplier returns the working directory path
     * @return the installed popup
     */
    public static AtCompletionPopup install(JTextArea area, Supplier<String> workingDirSupplier) {
        AtCompletionPopup popup = new AtCompletionPopup(area, workingDirSupplier);
        area.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                popup.onKeyReleased(e);
            }
            @Override
            public void keyPressed(KeyEvent e) {
                popup.onKeyPressed(e);
            }
        });
        return popup;
    }

    // -------------------------------------------------------------------------
    // Key handling
    // -------------------------------------------------------------------------

    private void onKeyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ESCAPE
                || code == KeyEvent.VK_ENTER
                || code == KeyEvent.VK_TAB
                || code == KeyEvent.VK_UP
                || code == KeyEvent.VK_DOWN
                || code == KeyEvent.VK_SPACE) {
            return;
        }
        updatePopup();
    }

    private void onKeyPressed(KeyEvent e) {
        if (!popup.isVisible()) return;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> {
                e.consume();
                int idx = list.getSelectedIndex();
                if (idx > 0) list.setSelectedIndex(idx - 1);
                list.ensureIndexIsVisible(list.getSelectedIndex());
            }
            case KeyEvent.VK_DOWN -> {
                e.consume();
                int idx = list.getSelectedIndex();
                if (idx < listModel.getSize() - 1) list.setSelectedIndex(idx + 1);
                list.ensureIndexIsVisible(list.getSelectedIndex());
            }
            case KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> {
                if (popup.isVisible() && list.getSelectedIndex() >= 0) {
                    e.consume();
                    insertSelected();
                }
            }
            case KeyEvent.VK_SPACE -> {
                e.consume();
                insertAsFile();
            }
            case KeyEvent.VK_ESCAPE -> {
                e.consume();
                popup.setVisible(false);
            }
            default -> { /* handled by keyReleased */ }
        }
    }

    // -------------------------------------------------------------------------
    // Popup update
    // -------------------------------------------------------------------------

    private void updatePopup() {
        String token = currentAtToken();
        if (token == null) {
            popup.setVisible(false);
            return;
        }
        ensureScanned();

        String typed = token.substring(1);           // text after '@'
        int lastSlash = typed.lastIndexOf('/');
        String currentDir = lastSlash >= 0 ? typed.substring(0, lastSlash + 1) : "";
        String filter     = lastSlash >= 0 ? typed.substring(lastSlash + 1)    : typed;
        String filterLc   = filter.toLowerCase(Locale.ROOT);

        // If currentDir resolves outside workingDir, delegate to filesystem listing
        if (!currentDir.isEmpty()) {
            String wdStr = workingDirSupplier.get();
            if (wdStr != null) {
                java.nio.file.Path wdPath = java.nio.file.Path.of(wdStr);
                java.nio.file.Path resolved = wdPath.resolve(currentDir).normalize();
                if (!resolved.startsWith(wdPath)) {
                    updatePopupExternal(resolved, filterLc);
                    return;
                }
            }
        }

        List<String> dirs  = new ArrayList<>();
        List<String> files = new ArrayList<>();
        Set<String> seenDirs = new HashSet<>();

        for (String path : allPaths) {
            if (!path.startsWith(currentDir)) continue;
            String rest  = path.substring(currentDir.length());
            int slash    = rest.indexOf('/');
            if (slash < 0) {
                // file directly in currentDir
                if (rest.toLowerCase(Locale.ROOT).startsWith(filterLc)) {
                    files.add(rest);
                }
            } else {
                // sub-directory
                String dir = rest.substring(0, slash + 1);
                if (dir.toLowerCase(Locale.ROOT).startsWith(filterLc) && seenDirs.add(dir)) {
                    dirs.add(dir);
                }
            }
            if (dirs.size() + files.size() >= 200) break;
        }

        if (dirs.isEmpty() && files.isEmpty()) {
            listModel.clear();
            popup.setVisible(false);
            return;
        }

        listModel.clear();
        listModel.addElement("..");       // always present
        for (String d : dirs)  listModel.addElement(d);
        for (String f : files) listModel.addElement(f);

        list.setSelectedIndex(0);

        if (!popup.isVisible()) {
            try {
                int caretPos = area.getCaretPosition();
                java.awt.Rectangle r = area.modelToView2D(caretPos).getBounds();
                popup.show(area, r.x, r.y + r.height);
            } catch (BadLocationException ex) {
                popup.show(area, 0, area.getHeight());
            }
        }
    }

    /** Lists entries in an external (outside workingDir) directory for the popup. */
    private void updatePopupExternal(java.nio.file.Path dir, String filterLc) {
        File[] entries = dir.toFile().listFiles();
        if (entries == null) {
            listModel.clear();
            popup.setVisible(false);
            return;
        }

        List<String> dirs  = new ArrayList<>();
        List<String> files = new ArrayList<>();
        for (File entry : entries) {
            if (entry.getName().startsWith(".")) continue;
            if (!entry.getName().toLowerCase(Locale.ROOT).startsWith(filterLc)) continue;
            if (entry.isDirectory()) dirs.add(entry.getName() + "/");
            else                     files.add(entry.getName());
        }
        Collections.sort(dirs);
        Collections.sort(files);

        if (dirs.isEmpty() && files.isEmpty()) {
            listModel.clear();
            popup.setVisible(false);
            return;
        }

        listModel.clear();
        listModel.addElement("..");
        for (String d : dirs)  listModel.addElement(d);
        for (String f : files) listModel.addElement(f);
        list.setSelectedIndex(0);

        if (!popup.isVisible()) {
            try {
                int caretPos = area.getCaretPosition();
                java.awt.Rectangle r = area.modelToView2D(caretPos).getBounds();
                popup.show(area, r.x, r.y + r.height);
            } catch (BadLocationException ex) {
                popup.show(area, 0, area.getHeight());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Insertion
    // -------------------------------------------------------------------------

    /** Insert selected item, navigating into directories or completing files. */
    private void insertSelected() {
        String selected = list.getSelectedValue();
        if (selected == null) return;

        String token     = currentAtToken();
        if (token == null) { popup.setVisible(false); return; }
        String typed     = token.substring(1);
        int lastSlash    = typed.lastIndexOf('/');
        String currentDir = lastSlash >= 0 ? typed.substring(0, lastSlash + 1) : "";

        if ("..".equals(selected)) {
            // Navigate up: strip the last path component from currentDir
            String parent = parentOf(currentDir);
            replaceToken("@" + parent);
            updatePopup();
        } else if (selected.endsWith("/")) {
            // Navigate into directory
            replaceToken("@" + currentDir + selected);
            updatePopup();
        } else {
            // Complete file — insert with trailing space and close popup
            popup.setVisible(false);
            replaceToken("@" + currentDir + selected + " ");
        }
    }

    /** Insert selected value as-is (with trailing space), regardless of whether it's a dir. */
    private void insertAsFile() {
        String selected = list.getSelectedValue();
        if (selected == null) return;
        popup.setVisible(false);

        String token = currentAtToken();
        if (token == null) return;
        String typed      = token.substring(1);
        int lastSlash     = typed.lastIndexOf('/');
        String currentDir = lastSlash >= 0 ? typed.substring(0, lastSlash + 1) : "";

        String value = "..".equals(selected) ? parentOf(currentDir) : currentDir + selected;
        replaceToken("@" + value + " ");
    }

    private void replaceToken(String newText) {
        String text     = area.getText();
        int caretPos    = area.getCaretPosition();
        int atIdx       = -1;
        for (int i = caretPos - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '@') { atIdx = i; break; }
            if (Character.isWhitespace(c)) break;
        }
        if (atIdx < 0) return;
        // Find end of token (walk forward past non-whitespace)
        int end = caretPos;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
        try {
            area.getDocument().remove(atIdx, end - atIdx);
            area.getDocument().insertString(atIdx, newText, null);
            // Place caret at end of inserted text
            area.setCaretPosition(atIdx + newText.length());
        } catch (BadLocationException ex) {
            area.append(newText);
        }
    }

    /**
     * Returns the parent directory path (with trailing slash).
     * At the workdir root (""), returns "../" to go above the working directory.
     */
    private static String parentOf(String currentDir) {
        if (currentDir.isEmpty()) return "../";
        String trimmed = currentDir.substring(0, currentDir.length() - 1);
        int idx = trimmed.lastIndexOf('/');
        if (idx >= 0) return trimmed.substring(0, idx + 1);
        return "..".equals(trimmed) ? "../../" : "";
    }

    // -------------------------------------------------------------------------
    // Token detection
    // -------------------------------------------------------------------------

    /**
     * Returns the {@code @}-prefixed token under the caret, or {@code null} if
     * the caret is not inside an {@code @\S+} token.
     */
    private String currentAtToken() {
        String text = area.getText();
        int pos = area.getCaretPosition();
        if (pos <= 0 || pos > text.length()) return null;

        // Walk back to find @
        int start = -1;
        for (int i = pos - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '@') { start = i; break; }
            if (Character.isWhitespace(c)) break;
        }
        if (start < 0) return null;

        // Walk forward to find end of token
        int end = pos;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;

        return text.substring(start, end);
    }

    // -------------------------------------------------------------------------
    // Background file scanning
    // -------------------------------------------------------------------------

    private void ensureScanned() {
        if (!allPaths.isEmpty() || scanning.getAndSet(true)) return;
        String wdStr = workingDirSupplier.get();
        if (wdStr == null) { scanning.set(false); return; }
        Path workDir = Path.of(wdStr);
        executor.submit(() -> {
            List<String> paths = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(workDir, MAX_DEPTH, FileVisitOption.FOLLOW_LINKS)) {
                stream.filter(p -> !Files.isDirectory(p))
                      .filter(p -> !isHidden(workDir, p))
                      .map(p -> workDir.relativize(p).toString().replace(File.separatorChar, '/'))
                      .limit(MAX_FILES)
                      .forEach(paths::add);
            } catch (Exception ignored) {
                // Partial results are fine
            }
            Collections.sort(paths);
            allPaths = Collections.unmodifiableList(paths);
            scanning.set(false);
        });
    }

    private static boolean isHidden(Path root, Path file) {
        Path rel = root.relativize(file);
        for (Path part : rel) {
            if (part.toString().startsWith(".")) return true;
        }
        return false;
    }

    /**
     * Clears the cached file list so it will be rescanned next time.
     * Called when the working directory changes.
     */
    public void clearCache() {
        allPaths = Collections.emptyList();
        scanning.set(false);
    }
}
