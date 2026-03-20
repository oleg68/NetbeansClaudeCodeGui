#!/usr/bin/env python3
"""
Stage 11 verification: PreToolUse HTTP hook intercepts Edit/Write/MultiEdit.

Tests that when .claude/settings.local.json registers a PreToolUse hook,
Claude Code calls it instead of showing the PTY permission dialog.

Usage:
    cd claude-launch-tests && python3 test_hook_pretooluse.py [--variant A|B]

Variants:
    A (default) – only hooks in settings.local.json, no mcpServers
    B           – hooks + mcpServers (full plugin config)

Exit codes:
    0 – PASS:hook_called
    1 – FAIL:*
"""

import argparse
import http.server
import json
import os
import socket
import subprocess
import sys
import tempfile
import threading
import time

# ── CLI args ──────────────────────────────────────────────────────────────────

parser = argparse.ArgumentParser()
parser.add_argument("--variant", choices=["A", "B"], default="A",
                    help="A = hooks only, B = hooks + mcpServers")
parser.add_argument("--timeout", type=int, default=60,
                    help="seconds to wait for hook call")
args = parser.parse_args()

# ── helpers ───────────────────────────────────────────────────────────────────

def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


HOOK_PORT = free_port()
MCP_PORT  = free_port()  # used only in variant B

hook_received = threading.Event()
hook_body: dict = {}


# ── HTTP hook server ──────────────────────────────────────────────────────────

class HookHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass  # suppress default access log

    def do_POST(self):
        global hook_body
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length)
        try:
            hook_body = json.loads(raw)
        except Exception:
            hook_body = {"raw": raw.decode(errors="replace")}

        print(f"[hook] received POST: {json.dumps(hook_body, indent=2)}")
        hook_received.set()

        # Deny the operation so Claude doesn't actually write to disk
        response = json.dumps({
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": "Blocked by test_hook_pretooluse.py"
            }
        }).encode()

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)


hook_server = http.server.HTTPServer(("127.0.0.1", HOOK_PORT), HookHandler)
hook_thread = threading.Thread(target=hook_server.serve_forever, daemon=True)
hook_thread.start()
print(f"[test] hook server listening on port {HOOK_PORT}")


# ── work directory setup ──────────────────────────────────────────────────────

workdir = tempfile.mkdtemp(prefix="hook_test_")
target  = os.path.join(workdir, "target.txt")
with open(target, "w") as f:
    f.write("hello world\n")

claude_dir = os.path.join(workdir, ".claude")
os.makedirs(claude_dir, exist_ok=True)

settings: dict = {
    "hooks": {
        "PreToolUse": [
            {
                "matcher": "Edit|Write|MultiEdit",
                "hooks": [
                    {
                        "type": "http",
                        "url": f"http://127.0.0.1:{HOOK_PORT}/hook"
                    }
                ]
            }
        ]
    }
}
if args.variant == "B":
    settings["mcpServers"] = {
        "netbeans": {
            "type": "sse",
            "url": f"http://127.0.0.1:{MCP_PORT}/sse"
        }
    }

settings_path = os.path.join(claude_dir, "settings.local.json")
with open(settings_path, "w") as f:
    json.dump(settings, f, indent=2)

print(f"[test] workdir: {workdir}")
print(f"[test] settings.local.json written (variant {args.variant})")


# ── spawn Claude ──────────────────────────────────────────────────────────────

prompt = (
    "Please edit target.txt and replace 'hello' with 'goodbye'. "
    "Just do it, no explanation needed."
)

cmd = ["claude", "--permission-mode", "default", "-p", prompt]
print(f"[test] running: {' '.join(cmd)}")

proc = subprocess.Popen(
    cmd,
    cwd=workdir,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
)


# ── wait for result ───────────────────────────────────────────────────────────

def drain(stream, label):
    for line in stream:
        print(f"[{label}] {line}", end="")

stdout_thread = threading.Thread(target=drain, args=(proc.stdout, "claude"), daemon=True)
stderr_thread = threading.Thread(target=drain, args=(proc.stderr, "claude-err"), daemon=True)
stdout_thread.start()
stderr_thread.start()

got_hook = hook_received.wait(timeout=args.timeout)

# Give Claude a moment to finish, then kill it
time.sleep(2)
if proc.poll() is None:
    proc.terminate()
    try:
        proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        proc.kill()

stdout_thread.join(timeout=2)
stderr_thread.join(timeout=2)
hook_server.shutdown()

# ── verdict ───────────────────────────────────────────────────────────────────

if got_hook:
    tool = hook_body.get("tool_name", "?")
    print(f"\nRESULT: PASS:hook_called  tool={tool}")
    sys.exit(0)
else:
    print(f"\nRESULT: FAIL:timeout  (hook not called within {args.timeout}s)")
    sys.exit(1)
