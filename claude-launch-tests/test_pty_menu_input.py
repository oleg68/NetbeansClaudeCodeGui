"""
Цель: выяснить правильный формат ввода для Claude Code TUI-меню.
Тестируем permission dialog при попытке записи в файл.

Проверяем форматы:
  A: digit only          — "1"     (нет \r)
  B: digit + enter       — "1\r"   (текущий код)
  C: enter_only          — "\r"    (выбор текущего highlighted)

Примечание: Claude запускается БЕЗ --dangerously-skip-permissions чтобы
получить permission dialog на запись файла.
"""
import pexpect
import time
import sys

CLAUDE = '/usr/local/bin/claude'
CWD = '/tmp'


def test_menu_input(label, send_bytes, timeout=20):
    print(f'\n=== {label} ===', flush=True)
    logfile = open(f'/tmp/pexpect_{label}.log', 'w')
    child = pexpect.spawn(
        CLAUDE, [],
        cwd=CWD, encoding='utf-8', timeout=30,
        logfile=logfile
    )
    try:
        # Ждём первого ❯ (может быть в "bypass permissions" warning или в prompt)
        child.expect(r'❯', timeout=20)
        time.sleep(0.5)
        # Читаем буфер чтобы понять контекст
        buf = child.before + child.after
        print(f'  First ❯ seen, buffer snippet: {repr(buf[-100:])}', flush=True)

        child.sendline('write a file called /tmp/test_menu_' + label + '.txt with content hello')
        print(f'  Prompt sent, waiting for permission menu...', flush=True)

        # Ждём цифру "1" в меню (permission dialog обычно показывает "1. Yes" или "1. No")
        # Используем широкий паттерн, ANSI-коды между символами
        idx = child.expect(['1\\.', pexpect.TIMEOUT], timeout=25)
        if idx != 0:
            print(f'  FAIL: menu with "1." not detected', flush=True)
            print(f'  Last output: {repr(child.before[-300:])}', flush=True)
            return
        print(f'  Menu detected (saw "1."), sending: {send_bytes!r}', flush=True)
        time.sleep(0.4)  # дать меню прорисоваться полностью

        child.send(send_bytes)

        # Успех = Claude продолжил работу (увидим новый ❯ или результат)
        idx = child.expect([r'❯', pexpect.TIMEOUT, pexpect.EOF], timeout=timeout)
        if idx == 0:
            print(f'  Result: OK - Claude continued (next ❯ seen)', flush=True)
        else:
            print(f'  Result: TIMEOUT/EOF - hung or no response', flush=True)
            print(f'  Last output: {repr(child.before[-300:])}', flush=True)
    except Exception as e:
        print(f'  ERROR: {e}', flush=True)
    finally:
        child.close(force=True)
        logfile.close()


test_menu_input('A_digit_only',  '1')
test_menu_input('B_digit_enter', '1\r')
test_menu_input('C_enter_only',  '\r')
