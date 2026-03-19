package org.openbeans.claude.netbeans.tools;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import org.openbeans.claude.netbeans.NbUtils;
import org.openbeans.claude.netbeans.tools.params.OpenFileParams;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 * Tool to open a file in the NetBeans editor.
 */
public class OpenFile implements Tool<OpenFileParams, String> {
    
    private static final Logger LOGGER = Logger.getLogger(OpenFile.class.getName());
    
    @Override
    public String getName() {
        return "openFile";
    }
    
    @Override
    public String getDescription() {
        return "Opens a file in the editor";
    }
    
    @Override
    public Class<OpenFileParams> getParameterClass() {
        return OpenFileParams.class;
    }

    /**
     * Opens the file and returns its EditorCookie.
     * Extracted as a protected method so tests can bypass file-system and
     * security calls without mocking static APIs.
     */
    protected EditorCookie doOpenFile(String filePath) throws Exception {
        if (!NbUtils.isPathWithinOpenProjects(filePath)) {
            throw new SecurityException("File open denied: Path is not within any open project directory: " + filePath);
        }
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        FileObject fileObject = FileUtil.toFileObject(file);
        if (fileObject != null) {
            DataObject dataObject = DataObject.find(fileObject);
            EditorCookie editorCookie = dataObject.getLookup().lookup(EditorCookie.class);
            if (editorCookie != null) {
                editorCookie.open();
                return editorCookie;
            }
        }
        throw new RuntimeException("Failed to open file in editor");
    }

    /**
     * Navigates to the first occurrence of {@code pattern} using the pane from
     * the given {@code EditorCookie} — avoids the focus-race bug where
     * EditorRegistry.lastFocusedComponent() would return the Claude terminal.
     */
    protected void navigateTo(EditorCookie ec, String pattern) {
        SwingUtilities.invokeLater(() -> {
            JEditorPane[] panes = ec.getOpenedPanes();
            if (panes == null || panes.length == 0) return;
            String text = panes[0].getText();
            int offset = text.indexOf(pattern);
            if (offset >= 0) {
                panes[0].setCaretPosition(offset);
                panes[0].requestFocusInWindow();
            }
        });
    }

    @Override
    public String run(OpenFileParams params) throws Exception {
        String filePath = params.getFilePath();
        try {
            EditorCookie ec = doOpenFile(filePath);
            String pattern = params.getPattern();
            if (pattern != null && !pattern.isEmpty()) {
                navigateTo(ec, pattern);
            }
            return "File opened successfully: " + filePath;
        } catch (Exception e) {
            throw new RuntimeException("Failed to open file: " + e.getMessage(), e);
        }
    }
}