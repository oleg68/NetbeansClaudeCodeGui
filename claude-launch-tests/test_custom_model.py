"""
Hypothesis: When claude is launched with ANTHROPIC_MODEL env var or --model flag,
the /model interactive menu still shows a full list of models (≥ 2 entries),
meaning the user can still interactively switch models.

Tests:
  A. ANTHROPIC_MODEL=claude-opus-4-6           — /model menu shows ≥ 2 model entries
  B. --model claude-opus-4-6                   — /model menu shows ≥ 2 model entries
     (or documents that the list is suppressed/single-entry)
  C. ANTHROPIC_DEFAULT_CUSTOM_MODEL=codex/openai-gpt5.4 — does it appear in /model menu?
  D. ANTHROPIC_MODEL=codex/openai-gpt5.4       — does CC start? what does /model show?
  E. --model codex/openai-gpt5.4               — does CC start? what does /model show?

In all cases dump the raw buffer so the exact output is visible.
"""

import pexpect
import os
import re
import time
import sys

CLAUDE = '/usr/local/bin/claude'
CWD = '/tmp'
ESC = '\x1b'
MODEL_ID = 'claude-opus-4-6'

MODEL_PAT = re.compile(r'[A-Z][a-z]+\s+\d+\.\d+')


def strip_ansi(text):
    text = re.sub(r'\x1b\[\d+C', ' ', text)
    return re.sub(r'\x1b\[[0-9;]*[mABCDEFGHJKLMPSTfhilmnsu]', '', text)


def wait_for_prompt(child, timeout=30):
    """Wait for Claude's ❯ prompt then pause to let auto-update settle."""
    child.expect(r'❯', timeout=timeout)
    # Extra wait: let any auto-update or re-render finish before sending input
    time.sleep(1.5)


def open_model_menu(child):
    """Send /model → ESC → \\r and wait for the numbered menu.
    Returns (success, models_list)."""
    child.send('/model')
    time.sleep(0.2)
    child.send(ESC)
    time.sleep(0.2)
    child.send('\r')
    time.sleep(0.5)

    idx = child.expect([r'1\.', pexpect.TIMEOUT], timeout=10)
    if idx != 0:
        return False, []

    # Read a bit more to get the full list
    time.sleep(0.4)
    buf = strip_ansi(child.before + child.after + (child.buffer or ''))
    models = MODEL_PAT.findall(buf)
    return True, models


def check_opus_in_header(child):
    """Return True if Opus is visible in the current screen content."""
    buf = strip_ansi(child.buffer or '')
    return 'opus' in buf.lower()


# ---------------------------------------------------------------------------
# Test A: ANTHROPIC_MODEL env var
# ---------------------------------------------------------------------------
print('\n=== Test A: ANTHROPIC_MODEL env var ===', flush=True)
env_a = os.environ.copy()
env_a['ANTHROPIC_MODEL'] = MODEL_ID

logfile_a = open('/tmp/pexpect_custom_model_a.log', 'w')
child = pexpect.spawn(CLAUDE, [], cwd=CWD, env=env_a, encoding='utf-8', timeout=30,
                      logfile=logfile_a)
try:
    wait_for_prompt(child)
    print('  Prompt ready', flush=True)

    # Check if model name appears in the initial screen (welcome header shows model name)
    buf_initial = strip_ansi(child.before + child.after + (child.buffer or ''))
    if 'opus' in buf_initial.lower():
        print('  INFO: Opus visible in welcome screen — ANTHROPIC_MODEL applied', flush=True)
    else:
        print('  INFO: Opus not visible in welcome screen', flush=True)

    menu_opened, models = open_model_menu(child)
    if not menu_opened:
        print('  FAIL: numbered model menu not detected after /model', flush=True)
        raw = strip_ansi(child.before[-400:])
        print(f'  Last output: {repr(raw)}', flush=True)
        sys.exit(1)

    print(f'  Models found in menu: {models}', flush=True)

    if len(models) >= 2:
        print(f'  PASS: /model menu shows {len(models)} entries — interactive selection preserved with ANTHROPIC_MODEL', flush=True)
    elif len(models) == 1:
        print(f'  RESULT: /model menu shows only 1 entry — interactive selection restricted when ANTHROPIC_MODEL is set', flush=True)
    else:
        print('  RESULT: /model menu opened but no recognisable model entries in buffer', flush=True)
        time.sleep(0.3)
        raw = strip_ansi(child.buffer or '')
        print(f'  Raw buffer: {repr(raw[:600])}', flush=True)

    child.send(ESC)
    time.sleep(0.3)

finally:
    child.close(force=True)
    logfile_a.close()

# ---------------------------------------------------------------------------
# Test B: --model flag
# ---------------------------------------------------------------------------
print('\n=== Test B: --model flag ===', flush=True)
logfile_b = open('/tmp/pexpect_custom_model_b.log', 'w')
child = pexpect.spawn(CLAUDE, ['--model', MODEL_ID], cwd=CWD, encoding='utf-8', timeout=30,
                      logfile=logfile_b)
