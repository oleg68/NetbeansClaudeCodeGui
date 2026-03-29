package io.github.nbclaudecodegui.ui;

import java.awt.BorderLayout;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import org.openide.filesystems.FileObject;
import org.openide.windows.TopComponent;

/**
 * TopComponent that shows a rendered markdown preview.
 * Can display a before/after side-by-side view (from diff) or a single file view.
 */
public class MarkdownPreviewTab extends TopComponent {

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

    /**
     * Opens a single-pane markdown preview for a file.
     *
     * @param fo the file object to preview
     */
    public static void openFile(FileObject fo) {
        try {
            String content = fo.asText();
            String html = MarkdownRenderer.toHtml(content);
            MarkdownPreviewTab tab = new MarkdownPreviewTab();
            tab.setDisplayName("Preview: " + fo.getNameExt());
            tab.setLayout(new BorderLayout());
            JEditorPane pane = new JEditorPane("text/html", html);
            pane.setEditable(false);
            tab.add(new JScrollPane(pane), BorderLayout.CENTER);
            tab.open();
            tab.requestActive();
        } catch (Exception e) {
            // silently ignore
        }
    }
}
