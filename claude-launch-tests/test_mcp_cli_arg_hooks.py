#!/usr/bin/env python3
"""Verify that hooks from settings.local.json still fire when Claude is launched with --mcp-config.

Question: does passing --mcp-config to claude disable hooks configured in
.claude/settings.local.json?

Expected result: NO — hooks fire regardless of --mcp-config.

Test procedure:
1. Start a minimal HTTP server to receive hook calls.
2. Write a Stop hook into .claude/settings.local.json of a temp workdir.
3. Launch: claude --mcp-config <json> --dangerously-skip-permissions
4. Wait for prompt, send /exit.
5. Wait for Stop hook HTTP request.
6. Report PASS if hook fired, FAIL otherwise.
"""
import json
import os
import pathlib
import pty
import select
import signal
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

CLAUDE = '/usr/local/bin/claude'

ENV = {k: v for k, v in os.environ.items()
       if k not in ('CLAUDECODE', 'CLAUDE_CODE_ENTRYPOINT')}
ENV['TERM'] = 'xterm-256color'

# ---------------------------------------------------------------------------
# HTTP server that receives hook POST requests
# ---------------------------------------------------------------------------

hook_received = threading.Event()
hook_payload = {}


class HookHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length)
        try:
            hook_payload.update(json.loads(body))
        except Exception:
            pass
        hook_received.set()
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'{}')

    def log_message(self, *args):
        pass


hook_server = HTTPServer(('127.0.0.1', 0), HookHandler)
hook_port = hook_server.server_address[1]
threading.Thread(target=hook_server.serve_forever, daemon=True).start()
print(f'Hook server listening on port {hook_port}', flush=True)

# ---------------------------------------------------------------------------
# Prepare temp workdir with settings.local.json containing Stop hook
# ---------------------------------------------------------------------------

workdir = pathlib.Path(tempfile.mkdtemp(prefix='nb-hook-test-'))
claude_dir = workdir / '.claude'
claude_dir.mkdir()

settings = {
    'hooks': {
        'Stop': [{
            'matcher': '.*',
            'hooks': [{'type': 'http', 'url': f'http://127.0.0.1:{hook_port}/stop'}]
        }]
    }
}
(claude_dir / 'settings.local.json').write_text(json.dumps(settings, indent=2))
print(f'Workdir: {workdir}', flush=True)
print(f'settings.local.json written with Stop hook → port {hook_port}', flush=True)

# ---------------------------------------------------------------------------
# MCP config passed via --mcp-config (minimal, no real server needed)
# ---------------------------------------------------------------------------

mcp_config = json.dumps({
    'mcpServers': {
        'netbeans': {'type': 'sse', 'url': 'http://127.0.0.1:1/sse'}  # unreachable — that's fine
    }
})

mcp_cfg_file = tempfile.NamedTemporaryFile(mode='w', suffix='.json',
                                            prefix='nb-mcp-cfg-', delete=False)
mcp_cfg_file.write(mcp_config)
mcp_cfg_file.close()
print(f'--mcp-config file: {mcp_cfg_file.name}', flush=True)

# ---------------------------------------------------------------------------
# Launch claude with --mcp-config
# ---------------------------------------------------------------------------

master_fd, slave_fd = pty.openpty()
proc = subprocess.Popen(
    [CLAUDE, '--mcp-config', mcp_cfg_file.name, '--dangerously-skip-permissions'],
    cwd=str(workdir),
    stdin=slave_fd, stdout=slave_fd, stderr=slave_fd,
    env=ENV,
    preexec_fn=os.setsid,
    close_fds=True,
)
os.close(slave_fd)
print(f'Claude started (pid={proc.pid})', flush=True)


def reader():
    while True:
        try:
            r, _, _ = select.select([master_fd], [], [], 0.5)
            if r:
                data = os.read(master_fd, 4096)
                print(data.decode('utf-8', errors='replace'), end='', flush=True)
        except OSError:
            break


threading.Thread(target=reader, daemon=True).start()

# ---------------------------------------------------------------------------
# Wait for prompt, accept bypass warning if shown, then /exit
# ---------------------------------------------------------------------------

print('\n--- Waiting for Claude to be ready ---', flush=True)
time.sleep(6)

# Accept the "bypass permissions" warning (press 2 if shown)
os.write(master_fd, b'2')
time.sleep(3)

print('\n--- Sending /exit ---', flush=True)
os.write(master_fd, b'/exit\r')

# ---------------------------------------------------------------------------
# Wait for Stop hook (up to 15 s)
# ---------------------------------------------------------------------------

print('\n--- Waiting for Stop hook ---', flush=True)
fired = hook_received.wait(timeout=15)

# ---------------------------------------------------------------------------
# Cleanup
# ---------------------------------------------------------------------------

try:
    proc.send_signal(signal.SIGTERM)
    proc.wait(timeout=3)
except Exception:
    proc.kill()

hook_server.shutdown()
os.unlink(mcp_cfg_file.name)
import shutil; shutil.rmtree(workdir, ignore_errors=True)

# ---------------------------------------------------------------------------
# Result
# ---------------------------------------------------------------------------

print('\n' + '=' * 60, flush=True)
if fired:
    print('PASS: Stop hook fired while running with --mcp-config', flush=True)
    print(f'      hook_event_name={hook_payload.get("hook_event_name")}', flush=True)
else:
    print('FAIL: Stop hook did NOT fire within 15 s', flush=True)
    print('      Conclusion: --mcp-config may suppress hooks from settings.local.json', flush=True)
print('=' * 60, flush=True)
