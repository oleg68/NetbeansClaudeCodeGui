// Originally forked from https://github.com/emilianbold/claude-code-netbeans
// Original: src/main/java/org/openbeans/claude/netbeans/ClaudeCodeInstaller.java
package io.github.nbclaudecodegui;

import io.github.nbclaudecodegui.mcp.MCPSseServer;
import io.github.nbclaudecodegui.mcp.NetBeansMCPHandler;
import io.github.nbclaudecodegui.settings.ClaudeCodePreferences;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.modules.ModuleInstall;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;
import org.openide.awt.NotificationDisplayer;
import org.openbeans.claude.netbeans.ClaudeCodeStatusService;

/**
 * Manages the lifecycle of the Claude Code NetBeans plugin.
 * Handles installation, startup, and shutdown of the plugin components.
 */
@ServiceProvider(service = ClaudeCodeStatusService.class)
public class ClaudeCodeInstaller extends ModuleInstall implements PropertyChangeListener, ClaudeCodeStatusService {

    private static final Logger LOGGER = Logger.getLogger(ClaudeCodeInstaller.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("ClaudeCode", 1);

    // Static so that the Lookup-created instance (separate from the ModuleInstall
    // instance managed by NetBeans) reads the same running server state.
    private static volatile MCPSseServer mcpServer;
    private NetBeansMCPHandler mcpHandler;

    /** Default constructor; called by the NetBeans module system. */
    public ClaudeCodeInstaller() {}

    /**
     * Called when the module is first installed.
     */
    @Override
    public void restored() {
        LOGGER.info("Claude Code NetBeans plugin is starting up...");

        // Remove any stale NetBeans lock files from previous sessions
        removeNetBeansLockFiles();

        // Initialize components
        initializeComponents();

        // Apply saved debug mode setting to logger level
        ClaudeCodePreferences.applyDebugMode(ClaudeCodePreferences.isDebugMode());

        // Start the MCP server
        startMCPServer();

        // Listen for project changes to update lock file
         OpenProjects.getDefault().addPropertyChangeListener(this);

        LOGGER.info("Claude Code NetBeans plugin started successfully");
    }

    /**
     * Called when the module is being uninstalled.
     */
    @Override
    public void uninstalled() {
        LOGGER.info("Claude Code NetBeans plugin is shutting down...");

         OpenProjects.getDefault().removePropertyChangeListener(this);

        // Stop MCP server
        stopMCPServer();

        LOGGER.info("Claude Code NetBeans plugin shut down complete");
    }

    /**
     * Called when NetBeans is closing.
     */
    @Override
    public void close() {
        uninstalled();
    }

    /**
     * Handles property changes, particularly open projects changes.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
         // No-op: lock file no longer used
    }

    /**
     * Initializes all plugin components.
     */
    private void initializeComponents() {
        try {
            mcpHandler = new NetBeansMCPHandler();
            mcpServer = new MCPSseServer(mcpHandler);
            LOGGER.info("Claude Code components initialized");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize Claude Code components", e);
            Exceptions.printStackTrace(e);
        }
    }

    /**
     * Starts the MCP SSE server on the configured port.
     * Fails immediately if the port is busy.
     */
    private void startMCPServer() {
        RP.post(() -> {
            try {
                int port = ClaudeCodePreferences.getMcpPort();
                if (mcpServer.start(port)) {
                    LOGGER.log(Level.INFO, "Claude Code MCP server started on port {0}", port);
                } else {
                    String msg = "Port " + port + " is busy. Change MCP port in Tools \u2192 Options \u2192 Claude Code.";
                    LOGGER.severe(msg);
                    NotificationDisplayer.getDefault().notify(
                            "Claude Code MCP server",
                            NotificationDisplayer.Priority.HIGH.getIcon(),
                            msg,
                            null,
                            NotificationDisplayer.Priority.HIGH);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error starting Claude Code MCP server", e);
                Exceptions.printStackTrace(e);
            }
        });
    }

    /**
     * Removes stale NetBeans lock files from ~/.claude/ide/ left by previous sessions.
     */
    private void removeNetBeansLockFiles() {
        try {
            Path ideDir = Paths.get(System.getProperty("user.home"), ".claude", "ide");
            if (!Files.exists(ideDir)) return;
            try (var stream = Files.list(ideDir)) {
                stream.filter(p -> p.toString().endsWith(".lock"))
                      .forEach(p -> {
                          try {
                              if (Files.readString(p).contains("\"ideName\":\"NetBeans\"")) {
                                  Files.delete(p);
                                  LOGGER.info("Removed stale NetBeans lock: " + p);
                              }
                          } catch (IOException e) { /* ignore */ }
                      });
            }
        } catch (IOException e) {
            LOGGER.warning("Could not clean ide dir: " + e.getMessage());
        }
    }

    /**
     * Stops the MCP SSE server.
     */
    private void stopMCPServer() {
        if (mcpServer != null && mcpServer.isRunning()) {
            try {
                mcpServer.stop();
                LOGGER.info("Claude Code MCP server stopped");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error stopping Claude Code MCP server", e);
            }
        }
    }

    /**
     * Gets the current status of the Claude Code integration.
     *
     * @return status information
     */
    @Override
    public String getStatus() {
        StringBuilder status = new StringBuilder();
        status.append("<b>Claude Code NetBeans Integration</b><br>");

        if (mcpServer != null) {
            if (mcpServer.isRunning()) {
                status.append("🟢 MCP SSE Server: Running on port ").append(mcpServer.getPort()).append("<br>");
            } else {
                status.append("🔴 MCP SSE Server: Stopped<br>");
            }
        } else {
            status.append("⚪ MCP Server: Not initialized<br>");
        }

        status.append("🔧 Process ID: ").append(ProcessHandle.current().pid());

        return status.toString();
    }

    /**
     * Checks if the MCP server is currently running.
     *
     * @return true if the server is running, false otherwise
     */
    @Override
    public boolean isServerRunning() {
        return mcpServer != null && mcpServer.isRunning();
    }

    /**
     * Gets the port number the MCP server is running on.
     *
     * @return port number, or -1 if server is not running
     */
    @Override
    public int getServerPort() {
        if (mcpServer != null && mcpServer.isRunning()) {
            return mcpServer.getPort();
        }
        return -1;
    }

    @Override
    public boolean isLockFileValid() {
        return false; // lock file no longer used
    }
}
