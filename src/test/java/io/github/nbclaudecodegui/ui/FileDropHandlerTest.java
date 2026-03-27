package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.model.AttachedFilesModel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.LocalFileSystem;
import org.openide.loaders.DataFolder;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.lookup.Lookups;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FileDropHandler}.
 *
 * <p>Tests the {@code importFromClipboard} and the shared {@code doImport} logic
 * via a test transferable.
 */
class FileDropHandlerTest {

    @TempDir
    File workDir;

    private AttachedFilesModel model;
    private AttachedFilesPanel chipsPanel;
    private FileDropHandler    handler;
    private JTextArea          area;

    @BeforeEach
    void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            model      = new AttachedFilesModel();
            chipsPanel = new AttachedFilesPanel(model, () -> {});
            handler    = new FileDropHandler(model, chipsPanel, () -> workDir.getAbsolutePath());
            area       = new JTextArea();
        });
    }

    // -------------------------------------------------------------------------
    // Helper: build a Transferable with a file list
    // -------------------------------------------------------------------------

    private static java.awt.datatransfer.Transferable fileListTransferable(List<File> files) {
        return new java.awt.datatransfer.Transferable() {
            @Override
            public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                return new java.awt.datatransfer.DataFlavor[]{
                        java.awt.datatransfer.DataFlavor.javaFileListFlavor
                };
            }
            @Override
            public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                return java.awt.datatransfer.DataFlavor.javaFileListFlavor.equals(flavor);
            }
            @Override
            public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) {
                return files;
            }
        };
    }

    private static java.awt.datatransfer.Transferable imageTransferable(java.awt.Image img) {
        return new java.awt.datatransfer.Transferable() {
            @Override
            public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                return new java.awt.datatransfer.DataFlavor[]{
                        java.awt.datatransfer.DataFlavor.imageFlavor
                };
            }
            @Override
            public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
            }
            @Override
            public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) {
                return img;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Tests: file inside workingDir → relative path inserted into textarea
    // -------------------------------------------------------------------------

    @Test
    void dropFileInsideWorkDirInsertsRelativePath() throws Exception {
        File inside = new File(workDir, "src/Foo.java");
        inside.getParentFile().mkdirs();
        inside.createNewFile();

        SwingUtilities.invokeAndWait(() -> {
            java.awt.datatransfer.Transferable t = fileListTransferable(List.of(inside));
            // Directly exercise the import logic via reflection-friendly approach:
            // We call importFromClipboardViaTransferable by temporarily setting clipboard
            // Actually, we need to test the internal doImport. We can call the TransferHandler
            // importData via a test support object or test the side-effect via area content.
            // Use a minimal approach: call via a test subclass that exposes doImport.
            boolean result = callDoImport(handler, t, area);
            assertTrue(result, "Should return true for a file list");
        });

        SwingUtilities.invokeAndWait(() -> {
            // Area should contain the relative path
            String text = area.getText();
            assertTrue(text.contains("src/Foo.java") || text.contains("src" + File.separator + "Foo.java"),
                    "Relative path must appear in textarea, got: " + text);
        });

        // Model should have no chips (file was inside workDir)
        assertEquals(0, model.getFiles().size(), "Inside-workDir file must NOT be in attachment model");
    }

    @Test
    void dropFileOutsideWorkDirAddsChip() throws Exception {
        File outside = File.createTempFile("outside", ".txt");
        outside.deleteOnExit();

        SwingUtilities.invokeAndWait(() -> {
            java.awt.datatransfer.Transferable t = fileListTransferable(List.of(outside));
            boolean result = callDoImport(handler, t, area);
            assertTrue(result);
        });

        assertEquals(1, model.getFiles().size(), "Outside-workDir file must be in attachment model");
        assertEquals(outside.getAbsolutePath(),
                model.getFiles().get(0).getAbsolutePath());
    }

    @Test
    void dropTwoInsideFilesInsertsBothSpaceSeparated() throws Exception {
        File a = new File(workDir, "a.txt"); a.createNewFile();
        File b = new File(workDir, "b.txt"); b.createNewFile();

        SwingUtilities.invokeAndWait(() -> {
            java.awt.datatransfer.Transferable t = fileListTransferable(List.of(a, b));
            callDoImport(handler, t, area);
        });

        SwingUtilities.invokeAndWait(() -> {
            String text = area.getText();
            assertTrue(text.contains("a.txt"), "First file must appear in textarea");
            assertTrue(text.contains("b.txt"), "Second file must appear in textarea");
        });

        assertEquals(0, model.getFiles().size(), "Both files inside workDir → no chips");
    }

    @Test
    void dropInsideAndOutsideFilesInsertsAndChips() throws Exception {
        File inside = new File(workDir, "inner.txt"); inside.createNewFile();
        File outside = File.createTempFile("outer", ".txt"); outside.deleteOnExit();

        SwingUtilities.invokeAndWait(() -> {
            java.awt.datatransfer.Transferable t = fileListTransferable(List.of(inside, outside));
            callDoImport(handler, t, area);
        });

        SwingUtilities.invokeAndWait(() -> {
            assertTrue(area.getText().contains("inner.txt"), "Inner file must be in textarea");
        });
        assertEquals(1, model.getFiles().size(), "Outer file must be in chip model");
    }

    // -------------------------------------------------------------------------
    // Tests: image → temp chip
    // -------------------------------------------------------------------------

    @Test
    void pasteImageAddsTempChip() throws Exception {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);

        SwingUtilities.invokeAndWait(() -> {
            java.awt.datatransfer.Transferable t = imageTransferable(img);
            boolean result = callDoImport(handler, t, area);
            assertTrue(result, "Image import must return true");
        });

        assertEquals(1, model.getFiles().size(), "Image must be added as a chip");
        File tmp = model.getFiles().get(0);
        assertTrue(tmp.exists(), "Temp file must exist");
        assertTrue(tmp.getName().endsWith(".png"), "Temp file must be PNG");
    }

    // -------------------------------------------------------------------------
    // Tests: plain-text transferable → inserted into textarea
    // -------------------------------------------------------------------------

    @Test
    void doImportPlainTextInsertsTextIntoTextArea() throws Exception {
        String plainText = "hello world";
        java.awt.datatransfer.Transferable t = stringTransferable(plainText);

        SwingUtilities.invokeAndWait(() -> {
            boolean result = callDoImport(handler, t, area);
            assertTrue(result, "Plain-text import must return true");
        });

        SwingUtilities.invokeAndWait(() -> {
            assertTrue(area.getText().contains(plainText),
                    "Plain text must appear in textarea, got: " + area.getText());
        });

        assertEquals(0, model.getFiles().size(), "Plain text paste must not add any chips");
    }

    @Test
    void canImportAcceptsStringFlavor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Build a mock TransferSupport — not straightforward, so test canImport
            // indirectly: stringFlavor-only transferable goes through doImport successfully
            java.awt.datatransfer.Transferable t = stringTransferable("test");
            boolean result = callDoImport(handler, t, area);
            assertTrue(result, "doImport must accept stringFlavor");
        });
    }

    private static java.awt.datatransfer.Transferable stringTransferable(String text) {
        return new java.awt.datatransfer.Transferable() {
            @Override
            public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                return new java.awt.datatransfer.DataFlavor[]{
                        java.awt.datatransfer.DataFlavor.stringFlavor
                };
            }
            @Override
            public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                return java.awt.datatransfer.DataFlavor.stringFlavor.equals(flavor);
            }
            @Override
            public Object getTransferData(java.awt.datatransfer.DataFlavor flavor)
                    throws java.awt.datatransfer.UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor)) throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
                return text;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Tests: NetBeans Node transfers (package nodes and project directory nodes)
    // -------------------------------------------------------------------------

    /**
     * Creates an AbstractNode whose Lookup contains a FileObject for {@code dir},
     * using a LocalFileSystem (works without the full NetBeans runtime).
     */
    private static Node nodeForDir(File dir) throws Exception {
        dir.mkdirs();
        LocalFileSystem lfs = new LocalFileSystem();
        lfs.setRootDirectory(dir);
        var fo = lfs.getRoot();
        assertNotNull(fo, "LocalFileSystem root must be non-null for " + dir);
        return new AbstractNode(Children.LEAF, Lookups.singleton(fo));
    }

    @Test
    void importNodesPackageDirInWorkDirInsertsFqn() throws Exception {
        // Create src/main/java/com/example/pkg directory inside workDir
        File pkgDir = new File(workDir, "src/main/java/com/example/pkg");
        pkgDir.mkdirs();
        Node node = nodeForDir(pkgDir);

        SwingUtilities.invokeAndWait(() -> {
            boolean result = callImportNodes(handler, new Node[]{node}, area);
            assertTrue(result, "importNodes must return true for package dir inside workDir");
        });

        SwingUtilities.invokeAndWait(() -> {
            String text = area.getText();
            assertTrue(text.contains("com.example.pkg"),
                    "FQN must be inserted into textarea, got: " + text);
        });
        assertEquals(0, model.getFiles().size(), "Package inside workDir must NOT be added as chip");
    }

    @Test
    void importNodesPackageDirOutsideWorkDirAddsChip() throws Exception {
        // Create a package dir in a separate temp location (outside workDir)
        File outsideDir = File.createTempFile("outside-pkg-", "");
        outsideDir.delete();
        outsideDir = new File(outsideDir.getParentFile(), outsideDir.getName());
        outsideDir.mkdirs();
        outsideDir.deleteOnExit();
        // Put it under a source root structure outside workDir
        File pkgDir = new File(outsideDir, "src/main/java/com/other/pkg");
        pkgDir.mkdirs();
        Node node = nodeForDir(pkgDir);

        final Node finalNode = node;
        SwingUtilities.invokeAndWait(() -> {
            boolean result = callImportNodes(handler, new Node[]{finalNode}, area);
            assertTrue(result, "importNodes must return true for dir outside workDir");
        });

        assertEquals(1, model.getFiles().size(), "Dir outside workDir must be added as chip");
        assertEquals("", area.getText().trim(), "No text must be inserted for outside-workDir dir");
    }

    @Test
    void importNodesProjectDirInsideWorkDirInsertsRelativePath() throws Exception {
        // A directory inside workDir but NOT under src/main/java etc.
        File subdir = new File(workDir, "docs/api");
        subdir.mkdirs();
        Node node = nodeForDir(subdir);

        SwingUtilities.invokeAndWait(() -> {
            boolean result = callImportNodes(handler, new Node[]{node}, area);
            assertTrue(result, "importNodes must return true for dir inside workDir");
        });

        SwingUtilities.invokeAndWait(() -> {
            String text = area.getText();
            assertTrue(text.contains("docs/api") || text.contains("docs" + File.separator + "api"),
                    "Relative path must be inserted for non-source-root dir, got: " + text);
        });
        assertEquals(0, model.getFiles().size(), "Dir inside workDir must NOT be added as chip");
    }

    @Test
    void importNodesMultiplePackagesInsertsFqnsSpaceSeparated() throws Exception {
        File pkg1 = new File(workDir, "src/main/java/com/a");
        File pkg2 = new File(workDir, "src/test/java/com/b");
        pkg1.mkdirs();
        pkg2.mkdirs();
        Node n1 = nodeForDir(pkg1);
        Node n2 = nodeForDir(pkg2);

        SwingUtilities.invokeAndWait(() -> {
            callImportNodes(handler, new Node[]{n1, n2}, area);
        });

        SwingUtilities.invokeAndWait(() -> {
            String text = area.getText();
            assertTrue(text.contains("com.a"), "First FQN must appear, got: " + text);
            assertTrue(text.contains("com.b"), "Second FQN must appear, got: " + text);
        });
    }

    /**
     * Creates an AbstractNode whose Lookup contains a FileObject for a *file*
     * (not a directory), using a LocalFileSystem.
     */
    private static Node nodeForFile(File file) throws Exception {
        file.getParentFile().mkdirs();
        file.createNewFile();
        LocalFileSystem lfs = new LocalFileSystem();
        lfs.setRootDirectory(file.getParentFile());
        var fo = lfs.findResource(file.getName());
        assertNotNull(fo, "LocalFileSystem must find file " + file.getName());
        return new AbstractNode(Children.LEAF, Lookups.singleton(fo));
    }

    @Test
    void importNodesFileNodeInsideWorkDirInsertsRelativePath() throws Exception {
        File javaFile = new File(workDir, "src/main/java/Foo.java");
        Node node = nodeForFile(javaFile);

        SwingUtilities.invokeAndWait(() -> {
            boolean result = callImportNodes(handler, new Node[]{node}, area);
            assertTrue(result, "importNodes must return true for file node inside workDir");
        });

        SwingUtilities.invokeAndWait(() -> {
            String text = area.getText();
            assertTrue(text.contains("src/main/java/Foo.java") || text.contains("Foo.java"),
                    "Relative path must be inserted for file node inside workDir, got: " + text);
        });
        assertEquals(0, model.getFiles().size(), "File inside workDir must NOT be added as chip");
    }

    @Test
    void importNodesFileNodeOutsideWorkDirAddsChip() throws Exception {
        File outsideFile = File.createTempFile("outside-node-", ".java");
        outsideFile.deleteOnExit();
        Node node = nodeForFile(outsideFile);

        SwingUtilities.invokeAndWait(() -> {
            boolean result = callImportNodes(handler, new Node[]{node}, area);
            assertTrue(result, "importNodes must return true for file node outside workDir");
        });

        assertEquals(1, model.getFiles().size(), "File node outside workDir must be added as chip");
        assertEquals("", area.getText().trim(), "No text must be inserted for outside-workDir file node");
    }

    @Test
    void importNodesProjectNodeInsideWorkDirInsertsRelativePath() throws Exception {
        // Simulate a project node: node with Project in Lookup whose projectDirectory is a subdir of workDir
        File subProjectDir = new File(workDir, "subproject");
        subProjectDir.mkdirs();
        LocalFileSystem lfs = new LocalFileSystem();
        lfs.setRootDirectory(subProjectDir);
        FileObject projectFo = lfs.getRoot();
        org.netbeans.api.project.Project fakeProject = new org.netbeans.api.project.Project() {
            @Override public FileObject getProjectDirectory() { return projectFo; }
            @Override public org.openide.util.Lookup getLookup() { return org.openide.util.Lookup.EMPTY; }
        };
        Node node = new AbstractNode(Children.LEAF, Lookups.singleton(fakeProject));

        SwingUtilities.invokeAndWait(() -> {
            boolean result = callImportNodes(handler, new Node[]{node}, area);
            assertTrue(result, "importNodes must return true for project node inside workDir");
        });

        SwingUtilities.invokeAndWait(() -> {
            String text = area.getText();
            assertTrue(text.contains("subproject"),
                    "Relative path of project dir must be inserted, got: " + text);
        });
        assertEquals(0, model.getFiles().size(), "Project dir inside workDir must NOT be added as chip");
    }

    // -------------------------------------------------------------------------
    // Tests: project root → @./
    // -------------------------------------------------------------------------

    @Test
    void testProjectRootFileListInsertsAtDotSlash() throws Exception {
        // Drop the workDir itself via file list
        SwingUtilities.invokeAndWait(() -> {
            java.awt.datatransfer.Transferable t = fileListTransferable(List.of(workDir));
            boolean result = callDoImport(handler, t, area);
            assertTrue(result, "Import of workDir root must return true");
        });

        SwingUtilities.invokeAndWait(() -> {
            String text = area.getText();
            assertTrue(text.contains("@./"),
                    "workDir root must insert @./ into textarea, got: " + text);
        });
        assertEquals(0, model.getFiles().size(), "workDir root must NOT be added as chip");
    }

    @Test
    void testProjectRootNodeInsertsAtDotSlash() throws Exception {
        // importNodes with a folder node whose path == workDir
        Node node = nodeForDir(workDir);

        SwingUtilities.invokeAndWait(() -> {
            boolean result = callImportNodes(handler, new Node[]{node}, area);
            assertTrue(result, "importNodes for workDir root must return true");
        });

        SwingUtilities.invokeAndWait(() -> {
            String text = area.getText();
            assertTrue(text.contains("@./"),
                    "workDir root node must insert @./ into textarea, got: " + text);
        });
        assertEquals(0, model.getFiles().size(), "workDir root node must NOT be added as chip");
    }

    // -------------------------------------------------------------------------
    // Tests: package-node DnD flavor (custom MIME, not openide-nodednd)
    // -------------------------------------------------------------------------

    private static java.awt.datatransfer.Transferable singleFlavorTransferable(
            java.awt.datatransfer.DataFlavor flavor, Object data) {
        return new java.awt.datatransfer.Transferable() {
            @Override
            public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                return new java.awt.datatransfer.DataFlavor[]{flavor};
            }
            @Override
            public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor f) {
                return flavor.equals(f);
            }
            @Override
            public Object getTransferData(java.awt.datatransfer.DataFlavor f) {
                return data;
            }
        };
    }

    @Test
    void canImportPackageNodeFlavor() throws Exception {
        File pkgDir = new File(workDir, "src/main/java/com/example/pkg");
        pkgDir.mkdirs();
        Node packageNode = nodeForDir(pkgDir);
        java.awt.datatransfer.DataFlavor pkgFlavor = new java.awt.datatransfer.DataFlavor(
                "application/x-java-org-netbeans-modules-java-project-packagenodednd" +
                ";class=org.openide.nodes.AbstractNode",
                "Package Node DnD", Thread.currentThread().getContextClassLoader());
        java.awt.datatransfer.Transferable t = singleFlavorTransferable(pkgFlavor, packageNode);
        SwingUtilities.invokeAndWait(() ->
            assertTrue(callCanImportTransferable(handler, t),
                    "canImport must accept package-node flavor"));
    }

    @Test
    void doImportPackageNodeInsertsPackageFqn() throws Exception {
        File pkgDir = new File(workDir, "src/main/java/com/example/pkg");
        pkgDir.mkdirs();
        Node packageNode = nodeForDir(pkgDir);
        java.awt.datatransfer.DataFlavor pkgFlavor = new java.awt.datatransfer.DataFlavor(
                "application/x-java-org-netbeans-modules-java-project-packagenodednd" +
                ";class=org.openide.nodes.AbstractNode",
                "Package Node DnD", Thread.currentThread().getContextClassLoader());
        java.awt.datatransfer.Transferable t = singleFlavorTransferable(pkgFlavor, packageNode);
        SwingUtilities.invokeAndWait(() ->
            assertTrue(callDoImport(handler, t, area)));
        SwingUtilities.invokeAndWait(() ->
            assertTrue(area.getText().contains("com.example.pkg"),
                    "FQN must be inserted, got: " + area.getText()));
        assertEquals(0, model.getFiles().size());
    }

    // -------------------------------------------------------------------------
    // Tests: canImport with NodeTransfer.nodeFlavor
    // -------------------------------------------------------------------------

    @Test
    void testCanImportNodeFlavor() throws Exception {
        // Build a transferable that advertises the NetBeans node DnD MIME type
        java.awt.datatransfer.DataFlavor nodeDndFlavor = new java.awt.datatransfer.DataFlavor(
                "application/x-java-openide-nodednd;class=org.openide.nodes.Node;mask=2",
                "NetBeans Node DnD", Thread.currentThread().getContextClassLoader());
        java.awt.datatransfer.Transferable t = new java.awt.datatransfer.Transferable() {
            @Override
            public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                return new java.awt.datatransfer.DataFlavor[]{nodeDndFlavor};
            }
            @Override
            public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                return nodeDndFlavor.equals(flavor);
            }
            @Override
            public Object getTransferData(java.awt.datatransfer.DataFlavor flavor)
                    throws java.awt.datatransfer.UnsupportedFlavorException {
                throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
            }
        };

        // Call canImport via reflection (it's a public method, but TransferSupport is hard to construct)
        // Instead call the package-private helper that checks the transferable flavor directly
        SwingUtilities.invokeAndWait(() -> {
            boolean result = callCanImportTransferable(handler, t);
            assertTrue(result, "canImport must return true when transferable supports NodeTransfer.nodeFlavor");
        });
    }

    /** Calls the package-private {@code canImportTransferable(Transferable)} via reflection. */
    private static boolean callCanImportTransferable(FileDropHandler handler, java.awt.datatransfer.Transferable t) {
        try {
            java.lang.reflect.Method m = FileDropHandler.class.getDeclaredMethod(
                    "canImportTransferable", java.awt.datatransfer.Transferable.class);
            m.setAccessible(true);
            return (boolean) m.invoke(handler, t);
        } catch (Exception ex) {
            throw new RuntimeException("Could not invoke FileDropHandler.canImportTransferable", ex);
        }
    }

    /** Calls the package-private {@code importNodes(Node[], JTextArea)} via reflection. */
    private static boolean callImportNodes(FileDropHandler handler, Node[] nodes, JTextArea area) {
        try {
            java.lang.reflect.Method m = FileDropHandler.class.getDeclaredMethod(
                    "importNodes", Node[].class, JTextArea.class);
            m.setAccessible(true);
            return (boolean) m.invoke(handler, nodes, area);
        } catch (Exception ex) {
            throw new RuntimeException("Could not invoke FileDropHandler.importNodes", ex);
        }
    }

    // -------------------------------------------------------------------------
    // Helper — call the package-internal doImport via the public TransferHandler
    // API. We replicate the logic by using a dummy TransferSupport backed by
    // a Transferable that we build ourselves.
    //
    // Since TransferSupport cannot be constructed directly outside its package
    // for the importData path, we test via a minimal sub-approach:
    // We expose a package-private method in FileDropHandler that accepts a
    // Transferable directly. For this test we call it via a reflective accessor.
    // -------------------------------------------------------------------------

    /**
     * Calls the package-private {@code doImport(Transferable, JTextArea)} via
     * a helper that uses reflection so we can test it without adding public API.
     *
     * <p>Falls back to using importFromClipboard if reflection fails, to avoid
     * polluting clipboard in CI; in that case the test is skipped gracefully.
     */
    private static boolean callDoImport(FileDropHandler handler,
                                        java.awt.datatransfer.Transferable t,
                                        JTextArea area) {
        try {
            java.lang.reflect.Method m = FileDropHandler.class.getDeclaredMethod(
                    "doImport",
                    java.awt.datatransfer.Transferable.class,
                    JTextArea.class);
            m.setAccessible(true);
            return (boolean) m.invoke(handler, t, area);
        } catch (Exception ex) {
            throw new RuntimeException("Could not invoke FileDropHandler.doImport", ex);
        }
    }
}
