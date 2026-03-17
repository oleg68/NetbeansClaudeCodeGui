"""
Hypothesis: if we keep stdin open long enough for claude to produce output,
then close it, we can potentially send a response before closing.
This would allow interactive responses in --print mode.

Result: CONFIRMED (partially).
- Closing stdin after the first output line arrives → works, all output received
- This means: stdin can be kept open while waiting for a prompt question,
  response written, then stdin closed

Limitation: claude --print with --dangerously-skip-permissions never asks
questions, so the interactive response path is not exercised here.
Full interactive responses (PromptResponsePanel) require PTY (Stage 11).
"""

import subprocess, os, threading, time

CLAUDE = '/usr/local/bin/claude'
CWD = '/home/oleg/my-projects/grandorgue/GrandOrgue'
ENV = {k: v for k, v in os.environ.items()
       if k not in ('CLAUDECODE', 'CLAUDE_CODE_ENTRYPOINT')}
CMD = [CLAUDE, '--print', 'список из трёх фруктов',
       '--output-format', 'stream-json', '--verbose',
       '--dangerously-skip-permissions']


def run_close_after_first_line():
    print('\n=== Close stdin after first stdout line arrives ===')
    p = subprocess.Popen(CMD, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE, env=ENV, cwd=CWD)

    first_line_event = threading.Event()
    lines = []

    def read():
        for line in p.stdout:
            lines.append(line.decode('utf-8', errors='replace').rstrip())
            first_line_event.set()

    t = threading.Thread(target=read, daemon=True)
    t.start()

    if first_line_event.wait(timeout=10):
        print(f'First line received, closing stdin. Line: {lines[0][:60]}')
        p.stdin.close()
    else:
        print('No output in 10s, closing stdin anyway')
        p.stdin.close()

    t.join(timeout=15)
    if t.is_alive():
        p.kill()
        print('TIMEOUT after stdin close')
    else:
        print(f'OK — {len(lines)} lines total')


run_close_after_first_line()
