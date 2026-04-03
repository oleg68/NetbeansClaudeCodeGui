# Bugs

- [x] Некорректное срабатывание ChoiceMenu при echoed previous selection (v0.14.6: false-positive guard for single option with no Esc/cancel/amend hint)
- [x] Не сработало ChoiceMenu для wrapped option lines (v0.14.6: allow up to 3 continuation lines in upward scan)
- [x] При первом запуске в новой директории замерзает на запросе разрешения каталога в pty (v0.14.6: detectYesNoPrompt + synthetic ChoiceMenuModel with y/n responses)
- [x] У gradle-проекта отсутствует "Claude Code" в настройках свойств проекта (v0.14.6: fixed layer.xml folder name org.netbeans.modules.gradle → org-netbeans-modules-gradle)
- [x] Сессия зависает при trust-запросе: detectInputPromptReady ложно срабатывает на ❯ в numbered menu (v0.14.14: negative lookahead (?!\s*\d) в INPUT_PROMPT; PTY buffer fallback для пустого экрана)
- [x] Не сработало choice menu
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
- [x] Не появилось choice-меню (v0.15.28: fixture added — detector works correctly, was a timing/render issue)
    ```
    ● Read 2 files (ctrl+o to expand)                                                                                                                                                                                                    

    ● Now let me understand the requirements better with a couple of clarification questions, and then design the plan.                                                                                                                  
    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
    ←  ☐ Trigger  ☐ Dir nav  ✔ Submit  →                                                                                                                                                                                                 

    Для пункта 4 (вставка проектов и пакетов): каким триггером пользователь вставляет проекты и пакеты? Это тоже через `@` в input area, или это через drag-and-drop из дерева проектов, или и то и другое?                              

    ❯ 1. Через @-completion                                                                                                                                                                                                              
         Проекты и пакеты появляются в popup при вводе @ наряду с файлами                                                                                                                                                              
      2. Drag-and-drop из дерева
         Перетаскивание узлов проектов/пакетов из Project Explorer в input area
      3. Оба способа
         И через @-completion popup, и через drag-and-drop из дерева проектов
      4. Type something.
    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
      5. Chat about this
      6. Skip interview and plan immediately

    Enter to select · Tab/Arrow keys to navigate · Esc to cancel
    ```

- [x] ложное срабатывание меню, когда перечисление - процитированный промпт, и есть новый маркер ввода (v0.15.28: guard — separator below last option with no option lines below it → return empty)
    ```
    ❯ /clear                                                                                                                                                                                                                             
      ⎿  (no content)

    ❯ 1. Свой проект вставляется как пусто. Вставлять как аттач "@./"                                                                                                                                                                    
    2. DND пакетов запрещён. Нужно разрешить.                                                                                                                                                                                            
    3. Порядок: bump - тесты - воспроизведение - исправление - успешные тесты - сборка                                                                                                                                                   

    ● Explore(Explore attached files and @-completion features)
      ⎿  Done (13 tool uses · 63.3k tokens · 48s)                                       
      (ctrl+o to expand)                                                                                                                                                                                                                 

    ● Reading 1 file… (ctrl+o to expand)                                                                                                                                                                                                 
      ⎿  src/main/java/io/github/nbclaudecodegui/ui/FileDropHandler.java                                                                                                                                                                 

    ✢ Herding… (1m 26s · ↓ 3.9k tokens · thinking with low effort)                                                                                                                                                                       

    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
    ❯                                                                                                                                                                                                                                    
    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
      ⏸ plan mode on (shift+tab to cycle) · esc to interrupt                                                                                             ✗ Auto-update failed · Try claude doctor or npm i -g @anthropic-ai/claude-code  
    ```
- [x] Не сработало choice menu (v0.15.28: fixture added — detector works correctly, was a timing/render issue)
    ```
    ╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
     Claude has written up a plan and is ready to execute. Would you like to proceed?

     ❯ 1. Yes, clear context (39% used) and auto-accept edits
       2. Yes, auto-accept edits
       3. Yes, manually approve edits
       4. Type here to tell Claude what to change

     ctrl-g to edit in nano · ~/.claude/plans/tranquil-roaming-forest.md
    ```
- [x] Stage 15 input panel fixes (v0.15.1):
  - Fix: plain-text Ctrl+V broken — FileDropHandler.canImport/doImport now handles stringFlavor
  - UX: Remove Attach button (DnD/clipboard sufficient)
  - UX: @-completion shows one directory level at a time; ".." always present; Enter on dir navigates in; Space completes as file; Enter on file completes with trailing space
  - UX: @path tokens rendered in foreground color (blue text) instead of background highlight

