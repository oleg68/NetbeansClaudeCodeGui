package io.github.nbclaudecodegui.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import org.netbeans.api.options.OptionsDisplayer;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

/**
 * Panel representing a single Claude Code session tab.
 *
 * <p>The top bar contains:
 * <ol>
 *   <li>A non-editable <em>project</em> combo box — lists open projects;
 *       selecting one copies its root path into the path combo.</li>
 *   <li>An editable <em>path</em> combo box — pre-populated with the last 10
 *       non-project paths from history (project paths are not stored).</li>
 *   <li>A <strong>…</strong> browse button.</li>
 *   <li>An <strong>Open</strong> button — validates, saves non-project path to
 *       history, and locks all controls except Settings.</li>
 *   <li>A <strong>⚙</strong> settings button (always active).</li>
 * </ol>
 *
 * <p>When created with {@code locked=true} (e.g. from the context menu) all
 * controls except Settings are immediately disabled.
 */
public final class ClaudeSessionPanel extends JPanel {

    // -------------------------------------------------------------------------
    // history persistence
    // -------------------------------------------------------------------------

    private static final String PREF_RECENT = "recentPaths";
    private static final int HISTORY_SIZE = 10;
    private static final String HISTORY_SEP = "\n";

    private static List<String> loadHistory() {
        Preferences prefs = NbPreferences.forModule(ClaudeSessionPanel.class);
        String raw = prefs.get(PREF_RECENT, "");
        List<String> list = new ArrayList<>();
        for (String p : raw.split(HISTORY_SEP, -1)) {
            if (!p.isBlank()) list.add(p);
        }
        return list;
    }

    private static void saveToHistory(String path) {
        List<String> history = loadHistory();
        history.remove(path);
        history.add(0, path);
        if (history.size() > HISTORY_SIZE) {
            history = history.subList(0, HISTORY_SIZE);
        }
        NbPreferences.forModule(ClaudeSessionPanel.class)
                .put(PREF_RECENT, String.join(HISTORY_SEP, history));
    }

    // -------------------------------------------------------------------------
    // fields
    // -------------------------------------------------------------------------

    private final JComboBox<ProjectItem> projectCombo;
    private final JComboBox<String> pathCombo;
    private final JButton browseButton;
    private final JButton openButton;
    private final JLabel errorLabel;
    private final JLabel placeholderLabel;

    private File confirmedDirectory;

    /** Listener notified when the confirmed working directory changes. */
    public interface DirectoryListener {
        /**
         * Called when the user confirms a valid directory.
         *
         * @param dir the confirmed directory
         */
        void directorySelected(File dir);
    }

    private boolean suppressProjectListener;
    private DirectoryListener directoryListener;

    // -------------------------------------------------------------------------
    // constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a session panel with no pre-set directory (selector active).
     */
    public ClaudeSessionPanel() {
        this(null, false);
    }

    /**
     * Creates a session panel.
     *
     * @param directory pre-set directory, or {@code null} for none
     * @param locked    {@code true} to lock the directory control immediately
     */
    public ClaudeSessionPanel(File directory, boolean locked) {
        super(new BorderLayout());
        this.confirmedDirectory = directory;

        // --- project combo ---
        projectCombo = new JComboBox<>();
        projectCombo.setRenderer(new ProjectItemRenderer());
        populateProjectCombo();
        if (directory != null) {
            preselectProjectForDirectory(directory);
        }
        projectCombo.addActionListener(e -> onProjectSelected());

        // --- path combo ---
        pathCombo = new JComboBox<>();
        pathCombo.setEditable(true);
        pathCombo.setPrototypeDisplayValue("MMMMMMMMMMMMMMMMMMMMMMMMMMMMMM");
        populatePathHistory();
        if (directory != null) {
            pathCombo.setSelectedItem(directory.getAbsolutePath());
        }

        // --- buttons ---
        browseButton = new JButton(NbBundle.getMessage(ClaudeSessionPanel.class, "BTN_Browse"));
        browseButton.addActionListener(e -> onBrowse());

        openButton = new JButton(NbBundle.getMessage(ClaudeSessionPanel.class, "BTN_Open"));
        openButton.addActionListener(e -> onOpen());

        JButton settingsButton = new JButton("\u2699");
        settingsButton.setToolTipText(NbBundle.getMessage(ClaudeSessionPanel.class, "TIP_Settings"));
        settingsButton.addActionListener(e ->
                OptionsDisplayer.getDefault().open("ClaudeCodeGUI"));

        // --- error label ---
        errorLabel = new JLabel();
        errorLabel.setForeground(Color.RED);
        errorLabel.setVisible(false);

        // --- control bar ---
        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        controlBar.add(projectCombo);
        controlBar.add(pathCombo);
        controlBar.add(browseButton);
        controlBar.add(openButton);
        controlBar.add(settingsButton);

        JPanel top = new JPanel(new BorderLayout());
        top.add(controlBar, BorderLayout.CENTER);
        top.add(errorLabel, BorderLayout.SOUTH);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        add(top, BorderLayout.NORTH);

        // --- placeholder content ---
        placeholderLabel = new JLabel(
                NbBundle.getMessage(ClaudeSessionPanel.class, "LBL_SelectDir"),
                SwingConstants.CENTER);
        placeholderLabel.setForeground(Color.GRAY);
        add(placeholderLabel, BorderLayout.CENTER);

        if (locked) {
            setControlsLocked(true);
        }
    }

    // -------------------------------------------------------------------------
    // public API
    // -------------------------------------------------------------------------

