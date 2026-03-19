"""
Проверяем правильную реализацию SSE MCP-транспорта.
По спеке MCP SSE (2024-11-05):
  - GET /sse  → text/event-stream, шлёт event:endpoint\ndata:/messages\n\n,
                потом держит соединение открытым
  - POST /messages → принимает JSON-RPC, возвращает 202, ответ шлёт ЧЕРЕЗ SSE-поток
  - ВСЕ server→client сообщения (ответы + нотификации) идут через SSE

Тест A: правильный SSE (ответы через SSE-поток)  → ожидаем "connected"
Тест B: упрощённый SSE (ответы через POST body)  → ожидаем или "connected" или "failed"
"""
import json, os, queue, sys, tempfile, threading, time
from http.server import BaseHTTPRequestHandler, HTTPServer
from socketserver import ThreadingMixIn

class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True

import pexpect

CLAUDE = '/usr/local/bin/claude'
CWD = '/tmp'
ENV = {k: v for k, v in os.environ.items()
       if k not in ('CLAUDECODE', 'CLAUDE_CODE_ENTRYPOINT')}


# ──────────────────────────────────────────────────────────────────────────────
# Test A: proper SSE — responses sent via SSE stream
# ──────────────────────────────────────────────────────────────────────────────

class ProperSseHandler(BaseHTTPRequestHandler):
    """SSE-транспорт по спеке: сервер→клиент только через SSE-поток."""

    def log_message(self, fmt, *args): pass

    def do_GET(self):
        if self.path != '/sse':
            self.send_response(404); self.end_headers(); return

        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.send_header('Cache-Control', 'no-cache')
        self.send_header('Connection', 'keep-alive')
        self.end_headers()

        # Сообщаем клиенту URL для POST-запросов
        self._write_sse('endpoint', '/messages')

        # Держим соединение и ждём сообщений от POST-обработчика через очередь
        q = self.server.sse_queue
        while True:
            try:
                msg = q.get(timeout=30)
                if msg is None:
                    break
                self._write_sse('message', msg)
            except Exception:
                break

    def _write_sse(self, event, data):
        try:
            line = f'event: {event}\ndata: {data}\n\n'
            self.wfile.write(line.encode())
            self.wfile.flush()
        except Exception:
            pass

    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length)

        self.send_response(202); self.end_headers()   # 202 Accepted, тело пусто

        try:
            msg = json.loads(body)
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
                return   # нотификация без ответа
            elif method == 'tools/list':
                resp = {'jsonrpc': '2.0', 'id': req_id,
                        'result': {'tools': []}}
            else:
                resp = {'jsonrpc': '2.0', 'id': req_id,
                        'error': {'code': -32601, 'message': 'Method not found'}}

            # Ответ идёт через SSE-поток, а не через тело POST
            self.server.sse_queue.put(json.dumps(resp))

        except Exception as e:
            print(f'  [server] POST error: {e}', flush=True)


class SseServer(HTTPServer):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.sse_queue = queue.Queue()


# ──────────────────────────────────────────────────────────────────────────────
# Test B: simplified SSE — responses in POST body (не по спеке)
# ──────────────────────────────────────────────────────────────────────────────

class SimpleSseHandler(BaseHTTPRequestHandler):
    """Упрощённый вариант: GET /sse держит соединение, POST отвечает в body."""

    def log_message(self, fmt, *args): pass

    def do_GET(self):
        if self.path != '/sse':
            self.send_response(404); self.end_headers(); return
        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.send_header('Cache-Control', 'no-cache')
        self.end_headers()
        try:
            self.wfile.write(b'event: endpoint\ndata: /messages\n\n')
            self.wfile.flush()
            time.sleep(60)
        except Exception:
            pass

    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length)
        try:
            msg = json.loads(body)
            method = msg.get('method', '')
            req_id = msg.get('id')

            if method == 'initialize':
                resp = {'jsonrpc': '2.0', 'id': req_id,
                        'result': {'protocolVersion': '2024-11-05',
                                   'capabilities': {'tools': {}},
                                   'serverInfo': {'name': 'netbeans', 'version': '1.0'}}}
            elif method == 'notifications/initialized':
                self.send_response(202); self.end_headers(); return
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
        except Exception as e:
            print(f'  [server B] error: {e}', flush=True)
            self.send_response(500); self.end_headers()


# ──────────────────────────────────────────────────────────────────────────────

def start_server(handler_class, server_class=HTTPServer):
    srv = server_class(('127.0.0.1', 0), handler_class)
    port = srv.server_address[1]
    t = threading.Thread(target=srv.serve_forever, daemon=True)
    t.start()
    return srv, port


def run_test(label, port):
    print(f'\n=== {label} (port={port}) ===', flush=True)
    cfg = json.dumps({'mcpServers': {'netbeans': {
        'type': 'sse', 'url': f'http://127.0.0.1:{port}/sse'}}})

    f = tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False)
    f.write(cfg); f.close()
    print(f'  config: {cfg}', flush=True)

    logfile = open(f'/tmp/pexpect_{label}.log', 'w')
    child = pexpect.spawn(CLAUDE, ['--mcp-config', f.name],
                          cwd=CWD, encoding='utf-8', timeout=40,
                          env=ENV, logfile=logfile)
    result = 'UNKNOWN'
    try:
        idx = child.expect([r'❯', pexpect.TIMEOUT, pexpect.EOF], timeout=25)
        if idx != 0:
            print('  FAIL: no Claude prompt', flush=True)
            return
        time.sleep(0.3)
        child.send('/mcp\r')

        # Ждём изменения статуса с connecting... (до 30 сек)
        idx = child.expect(['connected', r'✘', 'No MCP servers',
                            pexpect.TIMEOUT, pexpect.EOF], timeout=30)
        labels = ['PASS:connected', 'FAIL:failed(✘)', 'FAIL:no_servers',
                  'INFO:timeout(still_connecting?)', 'FAIL:eof']
        result = labels[idx]
        print(f'  Result: {result}', flush=True)
        if idx == 3:  # timeout — покажем что было
            child.expect([pexpect.TIMEOUT], timeout=1)
            print(f'  Last output snippet: {repr(child.before[-200:])}', flush=True)

    except Exception as e:
        print(f'  ERROR: {e}', flush=True)
    finally:
        child.close(force=True)
        logfile.close()
        os.unlink(f.name)
    return result


# ──────────────────────────────────────────────────────────────────────────────

class ThreadingSseServer(ThreadingMixIn, SseServer):
    daemon_threads = True

print('Starting Test A server (proper SSE — responses via SSE stream)...')
srv_a, port_a = start_server(ProperSseHandler, ThreadingSseServer)
time.sleep(0.1)

print('Starting Test B server (simplified SSE — responses in POST body)...')
srv_b, port_b = start_server(SimpleSseHandler, ThreadingHTTPServer)
time.sleep(0.1)

run_test('A_proper_sse', port_a)
run_test('B_simple_sse', port_b)

srv_a.shutdown()
srv_b.shutdown()
print('\nDone.')
