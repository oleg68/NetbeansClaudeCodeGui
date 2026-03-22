"""
Test C: verify that the empty ❯ input prompt appears as a separate bottom-area
line before /model is sent, and that /model succeeds only after that guard.

Hypothesis:
  - During the startup splash screen, ❯ may appear briefly but never as a
    standalone empty-prompt line at the very bottom of the rendered screen.
  - Once the genuine idle state is reached, a line containing ONLY ❯ (or ❯ + space)
    is visible in the bottom 5 lines of the rendered screen.
  - Sending /model only after that condition yields the numbered model list.
  - Sending /model too early (before ❯ standalone line) causes "Interrupted" or
    a garbled response.

Test C (in this file):
  - Launch claude, wait until a line consisting solely of ❯ appears in the PTY
    stream (simulating detectInputPromptReady).
  - Send /model → ESC → \\r.
  - Verify that a numbered model list appears.
"""

import pexpect
import re
import time
import sys

CLAUDE = '/usr/local/bin/claude'
CWD = '/tmp'
ESC = '\x1b'

MODEL_PAT = re.compile(r'[A-Z][a-z]+\s+\d+\.\d+')


def strip_ansi(text):
    text = re.sub(r'\x1b\[\d+C', ' ', text)
    return re.sub(r'\x1b\[[0-9;]*[mABCDEFGHJKLMPSTfhilmnsu]', '', text)


def wait_for_empty_prompt(child, timeout=30):
    """
    Wait until a line that is *only* ❯ (with optional trailing space) appears.
    This mirrors ScreenContentDetector.detectInputPromptReady() which looks for
    INPUT_PROMPT = ^[❯>▶]\\s*$ in the bottom 5 lines of the screen.
    """
    deadline = time.time() + timeout
    accumulated = ''
    while time.time() < deadline:
        try:
            chunk = child.read_nonblocking(size=4096, timeout=0.2)
            accumulated += chunk
        except pexpect.TIMEOUT:
            pass
        except pexpect.EOF:
            break
        # Check last 5 "lines" of stripped output for a standalone ❯ line
        stripped = strip_ansi(accumulated)
        recent_lines = stripped.splitlines()[-10:]
        for line in recent_lines[-5:]:
            trimmed = line.strip()
            if trimmed in ('❯', '>', '▶'):
                return True  # found empty prompt
    return False


# ---------------------------------------------------------------------------
# Test C: guard on empty ❯ prompt before sending /model
# ---------------------------------------------------------------------------
print('\n=== Test C: wait for empty ❯ prompt, then send /model ===', flush=True)
logfile_c = open('/tmp/pexpect_model_c.log', 'w')
child = pexpect.spawn(CLAUDE, [], cwd=CWD, encoding='utf-8', timeout=40,
                      logfile=logfile_c)
try:
    found = wait_for_empty_prompt(child, timeout=30)
    if not found:
        print('  FAIL: empty ❯ prompt never appeared in bottom lines', flush=True)
        sys.exit(1)
    print('  Empty ❯ prompt detected (detectInputPromptReady condition met)', flush=True)

    # Extra settle time after detecting prompt
    time.sleep(0.3)

    # Send /model command
    child.send('/model')
    time.sleep(0.2)
    child.send(ESC)
    time.sleep(0.2)
    child.send('\r')
    time.sleep(0.2)

    # Expect the numbered menu
    idx = child.expect([r'1\.', pexpect.TIMEOUT], timeout=10)
    if idx != 0:
        print('  FAIL: numbered model menu not detected after ❯ guard', flush=True)
        print(f'  Last output: {repr(child.before[-400:])}', flush=True)
        sys.exit(1)

    time.sleep(0.2)
    buf = strip_ansi(child.before + child.after + (child.buffer or ''))
    models = MODEL_PAT.findall(buf)
    print(f'  Models found: {models}', flush=True)

    if len(models) < 2:
        print(f'  FAIL: expected ≥ 2 model entries, got {len(models)}', flush=True)
        sys.exit(1)

    print(f'  PASS: {len(models)} model entries found after empty ❯ guard', flush=True)

    # Close menu
    child.send(ESC)
    time.sleep(0.3)

finally:
    child.close(force=True)
    logfile_c.close()

print('\nTest C passed.', flush=True)
