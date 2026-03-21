#!/usr/bin/env python3
"""Verify that Claude Code PermissionRequest hook fires before Ink permission dialogs.

Findings:
- PermissionRequest fires before Claude shows native PTY permission dialog (e.g. for Bash)
- Payload: session_id, cwd, hook_event_name="PermissionRequest", tool_name, tool_input, permission_suggestions
- Matcher is tool_name (use ".*" to match all)
- Does NOT fire for slash-command menus (/model, /effort, etc.)
- Only fires in normal permission mode (not bypassPermissions)

Usage: Run without --dangerously-skip-permissions to trigger native permission dialogs.
"""
import json, threading, subprocess, pathlib, time, pty, os, select, signal
from http.server import HTTPServer, BaseHTTPRequestHandler

received = []

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        path = self.path
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        data = json.loads(body)
        received.append({'path': path, 'body': data})
        print(f"\n[{path}] tool={data.get('tool_name','')} event={data.get('hook_event_name','')}", flush=True)
        print(f"  full: {json.dumps(data)[:300]}", flush=True)
        self.send_response(200); self.end_headers(); self.wfile.write(b'{}')
    def log_message(self, *a): pass

srv = HTTPServer(('localhost', 0), Handler)
port = srv.server_address[1]
threading.Thread(target=srv.serve_forever, daemon=True).start()

work = pathlib.Path('/home/oleg/my-projects/NetbeansClaudeCodePlugin')
local_json = work / '.claude' / 'settings.local.json'
existing = json.loads(local_json.read_text()) if local_json.exists() else {}
existing.setdefault('hooks', {})
url = lambda p: f"http://localhost:{port}/{p}"
existing['hooks']['PermissionRequest'] = [{"matcher": ".*", "hooks": [{"type": "http", "url": url("permission-request")}]}]
existing['hooks']['Stop']              = [{"matcher": ".*", "hooks": [{"type": "http", "url": url("stop")}]}]
local_json.write_text(json.dumps(existing, indent=2))

print(f"Server :{port}, workdir {work}", flush=True)
print("NOTE: Run without bypass permissions to see PermissionRequest for Bash", flush=True)

master_fd, slave_fd = pty.openpty()
env = os.environ.copy(); env['TERM'] = 'xterm-256color'
# No --dangerously-skip-permissions so Bash triggers permission dialog
proc = subprocess.Popen(['claude'],
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

time.sleep(6)
print("\n--- asking Claude to run ls ---", flush=True)
os.write(master_fd, b"run ls in current directory\r")
time.sleep(15)

proc.send_signal(signal.SIGTERM)
time.sleep(1); srv.shutdown()

existing['hooks'].pop('PermissionRequest', None)
existing['hooks'].pop('Stop', None)
if not existing['hooks']: del existing['hooks']
local_json.write_text(json.dumps(existing, indent=2))

print(f"\nTotal hooks: {len(received)}")
for r in received:
    print(f"  {r['path']}: {r['body'].get('hook_event_name')} / tool={r['body'].get('tool_name','')}")