    /**
     * Sets the listener notified when a directory is confirmed.
     *
     * @param listener the listener, or {@code null} to remove
     */
    public void setDirectoryListener(DirectoryListener listener) {
        this.directoryListener = listener;
    }

    /**
     * Returns the confirmed working directory, or {@code null} if none.
     *
     * @return the confirmed directory
     */
    public File getConfirmedDirectory() {
        return confirmedDirectory;
    }

    /**
     * Returns whether the directory controls are locked.
     *
     * @return {@code true} if locked
     */
    public boolean isLocked() {
        return !openButton.isEnabled();
    }

    /**
     * Resolves the tab label for a directory: project display name when the
     * path matches an open project root, otherwise the directory basename.
     *
     * @param dir the working directory
     * @return the resolved label
     */
    public static String resolveTabLabel(File dir) {
        if (dir == null) {
            return NbBundle.getMessage(ClaudeSessionPanel.class, "TAB_NewSession");
        }
        for (Project p : OpenProjects.getDefault().getOpenProjects()) {
            File projDir = FileUtil.toFile(p.getProjectDirectory());
            if (dir.equals(projDir)) {
                return ProjectUtils.getInformation(p).getDisplayName();
            }
        }
        return dir.getName();
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private void onProjectSelected() {
        if (suppressProjectListener) return;
        Object sel = projectCombo.getSelectedItem();
        if (sel instanceof ProjectItem item && item.project() != null) {
            File dir = FileUtil.toFile(item.project().getProjectDirectory());
            if (dir != null) {
                pathCombo.setSelectedItem(dir.getAbsolutePath());
            }
        }
    }

    private void preselectProjectForDirectory(File directory) {
        suppressProjectListener = true;
        try {
            for (int i = 0; i < projectCombo.getItemCount(); i++) {
                ProjectItem item = projectCombo.getItemAt(i);
                if (item.project() != null) {
                    File projDir = FileUtil.toFile(item.project().getProjectDirectory());
                    if (directory.equals(projDir)) {
                        projectCombo.setSelectedIndex(i);
                        return;
                    }
                }
            }
        } finally {
            suppressProjectListener = false;
        }
    }

    private void onBrowse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        Object cur = pathCombo.getSelectedItem();
        if (cur != null && !cur.toString().isBlank()) {
            File f = new File(cur.toString().trim());
            if (f.isDirectory()) chooser.setCurrentDirectory(f);
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathCombo.setSelectedItem(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onOpen() {
        Object item = pathCombo.getSelectedItem();
        if (item == null || item.toString().isBlank()) {
            showError(NbBundle.getMessage(ClaudeSessionPanel.class, "ERR_PathEmpty"));
            return;
        }
        String text = item.toString().trim();
        File dir = new File(text);
        if (!dir.exists() || !dir.isDirectory()) {
            showError(NbBundle.getMessage(ClaudeSessionPanel.class, "ERR_PathNotFound"));
            return;
        }

        // save to history only if not a project directory
        boolean isProject = isProjectDirectory(dir);
        if (!isProject) {
            saveToHistory(dir.getAbsolutePath());
        }

        errorLabel.setVisible(false);
        confirmedDirectory = dir;
        setControlsLocked(true);
        placeholderLabel.setVisible(false);
        revalidate();
        repaint();
        if (directoryListener != null) {
            directoryListener.directorySelected(dir);
        }
    }

    private boolean isProjectDirectory(File dir) {
        for (Project p : OpenProjects.getDefault().getOpenProjects()) {
            if (dir.equals(FileUtil.toFile(p.getProjectDirectory()))) {
                return true;
            }
        }
        return false;
    }

    private void setControlsLocked(boolean locked) {
        projectCombo.setEnabled(!locked);
        pathCombo.setEnabled(!locked);
        browseButton.setEnabled(!locked);
        openButton.setEnabled(!locked);
    }

    private void showError(String message) {
        errorLabel.setText("  " + message);
        errorLabel.setVisible(true);
        revalidate();
        repaint();
    }

    private void populateProjectCombo() {
        DefaultComboBoxModel<ProjectItem> model = new DefaultComboBoxModel<>();
        model.addElement(new ProjectItem(null, // placeholder
                NbBundle.getMessage(ClaudeSessionPanel.class, "LBL_SelectProject")));
        for (Project p : OpenProjects.getDefault().getOpenProjects()) {
            model.addElement(new ProjectItem(p,
                    ProjectUtils.getInformation(p).getDisplayName()));
        }
        projectCombo.setModel(model);
    }

    private void populatePathHistory() {
        for (String p : loadHistory()) {
            pathCombo.addItem(p);
        }
    }

    // -------------------------------------------------------------------------
    // inner types
    // -------------------------------------------------------------------------

    /** Item in the project combo box. */
    private record ProjectItem(Project project, String label) {
        @Override public String toString() { return label; }
    }

    /** Renders the placeholder item in gray. */
    private static final class ProjectItemRenderer
            extends JLabel implements ListCellRenderer<ProjectItem> {

        ProjectItemRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ProjectItem> list,
                ProjectItem value, int index,
                boolean isSelected, boolean cellHasFocus) {

            setText(value == null ? "" : value.label());
            boolean placeholder = (value == null || value.project() == null);
            setForeground(placeholder
                    ? (isSelected ? list.getSelectionForeground() : Color.GRAY)
                    : (isSelected ? list.getSelectionForeground() : list.getForeground()));
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            return this;
        }
    }
}
