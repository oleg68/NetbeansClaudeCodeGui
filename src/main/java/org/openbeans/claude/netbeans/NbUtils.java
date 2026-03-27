package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.text.NbDocument;
import org.openide.windows.TopComponent;

/**
 * Utility methods for interacting with the NetBeans IDE APIs.
 */
public class NbUtils {

    /** Private constructor — this class is a static utility and should not be instantiated. */
    private NbUtils() {}

    private static final Logger LOGGER = Logger.getLogger(NbUtils.class.getName());

    /**
     * Data class to hold current text selection information from NetBeans.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SelectionData {

        /** The selected text (empty string if no selection). */
        public final String text;
        /** Absolute path of the file containing the selection, or {@code null}. */
        public final String filePath;
        /** 1-based line number of the selection start. */
        public final int startLine;
        /** 0-based column of the selection start. */
        public final int startColumn;
        /** 1-based line number of the selection end. */
        public final int endLine;
        /** 0-based column of the selection end. */
        public final int endColumn;
        /** {@code true} if no text is selected. */
        public final boolean isEmpty;

        /**
         * Creates a new selection data snapshot.
         *
         * @param selectedText the selected text, or {@code null} for no selection
         * @param filePath     absolute file path, or {@code null}
         * @param startLine    1-based start line
         * @param startColumn  0-based start column
         * @param endLine      1-based end line
         * @param endColumn    0-based end column
         */
        public SelectionData(String selectedText, String filePath,
                int startLine, int startColumn,
                int endLine, int endColumn) {
            this.text = selectedText != null ? selectedText : "";
            this.filePath = filePath;
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
            this.isEmpty = selectedText == null || selectedText.isEmpty();
        }
    }

    /**
     * Extracts current text selection from the given JTextComponent.
     * The caller is responsible for supplying the correct component
     * (use EditorRegistry.lastFocusedComponent() to avoid returning null
     * when focus has moved away from the editor to another window).
     *
     * @param textComponent the editor component to read selection from, or null
     * @return SelectionData, or null if component is null or document is not a StyledDocument
     */
    public static SelectionData getCurrentSelectionData(JTextComponent textComponent) {
        if (textComponent == null || !(textComponent.getDocument() instanceof StyledDocument)) {
            return null;
        }

        StyledDocument doc = (StyledDocument) textComponent.getDocument();
        String selectedText = textComponent.getSelectedText();
        int selectionStart = textComponent.getSelectionStart();
        int selectionEnd = textComponent.getSelectionEnd();

        // Resolve file path from the document's stream-description property
        String filePath = null;
        Object streamDesc = doc.getProperty(javax.swing.text.Document.StreamDescriptionProperty);
        if (streamDesc instanceof DataObject) {
            FileObject fo = ((DataObject) streamDesc).getPrimaryFile();
            if (fo != null) {
                filePath = fo.getPath();
            }
        } else if (streamDesc instanceof FileObject) {
            filePath = ((FileObject) streamDesc).getPath();
        }

        int startLine   = NbDocument.findLineNumber(doc, selectionStart) + 1;
        int startColumn = NbDocument.findLineColumn(doc, selectionStart);
        int endLine     = NbDocument.findLineNumber(doc, selectionEnd)   + 1;
        int endColumn   = NbDocument.findLineColumn(doc, selectionEnd);

        return new SelectionData(selectedText, filePath, startLine, startColumn, endLine, endColumn);
    }

    /**
     * Returns the current text selection from the active editor using the legacy API.
     *
     * @deprecated Use {@link #getCurrentSelectionData(JTextComponent)} together with
     * {@code EditorRegistry.lastFocusedComponent()} instead. This overload relies on
     * {@code TopComponent.getRegistry().getActivated()}, which returns the wrong TC
     * (e.g. a Claude terminal) when focus has moved away from the editor.
     * @return selection data, or {@code null} if no editor is active
     */
    @Deprecated
    public static SelectionData getCurrentSelectionData() {
        TopComponent activeTC = TopComponent.getRegistry().getActivated();
        if (activeTC == null) {
            return null;
        }
        Node[] nodes = activeTC.getActivatedNodes();
        if (nodes == null || nodes.length == 0) {
            return null;
        }
        EditorCookie editorCookie = nodes[0].getLookup().lookup(EditorCookie.class);
        if (editorCookie == null) {
            return null;
        }
        JTextComponent[] panes = editorCookie.getOpenedPanes();
        if (panes == null || panes.length == 0) {
            return null;
        }
        return getCurrentSelectionData(panes[0]);
    }
 
    /**
     * Security validation: Checks if the given file path is within any open project directory.
     * This prevents unauthorized file operations outside of the user's active workspace.
     * 
     * @param filePath the file path to validate
     * @return true if the path is within an open project, false otherwise
     */
    public static boolean isPathWithinOpenProjects(String filePath) {
        try {
            File targetFile = new File(filePath).getCanonicalFile();
            String targetPath = targetFile.getAbsolutePath();
            
            // Get all open projects
            Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
            
            for (Project project : openProjects) {
                FileObject projectDir = project.getProjectDirectory();
                if (projectDir != null) {
                    File projectFile = new File(projectDir.getPath()).getCanonicalFile();
                    String projectPath = projectFile.getAbsolutePath();
                    
                    // Check if target path is within this project directory
                    if (targetPath.startsWith(projectPath + File.separator) || targetPath.equals(projectPath)) {
                        LOGGER.log(Level.FINE, "File path {0} is within project: {1}", 
                                  new Object[]{filePath, projectPath});
                        return true;
                    }
                }
            }
            
            LOGGER.log(Level.WARNING, "File path {0} is not within any open project directory", filePath);
            return false;
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error validating file path: " + filePath, e);
            return false; // Deny access on any path resolution errors
        }
    }
}
