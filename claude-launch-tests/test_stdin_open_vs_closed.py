"""
Hypothesis: claude --print hangs when stdin is kept open (no EOF).

Result: CONFIRMED.
- stdin open, never closed → TIMEOUT (hung), no output at all
- stdin closed immediately (EOF) → 4 lines of stream-json output, works fine
- stdin closed after short delay (0.3s) → also works fine

Conclusion: Java ProcessBuilder must close stdin (send EOF) after launching
claude --print. The prompt is passed as a CLI argument, not via stdin.
Interactive stdin responses require PTY (Stage 11).
"""

import subprocess, os, threading, time

CLAUDE = '/usr/local/bin/claude'
CWD = '/home/oleg/my-projects/grandorgue/GrandOrgue'
ENV = {k: v for k, v in os.environ.items()
       if k not in ('CLAUDECODE', 'CLAUDE_CODE_ENTRYPOINT')}
CMD = [CLAUDE, '--print', 'список из трёх фруктов',
       '--output-format', 'stream-json', '--verbose',
       '--dangerously-skip-permissions']


def run(label, close_stdin_after):
    """
    close_stdin_after: None = never close; float = close after N seconds
    """
    print(f'\n=== {label} ===')
    p = subprocess.Popen(CMD, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE, env=ENV, cwd=CWD)
    lines = []

    def read():
        for line in p.stdout:
            lines.append(line.decode('utf-8', errors='replace').rstrip())

    t = threading.Thread(target=read, daemon=True)
    t.start()

    if close_stdin_after is not None:
        time.sleep(close_stdin_after)
        p.stdin.close()

    t.join(timeout=15)
    if t.is_alive():
        p.kill()
        print('TIMEOUT — hung!')
    else:
        print(f'OK — {len(lines)} lines, first: {lines[0][:60] if lines else "none"}')


run('stdin OPEN, never closed',    close_stdin_after=None)
run('stdin closed immediately',    close_stdin_after=0.0)
run('stdin closed after 0.3s',     close_stdin_after=0.3)
