"""
Hypothesis: absence of TERM environment variable causes Node.js to buffer
stdout differently, causing claude --print to hang.

Result: REFUTED.
- WITHOUT TERM → 4 lines, works fine
- WITH TERM=dumb → 4 lines, works fine

Both tests used communicate() which closes stdin (EOF). The TERM variable
has no effect on whether claude produces output.
"""

import subprocess, os

CLAUDE = '/usr/local/bin/claude'
CWD = '/home/oleg/my-projects/grandorgue/GrandOrgue'
BASE_ENV = {k: v for k, v in os.environ.items()
            if k not in ('CLAUDECODE', 'CLAUDE_CODE_ENTRYPOINT')}
CMD = [CLAUDE, '--print', 'список из трёх фруктов',
       '--output-format', 'stream-json', '--verbose',
       '--dangerously-skip-permissions']


def run(label, env):
    print(f'\n=== {label} ===')
    p = subprocess.Popen(CMD, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE, env=env, cwd=CWD)
    try:
        out, err = p.communicate(timeout=15)
        lines = out.decode('utf-8', errors='replace').splitlines()
        print(f'OK — {len(lines)} lines, first: {lines[0][:60] if lines else "none"}')
        if err:
            print('stderr:', err.decode('utf-8', errors='replace')[:200])
    except subprocess.TimeoutExpired:
        p.kill()
        print('TIMEOUT — hung!')


env_no_term = {k: v for k, v in BASE_ENV.items() if k != 'TERM'}
env_with_term = dict(env_no_term, TERM='dumb')

run('WITHOUT TERM', env_no_term)
run('WITH TERM=dumb', env_with_term)
