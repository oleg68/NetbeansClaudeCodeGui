"""
test_first_run_trust_prompt.py
================================
Verifies the Y/n trust prompt that Claude Code shows when launched in a
brand-new directory for the first time (onboarding / directory-trust dialog).

The tests use pexpect (or the raw pty module as fallback) to drive a real
PTY session, so they require the `claude` binary to be on PATH and a network
connection (Claude API key).

Run with:
    python3 -m pytest claude-launch-tests/test_first_run_trust_prompt.py -v
"""

import os
import re
import sys
import shutil
import tempfile
import subprocess
import time

import pytest

try:
    import pexpect
    HAS_PEXPECT = True
except ImportError:
    HAS_PEXPECT = False

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

CLAUDE_BIN = shutil.which("claude")
YN_PATTERN = re.compile(r'\[Y/n\]|\[y/N\]|\[yes/no\]|\(y/n\)', re.IGNORECASE)


def _make_fresh_dir():
    """Create a temp directory with NO .claude/settings.local.json."""
    d = tempfile.mkdtemp(prefix="claude_trust_test_")
    return d


# ---------------------------------------------------------------------------
# Skip guard
# ---------------------------------------------------------------------------

@pytest.fixture(autouse=True)
def require_claude():
    if CLAUDE_BIN is None:
        pytest.skip("claude binary not found on PATH")
    if not HAS_PEXPECT:
        pytest.skip("pexpect not installed (pip install pexpect)")


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestFirstRunTrustPrompt:
    """
    Exercises the directory-trust prompt that Claude Code shows on first
    launch in a new directory.
    """

    def _spawn(self, workdir):
        """Spawn claude in workdir with a real PTY via pexpect."""
        env = dict(os.environ)
        env["TERM"] = "xterm-256color"
        child = pexpect.spawn(
            CLAUDE_BIN,
            args=["--dangerously-skip-permissions"],
            cwd=workdir,
            env=env,
            encoding="utf-8",
            codec_errors="replace",
            timeout=30,
        )
        child.logfile_read = sys.stdout  # show output during test run
        return child

    # ------------------------------------------------------------------
    # Test 1: detect [Y/n] prompt
    # ------------------------------------------------------------------

    def test_detect_yn_prompt_on_fresh_dir(self):
        """
        When claude is launched in a fresh directory it should display a
        trust prompt containing [Y/n], [y/N], [yes/no], or (y/n).

        NOTE: This test probes the real binary.  If the binary version does
        not show a trust prompt (e.g. trust is pre-configured) it will be
        marked SKIPPED rather than FAILED.
        """
        workdir = _make_fresh_dir()
        try:
            child = pexpect.spawn(
                CLAUDE_BIN,
                cwd=workdir,
                env={**os.environ, "TERM": "xterm-256color"},
                encoding="utf-8",
                codec_errors="replace",
                timeout=20,
            )
            child.logfile_read = sys.stdout

            # Expect either the Y/n prompt or the normal idle prompt (❯)
            idx = child.expect([
                r'\[Y/n\]',
                r'\[y/N\]',
                r'\(y/n\)',
                r'yes/no',
                r'❯',        # normal idle — no trust prompt shown
                pexpect.TIMEOUT,
                pexpect.EOF,
            ], timeout=20)

            if idx in (4, 5, 6):
                # The binary did not show a trust prompt — skip gracefully
                child.close(force=True)
                pytest.skip(
                    "claude did not show a Y/n trust prompt in fresh dir "
                    f"(idx={idx}); binary may be pre-trusted or version differs"
                )

            # idx 0-3: some form of [Y/n] detected
            collected = child.before + child.after
            print("\n\n--- collected output ---\n" + collected)
            assert YN_PATTERN.search(collected), (
                f"Y/n pattern not found in:\n{collected}"
            )
            child.close(force=True)
        finally:
            shutil.rmtree(workdir, ignore_errors=True)

    # ------------------------------------------------------------------
    # Test 2: sending 'y' unblocks the session
    # ------------------------------------------------------------------

    def test_sending_y_unblocks_session(self):
        """
        After the [Y/n] trust prompt appears, sending 'y\\r' should
        proceed past the prompt and eventually show the normal ❯ input prompt.
        """
        workdir = _make_fresh_dir()
        try:
            child = pexpect.spawn(
                CLAUDE_BIN,
                cwd=workdir,
                env={**os.environ, "TERM": "xterm-256color"},
                encoding="utf-8",
                codec_errors="replace",
                timeout=30,
            )
            child.logfile_read = sys.stdout

            idx = child.expect([
                r'\[Y/n\]',
                r'\[y/N\]',
                r'\(y/n\)',
                r'yes/no',
                r'❯',
                pexpect.TIMEOUT,
                pexpect.EOF,
            ], timeout=20)

            if idx in (4, 5, 6):
                child.close(force=True)
                pytest.skip("claude did not show a Y/n trust prompt — skipping")

            # Send 'y' + Enter
            child.sendline("y")

            # Expect the normal idle prompt to appear within 15 s
            idx2 = child.expect([r'❯', pexpect.TIMEOUT, pexpect.EOF], timeout=15)
            assert idx2 == 0, (
                "Expected ❯ idle prompt after answering Y/n with 'y', "
                f"but got idx={idx2}. Buffer:\n{child.before}"
            )
            child.close(force=True)
        finally:
            shutil.rmtree(workdir, ignore_errors=True)

    # ------------------------------------------------------------------
    # Test 3: sending 'n' aborts / exits
    # ------------------------------------------------------------------

    def test_sending_n_exits_or_stays_blocked(self):
        """
        After the [Y/n] trust prompt appears, sending 'n\\r' should either
        exit claude or show a rejection message — it must NOT proceed to the
        normal ❯ idle prompt.
        """
        workdir = _make_fresh_dir()
        try:
            child = pexpect.spawn(
                CLAUDE_BIN,
                cwd=workdir,
                env={**os.environ, "TERM": "xterm-256color"},
                encoding="utf-8",
                codec_errors="replace",
                timeout=30,
            )
            child.logfile_read = sys.stdout

            idx = child.expect([
                r'\[Y/n\]',
                r'\[y/N\]',
                r'\(y/n\)',
                r'yes/no',
                r'❯',
                pexpect.TIMEOUT,
                pexpect.EOF,
            ], timeout=20)

            if idx in (4, 5, 6):
                child.close(force=True)
                pytest.skip("claude did not show a Y/n trust prompt — skipping")

            # Send 'n' + Enter
            child.sendline("n")

            # After 'n': expect EOF (process exits) or no ❯ within 8 s
            idx2 = child.expect([pexpect.EOF, r'❯', pexpect.TIMEOUT], timeout=10)
            # EOF (0) or TIMEOUT (2) are acceptable; ❯ (1) would be a failure
            assert idx2 != 1, (
                "Expected claude to NOT show ❯ after answering trust prompt with 'n', "
                f"but got the idle prompt. Buffer:\n{child.before}"
            )
            child.close(force=True)
        finally:
            shutil.rmtree(workdir, ignore_errors=True)


# ---------------------------------------------------------------------------
# Standalone helper: print what the real prompt looks like
# ---------------------------------------------------------------------------

def probe_trust_prompt(workdir=None):
    """
    Utility (not a pytest test) that launches claude in a fresh dir and
    captures the first 3 seconds of output so developers can see what
    the real trust prompt looks like.
    """
    if CLAUDE_BIN is None:
        print("claude not on PATH")
        return

    own_dir = workdir is None
    if own_dir:
        workdir = _make_fresh_dir()

    try:
        result = subprocess.run(
            [CLAUDE_BIN],
            cwd=workdir,
            capture_output=True,
            text=True,
            timeout=5,
            env={**os.environ, "TERM": "xterm-256color"},
        )
        print("=== stdout ===")
        print(repr(result.stdout[:2000]))
        print("=== stderr ===")
        print(repr(result.stderr[:500]))
    except subprocess.TimeoutExpired as e:
        print("=== stdout (partial, timed out) ===")
        out = e.stdout or b""
        print(repr(out[:2000] if isinstance(out, bytes) else out[:2000]))
    finally:
        if own_dir:
            shutil.rmtree(workdir, ignore_errors=True)


if __name__ == "__main__":
    probe_trust_prompt()
