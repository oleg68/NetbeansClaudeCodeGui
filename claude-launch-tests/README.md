# Claude launch tests

Python scripts for testing different claude launch modes.
Each file tests a specific hypothesis about how claude behaves with various
stdin/stdout/env configurations.

Run any script directly:
    python3 <script>.py

All scripts unset CLAUDECODE and CLAUDE_CODE_ENTRYPOINT to avoid the
"nested session" error when running from inside a Claude Code session.

## Scripts

| File | What it tests |
|------|---------------|
| `test_stdin_open_vs_closed.py` | Does claude --print hang when stdin is kept open (no EOF)? |
| `test_term_env.py` | Does presence/absence of TERM affect output buffering? |
| `test_stdin_close_after_output.py` | Can we send a response via stdin if we close it after receiving output? |
| `test_interactive_choices.py` | Can claude ask clarifying questions and receive answers via stdin in --print mode? |

## Conclusion: interactivity in --print mode

| Scenario | Result |
|----------|--------|
| Clarifying questions + stdin open | Deadlock — claude produces no output until stdin is closed (EOF) |
| AskUserQuestion tool in --print mode | Disabled — returns error immediately, claude never waits for stdin |
| Permission prompts in --print mode | Reported as JSON `permission_denials` in the `result` event, not interactive prompts |
| Long-lived process without --print | Not possible — claude requires `--print` for pipe mode, otherwise prints error |

**Interactive stdin responses are not possible in `--print` mode.** This is an architectural constraint
of the CLI, not a PTY issue. PTY is only needed for permission prompts when running without
`--dangerously-skip-permissions`. For the plugin, `--dangerously-skip-permissions` is an acceptable
default (with a warning in the UI), deferring PTY support to a later optional stage.
