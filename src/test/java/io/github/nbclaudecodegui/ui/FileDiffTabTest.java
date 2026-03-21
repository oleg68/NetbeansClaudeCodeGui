package io.github.nbclaudecodegui.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FileDiffTab} static helpers.
 */
class FileDiffTabTest {

    @Test
    void fileInsideDirectory() {
        assertTrue(FileDiffTab.isFileUnderDirectory("/home/user/project/src/Foo.java", "/home/user/project"));
    }

    @Test
    void fileIsDirectory() {
        assertTrue(FileDiffTab.isFileUnderDirectory("/home/user/project", "/home/user/project"));
    }

    @Test
    void fileInSubdirectory() {
        assertTrue(FileDiffTab.isFileUnderDirectory("/home/user/project/a/b/c/D.java", "/home/user/project"));
    }

    @Test
    void fileOutsideDirectory() {
        assertFalse(FileDiffTab.isFileUnderDirectory("/home/user/other/Foo.java", "/home/user/project"));
    }

    @Test
    void prefixNotSeparator() {
        // "/home/user/project2" should NOT match dir "/home/user/project"
        assertFalse(FileDiffTab.isFileUnderDirectory("/home/user/project2/Foo.java", "/home/user/project"));
    }

    @Test
    void nullFilePath() {
        assertFalse(FileDiffTab.isFileUnderDirectory(null, "/home/user/project"));
    }

    @Test
    void nullDirPath() {
        assertFalse(FileDiffTab.isFileUnderDirectory("/home/user/project/Foo.java", null));
    }

    @Test
    void bothNull() {
        assertFalse(FileDiffTab.isFileUnderDirectory(null, null));
    }
}
