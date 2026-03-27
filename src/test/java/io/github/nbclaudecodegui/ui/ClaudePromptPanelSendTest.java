package io.github.nbclaudecodegui.ui;

import io.github.nbclaudecodegui.model.AttachedFilesModel;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@code doSend()} behaviour of {@link ClaudePromptPanel}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Attached file chips are prepended as {@code @/abs/path\n} lines.</li>
 *   <li>When no chips are attached, plain text is sent unchanged.</li>
 *   <li>When chips are present but text is empty, chip paths are sent.</li>
 * </ul>
 */
class ClaudePromptPanelSendTest {

    @TempDir
    File tempDir;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Captures the value passed to onSend. */
    private AtomicReference<String> sentText;
    private ClaudePromptPanel       panel;
    private AttachedFilesModel      model;

    private void buildPanel(String workingDir) throws Exception {
        sentText = new AtomicReference<>();
        Supplier<List<String>> hist = Collections::emptyList;
        Supplier<String>       wd   = () -> workingDir;
        Consumer<String>       send = sentText::set;
        Runnable               noop = () -> {};

        SwingUtilities.invokeAndWait(() -> {
            panel = new ClaudePromptPanel(send, noop, noop, hist, wd);
        });

        // Extract the AttachedFilesModel from the panel via reflection
        try {
            java.lang.reflect.Field f = ClaudePromptPanel.class.getDeclaredField("attachedModel");
            f.setAccessible(true);
            model = (AttachedFilesModel) f.get(panel);
        } catch (Exception ex) {
            throw new RuntimeException("Cannot access attachedModel field", ex);
        }
    }

    /** Calls the private {@code doSend()} via reflection. */
    private void callDoSend() throws Exception {
        java.lang.reflect.Method m = ClaudePromptPanel.class.getDeclaredMethod("doSend");
        m.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                m.invoke(panel);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    /** Sets the text area content. */
    private void setInputText(String text) throws Exception {
        java.lang.reflect.Field f = ClaudePromptPanel.class.getDeclaredField("inputArea");
        f.setAccessible(true);
        javax.swing.JTextArea area = (javax.swing.JTextArea) f.get(panel);
        SwingUtilities.invokeAndWait(() -> area.setText(text));
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void twoAttachedFilesAndTextProducesCorrectPrompt() throws Exception {
        buildPanel(tempDir.getAbsolutePath());

        File f1 = new File(tempDir, "path1.txt"); f1.createNewFile();
        File f2 = new File(tempDir, "path2.txt"); f2.createNewFile();
        model.addFile(f1);
        model.addFile(f2);
        setInputText("do the thing");

        callDoSend();

        String sent = sentText.get();
        assertNotNull(sent, "onSend must have been called");
        assertTrue(sent.contains("@" + f1.getAbsolutePath() + "\n"),
                "Sent text must contain @path1\n");
        assertTrue(sent.contains("@" + f2.getAbsolutePath() + "\n"),
                "Sent text must contain @path2\n");
        assertTrue(sent.endsWith("do the thing"),
                "User text must be at the end");
    }

    @Test
    void noFilesAndTextSendsPlainText() throws Exception {
        buildPanel(tempDir.getAbsolutePath());
        setInputText("hello claude");

        callDoSend();

        String sent = sentText.get();
        assertNotNull(sent);
        assertEquals("hello claude", sent,
                "With no attachments, sent text must equal the plain input");
    }

    @Test
    void filesOnlyNoTextSendsFilePaths() throws Exception {
        buildPanel(tempDir.getAbsolutePath());

        File f = new File(tempDir, "only.txt"); f.createNewFile();
        model.addFile(f);
        setInputText(""); // empty text

        callDoSend();

        String sent = sentText.get();
        assertNotNull(sent, "onSend must be called even with no text if files are attached");
        assertTrue(sent.startsWith("@" + f.getAbsolutePath() + "\n"),
                "Sent text must start with @path\n when no user text");
    }

    @Test
    void noAttachButtonPresent() throws Exception {
        buildPanel(tempDir.getAbsolutePath());
        SwingUtilities.invokeAndWait(() -> {
            boolean found = hasButtonWithText(panel, "Attach");
            assertFalse(found, "Attach button must NOT be present in the panel");
        });
    }

    /** Recursively searches for a JButton whose text contains the given substring. */
    private static boolean hasButtonWithText(java.awt.Container container, String substring) {
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof javax.swing.JButton btn) {
                String text = btn.getText();
                if (text != null && text.contains(substring)) return true;
            }
            if (c instanceof java.awt.Container child) {
                if (hasButtonWithText(child, substring)) return true;
            }
        }
        return false;
    }

    @Test
    void emptyTextAndNoFilesDoesNotFire() throws Exception {
        buildPanel(tempDir.getAbsolutePath());
        setInputText("");

        callDoSend();

        assertNull(sentText.get(), "onSend must NOT be called when both text and files are empty");
    }

    @Test
    void afterSendFilesAreCleared() throws Exception {
        buildPanel(tempDir.getAbsolutePath());

        File f = new File(tempDir, "clear.txt"); f.createNewFile();
        model.addFile(f);
        setInputText("test");

        callDoSend();

        assertTrue(model.isEmpty(), "Attached files must be cleared after send");
    }
}
