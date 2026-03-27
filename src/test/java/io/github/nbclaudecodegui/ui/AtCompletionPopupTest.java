package io.github.nbclaudecodegui.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AtCompletionPopup} — one-level directory navigation.
 *
 * <p>Tests the filtering logic by directly injecting {@code allPaths} and
 * calling {@code updatePopup()} via reflection.
 */
class AtCompletionPopupTest {

    private JTextArea area;
    private AtCompletionPopup popup;
    private DefaultListModel<String> listModel;

    @BeforeEach
    void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            area  = new JTextArea();
            popup = AtCompletionPopup.install(area, () -> "/fake/workdir");
        });
        listModel = getListModel(popup);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static DefaultListModel<String> getListModel(AtCompletionPopup p) throws Exception {
        Field f = AtCompletionPopup.class.getDeclaredField("listModel");
        f.setAccessible(true);
        return (DefaultListModel<String>) f.get(p);
    }

    private static void injectPaths(AtCompletionPopup p, List<String> paths) throws Exception {
        Field f = AtCompletionPopup.class.getDeclaredField("allPaths");
        f.setAccessible(true);
        f.set(p, Collections.unmodifiableList(paths));
    }

    private void callUpdatePopup() throws Exception {
        Method m = AtCompletionPopup.class.getDeclaredMethod("updatePopup");
        m.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try { m.invoke(popup); }
            catch (java.lang.reflect.InvocationTargetException ex) {
                // In headless tests, modelToView2D returns null (no visible frame) —
                // the list model is populated before that call so this is expected.
                Throwable cause = ex.getCause();
                if (!(cause instanceof NullPointerException)) {
                    throw new RuntimeException(ex);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private List<String> listItems() {
        List<String> result = new java.util.ArrayList<>();
        for (int i = 0; i < listModel.getSize(); i++) result.add(listModel.getElementAt(i));
        return result;
    }

    // -------------------------------------------------------------------------
    // Tests: root level — only top-level items shown, ".." always present
    // -------------------------------------------------------------------------

    @Test
    void atRootShowsTopLevelDirsAndFiles() throws Exception {
        injectPaths(popup, List.of(
                "src/main/Foo.java",
                "src/test/Bar.java",
                "pom.xml",
                "README.md"
        ));

        SwingUtilities.invokeAndWait(() -> area.setText("@"));

        callUpdatePopup();

        List<String> items = listItems();
        // Must contain ".." even at root
        assertTrue(items.contains(".."), "'..' must be shown even at root level");
        // Dirs with trailing /
        assertTrue(items.contains("src/"), "top-level dir 'src/' must appear");
        // Files
        assertTrue(items.contains("pom.xml"), "top-level file must appear");
        assertTrue(items.contains("README.md"), "top-level file must appear");
        // Must NOT show nested paths directly
        assertFalse(items.contains("src/main/Foo.java"), "nested path must NOT be shown at root level");
        assertFalse(items.contains("src/test/Bar.java"), "nested path must NOT be shown at root level");
        // 'src/' must appear only once
        assertEquals(1, items.stream().filter("src/"::equals).count(), "'src/' must appear exactly once");
    }

    @Test
    void atSubdirShowsOnlyImmediateChildren() throws Exception {
        injectPaths(popup, List.of(
                "src/main/java/Foo.java",
                "src/main/resources/app.xml",
                "src/test/java/FooTest.java",
                "pom.xml"
        ));

        SwingUtilities.invokeAndWait(() -> area.setText("@src/"));

        callUpdatePopup();

        List<String> items = listItems();
        assertTrue(items.contains(".."), "'..' must be present when inside subdir");
        assertTrue(items.contains("main/"), "'main/' dir must appear");
        assertTrue(items.contains("test/"), "'test/' dir must appear");
        assertFalse(items.contains("main/java/"), "nested dir must not be shown");
        assertFalse(items.contains("pom.xml"), "top-level file outside currentDir must not appear");
    }

    @Test
    void filterNarrowsResults() throws Exception {
        injectPaths(popup, List.of(
                "src/main/Foo.java",
                "src/main/Bar.java",
                "src/test/FooTest.java",
                "build/classes/Foo.class"
        ));

        SwingUtilities.invokeAndWait(() -> area.setText("@src/main/F"));

        callUpdatePopup();

        List<String> items = listItems();
        assertTrue(items.contains("Foo.java"), "Foo.java must match filter 'F'");
        assertFalse(items.contains("Bar.java"), "Bar.java must NOT match filter 'F'");
    }

    // -------------------------------------------------------------------------
    // Tests: parentOf — navigation helper
    // -------------------------------------------------------------------------

    private static String callParentOf(String currentDir) throws Exception {
        Method m = AtCompletionPopup.class.getDeclaredMethod("parentOf", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, currentDir);
    }

    @Test
    void parentOfEmptyReturnsParentDotDot() throws Exception {
        assertEquals("../", callParentOf(""), "parentOf(\"\") must return \"../\" to go above workdir");
    }

    @Test
    void parentOfDotDotReturnsDoubleDotDot() throws Exception {
        assertEquals("../../", callParentOf("../"), "parentOf(\"../\") must return \"../../\"");
    }

    @Test
    void parentOfSubdirReturnsRoot() throws Exception {
        assertEquals("", callParentOf("a/"), "parentOf(\"a/\") must return \"\" (back to workdir root)");
    }

    @Test
    void parentOfNestedSubdirReturnsParent() throws Exception {
        assertEquals("a/", callParentOf("a/b/"), "parentOf(\"a/b/\") must return \"a/\"");
    }

    // -------------------------------------------------------------------------
    // Tests: no-match hides popup
    // -------------------------------------------------------------------------

    @Test
    void noMatchHidesPopup() throws Exception {
        injectPaths(popup, List.of(
                "src/main/Foo.java",
                "pom.xml"
        ));

        SwingUtilities.invokeAndWait(() -> area.setText("@zzznomatch"));

        callUpdatePopup();

        assertEquals(0, listModel.getSize(), "listModel must be empty when no paths match the filter");
    }

    @Test
    void directoriesListedBeforeFiles() throws Exception {
        injectPaths(popup, List.of(
                "src/main/Foo.java",
                "src/test/FooTest.java",
                "pom.xml"
        ));

        SwingUtilities.invokeAndWait(() -> area.setText("@"));

        callUpdatePopup();

        List<String> items = listItems();
        int dotDotIdx = items.indexOf("..");
        int srcIdx    = items.indexOf("src/");
        int pomIdx    = items.indexOf("pom.xml");

        assertTrue(dotDotIdx >= 0,  "'..' must be present");
        assertTrue(srcIdx    >= 0,  "'src/' must be present");
        assertTrue(pomIdx    >= 0,  "'pom.xml' must be present");
        // ".." first, then dirs, then files
        assertTrue(dotDotIdx < srcIdx, "'..' must come before 'src/'");
        assertTrue(srcIdx    < pomIdx, "directories must come before files");
    }
}
