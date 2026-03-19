package org.openbeans.claude.netbeans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP server providing MCP SSE transport for Claude Code CLI.
 *
 * <p>Implements the MCP SSE transport (protocol 2024-11-05):
 * <ul>
 *   <li>{@code GET /sse}       — establishes the SSE stream; sends an
 *       {@code event:endpoint} frame so the client knows where to POST,
 *       then streams all server→client messages (responses + notifications).</li>
 *   <li>{@code POST /messages} — receives JSON-RPC requests from Claude,
 *       returns {@code 202 Accepted}, and routes the response via the SSE
 *       stream (not the HTTP response body).</li>
 * </ul>
 *
 * <p>Verified by Python integration test {@code test_mcp_sse_transport.py}:
 * only variant A (responses via SSE stream) results in Claude showing
 * {@code connected}; variant B (responses in POST body) shows {@code failed}.
 */
public class MCPSseServer {

    private static final Logger LOGGER = Logger.getLogger(MCPSseServer.class.getName());

    private Server server;
    private int port;
    private final NetBeansMCPHandler mcpHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Shared queue: items placed here are written to the active SSE stream.
     * Capacity 1 000 avoids unbounded growth when no SSE client is connected.
     */
    private final BlockingQueue<String> sseQueue = new LinkedBlockingQueue<>(1000);

    public MCPSseServer(NetBeansMCPHandler mcpHandler) {
        this.mcpHandler = mcpHandler;
    }

    /**
     * Starts the HTTP/SSE server on the specified port.
     * Fails immediately if the port is already in use.
     *
     * @param port the port to listen on
     * @return {@code true} if the server started successfully
     */
    public boolean start(int port) {
        try (ServerSocket test = new ServerSocket(port)) {
            // port is free — close probe socket and let Jetty bind it
        } catch (Exception e) {
            LOGGER.severe("MCP port " + port + " is busy: " + e.getMessage());
            return false;
        }
        try {
            this.port = port;

            server = new Server();
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            server.addConnector(connector);

            ServletContextHandler ctx =
                    new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            ctx.setContextPath("/");
            ctx.addServlet(new ServletHolder(new SseServlet()), "/sse");
            ctx.addServlet(new ServletHolder(new MessagesServlet()), "/messages");

            server.setHandler(ctx);
            server.start();

            mcpHandler.setSseQueue(sseQueue);

            LOGGER.log(Level.INFO, "MCP SSE server started on port {0}", port);
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start MCP SSE server", e);
            return false;
        }
    }

    /** Stops the server. */
    public void stop() {
        if (server != null && server.isStarted()) {
            try {
                server.stop();
                LOGGER.info("MCP SSE server stopped");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error stopping MCP SSE server", e);
            }
        }
    }

    /** @return the port this server listens on */
    public int getPort() { return port; }

    /** @return {@code true} while the Jetty server is running */
    public boolean isRunning() { return server != null && server.isStarted(); }

    // -------------------------------------------------------------------------
    // GET /sse
    // -------------------------------------------------------------------------

    /**
     * Long-lived SSE endpoint.
     *
     * <ol>
     *   <li>Sends {@code event:endpoint\ndata:/messages\n\n} so Claude knows
     *       where to POST JSON-RPC requests.</li>
     *   <li>Blocks on {@link #sseQueue}, writing each item as a
     *       {@code data:} frame until the client disconnects.</li>
     * </ol>
     */
    private class SseServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {

            resp.setContentType("text/event-stream");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader("Cache-Control", "no-cache");
            resp.setHeader("Connection", "keep-alive");
            resp.flushBuffer();

            PrintWriter writer = resp.getWriter();
            writeEvent(writer, "endpoint", "/messages");

            LOGGER.log(Level.INFO, "SSE client connected from {0}", req.getRemoteAddr());

            try {
                while (!writer.checkError()) {
                    String msg = sseQueue.poll(5, TimeUnit.SECONDS);
                    if (msg == null) {
                        writer.write(": ping\n\n");
                        writer.flush();
                    } else {
                        writeEvent(writer, "message", msg);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                LOGGER.info("SSE client disconnected");
            }
        }

        private void writeEvent(PrintWriter w, String event, String data) {
            w.write("event: " + event + "\n");
            w.write("data: " + data + "\n\n");
            w.flush();
        }
    }

    // -------------------------------------------------------------------------
    // POST /messages
    // -------------------------------------------------------------------------

    /**
     * Accepts JSON-RPC requests from Claude Code, routes them to
     * {@link NetBeansMCPHandler}, and enqueues any response for the SSE stream.
     *
     * <p>Per the MCP SSE spec the HTTP response is always {@code 202 Accepted}
     * with an empty body; the actual JSON-RPC response is sent via SSE.
     */
    private class MessagesServlet extends HttpServlet {

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {

            resp.setStatus(HttpServletResponse.SC_ACCEPTED);
            resp.flushBuffer();

            try {
                JsonNode message = objectMapper.readTree(req.getInputStream());
                LOGGER.log(Level.FINE, "MCP request: {0}", message);

                String response = mcpHandler.handleMessage(message);
                if (response != null) {
                    if (!sseQueue.offer(response)) {
                        LOGGER.warning("SSE queue full; MCP response dropped");
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error handling MCP POST", e);
            }
        }
    }

}
