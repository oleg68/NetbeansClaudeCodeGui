package io.github.nbclaudecodegui.process;

import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Bridges a {@link PtyProcess} (pty4j) to JediTerm's {@link TtyConnector}.
 */
public final class PtyTtyConnector implements TtyConnector {

    private final PtyProcess process;
    private final InputStreamReader reader;

    /**
     * Creates a connector for the given PTY process.
     *
     * @param process a running PTY process
     */
    public PtyTtyConnector(PtyProcess process) {
        this.process = process;
        this.reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        process.getOutputStream().write(bytes);
        process.getOutputStream().flush();
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public void close() {
        process.destroy();
    }

    @Override
    public void resize(Dimension termWinSize) {
        if (process.isAlive()) {
            process.setWinSize(
                    new WinSize(termWinSize.width, termWinSize.height));
        }
    }

    @Override
    public String getName() {
        return "claude";
    }

    @Override
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }
}
