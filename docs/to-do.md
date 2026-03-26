# Bugs

- [x] Некорректное срабатывание ChoiceMenu при echoed previous selection (v0.14.6: false-positive guard for single option with no Esc/cancel/amend hint)
- [x] Не сработало ChoiceMenu для wrapped option lines (v0.14.6: allow up to 3 continuation lines in upward scan)
- [x] При первом запуске в новой директории замерзает на запросе разрешения каталога в pty (v0.14.6: detectYesNoPrompt + synthetic ChoiceMenuModel with y/n responses)
- [x] У gradle-проекта отсутствует "Claude Code" в настройках свойств проекта (v0.14.6: fixed layer.xml folder name org.netbeans.modules.gradle → org-netbeans-modules-gradle)
- [x] Сессия зависает при trust-запросе: detectInputPromptReady ложно срабатывает на ❯ в numbered menu (v0.14.14: negative lookahead (?!\s*\d) в INPUT_PROMPT; PTY buffer fallback для пустого экрана)


# Features

- [ ] Возможность подключения md-вьюера в FileDiff
