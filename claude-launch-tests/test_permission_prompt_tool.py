"""
Проверяем, что permissionPromptTool работает через --mcp-config или settings.local.json.

PASS если сервер получил tools/call с name=permission_prompt.
FAIL если Claude показал PTY-диалог (Do you want to make this edit...).

Примечание: в ~/.claude/settings.json может быть "defaultMode": "plan".
Все варианты запускаются с --permission-mode default чтобы обойти это.

Run: python3 test_permission_prompt_tool.py
"""
import json
import os
import queue
import shutil
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from socketserver import ThreadingMixIn

import pexpect

CLAUDE = '/usr/local/bin/claude'
ENV = {k: v for k, v in os.environ.items()
       if k not in ('CLAUDECODE', 'CLAUDE_CODE_ENTRYPOINT')}

PERMISSION_PROMPT_TOOL = {
    'name': 'permission_prompt',
    'description': 'Permission prompt tool',
    'inputSchema': {
        'type': 'object',
        'properties': {
            'tool_name': {'type': 'string'},
            'tool_input': {'type': 'object'},
        },
        'required': ['tool_name', 'tool_input'],
    },
}


class PermissionSseHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args): pass

    def do_GET(self):
        if self.path != '/sse':
            self.send_response(404); self.end_headers(); return
        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.send_header('Cache-Control', 'no-cache')
        self.send_header('Connection', 'keep-alive')
        self.end_headers()
        self._sse('endpoint', '/messages')
        q = self.server.sse_queue
        while True:
            try:
                msg = q.get(timeout=60)
                if msg is None: break
                self._sse('message', msg)
            except Exception: break

    def _sse(self, event, data):
        try:
            self.wfile.write(f'event: {event}\ndata: {data}\n\n'.encode())
            self.wfile.flush()
        except Exception: pass

    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length)
        self.send_response(202); self.end_headers()
        try:
            msg = json.loads(body)
            method = msg.get('method', '')
            req_id = msg.get('id')
            params = msg.get('params', {})
            print(f'  [srv] {method}', flush=True)
            if method == 'initialize':
                resp = {'jsonrpc': '2.0', 'id': req_id, 'result': {
                    'protocolVersion': '2024-11-05',
                    'capabilities': {'tools': {}},
                    'serverInfo': {'name': 'netbeans', 'version': '1.0'},
                }}
            elif method == 'notifications/initialized':
                return
            elif method == 'tools/list':
                resp = {'jsonrpc': '2.0', 'id': req_id,
                        'result': {'tools': [PERMISSION_PROMPT_TOOL]}}
            elif method == 'tools/call':
                name = params.get('name', '')
                print(f'  [srv] tools/call name={name} args={params.get("arguments")}', flush=True)
                if name == 'permission_prompt':
                    self.server.permission_called.set()
                resp = {'jsonrpc': '2.0', 'id': req_id,
                        'result': {'content': [{'type': 'text', 'text': 'allow'}]}}
            else:
                resp = {'jsonrpc': '2.0', 'id': req_id,
                        'error': {'code': -32601, 'message': 'Method not found'}}
            self.server.sse_queue.put(json.dumps(resp))
        except Exception as e:
            print(f'  [srv] error: {e}', flush=True)


class PermissionSseServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.sse_queue = queue.Queue()
        self.permission_called = threading.Event()


