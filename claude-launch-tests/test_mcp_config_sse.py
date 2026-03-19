"""
TC-03 hypothesis test: does Claude Code see a netbeans MCP server
when launched with --mcp-config pointing to a minimal SSE endpoint?

Tests two hypotheses:
  A: SSE transport with a real SSE endpoint  → Claude should show "netbeans" in /mcp
  B: WebSocket server, SSE config (current)  → Claude should show "not connected" or error

Run: python3 test_mcp_config_sse.py
"""
import json
import os
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

import pexpect

CLAUDE = '/usr/local/bin/claude'
CWD = '/tmp'

# Strip CLAUDECODE env to avoid "nested session" error
ENV = {k: v for k, v in os.environ.items()
       if k not in ('CLAUDECODE', 'CLAUDE_CODE_ENTRYPOINT')}


# ---------------------------------------------------------------------------
# Minimal SSE MCP server (responds to GET /sse with SSE stream, handles POST)
# ---------------------------------------------------------------------------

class MinimalSSEHandler(BaseHTTPRequestHandler):
    """
    Minimal MCP SSE server that responds to the initialize handshake.
    Enough for Claude to consider the server 'connected'.
    """

    def log_message(self, fmt, *args):
        pass  # silence access log

    def do_GET(self):
        if self.path == '/sse':
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('Connection', 'keep-alive')
            self.end_headers()
            # Send the endpoint event that MCP SSE protocol requires
            endpoint_msg = 'event: endpoint\ndata: /messages\n\n'
            try:
                self.wfile.write(endpoint_msg.encode())
                self.wfile.flush()
                # Keep connection alive for a while
                time.sleep(30)
            except BrokenPipeError:
                pass
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length)
        try:
            msg = json.loads(body)
        except Exception:
            self.send_response(400)
            self.end_headers()
            return

        method = msg.get('method', '')
        req_id = msg.get('id')

        if method == 'initialize':
            resp = {
                'jsonrpc': '2.0',
                'id': req_id,
                'result': {
                    'protocolVersion': '2024-11-05',
                    'capabilities': {'tools': {}},
                    'serverInfo': {'name': 'netbeans', 'version': '1.0'}
                }
            }
        elif method == 'notifications/initialized':
            self.send_response(202)
            self.end_headers()
            return
        elif method == 'tools/list':
            resp = {
                'jsonrpc': '2.0',
                'id': req_id,
                'result': {'tools': []}
            }
        else:
            resp = {
                'jsonrpc': '2.0',
                'id': req_id,
                'error': {'code': -32601, 'message': 'Method not found'}
            }

        data = json.dumps(resp).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def start_sse_server():
    """Start SSE server on a random port, return (server, port)."""
    server = HTTPServer(('127.0.0.1', 0), MinimalSSEHandler)
    port = server.server_address[1]
    t = threading.Thread(target=server.serve_forever, daemon=True)
    t.start()
    return server, port


# ---------------------------------------------------------------------------
# Test runner
# ---------------------------------------------------------------------------

def run_test(label, mcp_config_json):
    print(f'\n=== {label} ===', flush=True)

    # Write config to temp file
    cfg_file = tempfile.NamedTemporaryFile(mode='w', suffix='.json',
                                           prefix='nb-mcp-test-', delete=False)
    cfg_file.write(mcp_config_json)
    cfg_file.close()
    print(f'  Config: {cfg_file.name}', flush=True)
    print(f'  JSON:   {mcp_config_json}', flush=True)

    logfile = open(f'/tmp/pexpect_{label}.log', 'w')
    child = pexpect.spawn(
        CLAUDE, ['--mcp-config', cfg_file.name],
        cwd=CWD, encoding='utf-8', timeout=40,
        env=ENV, logfile=logfile
    )

    try:
        # Wait for Claude prompt
        idx = child.expect([r'❯', pexpect.TIMEOUT, pexpect.EOF], timeout=25)
        if idx != 0:
            print(f'  FAIL: Claude did not show prompt', flush=True)
            return

        time.sleep(0.5)
        print(f'  Claude ready, sending /mcp', flush=True)
        child.send('/mcp\r')

        # Look for netbeans in output, then check status
        idx = child.expect(['netbeans', 'No MCP servers', pexpect.TIMEOUT, pexpect.EOF], timeout=15)
        if idx == 0:
            # Seen "netbeans", now collect a bit more output to see status
            time.sleep(8)
            child.expect([pexpect.TIMEOUT], timeout=3)
            output = child.before
            print(f'  "netbeans" found. Output after: {repr(output[:500])}', flush=True)
            if 'connected' in output.lower():
                print(f'  PASS: server connected', flush=True)
            elif 'error' in output.lower() or 'failed' in output.lower() or 'disconnect' in output.lower():
                print(f'  FAIL: server error/disconnected', flush=True)
            else:
                print(f'  UNCERTAIN: status unclear', flush=True)
        elif idx == 1:
            print(f'  FAIL: "No MCP servers configured"', flush=True)
        else:
            print(f'  FAIL: Timeout / no output', flush=True)
            print(f'  Last: {repr(child.before[-300:])}', flush=True)

    except Exception as e:
        print(f'  ERROR: {e}', flush=True)
    finally:
        child.close(force=True)
        logfile.close()
        os.unlink(cfg_file.name)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

print('Starting minimal SSE MCP server...', flush=True)
sse_server, sse_port = start_sse_server()
print(f'SSE server on port {sse_port}', flush=True)
time.sleep(0.2)

# Test A: correct SSE endpoint
run_test(
    'A_sse_correct',
    json.dumps({'mcpServers': {'netbeans': {'type': 'sse', 'url': f'http://127.0.0.1:{sse_port}/sse'}}})
)

# Test B: SSE config but wrong path (simulates current plugin bug: /sse on WS server)
run_test(
    'B_sse_wrong_path',
    json.dumps({'mcpServers': {'netbeans': {'type': 'sse', 'url': f'http://127.0.0.1:{sse_port}/wrong'}}})
)

sse_server.shutdown()
print('\nDone.', flush=True)
