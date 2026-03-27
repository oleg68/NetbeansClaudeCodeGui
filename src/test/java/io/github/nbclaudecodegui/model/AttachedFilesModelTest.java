package io.github.nbclaudecodegui.model;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AttachedFilesModel}.
 */
class AttachedFilesModelTest {

    @TempDir
    File tempDir;

    private AttachedFilesModel model;

    @BeforeEach
    void setUp() {
        model = new AttachedFilesModel();
    }

    // -------------------------------------------------------------------------
    // addFile
    // -------------------------------------------------------------------------

    @Test
    void addFileSizeIsOne() throws Exception {
        File f = new File(tempDir, "test.txt");
        f.createNewFile();
        boolean added = model.addFile(f);
        assertTrue(added, "addFile should return true on first add");
        assertEquals(1, model.getFiles().size());
    }

    @Test
    void addSameFileTwiceDeduplicates() throws Exception {
        File f = new File(tempDir, "dup.txt");
        f.createNewFile();
        model.addFile(f);
        boolean second = model.addFile(f);
        assertFalse(second, "addFile should return false when file already present");
        assertEquals(1, model.getFiles().size(), "Duplicate file must not be added twice");
    }

    @Test
    void addMultipleDistinctFiles() throws Exception {
        File a = new File(tempDir, "a.txt"); a.createNewFile();
        File b = new File(tempDir, "b.txt"); b.createNewFile();
        model.addFile(a);
        model.addFile(b);
        assertEquals(2, model.getFiles().size());
    }

    // -------------------------------------------------------------------------
    // removeFile
    // -------------------------------------------------------------------------

    @Test
    void removeFileLeavesEmptyList() throws Exception {
        File f = new File(tempDir, "remove.txt");
        f.createNewFile();
        model.addFile(f);
        model.removeFile(f);
        assertTrue(model.getFiles().isEmpty(), "List must be empty after removing the only file");
    }

    @Test
    void removeNonExistentFileIsNoOp() throws Exception {
        File f = new File(tempDir, "x.txt"); f.createNewFile();
        File g = new File(tempDir, "y.txt"); g.createNewFile();
        model.addFile(f);
        model.removeFile(g); // g was never added
        assertEquals(1, model.getFiles().size());
    }

    // -------------------------------------------------------------------------
    // clearFiles
    // -------------------------------------------------------------------------

    @Test
    void clearFilesLeavesEmptyList() throws Exception {
        File a = new File(tempDir, "a.txt"); a.createNewFile();
        File b = new File(tempDir, "b.txt"); b.createNewFile();
        model.addFile(a);
        model.addFile(b);
        model.clearFiles();
        assertTrue(model.getFiles().isEmpty());
    }

    // -------------------------------------------------------------------------
    // getFiles returns immutable copy
    // -------------------------------------------------------------------------

    @Test
    void getFilesReturnsUnmodifiableCopy() throws Exception {
        File f = new File(tempDir, "f.txt"); f.createNewFile();
        model.addFile(f);
        List<File> snapshot = model.getFiles();
        // Mutating the returned list must not affect the model
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(new File("another")),
                "getFiles() must return an unmodifiable list");
    }

    // -------------------------------------------------------------------------
    // addImageFromClipboard + cleanup
    // -------------------------------------------------------------------------

    @Test
    void addImageFromClipboardCreatesTempFile() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        File tmp = model.addImageFromClipboard(img);
        assertTrue(tmp.exists(), "Temp file must exist after addImageFromClipboard");
        assertTrue(tmp.getName().endsWith(".png"), "Temp file must be a PNG");
        assertEquals(1, model.getFiles().size(), "Temp file must be in file list");
    }

    @Test
    void cleanupDeletesTempFile() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        File tmp = model.addImageFromClipboard(img);
        assertTrue(tmp.exists(), "Temp file must exist before cleanup");
        model.cleanup();
        assertFalse(tmp.exists(), "Temp file must be deleted after cleanup");
    }

    @Test
    void cleanupDoesNotDeleteNonTempFiles() throws Exception {
        File regular = new File(tempDir, "regular.txt");
        regular.createNewFile();
        model.addFile(regular);

        BufferedImage img = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
        File tmp = model.addImageFromClipboard(img);

        model.cleanup();
        assertTrue(regular.exists(), "Regular (non-temp) file must not be deleted by cleanup");
        assertFalse(tmp.exists(), "Temp file must be deleted by cleanup");
    }

    // -------------------------------------------------------------------------
    // isEmpty
    // -------------------------------------------------------------------------

    @Test
    void isEmptyInitially() {
        assertTrue(model.isEmpty());
    }

    @Test
    void isNotEmptyAfterAdd() throws Exception {
        File f = new File(tempDir, "f.txt"); f.createNewFile();
        model.addFile(f);
        assertFalse(model.isEmpty());
    }
}