def run_test(label, extra_args, write_settings_local=False):
    """
    extra_args          — дополнительные CLI-аргументы для claude
    write_settings_local — писать .claude/settings.local.json в CWD
    """
    print(f'\n=== {label} ===', flush=True)
    srv = PermissionSseServer(('127.0.0.1', 0), PermissionSseHandler)
    port = srv.server_address[1]
    threading.Thread(target=srv.serve_forever, daemon=True).start()

    workdir = tempfile.mkdtemp(prefix='ppt_wd_')
    open(os.path.join(workdir, 'target.txt'), 'w').write('hello world\n')

    settings = {
        'mcpServers': {'netbeans': {'type': 'sse', 'url': f'http://127.0.0.1:{port}/sse'}},
        'permissionPromptTool': 'mcp__netbeans__permission_prompt',
    }
    print(f'  settings={json.dumps(settings)}', flush=True)

    if write_settings_local:
        d = os.path.join(workdir, '.claude')
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, 'settings.local.json'), 'w') as f:
            json.dump(settings, f)
        print(f'  wrote .claude/settings.local.json', flush=True)

    # Resolve placeholders in extra_args
    resolved = []
    for a in extra_args:
        if a == '__CFG__':
            f = tempfile.NamedTemporaryFile(mode='w', suffix='.json', prefix='ppt_cfg_', delete=False)
            json.dump(settings, f); f.close()
            resolved.append(f.name)
            print(f'  config file={f.name}', flush=True)
        else:
            resolved.append(a)

    # Always add --permission-mode default to bypass ~/.claude/settings.json defaultMode=plan
    args = ['--permission-mode', 'default'] + resolved
    print(f'  args={args}', flush=True)

    logfile = open(f'/tmp/pexpect_ppt_{label}.log', 'w')
    child = pexpect.spawn(CLAUDE, args, cwd=workdir, encoding='utf-8',
                          timeout=70, env=ENV, logfile=logfile)
    result = 'UNKNOWN'
    try:
        idx = child.expect([r'❯', pexpect.TIMEOUT, pexpect.EOF], timeout=30)
        if idx != 0:
            result = 'FAIL:no_prompt'; return result

        print('  Claude ready, sending edit request...', flush=True)
        time.sleep(0.5)
        child.send('Please edit target.txt: replace "hello" with "goodbye". Use the Edit tool.\r')

        deadline = time.time() + 50
        while time.time() < deadline:
            if srv.permission_called.wait(timeout=0.3):
                result = 'PASS:permission_prompt_called'; break
            try:
                idx2 = child.expect(
                    ['Do you want to make this edit',
                     'Ready to code',
                     pexpect.TIMEOUT],
                    timeout=0.3)
                if idx2 == 0:
                    result = 'FAIL:pty_dialog'; break
                elif idx2 == 1:
                    # Shouldn't happen with --permission-mode default, but handle it
                    print('  [dialog] Ready to code → 3', flush=True)
                    child.send('3')
            except pexpect.EOF:
                result = 'FAIL:eof'; break
        else:
            result = 'FAIL:timeout'
    except Exception as e:
        result = f'ERROR:{e}'
    finally:
        try:
            child.close(force=True)
        except Exception:
            pass
        logfile.close()
        srv.shutdown()
        shutil.rmtree(workdir, ignore_errors=True)

    print(f'  => {result}   log=/tmp/pexpect_ppt_{label}.log', flush=True)
    return result


print('=== test_permission_prompt_tool.py ===\n')
print('Note: all tests use --permission-mode default to bypass ~/.claude/settings.json plan mode\n')

# A: --mcp-config только (текущий подход плагина)
r_a = run_test('A_mcp_config',
               ['--mcp-config', '__CFG__'])

# B: --settings <file>
r_b = run_test('B_settings_flag',
               ['--settings', '__CFG__'])

# C: .claude/settings.local.json в CWD, без доп. флагов
r_c = run_test('C_settings_local_json',
               [],
               write_settings_local=True)

# D: --mcp-config + --settings (separate files, combined)
r_d = run_test('D_mcp_config_plus_settings',
               ['--mcp-config', '__CFG__', '--settings', '__CFG__'])

print('\n=== Summary ===')
print(f'A --mcp-config:                  {r_a}')
print(f'B --settings <file>:             {r_b}')
print(f'D --mcp-config + --settings:     {r_d}')
print(f'C settings.local.json:   {r_c}')
