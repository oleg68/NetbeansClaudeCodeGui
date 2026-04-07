"""
Test which location Claude Code reads mcpServers from.

Hypotheses:
  A: --mcp-config CLI arg          → Claude sees MCP server (expected: CONNECTED)
  B: .mcp.json in project dir      → Claude sees MCP server (expected: CONNECTED)
  C: .claude/settings.local.json   → Claude sees MCP server (expected: NOT CONNECTED in 2.x)

Run: python3 test_mcp_settings_location.py
"""
import json
import os
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import pexpect

CLAUDE = '/usr/local/bin/claude'
ENV = {k: v for k, v in os.environ.items()
       if k not in ('CLAUDECODE', 'CLAUDE_CODE_ENTRYPOINT')}


# ---------------------------------------------------------------------------
# Minimal SSE MCP server
# ---------------------------------------------------------------------------

class MinimalSSEHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def do_GET(self):
        if self.path == '/sse':
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('Connection', 'keep-alive')
            self.end_headers()
            try:
                self.wfile.write(b'event: endpoint\ndata: /messages\n\n')
                self.wfile.flush()
                time.sleep(60)
            except (BrokenPipeError, OSError):
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
                'jsonrpc': '2.0', 'id': req_id,
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
            resp = {'jsonrpc': '2.0', 'id': req_id, 'result': {'tools': []}}
        else:
            resp = {'jsonrpc': '2.0', 'id': req_id,
                    'error': {'code': -32601, 'message': 'Method not found'}}

        data = json.dumps(resp).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def start_sse_server():
    server = HTTPServer(('127.0.0.1', 0), MinimalSSEHandler)
    port = server.server_address[1]
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, port


# ---------------------------------------------------------------------------
# Test runner
# ---------------------------------------------------------------------------

def run_test(label, cwd, extra_args=None):
    """Launch claude in cwd, send /mcp, check if 'netbeans' is connected."""
    print(f'\n=== {label} ===', flush=True)
    args = extra_args or []
    safe_label = label.split('(')[0].strip().replace(' ', '_')
    logfile = open(f'/tmp/pexpect_mcp_loc_{safe_label}.log', 'w')
    child = pexpect.spawn(
        CLAUDE, args,
        cwd=cwd, encoding='utf-8', timeout=40,
        env=ENV, logfile=logfile
    )
    result = 'UNKNOWN'
    try:
        idx = child.expect([r'❯', pexpect.TIMEOUT, pexpect.EOF], timeout=25)
        if idx != 0:
            print(f'  FAIL: Claude did not show prompt', flush=True)
            result = 'NO_PROMPT'
            return result

        time.sleep(0.3)
        child.send('/mcp\r')

        idx = child.expect(['netbeans', 'No MCP servers', pexpect.TIMEOUT, pexpect.EOF], timeout=15)
        if idx == 0:
            time.sleep(12)
            child.expect([pexpect.TIMEOUT], timeout=2)
            output = child.before or ''
            print(f'  "netbeans" found. Tail: {repr(output[:300])}', flush=True)
            if 'connected' in output.lower():
                print(f'  RESULT: CONNECTED', flush=True)
                result = 'CONNECTED'
            elif 'connecting' in output.lower():
                # Server is discovered (config read) but our minimal test server
                # doesn't respond via SSE stream — that's expected for this test
                print(f'  RESULT: DISCOVERED (connecting... — config is read)', flush=True)
                result = 'DISCOVERED'
            elif 'error' in output.lower() or 'failed' in output.lower():
                print(f'  RESULT: ERROR/FAILED', flush=True)
                result = 'ERROR'
            else:
                print(f'  RESULT: FOUND_BUT_UNKNOWN_STATUS', flush=True)
                result = 'UNKNOWN_STATUS'
        elif idx == 1:
            print(f'  RESULT: NOT_CONFIGURED (No MCP servers)', flush=True)
            result = 'NOT_CONFIGURED'
        else:
            print(f'  RESULT: TIMEOUT', flush=True)
            result = 'TIMEOUT'
    except Exception as e:
        print(f'  ERROR: {e}', flush=True)
        result = f'EXCEPTION: {e}'
    finally:
        child.close(force=True)
        logfile.close()
    return result


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

print('Starting SSE MCP server...', flush=True)
sse_server, port = start_sse_server()
mcp_cfg = {'mcpServers': {'netbeans': {'type': 'sse', 'url': f'http://127.0.0.1:{port}/sse'}}}
print(f'SSE server on port {port}', flush=True)
time.sleep(0.2)

results = {}

# --- Test A: --mcp-config CLI arg (JSON string) ---
with tempfile.TemporaryDirectory() as d:
    results['A_cli_arg'] = run_test(
        'A_cli_arg (--mcp-config JSON string)',
        cwd=d,
        extra_args=['--mcp-config', json.dumps(mcp_cfg)]
    )

# --- Test B: .mcp.json in project root ---
with tempfile.TemporaryDirectory() as d:
    Path(d, '.mcp.json').write_text(json.dumps(mcp_cfg))
    results['B_mcp_json'] = run_test(
        'B_mcp_json (.mcp.json in project root)',
        cwd=d
    )

# --- Test C: .claude/settings.local.json ---
with tempfile.TemporaryDirectory() as d:
    claude_dir = Path(d, '.claude')
    claude_dir.mkdir()
    (claude_dir / 'settings.local.json').write_text(json.dumps(mcp_cfg))
    results['C_settings_local'] = run_test(
        'C_settings_local (.claude/settings.local.json)',
        cwd=d
    )

sse_server.shutdown()

print('\n' + '='*50, flush=True)
print('SUMMARY:', flush=True)
for k, v in results.items():
    print(f'  {k}: {v}', flush=True)
