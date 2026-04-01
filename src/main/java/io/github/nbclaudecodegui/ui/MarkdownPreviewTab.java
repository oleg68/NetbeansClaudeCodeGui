package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.ui.common.MarkdownRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.windows.TopComponent;

/**
 * TopComponent that shows a rendered markdown preview.
 * Can display a before/after side-by-side view (from diff) or a single live-updating file view.
 *
 * <p>The {@link #openLive} factory deduplicates tabs by file path and updates existing
 * tabs when called again for the same path.  File-system changes are tracked via
 * {@link FileChangeListener} when a {@link FileObject} is provided.
 */
public class MarkdownPreviewTab extends TopComponent {

    /**
     * Open live-preview tabs keyed by absolute file path.
     * WeakHashMap so that closed / GC'd TCs are eventually removed automatically.
     */
    private static final Map<String, MarkdownPreviewTab> OPEN_TABS =
            Collections.synchronizedMap(new HashMap<>());

    /** The editor pane that renders HTML-converted markdown. */
    JEditorPane pane;

    /** The scroll pane wrapping {@link #pane}, used to preserve scroll position on updates. */
    private JScrollPane scrollPane;

    /** Listener attached to the backing FileObject, if any. */
    private FileChangeListener fileListener;

    /** The FileObject being monitored (may be null for diff-pinned previews). */
    private FileObject fileObject;

    /** Absolute path key used to identify this tab in {@link #OPEN_TABS}. */
    String filePath;

    /**
     * Opens (or re-activates) a live-updating markdown preview tab for {@code filePath}.
     *
     * <ul>
     *   <li>If an open tab already exists for {@code filePath} it is updated with
     *       {@code content} and brought to the front — no new tab is created.</li>
     *   <li>Otherwise a new tab is created.  If {@code fo} is non-null a
     *       {@link FileChangeListener} is attached so the tab refreshes automatically
     *       when the file changes on disk.</li>
     * </ul>
     *
     * @param filePath absolute path of the file (used as dedup key and tab title)
     * @param content  initial markdown text to render
     * @param fo       optional {@link FileObject} to watch for file-system changes
     */
    public static void openLive(String filePath, String content, FileObject fo) {
        MarkdownPreviewTab existing = OPEN_TABS.get(filePath);
        if (existing != null && existing.isOpened()) {
            existing.updateContent(content);
            if (existing.fileObject == null && fo != null) {
                existing.fileObject = fo;
                existing.fileListener = makeFileListener(existing, fo);
                fo.addFileChangeListener(existing.fileListener);
            }
            existing.requestActive();
            return;
        }

        MarkdownPreviewTab tab = new MarkdownPreviewTab();
        tab.filePath = filePath;
        tab.fileObject = fo;

        String html = MarkdownRenderer.toHtml(content != null ? content : "");
        tab.pane = MarkdownRenderer.createOutputPane(html);

        String name = new java.io.File(filePath).getName();
        tab.setDisplayName("Preview: " + name);
        tab.setLayout(new BorderLayout());
        tab.scrollPane = new JScrollPane(tab.pane);
        tab.add(tab.scrollPane, BorderLayout.CENTER);

        if (fo != null) {
            tab.fileListener = makeFileListener(tab, fo);
            fo.addFileChangeListener(tab.fileListener);
        }

        OPEN_TABS.put(filePath, tab);
        tab.open();
        tab.requestActive();
    }

    /**
     * Updates the rendered content of this preview tab.
     * Schedules the update on the EDT if not already there.
     *
     * @param markdown markdown text to render
     */
    public void updateContent(String markdown) {
        String html = MarkdownRenderer.toHtml(markdown != null ? markdown : "");
        SwingUtilities.invokeLater(() -> {
            if (pane == null) return;
            if (scrollPane == null) {
                pane.setText(html);
                return;
            }
            JViewport viewport = scrollPane.getViewport();
            Dimension viewSize = viewport.getViewSize();
            double relY = viewSize.height > 0
                    ? (double) viewport.getViewPosition().y / viewSize.height : 0.0;
            pane.setText(html);
            SwingUtilities.invokeLater(() -> {
                Dimension newSize = viewport.getViewSize();
                int newY = (int) (relY * newSize.height);
                viewport.setViewPosition(new Point(0, newY));
            });
        });
    }

    @Override
    public void componentClosed() {
        super.componentClosed();
        if (fileObject != null && fileListener != null) {
            fileObject.removeFileChangeListener(fileListener);
        }
        if (filePath != null) {
            OPEN_TABS.remove(filePath);
        }
    }

    /**
     * Creates a {@link FileChangeListener} that refreshes {@code tab} whenever
     * {@code fo} changes on disk.  Handles both in-place writes ({@code fileChanged})
     * and atomic-replace writes ({@code fileDataCreated}) and calls
     * {@link FileObject#refresh} before reading to avoid stale VFS cache.
     */
    private static FileChangeListener makeFileListener(MarkdownPreviewTab tab, FileObject fo) {
        return new FileChangeAdapter() {
            @Override
            public void fileChanged(FileEvent fe) {
                reload();
            }
            @Override
            public void fileDataCreated(FileEvent fe) {
                reload();
            }
            private void reload() {
                try {
                    fo.refresh(false);
                    tab.updateContent(fo.asText());
                } catch (IOException e) {
                    // silently ignore
                }
            }
        };
    }

    /**
     * Opens a single-pane markdown preview for a file.
     * Uses {@link #openLive} so the tab deduplicates and refreshes on file changes.
     *
     * @param fo the file object to preview
     */
    public static void openFile(FileObject fo) {
        try {
            openLive(fo.getPath(), fo.asText(), fo);
        } catch (Exception e) {
            // silently ignore
        }
    }

    /**
     * Opens a before/after side-by-side markdown preview for a diff.
     *
     * @param filePath the file path (used for tab title)
     * @param before   the original markdown content
     * @param after    the modified markdown content
     */
    public static void open(String filePath, String before, String after) {
        MarkdownPreviewTab tab = new MarkdownPreviewTab();
        String name = filePath.contains("/")
                ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath;
        tab.setDisplayName("Preview: " + name);
        tab.setLayout(new BorderLayout());
        tab.add(new MarkdownDiffPanel(before, after), BorderLayout.CENTER);
        tab.open();
        tab.requestActive();
    }

    // --- Test hooks (package-private) ---

    static void clearOpenTabsForTest() {
        OPEN_TABS.clear();
    }

    static Map<String, MarkdownPreviewTab> getOpenTabsForTest() {
        return OPEN_TABS;
    }

    void setPaneForTest(JEditorPane testPane) {
        this.pane = testPane;
    }

    void setFilePathForTest(String path) {
        this.filePath = path;
    }

    /** Calls the componentClosed logic without invoking the NB TC lifecycle. */
    void componentClosedForTest() {
        if (fileObject != null && fileListener != null) {
            fileObject.removeFileChangeListener(fileListener);
        }
        if (filePath != null) {
            OPEN_TABS.remove(filePath);
        }
    }
}
