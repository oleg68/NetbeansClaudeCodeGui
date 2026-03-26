# Bugs

- [x] Некорректное срабатывание ChoiceMenu при echoed previous selection (v0.14.6: false-positive guard for single option with no Esc/cancel/amend hint)
- [x] Не сработало ChoiceMenu для wrapped option lines (v0.14.6: allow up to 3 continuation lines in upward scan)
- [x] При первом запуске в новой директории замерзает на запросе разрешения каталога в pty (v0.14.6: detectYesNoPrompt + synthetic ChoiceMenuModel with y/n responses)
- [x] У gradle-проекта отсутствует "Claude Code" в настройках свойств проекта (v0.14.6: fixed layer.xml folder name org.netbeans.modules.gradle → org-netbeans-modules-gradle)
- [x] Сессия зависает при trust-запросе: detectInputPromptReady ложно срабатывает на ❯ в numbered menu (v0.14.14: negative lookahead (?!\s*\d) в INPUT_PROMPT; PTY buffer fallback для пустого экрана)
- [ ] Не сработало choice menu
    ```
    ● Bash(git add pom.xml README.md docs/manual-test-mcp.md && git commit -m "$(cat <<'EOF'                                         
          Rename Maven project: name to "Netbeans Claude Code GUI", artifactId to "netbe…)                                         
      ⎿  Running…                                                                                                                    

    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
     Bash command                                                                                                                  

       git add pom.xml README.md docs/manual-test-mcp.md && git commit -m "$(cat <<'EOF'
       Rename Maven project: name to "Netbeans Claude Code GUI", artifactId to "netbeans-claude-code-gui" (0.14.17-SNAPSHOT)

       Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
       EOF
       )"
       Run shell command

     Do you want to proceed?
     ❯ 1. Yes
       2. Yes, and don't ask again for git add and git commit commands in /home/oleg/my-projects/NetbeansClaudeCodePlugin
       3. No

     Esc to cancel · Tab to amend · ctrl+e to explain
    ```

# Features

- [ ] Возможность подключения md-вьюера в FileDiff
