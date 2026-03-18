package io.github.nbclaudecodegui.process;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.awt.Dimension;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PtyTtyConnector}.
 */
class PtyTtyConnectorTest {

    private static PtyProcess startCat() throws Exception {
        return new PtyProcessBuilder(new String[]{"/bin/cat"})
                .setEnvironment(Map.of("TERM", "xterm"))
                .setDirectory(System.getProperty("java.io.tmpdir"))
                .setConsole(false)
                .setRedirectErrorStream(true)
                .start();
    }

    @Test
    void testIsConnected() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"));

        PtyProcess p = startCat();
        PtyTtyConnector c = new PtyTtyConnector(p);
        assertTrue(c.isConnected());
        c.close();
        p.waitFor();
        assertFalse(c.isConnected());
    }

    @Test
    void testClose() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"));

        PtyProcess p = startCat();
        PtyTtyConnector c = new PtyTtyConnector(p);
        c.close();
        p.waitFor();
        assertFalse(p.isAlive());
    }

    @Test
    void testResize() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"));

        PtyProcess p = startCat();
        PtyTtyConnector c = new PtyTtyConnector(p);
        assertDoesNotThrow(() -> c.resize(new Dimension(100, 50)));
        c.close();
        p.waitFor();
    }

    @Test
    void testGetName() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("win"));

        PtyProcess p = startCat();
        PtyTtyConnector c = new PtyTtyConnector(p);
        assertEquals("claude", c.getName());
        c.close();
        p.waitFor();
    }
}
