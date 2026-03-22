# Bugs
- [x] Accept/Decline panel:
    - Нет циклисеского перемещения фокуса
    - Неправильный размер кнопки Cancel. Должна быть центрирована по высоте с отступами со всез сторон, а не растягиваться.
- [x] Изменить порядок режимов в комбо от более безопасного к менее безопасному
- [x] В панели ввода промпта Esc должен нажимать Cancel
- [x] ChoiceMenu:
    - обеспечить порядок  фокуса: Yes, No, радиокнопки, поля ввода при радиокнопках, Send, Cancel
    - Не делать надпись у радиокнопки Type... Располагать поле ввода справа от радиокнопки.
    - Отправка текстового поля: исправлено (PTY-тест показал: нужно сначала отправить номер опции, потом текст).
- [x] Не сохраняется размер области ввода промпта при рестарте netbeans.
- [x] Контекстное меню поля ввода промпта: включать/выключать доступность Prev/Next Message
- [ ] ChoiceMenu не показалось при запросе.
    ````
    ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
     Bash command                                                                                                                                                            

       grep -n "ICON_REJECT\|Reject\|Decline" /home/oleg/my-projects/NetbeansClaudeCodePlugin/src/main/java/io/github/nbclaudecodegui/ui/*.java | grep -v Binary             
       Run shell command                                                                                                                                                     

     Do you want to proceed?                                                                                                                                                 
     ❯ 1. Yes                                                                                                                                                                
      2. Yes, and don’t ask again for: grep -n "ICON_REJECT\|Reject\|Decline"                                                                                                
                                     /home/oleg/my-projects/NetbeansClaudeCodePlugin/src/main/java/io/github/nbclaudecodegui/ui/*.java                                       
       3. No                                                                                                                                                                 

     Esc to cancel · Tab to amend · ctrl+e to explain                                                                                                                        
    ```

    Эффект не стабилен: иногда похожие запросы вызывают срабатывание, иногда нет.
- [ ] Поле ввода промпта: поддержать переключение режимов по Shift+Tab (должен выбираться следующий режим из комбо)

# Features:
  - 