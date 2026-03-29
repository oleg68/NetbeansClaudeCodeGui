package io.github.nbclaudecodegui.actions;

import io.github.nbclaudecodegui.ui.MarkdownPreviewTab;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.netbeans.api.annotations.common.StaticResource;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;

/**
 * Context-menu action that opens a rendered markdown preview for {@code .md} files.
 *
 * <p>Appears in the right-click menu of {@code .md} and {@code .markdown} files
 * in the Projects and Files trees.  Multiple selected files are each opened in
 * their own preview tab.
 */
@ActionID(category = "File", id = "io.github.nbclaudecodegui.actions.PreviewMarkdownAction")
@ActionRegistration(displayName = "#CTL_PreviewMarkdownAction", lazy = false)
@ActionReferences({
    @ActionReference(path = "Loaders/text/x-markdown-nb/Actions", position = 100),
    @ActionReference(path = "Loaders/text/x-markdown/Actions",    position = 100),
    @ActionReference(path = "Loaders/text/plain/Actions",          position = 150)
})
@Messages("CTL_PreviewMarkdownAction=Preview Markdown")
public final class PreviewMarkdownAction extends AbstractAction implements ContextAwareAction {

    public PreviewMarkdownAction() {
        super("Preview Markdown");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // global fallback — not called when context-aware instance is used
    }

    @Override
    public Action createContextAwareInstance(Lookup context) {
        List<FileObject> mdFiles = new ArrayList<>();
        for (DataObject dob : context.lookupAll(DataObject.class)) {
            FileObject fo = dob.getPrimaryFile();
            String ext = fo.getExt().toLowerCase();
            if (ext.equals("md") || ext.equals("markdown")) {
                mdFiles.add(fo);
            }
        }
        if (mdFiles.isEmpty()) {
            AbstractAction disabled = new AbstractAction("Preview Markdown") {
                @Override public void actionPerformed(ActionEvent e) {}
            };
            disabled.setEnabled(false);
            return disabled;
        }
        return new ContextAction(mdFiles);
    }

    private static final class ContextAction extends AbstractAction {
        private final List<FileObject> files;

        ContextAction(List<FileObject> files) {
            super("Preview Markdown");
            this.files = files;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            for (FileObject fo : files) {
                MarkdownPreviewTab.openFile(fo);
            }
        }
    }
}