- [x] Compilation warnings

- [x] Не распознаётся Choice Menu (v0.18.4: fixture added — detector works correctly, was a timing/render issue)
    ```
    ● User answered Claude's questions:
      ⎿  · MarkdownDiffPanel — «Hide»: что должно происходить? → Да, но после сокрытия будет негде делать повторный клик. Поэтому есть второй пункт о Preview Markdown
         · MarkdownDiffPanel — «Persist»: что должно сохраняться? → Открыть Markdown Preview того файла, который сейчас изменяется в отдельном табе, который останется открытым после закрытия diff. Предлажить варианты более удачного названия пункта
         · FileDiffTab — «Preview MarkDown»: как должен работать пункт меню? →  Включить/выключить MarkdownDiffPanel прямо в текущей сессии diff (без изменения глобальных настроек). Чекбокс?
         · Где располагается контекстное меню в FileDiffTab? → ПКМ на diff-панели
    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
    ←  ☒ Название  ☐ Toggle связь  ✔ Submit  →

    Уточнение дизайна: «Hide» в MD-панели и чекбокс «Preview Markdown» в diff-панели — это один и тот же toggle, просто доступный из двух мест?

    ❯ 1. Да, один toggle 
         Hide в MD-панели = снять галку Preview Markdown в diff-панели. Оба синхронизированы.
      2. Нет, независимые  
         Hide только прячет MD-панель, а Preview Markdown — отдельная опция с другой логикой.
      3. Type something.  
    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
      4. Chat about this      
      5. Skip interview and plan immediately                         

    Enter to select · Tab/Arrow keys to navigate · Esc to cancel                                                                                                                                                                                             
    ```
- [x] Markdown Preview: добавить пункт контекстного меню Copy Url при наведении на гиперссылку. Для md-файлов - абсолютный путь, для внешних ссылок - url
- [x] Настройки: переставить местами Profiles и Favorites
- [x] Настройки профилей: добавить гиперссылки на сайты для получения токенов/ключей рядом с полями в табе настройки профиля (claude.ai для Subscription, console.anthropic.com для Claude API)
- [x] User manual: найти и вставить URL на документацию Claude Code по env vars и список совместимых провайдеров (для раздела Extra environment variables в профилях)
- [x] Profiles: поменять дефолтную директорию профилей с ~/.netbeans/claude-profiles/ на ~/.netbeans/claude-plugin/profiles/
- [x] Profiles: переименовать лейблы — "Profiles directory:" → "New Profiles Directory:", "Config directory:" → "Profile Storage Directory:", кнопку "Change…" → синхронизировать с новым названием поля; после этого обновить user-manual.md
- [x] Diff panel: кнопка должна называться "Accept All" (два слова), сейчас "AcceptAll"

# Features

- [x] Возможность подключения md-вьюера в FileDiff

- [ ] Поддержать меню с чекбоксами
    ```
    ● Master соответствует релизу 2026.2 (последний тег sigma-dbk-2026.2.296 на master).

    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
    ←  ☐ Релизы  ✔ Submit  →

    Для каких релизов актуально обновление netty до 4.2.12.Final?

    ❯ 1. [ ] 2026.2
      Целевая ветка: master
      2. [ ] 2026.1
      Целевая ветка: release/2026.1
      3. [ ] 2025.2
      Целевая ветка: release/2025.2
      4. [ ] 2025.1
      Целевая ветка: release/2025.1
      5. [ ] Type something
         Submit
    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
      6. Chat about this

    Enter to select · ↑/↓ to navigate · Esc to cancel
    ```
    Отправлять ответ так: сначала выбранные цмфры, потом стрелку вправо.

- [ ] MCP Tools: добиться работоспособности MCP инструментов (get_open_editors, get_current_selection и др. не вызываются Claude автоматически), после чего написать раздел в user-manual.md с примерами промптов
- [ ] Profiles: добавить алиас "custom" для кастомной модели (Claude Code поддерживает ANTHROPIC_DEFAULT_CUSTOM_MODEL)
- [ ] History dialog: кнопка "Clear older…" должна удалять записи до даты выбранного элемента, а не запрашивать дату вручную
- [ ] Session startup: если `claude` не найден (или процесс упал при старте), выводить внятное сообщение об ошибке; не оставлять сессию-зомби (сейчас таб открывается, но остаётся пустым)
- [ ] MCP server: выяснить, как проявляется ошибка запуска MCP-сервера (порт занят и т.п.) — в UI или только в логе; добавить внятную диагностику и вернуть пункт в Troubleshooting
