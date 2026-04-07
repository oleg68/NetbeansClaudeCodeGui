package io.github.nbclaudecodegui.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TC: MCPSseServer must route /messages responses only to the originating SSE session.
 *
 * <p>Root-cause regression test for the "1 MCP server failed" bug: with a single
 * shared queue, session B could steal the initialize/tools-list response intended
 * for session A, leaving session A without a response.
 *
 * <p>Fix: per-session queues keyed by {@code sessionId}.  The endpoint event sent
 * to each SSE client contains {@code /messages?sessionId=<uuid>}; the client uses
 * that URL for all subsequent POSTs; the server routes the response exclusively to
 * the matching session queue.
 */
public class MCPSseServerPerSessionTest {

    private MCPSseServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        port = findFreePort();
        server = new MCPSseServer(new NetBeansMCPHandler());
        assertTrue(server.start(port), "server must start");
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // -----------------------------------------------------------------------
    // TC-1: endpoint event contains sessionId
    // -----------------------------------------------------------------------

    /**
     * GET /sse must send an {@code event:endpoint} whose data contains
     * {@code /messages?sessionId=} so the client knows which URL to POST to.
     */
    @Test
    void testEndpointEventContainsSessionId() throws Exception {
        AtomicReference<String> endpointData = new AtomicReference<>();
        CountDownLatch received = new CountDownLatch(1);

        Thread sseThread = new Thread(() -> {
            try {
                URL url = new URL("http://localhost:" + port + "/sse");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(5000);

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            endpointData.set(line.substring(5).trim());
                            received.countDown();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // timeout — latch never counted down
            }
        });
        sseThread.setDaemon(true);
        sseThread.start();

        assertTrue(received.await(4, TimeUnit.SECONDS), "endpoint event not received");
        String data = endpointData.get();
        assertNotNull(data, "endpoint data must not be null");
        assertTrue(data.contains("/messages?sessionId="),
                "endpoint data must contain '/messages?sessionId=', got: " + data);
    }

    // -----------------------------------------------------------------------
    // TC-2: two sessions each receive only their own response
    // -----------------------------------------------------------------------

    /**
     * Two concurrent SSE sessions post {@code tools/list} requests.
     * Each session must receive exactly its own response, not the other's.
     */
    @Test
    void testTwoSessionsDoNotStealEachOthersResponse() throws Exception {
        SseClient sessionA = new SseClient();
        SseClient sessionB = new SseClient();

        sessionA.connect(port);
        sessionB.connect(port);

        // Both wait for their endpoint event
        assertTrue(sessionA.awaitEndpoint(4), "session A endpoint timeout");
        assertTrue(sessionB.awaitEndpoint(4), "session B endpoint timeout");

        String urlA = sessionA.messagesUrl;
        String urlB = sessionB.messagesUrl;

        assertNotEquals(urlA, urlB, "each session must get a distinct messages URL");

        // Session A posts tools/list (id=1), session B posts tools/list (id=2)
        // The responses should each land in the correct SSE stream.
        sessionA.startCollecting();
        sessionB.startCollecting();

        postMessage(urlA, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");
        postMessage(urlB, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");

        // Give the server time to process
        Thread.sleep(500);

        List<String> messagesA = sessionA.collectedMessages();
        List<String> messagesB = sessionB.collectedMessages();

        // Session A must have id=1, NOT id=2
        assertTrue(messagesA.stream().anyMatch(m -> m.contains("\"id\":1")),
                "session A must receive response with id=1; got: " + messagesA);
        assertFalse(messagesA.stream().anyMatch(m -> m.contains("\"id\":2")),
                "session A must NOT receive session B's response (id=2); got: " + messagesA);

        // Session B must have id=2, NOT id=1
        assertTrue(messagesB.stream().anyMatch(m -> m.contains("\"id\":2")),
                "session B must receive response with id=2; got: " + messagesB);
        assertFalse(messagesB.stream().anyMatch(m -> m.contains("\"id\":1")),
                "session B must NOT receive session A's response (id=1); got: " + messagesB);
    }

    // -----------------------------------------------------------------------
    // TC-3: unknown sessionId returns 400
    // -----------------------------------------------------------------------

    /**
     * Posting to {@code /messages?sessionId=nonexistent} must return 4xx
     * (or at minimum not crash the server).
     */
    @Test
    void testPostWithUnknownSessionIdReturnsError() throws Exception {
        URL url = new URL("http://localhost:" + port + "/messages?sessionId=nonexistent-uuid");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        byte[] body = "{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"tools/list\",\"params\":{}}"
                .getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }

        int status = conn.getResponseCode();
        // Must NOT be 202 (which would imply it was accepted and silently dropped).
        // Acceptable codes: 400, 404, 410.
        assertNotEquals(HttpURLConnection.HTTP_ACCEPTED, status,
                "posting to unknown sessionId must not return 202 Accepted");
    }

    // -----------------------------------------------------------------------
    // TC-4: session count tracked correctly
    // -----------------------------------------------------------------------

    /**
     * {@code getSessionCount()} must reflect connected sessions.
     */
    @Test
    void testSessionCountTracked() throws Exception {
        assertEquals(0, server.getSessionCount(), "no sessions initially");

        SseClient sessionA = new SseClient();
        SseClient sessionB = new SseClient();
        sessionA.connect(port);
        sessionB.connect(port);

        assertTrue(sessionA.awaitEndpoint(4), "session A endpoint timeout");
        assertTrue(sessionB.awaitEndpoint(4), "session B endpoint timeout");

        assertEquals(2, server.getSessionCount(), "two sessions after both connect");

        sessionA.disconnect();
        sessionB.disconnect();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void postMessage(String messagesUrl, String json) throws Exception {
        URL url = new URL(messagesUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }
        conn.getResponseCode(); // ensure request is sent
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /**
     * Minimal SSE client that connects to GET /sse, extracts the endpoint URL
     * from the first {@code event:endpoint} frame, then optionally collects
     * subsequent {@code event:message} frames.
     */
    private static class SseClient {

        volatile String messagesUrl;
        private volatile boolean collecting = false;
        private final List<String> messages = new ArrayList<>();
        private volatile Thread thread;
        private volatile HttpURLConnection conn;
        private final CountDownLatch endpointLatch = new CountDownLatch(1);

        void connect(int port) {
            thread = new Thread(() -> {
                try {
                    URL url = new URL("http://localhost:" + port + "/sse");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(10000);

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("event:") || line.startsWith("event: ")) {
                                String eventType = line.replaceFirst("event:\\s*", "").trim();
                                String dataLine = reader.readLine(); // next line is data:
                                if (dataLine != null && dataLine.startsWith("data:")) {
                                    String data = dataLine.substring(5).trim();
                                    if ("endpoint".equals(eventType)) {
                                        messagesUrl = "http://localhost:" + port + data;
                                        endpointLatch.countDown();
                                    } else if ("message".equals(eventType) && collecting) {
                                        synchronized (messages) {
                                            messages.add(data);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // disconnect or timeout
                    endpointLatch.countDown(); // unblock if we were waiting
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        boolean awaitEndpoint(int timeoutSeconds) throws InterruptedException {
            return endpointLatch.await(timeoutSeconds, TimeUnit.SECONDS) && messagesUrl != null;
        }

        void startCollecting() {
            collecting = true;
        }

        List<String> collectedMessages() {
            synchronized (messages) {
                return new ArrayList<>(messages);
            }
        }

        void disconnect() {
            if (conn != null) conn.disconnect();
            if (thread != null) thread.interrupt();
        }
    }
}
