package io.github.nbclaudecodegui.model;

import java.awt.Image;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Holds the list of files to attach to the next prompt, including temp files.
 *
 * <p>Thread-safe: all public methods are synchronized.
 *
 * <p>Temp files (created from clipboard images) are tracked separately so that
 * {@link #cleanup()} can delete them from disk without affecting regular file
 * attachments.
 */
public final class AttachedFilesModel {

    private final List<File> files    = new ArrayList<>();
    private final Set<File>  tempFiles = new HashSet<>();

    /**
     * Adds a file to the attachment list. Files are deduplicated by absolute path.
     *
     * @param f the file to add
     * @return {@code true} if the file was added; {@code false} if it was already present
     */
    public synchronized boolean addFile(File f) {
        File abs = f.getAbsoluteFile();
        for (File existing : files) {
            if (existing.getAbsolutePath().equals(abs.getAbsolutePath())) {
                return false;
            }
        }
        files.add(abs);
        return true;
    }

    /**
     * Removes a file from the attachment list (matched by absolute path).
     *
     * @param f the file to remove
     */
    public synchronized void removeFile(File f) {
        String absPath = f.getAbsoluteFile().getAbsolutePath();
        files.removeIf(existing -> existing.getAbsolutePath().equals(absPath));
        tempFiles.removeIf(existing -> existing.getAbsolutePath().equals(absPath));
    }

    /**
     * Returns an immutable snapshot of the current file list.
     *
     * @return unmodifiable list of attached files
     */
    public synchronized List<File> getFiles() {
        return Collections.unmodifiableList(new ArrayList<>(files));
    }

    /**
     * Removes all files from the attachment list without deleting temp files from disk.
     */
    public synchronized void clearFiles() {
        files.clear();
        tempFiles.clear();
    }

    /**
     * Deletes all temp files (clipboard images) from disk.
     * Does not modify the in-memory file list; call {@link #clearFiles()} separately
     * if you also want to clear the list.
     */
    public synchronized void cleanup() {
        for (File tmp : tempFiles) {
            try {
                Files.deleteIfExists(tmp.toPath());
            } catch (IOException ignored) {
                // Best-effort deletion
            }
        }
        tempFiles.clear();
    }

    /**
     * Saves an AWT {@link Image} as a temporary PNG file, registers it as a temp
     * attachment, and returns the file.
     *
     * @param img the image to save
     * @return the newly created temp file
     * @throws IOException if writing the temp file fails
     */
    public File addImageFromClipboard(Image img) throws IOException {
        File tmp = File.createTempFile("claude-attach-", ".png");
        tmp.deleteOnExit();
        if (img instanceof RenderedImage ri) {
            ImageIO.write(ri, "png", tmp);
        } else {
            // Convert AWT Image to BufferedImage first
            java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(
                    img.getWidth(null), img.getHeight(null),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = bi.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            ImageIO.write(bi, "png", tmp);
        }
        synchronized (this) {
            files.add(tmp);
            tempFiles.add(tmp);
        }
        return tmp;
    }

    /**
     * Returns whether the file list is empty.
     */
    public synchronized boolean isEmpty() {
        return files.isEmpty();
    }
}
