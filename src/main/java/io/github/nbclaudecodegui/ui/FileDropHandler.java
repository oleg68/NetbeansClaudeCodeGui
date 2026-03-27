package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.model.AttachedFilesModel;
import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.TransferHandler;
import org.openide.filesystems.FileUtil;
import org.openide.nodes.Node;
import org.openide.nodes.NodeTransfer;

/**
 * {@link TransferHandler} that handles drag-and-drop of files onto the prompt
 * panel and clipboard paste of file lists or images.
 *
 * <p>Logic:
 * <ul>
 *   <li>If a file is <em>inside</em> {@code workingDir}, its relative path is
 *       inserted as plain text at the caret. Multiple inside-files are
 *       space-separated.</li>
 *   <li>If a file is <em>outside</em> {@code workingDir}, it is added to the
 *       {@link AttachedFilesModel} as a chip.</li>
 *   <li>If the clipboard / DnD payload contains an image, it is written to a
 *       temp PNG and added as a chip.</li>
 * </ul>
 *
 * <p>This class also exposes {@link #importFromClipboard(JTextArea)} which
 * {@link ClaudePromptPanel} calls on Ctrl+V to intercept file/image pastes
 * before the default text-paste behaviour.
 */
public final class FileDropHandler extends TransferHandler {

    private static final List<String> SOURCE_ROOTS = List.of(
            "src/main/java/", "src/test/java/",
            "src/main/groovy/", "src/test/groovy/",
            "src/main/kotlin/", "src/test/kotlin/"
    );

    private final AttachedFilesModel model;
    private final AttachedFilesPanel chipsPanel;
    /** Returns the absolute working-directory path, or {@code null} if not yet set. */
    private final java.util.function.Supplier<String> workingDirSupplier;

    /**
     * @param model             attachment model to populate with outside-workingDir files
     * @param chipsPanel        chip panel to refresh after adding a chip
     * @param workingDirSupplier supplier for the current working directory path
     */
    public FileDropHandler(AttachedFilesModel model,
                           AttachedFilesPanel chipsPanel,
                           java.util.function.Supplier<String> workingDirSupplier) {
        this.model              = model;
        this.chipsPanel         = chipsPanel;
        this.workingDirSupplier = workingDirSupplier;
    }

    // -------------------------------------------------------------------------
    // TransferHandler overrides — used for DnD
    // -------------------------------------------------------------------------

    @Override
    public boolean canImport(TransferSupport support) {
        Transferable t = support.getTransferable();
        boolean fileList = support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        boolean image    = support.isDataFlavorSupported(DataFlavor.imageFlavor);
        boolean string   = support.isDataFlavorSupported(DataFlavor.stringFlavor);
        boolean nodeFlav = hasNodeDndFlavor(t);
        return fileList || image || string || nodeFlav;
    }

    /** Package-private helper used by tests to check flavor support on a bare Transferable. */
    boolean canImportTransferable(Transferable t) {
        return t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || t.isDataFlavorSupported(DataFlavor.imageFlavor)
                || t.isDataFlavorSupported(DataFlavor.stringFlavor)
                || hasNodeDndFlavor(t);
    }

    /** Returns {@code true} if the transferable carries any flavor whose data is a {@link Node}. */
    private static boolean hasNodeDndFlavor(Transferable t) {
        if (NodeTransfer.nodes(t, NodeTransfer.DND_COPY_OR_MOVE) != null) return true;
        for (DataFlavor f : t.getTransferDataFlavors()) {
            if (f.getMimeType().startsWith("application/x-java-openide-nodednd")) return true;
            try {
                if (t.getTransferData(f) instanceof Node) return true;
            } catch (Exception ex) { /* ignore */ }
        }
        return false;
    }

