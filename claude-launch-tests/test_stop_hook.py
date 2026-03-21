#!/usr/bin/env python3
"""Verify that Claude Code Stop hook fires when Claude finishes a task.

Findings:
- Stop hook fires immediately after Claude completes its response turn
- Payload includes: session_id, cwd, hook_event_name="Stop", stop_hook_active, last_assistant_message
- Works for both plain responses AND numbered list responses (can detect menu via last_assistant_message)
- Does NOT fire for Ink TUI slash-command menus (/model, /effort, etc.)
"""
import json, threading, subprocess, pathlib, time, pty, os, select, signal
from http.server import HTTPServer, BaseHTTPRequestHandler

received = []

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        data = json.loads(body)
        received.append(data)
        print(f"\n[STOP] last_assistant_message:\n{data.get('last_assistant_message','')}\n", flush=True)
        self.send_response(200); self.end_headers(); self.wfile.write(b'{}')
    def log_message(self, *a): pass

srv = HTTPServer(('localhost', 0), Handler)
port = srv.server_address[1]
threading.Thread(target=srv.serve_forever, daemon=True).start()

work = pathlib.Path('/home/oleg/my-projects/NetbeansClaudeCodePlugin')
local_json = work / '.claude' / 'settings.local.json'
existing = json.loads(local_json.read_text()) if local_json.exists() else {}
existing.setdefault('hooks', {})
existing['hooks']['Stop'] = [{"matcher": ".*", "hooks": [{"type": "http", "url": f"http://localhost:{port}/stop"}]}]
local_json.write_text(json.dumps(existing, indent=2))

print(f"Server :{port}, workdir {work}", flush=True)

master_fd, slave_fd = pty.openpty()
env = os.environ.copy(); env['TERM'] = 'xterm-256color'
proc = subprocess.Popen(['claude', '--dangerously-skip-permissions'],
    cwd=str(work), stdin=slave_fd, stdout=slave_fd, stderr=slave_fd,
    env=env, preexec_fn=os.setsid, close_fds=True)
os.close(slave_fd)

def reader():
    while True:
        try:
            r, _, _ = select.select([master_fd], [], [], 0.5)
            if r: print(os.read(master_fd, 4096).decode('utf-8', errors='replace'), end='', flush=True)
        except OSError: break
threading.Thread(target=reader, daemon=True).start()

time.sleep(5)
os.write(master_fd, b"2")  # accept bypass warning
time.sleep(3)

print("\n--- prompt 1: say hello ---", flush=True)
os.write(master_fd, b"say hello\r")
time.sleep(10)

print("\n--- prompt 2: numbered list ---", flush=True)
os.write(master_fd, b"give me options: 1. apple 2. banana 3. cherry, ask me to pick\r")
time.sleep(10)

proc.send_signal(signal.SIGTERM)
time.sleep(1); srv.shutdown()

existing['hooks'].pop('Stop', None)
if not existing['hooks']: del existing['hooks']
local_json.write_text(json.dumps(existing, indent=2))

print(f"\nTotal Stop hooks: {len(received)}")
