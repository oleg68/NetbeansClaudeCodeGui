"""
Hypothesis: when Claude shows a choice menu with a type-input option, the plugin
must send the option NUMBER first (to activate text-entry mode), then the typed text + \r.

Current (broken) plugin behavior: sends "text\r" directly.
Hypothesis for correct behavior:
  1. Send option digit (e.g. "3") — activates Claude's text-entry mode
  2. Send typed text + "\r"

Tests:
  A. Trigger a Claude choice menu that has a text-input option (plan mode exit scenario).
     Attempt to respond using just "text\r" — verify it DOES NOT work (reproduces the bug).
  B. Respond using "digit" + wait + "text\r" — verify it works.

How to trigger a type-input prompt:
  - Start Claude, send a message to enter plan mode.
  - Claude should show "How would you like to respond?" with a type-input option.

If triggering a type-input prompt proves unreliable in this test, a fallback approach
is documented at the bottom.
"""

import pexpect
import re
import time
import sys
import os

CLAUDE = '/usr/local/bin/claude'
CWD = '/tmp'
ESC = '\x1b'
CHOICE_PAT = re.compile(r'(\d+)\.\s')  # matches "1. ", "2. " etc.
TYPE_INPUT_PAT = re.compile(r'Type|type here|enter text', re.IGNORECASE)


def strip_ansi(text):
    text = re.sub(r'\x1b\[\d+C', ' ', text)
    return re.sub(r'\x1b\[[0-9;]*[mABCDEFGHJKLMPSTfhilmnsu]', '', text)


def wait_for_prompt(child, timeout=30):
    """Wait for Claude's interactive prompt (❯ in footer area)."""
    child.expect(r'❯', timeout=timeout)
    time.sleep(0.5)


def read_screen(child, extra_wait=0.5):
    """Read available PTY output as plain text."""
    time.sleep(extra_wait)
    buf = ''
    try:
        child.expect(r'.+', timeout=0.2)
        buf = child.before + child.after + (child.buffer or '')
    except pexpect.TIMEOUT:
        buf = child.buffer or ''
    return strip_ansi(buf)


# ---------------------------------------------------------------------------
# Helper: trigger plan-mode prompt
# Claude shows a choice menu when you ask it to enter/exit plan mode.
# The menu has options like:
#   1. Yes, exit plan mode
#   2. No, stay in plan mode
#   3. (type a message)
# ---------------------------------------------------------------------------
def trigger_type_input_menu(child):
    """
    Send a message that triggers a plan-mode choice with a type-input option.
    Returns (screen_text, type_input_option_number) or None if not triggered.
    """
    # Ask Claude to plan something — this often triggers plan mode confirmation
    msg = 'Create a plan for a hello world Python script'
    child.send(msg)
    child.send('\r')
    time.sleep(1.0)

    # Look for a numbered choice menu
    try:
        idx = child.expect([r'1\.', pexpect.TIMEOUT], timeout=30)
        if idx != 0:
            return None
    except Exception:
        return None

    time.sleep(0.5)
    buf = strip_ansi(child.before + child.after + (child.buffer or ''))

    # Find all choice numbers
    choices = CHOICE_PAT.findall(buf)
    print(f'  Choices detected: {choices}', flush=True)
    print(f'  Screen excerpt: {repr(buf[-600:])}', flush=True)

    # Find which option is a type-input (contains "Type" or similar)
    lines = buf.split('\n')
    for line in lines:
        m = re.match(r'\s*(\d+)\.\s*(.*)', line)
        if m and TYPE_INPUT_PAT.search(m.group(2)):
            return buf, int(m.group(1))

    # If no obvious type-input option, check for a free-text-looking option
    # (last option that's not just "Yes"/"No")
    if len(choices) >= 3:
        return buf, int(choices[-1])

    return buf, None


# ---------------------------------------------------------------------------
# Test A: reproduce the bug — "text\r" without option number
# ---------------------------------------------------------------------------
print('\n=== Test A: current plugin behavior (text\\r only) ===', flush=True)
logfile_a = open('/tmp/pexpect_typeinput_a.log', 'w')
child = pexpect.spawn(CLAUDE, [], cwd=CWD, encoding='utf-8', timeout=60,
                      logfile=logfile_a)