    /** Extracts {@link Node}s from a transferable, trying standard NodeTransfer first,
     *  then scanning all flavors for any that yield a Node instance. */
    private static Node[] extractNodes(Transferable t) {
        Node[] nodes = NodeTransfer.nodes(t, NodeTransfer.DND_COPY_OR_MOVE);
        if (nodes != null) return nodes;
        for (DataFlavor f : t.getTransferDataFlavors()) {
            try {
                Object data = t.getTransferData(f);
                if (data instanceof Node n) return new Node[]{n};
            } catch (Exception ex) { /* ignore */ }
        }
        return null;
    }

    @Override
    public boolean importData(TransferSupport support) {
        Transferable t = support.getTransferable();
        JTextArea textArea = findTextArea(support.getComponent());
        return doImport(t, textArea);
    }

    // -------------------------------------------------------------------------
    // Clipboard paste — called explicitly from ClaudePromptPanel on Ctrl+V
    // -------------------------------------------------------------------------

    /**
     * Attempts to import file list or image from the system clipboard.
     *
     * @param textArea the text area into which relative paths should be inserted
     * @return {@code true} if file/image content was imported; {@code false} if
     *         the clipboard contains only plain text (caller should fall through
     *         to default paste)
     */
    public boolean importFromClipboard(JTextArea textArea) {
        java.awt.datatransfer.Clipboard cb =
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable t = cb.getContents(null);
        if (t == null) return false;
        boolean hasFiles = t.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        boolean hasImage = t.isDataFlavorSupported(DataFlavor.imageFlavor);
        boolean hasText  = t.isDataFlavorSupported(DataFlavor.stringFlavor);
        if (!hasFiles && !hasImage && !hasText) return false;
        return doImport(t, textArea);
    }

    // -------------------------------------------------------------------------
    // Shared import logic
    // -------------------------------------------------------------------------

