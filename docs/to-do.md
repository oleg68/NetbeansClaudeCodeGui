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

# Features

- [ ] Возможность подключения md-вьюера в FileDiff

