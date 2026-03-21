#!/usr/bin/env python3
"""Verify that Claude Code Notification hook fires for numbered menu prompts."""
import json, threading, subprocess, tempfile, pathlib
from http.server import HTTPServer, BaseHTTPRequestHandler

received = []

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        received.append(json.loads(body))
        self.send_response(200); self.end_headers(); self.wfile.write(b'{}')
    def log_message(self, *a): pass

srv = HTTPServer(('localhost', 0), Handler)
port = srv.server_address[1]
threading.Thread(target=srv.serve_forever, daemon=True).start()

work = pathlib.Path(tempfile.mkdtemp())
(work / '.claude').mkdir()
(work / '.claude' / 'settings.json').write_text(json.dumps({
    "hooks": {"Notification": [{"matcher": "", "hooks": [
        {"type": "http", "url": f"http://localhost:{port}/notification"}
    ]}]}
}))

print(f"Server :{port}, workdir {work}")
subprocess.run(['claude', '--print', 'list files in current dir'],
               timeout=30, cwd=str(work))
srv.shutdown()
print("Received:", json.dumps(received, indent=2))