    private boolean doImport(Transferable t, JTextArea textArea) {
        try {
            Node[] nodes = extractNodes(t);
            if (nodes != null && nodes.length > 0) {
                return importNodes(nodes, textArea);
            }
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                if (img != null) {
                    model.addImageFromClipboard(img);
                    refreshChips();
                    return true;
                }
            }
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> dropped = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                if (dropped == null || dropped.isEmpty()) return false;

                String wdStr = workingDirSupplier.get();
                Path workDir = wdStr != null ? Path.of(wdStr) : null;

                StringBuilder relativePaths = new StringBuilder();
                boolean anyOutside = false;

                for (File f : dropped) {
                    Path filePath = f.toPath().toAbsolutePath().normalize();
                    if (workDir != null && filePath.startsWith(workDir.normalize())) {
                        // Inside workingDir → insert relative path
                        Path rel = workDir.normalize().relativize(filePath);
                        String relStr = rel.toString().replace(File.separatorChar, '/');
                        if (relativePaths.length() > 0) relativePaths.append(" ");
                        if (relStr.isEmpty()) {
                            relativePaths.append("@./");
                        } else {
                            relativePaths.append(relStr);
                        }
                    } else {
                        // Outside workingDir → add as chip
                        model.addFile(f);
                        anyOutside = true;
                    }
                }

                if (anyOutside) {
                    refreshChips();
                }

                if (relativePaths.length() > 0 && textArea != null) {
                    insertAtCaret(textArea, relativePaths.toString());
                }
                return true;
            }
            if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String s = (String) t.getTransferData(DataFlavor.stringFlavor);
                if (s != null && textArea != null) { insertAtCaret(textArea, s); return true; }
            }
        } catch (Exception ex) {
            // Import failed; fall through to return false
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Node-transfer import (NetBeans project tree DnD)
    // -------------------------------------------------------------------------

    /**
     * Processes an array of NetBeans {@link Node}s dropped or pasted.
     *
     * <ul>
     *   <li>Package directory <em>inside</em> workingDir (under a known source root):
     *       fully-qualified package name inserted as plain text at caret.</li>
     *   <li>Any directory <em>inside</em> workingDir but not under a source root:
     *       relative path inserted as plain text.</li>
     *   <li>Any directory <em>outside</em> workingDir: added as a chip attachment.</li>
     * </ul>
     */
    boolean importNodes(Node[] nodes, JTextArea textArea) {
        if (nodes == null || nodes.length == 0) return false;
        String wdStr = workingDirSupplier.get();
        Path workDir = wdStr != null ? Path.of(wdStr).normalize() : null;

        StringBuilder inlineText = new StringBuilder();
        boolean anyChip = false;

        for (Node n : nodes) {
            // Resolve FileObject via priority chain
            org.openide.filesystems.FileObject fo =
                    n.getLookup().lookup(org.openide.filesystems.FileObject.class);
            if (fo == null) {
                var dobj = n.getLookup().lookup(org.openide.loaders.DataObject.class);
                if (dobj != null) fo = dobj.getPrimaryFile();
            }
            if (fo == null) {
                var proj = n.getLookup().lookup(org.netbeans.api.project.Project.class);
                if (proj != null) fo = proj.getProjectDirectory();
            }
            if (fo == null) continue;

            File f = FileUtil.toFile(fo);
            if (f == null) continue;

            Path filePath = f.toPath().normalize();
            boolean insideWorkDir = workDir != null && filePath.startsWith(workDir);

            if (fo.isFolder()) {
                if (insideWorkDir) {
                    String relStr = workDir.relativize(filePath).toString()
                            .replace(File.separatorChar, '/');
                    if (relStr.isEmpty()) {
                        // workDir root itself → insert @./
                        if (inlineText.length() > 0) inlineText.append(" ");
                        inlineText.append("@./");
                        continue;
                    }
                    String withSlash = relStr + "/";
                    String matchedRoot = SOURCE_ROOTS.stream()
                            .filter(withSlash::startsWith)
                            .findFirst().orElse(null);

                    String toInsert;
                    if (matchedRoot != null) {
                        // Package dir under a source root → FQN
                        String pkg = withSlash.substring(matchedRoot.length());
                        if (pkg.endsWith("/")) pkg = pkg.substring(0, pkg.length() - 1);
                        toInsert = pkg.replace('/', '.');
                    } else {
                        // Other dir inside workDir → relative path
                        toInsert = relStr;
                    }
                    if (inlineText.length() > 0) inlineText.append(" ");
                    inlineText.append(toInsert);
                } else {
                    // Outside workDir → chip
                    model.addFile(f);
                    anyChip = true;
                }
            } else {
                // File node — relative path if inside workDir, chip if outside
                if (insideWorkDir) {
                    String rel = workDir.relativize(filePath).toString()
                            .replace(File.separatorChar, '/');
                    if (inlineText.length() > 0) inlineText.append(" ");
                    inlineText.append(rel);
                } else {
                    model.addFile(f);
                    anyChip = true;
                }
            }
        }

        if (anyChip) refreshChips();
        if (inlineText.length() > 0 && textArea != null) {
            insertAtCaret(textArea, inlineText.toString());
        }
        return anyChip || inlineText.length() > 0;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void refreshChips() {
        javax.swing.SwingUtilities.invokeLater(chipsPanel::refresh);
    }

    private static void insertAtCaret(JTextArea area, String text) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            int pos = area.getCaretPosition();
            // If not at start of document, add a leading space if not already there
            try {
                String before = area.getText(0, pos);
                String toInsert = (!before.isEmpty() && !before.endsWith(" ") && !before.endsWith("\n"))
                        ? " " + text
                        : text;
                area.getDocument().insertString(pos, toInsert, null);
            } catch (javax.swing.text.BadLocationException ex) {
                area.append(" " + text);
            }
        });
    }

    private static JTextArea findTextArea(java.awt.Component c) {
        if (c instanceof JTextArea ta) return ta;
        if (c instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                JTextArea found = findTextArea(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
