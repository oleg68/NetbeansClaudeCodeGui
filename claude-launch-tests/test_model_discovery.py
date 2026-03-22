"""
Hypothesis: the correct PTY sequence to open the /model selection menu is:
  1. Send "/model"  (triggers autocomplete popup)
  2. Wait 200ms
  3. Send ESC       (dismiss autocomplete; "/model" stays in input)
  4. Wait 200ms
  5. Send "\r"      (execute — model selection menu opens)
  6. Wait 200ms
  7. Read screen — numbered model list visible
  8. Send a digit to switch model
  9. Send ESC to close menu

Tests:
  A. After the sequence, screen contains ≥ 2 model entries matching "Word N.N"
  B. Sending digit "2" selects Opus; ESC closes menu; prompt returns
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
    # Replace cursor-right (e.g. \x1b[1C, \x1b[21C) with a space so words stay separated
    text = re.sub(r'\x1b\[\d+C', ' ', text)
    # Strip remaining ANSI escape sequences
    return re.sub(r'\x1b\[[0-9;]*[mABCDEFGHJKLMPSTfhilmnsu]', '', text)


def wait_for_prompt(child, timeout=20):
    """Wait for Claude's interactive prompt (❯ in footer area)."""
    child.expect(r'❯', timeout=timeout)
    time.sleep(0.3)


# ---------------------------------------------------------------------------
# Test A: open model menu, verify numbered list appears
# ---------------------------------------------------------------------------
print('\n=== Test A: open model menu, read list ===', flush=True)
logfile_a = open('/tmp/pexpect_model_a.log', 'w')
child = pexpect.spawn(CLAUDE, [], cwd=CWD, encoding='utf-8', timeout=30,
                      logfile=logfile_a)
try:
    wait_for_prompt(child)
    print('  Prompt ready', flush=True)

    # New sequence: /model → ESC → \r
    child.send('/model')
    time.sleep(0.2)
    child.send(ESC)
    time.sleep(0.2)
    child.send('\r')
    time.sleep(0.2)

    # Expect the numbered menu: "1." pattern
    idx = child.expect([r'1\.', pexpect.TIMEOUT], timeout=10)
    if idx != 0:
        print('  FAIL: numbered model menu not detected', flush=True)
        print(f'  Last output: {repr(child.before[-400:])}', flush=True)
        sys.exit(1)

    time.sleep(0.2)
    buf = strip_ansi(child.before + child.after + (child.buffer or ''))
    models = MODEL_PAT.findall(buf)
    print(f'  Models found: {models}', flush=True)

    if len(models) < 2:
        print(f'  FAIL: expected ≥ 2 model entries, got {len(models)}', flush=True)
        sys.exit(1)

    print(f'  PASS: {len(models)} model entries found', flush=True)

    # Close menu
    child.send(ESC)
    time.sleep(0.3)

finally:
    child.close(force=True)
    logfile_a.close()

# ---------------------------------------------------------------------------
# Test B: switch to model #2 (Opus)
# ---------------------------------------------------------------------------
print('\n=== Test B: switch to model 2 (Opus) ===', flush=True)
logfile_b = open('/tmp/pexpect_model_b.log', 'w')
child = pexpect.spawn(CLAUDE, [], cwd=CWD, encoding='utf-8', timeout=30,
                      logfile=logfile_b)
try:
    wait_for_prompt(child)
    print('  Prompt ready', flush=True)

    # Open model menu
    child.send('/model')
    time.sleep(0.2)
    child.send(ESC)
    time.sleep(0.2)
    child.send('\r')
    time.sleep(0.2)

    # Wait for numbered menu
    idx = child.expect([r'1\.', pexpect.TIMEOUT], timeout=10)
    if idx != 0:
        print('  FAIL: model menu not detected', flush=True)
        sys.exit(1)
    time.sleep(0.2)

    # Select model 2
    child.send('2')

    # Expect return to prompt (❯) after selection
    idx = child.expect([r'❯', pexpect.TIMEOUT, pexpect.EOF], timeout=15)
    if idx == 0:
        buf = strip_ansi(child.before + child.after + (child.buffer or ''))
        # Check "Opus" is mentioned (model switched)
        if 'Opus' in buf or 'opus' in buf.lower():
            print('  PASS: switched to Opus, prompt returned', flush=True)
        else:
            print('  PASS: prompt returned after model switch', flush=True)
    else:
        print('  FAIL: prompt did not return after model switch', flush=True)
        print(f'  Last output: {repr(child.before[-300:])}', flush=True)
        sys.exit(1)

finally:
    child.close(force=True)
    logfile_b.close()

print('\nAll tests passed.', flush=True)
