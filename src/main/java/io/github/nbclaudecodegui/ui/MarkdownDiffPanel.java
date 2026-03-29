package io.github.nbclaudecodegui.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbPreferences;

/**
 * Side-by-side rendered markdown preview panel for before/after diff comparison.
 * Provides percentage-based scroll sync between the before and after panes.
 */
public class MarkdownDiffPanel extends JPanel {

    private static final String PREF_DIVIDER = "mdSplitDivider";

    private final JEditorPane mdBeforePane;
    private final JEditorPane mdAfterPane;
    private final JScrollPane beforeScroll;
    private final JScrollPane afterScroll;
    private final JSplitPane splitPane;
    private boolean syncActive = false;

    /**
     * Creates a side-by-side markdown diff panel.
     *
     * @param before markdown text for the "before" (original) state
     * @param after  markdown text for the "after" (modified) state
     */
    public MarkdownDiffPanel(String before, String after) {
        super(new BorderLayout());

        String beforeHtml = MarkdownRenderer.toHtml(before != null ? before : "");
        String afterHtml  = MarkdownRenderer.toHtml(after  != null ? after  : "");

        mdBeforePane = new JEditorPane("text/html", beforeHtml);
        mdBeforePane.setEditable(false);
        mdAfterPane  = new JEditorPane("text/html", afterHtml);
        mdAfterPane.setEditable(false);

        beforeScroll = new JScrollPane(mdBeforePane);
        afterScroll  = new JScrollPane(mdAfterPane);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, beforeScroll, afterScroll);
        splitPane.setResizeWeight(0.5);

        // restore divider
        SwingUtilities.invokeLater(() -> {
            int w = splitPane.getWidth();
            if (w > 0) {
                int saved = NbPreferences.forModule(MarkdownDiffPanel.class)
                        .getInt(PREF_DIVIDER, w / 2);
                splitPane.setDividerLocation(saved);
            }
        });

        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e ->
            NbPreferences.forModule(MarkdownDiffPanel.class)
                    .putInt(PREF_DIVIDER, (int) e.getNewValue()));

        add(splitPane, BorderLayout.CENTER);
        wireScrollSync();
    }

    private void wireScrollSync() {
        beforeScroll.getViewport().addChangeListener(e -> {
            if (syncActive) return;
            syncActive = true;
            try { syncViewport(beforeScroll, afterScroll); }
            finally { syncActive = false; }
        });
        afterScroll.getViewport().addChangeListener(e -> {
            if (syncActive) return;
            syncActive = true;
            try { syncViewport(afterScroll, beforeScroll); }
            finally { syncActive = false; }
        });
    }

    private static void syncViewport(JScrollPane src, JScrollPane dst) {
        int srcMax = src.getVerticalScrollBar().getMaximum()
                   - src.getVerticalScrollBar().getVisibleAmount();
        if (srcMax <= 0) return;
        double ratio = (double) src.getVerticalScrollBar().getValue() / srcMax;
        int dstMax = dst.getVerticalScrollBar().getMaximum()
                   - dst.getVerticalScrollBar().getVisibleAmount();
        dst.getVerticalScrollBar().setValue((int) (ratio * dstMax));
    }

    /**
     * Tries to attach bidirectional percentage scroll sync between the raw diff view
     * component and the markdown panes. Silently does nothing if no JScrollPane is found.
     *
     * @param diffViewComponent the raw diff view component (may contain a JScrollPane)
     */
    public void attachRawDiffSync(Component diffViewComponent) {
        JScrollPane rawScroll = findScrollPane(diffViewComponent);
        if (rawScroll == null) return;

        // before → raw
        beforeScroll.getViewport().addChangeListener(e -> {
            if (syncActive) return;
            syncActive = true;
            try { syncViewport(beforeScroll, rawScroll); }
            finally { syncActive = false; }
        });
        // after → raw
        afterScroll.getViewport().addChangeListener(e -> {
            if (syncActive) return;
            syncActive = true;
            try { syncViewport(afterScroll, rawScroll); }
            finally { syncActive = false; }
        });
        // raw → before and after
        rawScroll.getViewport().addChangeListener(e -> {
            if (syncActive) return;
            syncActive = true;
            try {
                syncViewport(rawScroll, beforeScroll);
                syncViewport(rawScroll, afterScroll);
            } finally { syncActive = false; }
        });
    }

    private static JScrollPane findScrollPane(Component c) {
        if (c instanceof JScrollPane) return (JScrollPane) c;
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                JScrollPane found = findScrollPane(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