try:
    wait_for_prompt(child)
    print('  Prompt ready', flush=True)

    buf_initial = strip_ansi(child.before + child.after + (child.buffer or ''))
    if 'opus' in buf_initial.lower():
        print('  INFO: Opus visible in welcome screen — --model applied', flush=True)
    else:
        print('  INFO: Opus not visible in welcome screen', flush=True)

    menu_opened, models = open_model_menu(child)
    if not menu_opened:
        print('  FAIL: numbered model menu not detected after /model', flush=True)
        raw = strip_ansi(child.before[-400:])
        print(f'  Last output: {repr(raw)}', flush=True)
        sys.exit(1)

    print(f'  Models found in menu: {models}', flush=True)

    if len(models) >= 2:
        print(f'  PASS: /model menu shows {len(models)} entries — interactive selection preserved with --model flag', flush=True)
    elif len(models) == 1:
        print(f'  RESULT: /model menu shows only 1 entry — interactive selection restricted when --model is set', flush=True)
    else:
        print('  RESULT: /model menu opened but no recognisable model entries in buffer', flush=True)
        time.sleep(0.3)
        raw = strip_ansi(child.buffer or '')
        print(f'  Raw buffer: {repr(raw[:600])}', flush=True)

    child.send(ESC)
    time.sleep(0.3)

finally:
    child.close(force=True)
    logfile_b.close()

CUSTOM_MODEL_ID = 'codex/openai-gpt5.4'


def dump_model_menu(child, label):
    """Open /model menu and dump the full raw buffer regardless of content."""
    child.send('/model')
    time.sleep(0.2)
    child.send(ESC)
    time.sleep(0.2)
    child.send('\r')
    time.sleep(0.8)

    idx = child.expect([r'1\.', pexpect.TIMEOUT], timeout=10)
    if idx != 0:
        raw = strip_ansi(child.before[-600:])
        print(f'  {label}: numbered menu NOT detected. Last output: {repr(raw)}', flush=True)
        return

    time.sleep(0.5)
    buf = strip_ansi(child.before + child.after + (child.buffer or ''))
    models = MODEL_PAT.findall(buf)
    print(f'  {label}: menu opened. Standard models found: {models}', flush=True)
    # Check for custom/non-claude model IDs in the menu
    for keyword in ('codex', 'openai', 'custom', CUSTOM_MODEL_ID):
        if keyword.lower() in buf.lower():
            print(f'  {label}: keyword "{keyword}" found in menu buffer!', flush=True)
    print(f'  {label}: raw buffer (first 800 chars): {repr(buf[:800])}', flush=True)
    child.send(ESC)
    time.sleep(0.3)


def launch_and_check(args, env, label, logpath):
    """Spawn CC, wait for prompt, dump initial screen and /model menu."""
    logfile = open(logpath, 'w')
    child = pexpect.spawn(CLAUDE, args, cwd=CWD, env=env, encoding='utf-8', timeout=30,
                          logfile=logfile)
    try:
        idx = child.expect([r'❯', pexpect.TIMEOUT, pexpect.EOF], timeout=30)
        if idx != 0:
            raw = strip_ansi(child.before[-600:])
            print(f'  {label}: prompt NOT reached (idx={idx}). Output: {repr(raw)}', flush=True)
            return
        time.sleep(1.5)
        print(f'  {label}: prompt ready', flush=True)

        buf_init = strip_ansi(child.before + child.after + (child.buffer or ''))
        for keyword in ('codex', 'openai', 'custom', CUSTOM_MODEL_ID, 'error', 'Error'):
            if keyword.lower() in buf_init.lower():
                print(f'  {label}: keyword "{keyword}" in welcome screen', flush=True)
        print(f'  {label}: welcome screen (first 600 chars): {repr(buf_init[:600])}', flush=True)

        dump_model_menu(child, label)
    finally:
        child.close(force=True)
        logfile.close()


# ---------------------------------------------------------------------------
# Test C: ANTHROPIC_DEFAULT_CUSTOM_MODEL=codex/openai-gpt5.4
# ---------------------------------------------------------------------------
print(f'\n=== Test C: ANTHROPIC_DEFAULT_CUSTOM_MODEL={CUSTOM_MODEL_ID} ===', flush=True)
env_c = os.environ.copy()
env_c['ANTHROPIC_DEFAULT_CUSTOM_MODEL'] = CUSTOM_MODEL_ID
launch_and_check([], env_c, 'Test C', '/tmp/pexpect_custom_model_c.log')

# ---------------------------------------------------------------------------
# Test D: ANTHROPIC_MODEL=codex/openai-gpt5.4
# ---------------------------------------------------------------------------
print(f'\n=== Test D: ANTHROPIC_MODEL={CUSTOM_MODEL_ID} ===', flush=True)
env_d = os.environ.copy()
env_d['ANTHROPIC_MODEL'] = CUSTOM_MODEL_ID
launch_and_check([], env_d, 'Test D', '/tmp/pexpect_custom_model_d.log')

# ---------------------------------------------------------------------------
# Test E: --model codex/openai-gpt5.4
# ---------------------------------------------------------------------------
print(f'\n=== Test E: --model {CUSTOM_MODEL_ID} ===', flush=True)
launch_and_check(['--model', CUSTOM_MODEL_ID], os.environ.copy(),
                 'Test E', '/tmp/pexpect_custom_model_e.log')

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
print('\nTests complete. Review PASS/RESULT lines above.', flush=True)
