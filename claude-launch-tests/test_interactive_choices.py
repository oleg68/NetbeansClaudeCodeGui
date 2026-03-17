"""
Hypothesis: even in --print mode (without --dangerously-skip-permissions),
claude can ask the user for implementation choices and receive answers via stdin.

Two scenarios tested:
1. Claude asks a clarifying question before proceeding (implementation choices).
   Prompt explicitly asks claude to ask before acting.
2. Permission prompt — claude tries to run bash without --dangerously-skip-permissions.
   Does it block waiting for stdin, or fail immediately?

We keep stdin open, watch stdout for non-JSON lines (potential prompts),
and send an answer via stdin when detected.

Expected outcomes (unknown — this is what we're testing):
- If claude can receive stdin answers in --print mode for clarifying questions,
  interactive workflows are possible without PTY.
- If permission prompts appear as JSON events we can parse and respond to,
  PTY may not be needed at all.
- If both fail, PTY remains necessary for Stage 7.
"""

import subprocess, os, threading, time, json, sys

CLAUDE = '/usr/local/bin/claude'
CWD = '/home/oleg/my-projects/grandorgue/GrandOrgue'
ENV = {k: v for k, v in os.environ.items()
       if k not in ('CLAUDECODE', 'CLAUDE_CODE_ENTRYPOINT')}


def run(label, prompt, answer=None, skip_permissions=False, timeout=40):
    """
    answer: string to send to stdin when stdout produces a non-JSON line
            OR a JSON line with type matching a prompt/question pattern.
            If None, stdin is closed after 1s.
    skip_permissions: add --dangerously-skip-permissions flag
    """
    print(f'\n{"="*60}')
    print(f'SCENARIO: {label}')
    print(f'{"="*60}')

    cmd = [CLAUDE, '--print', prompt,
           '--output-format', 'stream-json', '--verbose']
    if skip_permissions:
        cmd.append('--dangerously-skip-permissions')

    print(f'CMD: {" ".join(cmd[:6])} ...')

    p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE, env=ENV, cwd=CWD,
                         bufsize=0)

    lines = []
    answer_sent = threading.Event()
    stdin_closed = threading.Event()

    def read_stdout():
        for raw in iter(p.stdout.readline, b''):
            line = raw.decode('utf-8', errors='replace').rstrip()
            lines.append(line)
            # Categorize line
            if line.startswith('{'):
                try:
                    obj = json.loads(line)
                    t = obj.get('type', '?')
                    subtype = obj.get('subtype', '')
                    # Print abbreviated version
                    if t == 'assistant':
                        texts = [c.get('text', '')[:60]
                                 for c in obj.get('message', {}).get('content', [])
                                 if c.get('type') == 'text']
                        print(f'  [JSON:{t}] {texts}')
                    elif t == 'tool_use':
                        print(f'  [JSON:{t}] name={obj.get("name")} input_keys={list(obj.get("input",{}).keys())}')
                    elif t == 'tool_result':
                        content = str(obj.get('content', ''))[:80]
                        print(f'  [JSON:{t}] {content}')
                    elif t == 'system':
                        print(f'  [JSON:{t}/{subtype}]')
                    elif t == 'result':
                        print(f'  [JSON:{t}] subtype={subtype} result={str(obj.get("result",""))[:80]}')
                    else:
                        print(f'  [JSON:{t}] {line[:80]}')

                    # Check for AskUserQuestion tool use
                    if t == 'tool_use' and obj.get('name') == 'AskUserQuestion':
                        q = obj.get('input', {}).get('question', '')
                        print(f'  *** AskUserQuestion detected: {q!r}')
                        if answer and not answer_sent.is_set():
                            _send_answer(p, answer, answer_sent)

                except json.JSONDecodeError:
                    print(f'  [JSON-ERR] {line[:80]}')
            else:
                # Non-JSON — could be a plain-text interactive prompt
                print(f'  [PLAIN] {line[:120]}')
                if answer and not answer_sent.is_set():
                    print(f'  *** Plain-text prompt detected — sending answer: {answer!r}')
                    _send_answer(p, answer, answer_sent)

    def read_stderr():
        for raw in iter(p.stderr.readline, b''):
            line = raw.decode('utf-8', errors='replace').rstrip()
            if line:
                print(f'  [STDERR] {line[:120]}')

    def _send_answer(proc, ans, sent_event):
        try:
            proc.stdin.write((ans + '\n').encode('utf-8'))
            proc.stdin.flush()
            print(f'  >>> Sent to stdin: {ans!r}')
        except OSError as e:
            print(f'  !!! stdin write error: {e}')
        sent_event.set()

    t_out = threading.Thread(target=read_stdout, daemon=True)
    t_err = threading.Thread(target=read_stderr, daemon=True)
    t_out.start()
    t_err.start()

    # If no answer expected, close stdin after a short delay
    if answer is None:
        time.sleep(1.0)
        try:
            p.stdin.close()
        except OSError:
            pass
        stdin_closed.set()

    t_out.join(timeout=timeout)
    t_err.join(timeout=2)

    if t_out.is_alive():
        p.kill()
        print(f'\nRESULT: TIMEOUT after {timeout}s — process hung waiting for input')
    else:
        rc = p.wait()
        print(f'\nRESULT: exit_code={rc}, total_lines={len(lines)}')
        if not answer_sent.is_set() and answer is not None:
            print('NOTE: answer was never triggered (no matching prompt detected)')


# ---------------------------------------------------------------------------
# Scenario 1: Ask claude to clarify before acting (no tool use, pure question)
# With --dangerously-skip-permissions so we isolate the clarifying question.
# ---------------------------------------------------------------------------
run(
    label='Clarifying question — ask before acting (skip-permissions)',
    prompt=(
        'Before you do anything, ask me ONE clarifying question about which '
        'approach I prefer. Wait for my answer before proceeding. '
        'Task: create a utility function that reads a file.'
    ),
    answer='Use BufferedReader approach',
    skip_permissions=True,
    timeout=45,
)

# ---------------------------------------------------------------------------
# Scenario 2: Same, WITHOUT --dangerously-skip-permissions
# Does --print mode allow AskUserQuestion at all without the flag?
# ---------------------------------------------------------------------------
run(
    label='Clarifying question — without skip-permissions',
    prompt=(
        'Before you do anything, ask me ONE clarifying question about which '
        'approach I prefer. Wait for my answer before proceeding. '
        'Task: create a utility function that reads a file.'
    ),
    answer='Use BufferedReader approach',
    skip_permissions=False,
    timeout=45,
)

# ---------------------------------------------------------------------------
# Scenario 3: Permission prompt — try to run bash without skip-permissions.
# Does claude block on stdin or fail with an error event?
# ---------------------------------------------------------------------------
run(
    label='Permission prompt — bash without skip-permissions',
    prompt='Run: echo hello',
    answer='yes',
    skip_permissions=False,
    timeout=30,
)
