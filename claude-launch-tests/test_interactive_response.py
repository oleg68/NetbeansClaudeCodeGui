"""
Hypothesis: with claude --print and stdin open, if claude asks a question
(outputs a prompt line), we can write an answer to stdin and claude will
continue processing.

Result: REFUTED for --print mode.
- AskUserQuestion tool in --print mode returns an error immediately:
  tool_use_result = "Error: Answer questions?" — the tool is disabled/unavailable
  in --print mode. Claude never waits for stdin input.
- There are no non-JSON lines in stdout — no plain-text question to detect.
- Writing to stdin has no effect; claude does not read it in --print mode.

Conclusion: interactive responses via stdin are not possible in --print mode.
Full interactive support requires PTY + long-lived process mode (Stage 11).
"""

import subprocess, os, threading, time, json

CLAUDE = '/usr/local/bin/claude'
CWD = '/home/oleg/my-projects/grandorgue/GrandOrgue'
ENV = {k: v for k, v in os.environ.items()
       if k not in ('CLAUDECODE', 'CLAUDE_CODE_ENTRYPOINT')}


def run(label, prompt, answer=None, close_stdin_after=None):
    """
    answer: if set, write this to stdin when a non-JSON line appears in stdout
    close_stdin_after: seconds after start to close stdin (None = only on EOF of stdout)
    """
    print(f'\n=== {label} ===')
    cmd = [CLAUDE, '--print', prompt,
           '--output-format', 'stream-json', '--verbose',
           '--dangerously-skip-permissions']

    p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE, env=ENV, cwd=CWD)

    lines = []
    answer_sent = threading.Event()

    def read():
        for raw in p.stdout:
            line = raw.decode('utf-8', errors='replace').rstrip()
            lines.append(line)
            print(f'  stdout: {line[:100]}')
            # If line is not JSON it might be an interactive prompt
            if answer and not line.startswith('{') and not answer_sent.is_set():
                print(f'  → non-JSON line detected, sending answer: {answer!r}')
                try:
                    p.stdin.write((answer + '\n').encode('utf-8'))
                    p.stdin.flush()
                    p.stdin.close()
                except OSError:
                    pass
                answer_sent.set()

    t = threading.Thread(target=read, daemon=True)
    t.start()

    if close_stdin_after is not None:
        time.sleep(close_stdin_after)
        if not answer_sent.is_set():
            print(f'  → closing stdin after {close_stdin_after}s')
            try:
                p.stdin.close()
            except OSError:
                pass

    t.join(timeout=30)
    if t.is_alive():
        p.kill()
        print('TIMEOUT — hung!')
    else:
        rc = p.wait()
        print(f'Exit code: {rc}, total lines: {len(lines)}')
        # Show result line if present
        for l in lines:
            try:
                obj = json.loads(l)
                if obj.get('type') == 'result':
                    print(f'  result: {obj.get("result", "")[:120]}')
            except Exception:
                pass


# Scenario 1: simple task, no question expected, close stdin after 0.5s
run(
    label='Simple task, close stdin after 0.5s',
    prompt='Назови одну планету солнечной системы. Ответь одним словом.',
    close_stdin_after=0.5,
)

# Scenario 2: ask claude to use AskUserQuestion tool, reply via stdin
run(
    label='AskUserQuestion tool — send answer via stdin on non-JSON line',
    prompt='Use the AskUserQuestion tool to ask me: "What is your favourite fruit?" Then use my answer in your response.',
    answer='манго',
    close_stdin_after=25,
)