try:
    wait_for_prompt(child)
    print('  Prompt ready', flush=True)

    result = trigger_type_input_menu(child)
    if result is None or result[1] is None:
        print('  SKIP: could not trigger a type-input menu — plan B needed', flush=True)
        sys.exit(0)

    screen, opt_num = result
    print(f'  Type-input option number: {opt_num}', flush=True)

    # Current plugin behavior: just send text + \r
    test_text = 'This is a test message from the plugin'
    child.send(test_text + '\r')
    time.sleep(1.5)

    buf_after = strip_ansi(child.buffer or '')
    print(f'  Buffer after send: {repr(buf_after[-300:])}', flush=True)

    # Check if Claude accepted the text (returned to prompt or echoed text)
    accepted = (test_text[:10] in buf_after) or ('❯' in buf_after)
    if accepted:
        print('  NOTE: text+\\r worked — bug may be intermittent or already fixed', flush=True)
    else:
        print('  CONFIRMED: text+\\r alone did NOT cause Claude to accept text', flush=True)
        print('  (Bug reproduced: plugin needs to send option number first)', flush=True)

finally:
    child.close(force=True)
    logfile_a.close()


# ---------------------------------------------------------------------------
# Test B: correct approach — send option number first, then text\r
# ---------------------------------------------------------------------------
print('\n=== Test B: hypothesis — digit then text\\r ===', flush=True)
logfile_b = open('/tmp/pexpect_typeinput_b.log', 'w')
child = pexpect.spawn(CLAUDE, [], cwd=CWD, encoding='utf-8', timeout=60,
                      logfile=logfile_b)
try:
    wait_for_prompt(child)
    print('  Prompt ready', flush=True)

    result = trigger_type_input_menu(child)
    if result is None or result[1] is None:
        print('  SKIP: could not trigger a type-input menu', flush=True)
        sys.exit(0)

    screen, opt_num = result
    print(f'  Type-input option number: {opt_num}', flush=True)

    # Hypothesis: send option digit first
    child.send(str(opt_num))
    time.sleep(0.5)

    buf_mid = strip_ansi(child.buffer or '')
    print(f'  Buffer after digit: {repr(buf_mid[-200:])}', flush=True)

    # Then send the text
    test_text = 'This is a test message from the plugin'
    child.send(test_text + '\r')
    time.sleep(1.5)

    try:
        idx = child.expect([r'❯', pexpect.TIMEOUT], timeout=15)
        if idx == 0:
            print('  PASS: Claude returned to prompt after digit+text\\r', flush=True)
            buf_final = strip_ansi(child.before + child.after + (child.buffer or ''))
            print(f'  Response contains typed text: {test_text[:15] in buf_final}', flush=True)
        else:
            print('  FAIL: Claude did not return to prompt after digit+text\\r', flush=True)
            print(f'  Buffer: {repr(child.buffer[-300:])}', flush=True)
    except Exception as e:
        print(f'  ERROR: {e}', flush=True)

finally:
    child.close(force=True)
    logfile_b.close()


# ---------------------------------------------------------------------------
# Test C: alternative — Tab to activate text field, then text\r
# ---------------------------------------------------------------------------
print('\n=== Test C: hypothesis — Tab to focus text field, then text\\r ===', flush=True)
logfile_c = open('/tmp/pexpect_typeinput_c.log', 'w')
child = pexpect.spawn(CLAUDE, [], cwd=CWD, encoding='utf-8', timeout=60,
                      logfile=logfile_c)
try:
    wait_for_prompt(child)
    print('  Prompt ready', flush=True)

    result = trigger_type_input_menu(child)
    if result is None or result[1] is None:
        print('  SKIP: could not trigger a type-input menu', flush=True)
        sys.exit(0)

    screen, opt_num = result
    print(f'  Type-input option number: {opt_num}', flush=True)

    # Tab to activate text field
    child.send('\t')
    time.sleep(0.3)

    buf_mid = strip_ansi(child.buffer or '')
    print(f'  Buffer after Tab: {repr(buf_mid[-200:])}', flush=True)

    test_text = 'This is a test message from the plugin'
    child.send(test_text + '\r')
    time.sleep(1.5)

    try:
        idx = child.expect([r'❯', pexpect.TIMEOUT], timeout=15)
        if idx == 0:
            print('  PASS: Claude returned to prompt after Tab+text\\r', flush=True)
        else:
            print('  FAIL: Claude did not return to prompt after Tab+text\\r', flush=True)
            print(f'  Buffer: {repr(child.buffer[-300:])}', flush=True)
    except Exception as e:
        print(f'  ERROR: {e}', flush=True)

finally:
    child.close(force=True)
    logfile_c.close()

print('\nAll tests complete. Check /tmp/pexpect_typeinput_*.log for raw PTY output.', flush=True)
