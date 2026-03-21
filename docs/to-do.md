# Баги
- [x] Панель ввода промрта теряет фокус после Send/Cancel. Оставлять фокус на поле промпта пока пользователь явно не переключится на что-то другое.
- [x] ChoiceMenuPanel не работает навигация стрелками и табом
- [ ] InputPromptPanel: После Cancel сессия продолжает считать, что claude занят
- [ ] 
    ```
    Claude has written up a plan and is ready to execute. Would you like to proceed?
    
     ❯ 1. Yes, clear context (27% used) and auto-accept edits
       2. Yes, auto-accept edits
       3. Yes, manually approve edits
       4. Type here to tell Claude what to change
    
     ctrl-g to edit in nano · ~/.claude/plans/whimsical-sleeping-cherny.md
    ```

    Строка "Type here to tell Claude what to change" должна быть хинтом в поле ввода, а не надпиью радиокнопки
- [ ] ChoiceMenuPanel: Enter не вызывает send
  Claude has written up a plan and is ready to execute. Would you like to proceed?

 ❯ 1. Yes, clear context (14% used) and auto-accept edits
   2. Yes, auto-accept edits
   3. Yes, manually approve edits
   4. Type here to tell Claude what to change

 ctrl-g to edit in nano · ~/.claude/plans/whimsical-sleeping-cherny.md

- [ ] При клавиатурной навигации по ChoiceMenuPanel фокус покидает эту панель и больше не возвращается. Нужно перебирать элементы циклически