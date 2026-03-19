# Plan: PromptResponsePanel UX — расширенный вид с Yes/No кнопками и радиокнопками

## Context
Реальный вывод Claude для нумерованного меню выглядит как:
- `❯ 1. Yes` — первая опция, отмеченная курсором `❯` → **дефолтная**
- `2. Yes, and always allow access to GrandOrgue/ from this project`
- `3. No`

Нужно: выделить Yes/No как цветные кнопки, прочие опции — радиокнопки, увеличить шрифт вопроса, добавить логику дефолтного фокуса.

---

## Шаг 1. `pom.xml` — инкремент версии до 0.9.9-SNAPSHOT

---

## Шаг 2. `PromptRequest` — добавить `defaultOptionIndex`

```java
// В PromptResponsePanel.java
public record PromptRequest(String text, List<Option> options, int defaultOptionIndex) {}
// defaultOptionIndex: 0-based индекс в options, или -1 если неизвестен
```

**Все места создания `PromptRequest` обновить** — добавить третий аргумент.

---

## Шаг 3. `TtyPromptDetector` — проставлять `defaultOptionIndex`

- Нумерованное меню → `defaultOptionIndex = 0` (trigger-строка с `❯` всегда первая)
- Inline prompt → `defaultOptionIndex = -1`
- JSON-событие → `defaultOptionIndex = -1`

---

## Шаг 4. `PromptResponsePanel.show()` — новый макет

### Логика разбиения опций
```
yesNoOptions  = options where display.trim().equalsIgnoreCase("Yes") or "No"
otherOptions  = остальные
```

### Структура (BoxLayout Y_AXIS):

**Строка 1 — вопрос** (если `req.text()` непустой):
```java
JLabel label = new JLabel("<html>..." + text + "...</html>");
// Шрифт — вдвое больше стандартного: label.setFont(label.getFont().deriveFont(label.getFont().getSize() * 2f))
```

**Строка 2 — кнопки Yes / No** (если `yesNoOptions` непусты):
- Yes: `setBackground(new Color(34, 139, 34))`, `setForeground(Color.WHITE)`, `setOpaque(true)`
- No: `setBackground(new Color(178, 34, 34))`, `setForeground(Color.WHITE)`, `setOpaque(true)`
- Клик → немедленный `submitAnswer(option.response())`

**Строки 3..N — радиокнопки для `otherOptions`** (если есть):
- `ButtonGroup group = new ButtonGroup()`
- Для каждой опции: `JRadioButton rb = new JRadioButton(opt.display().trim())`
- `group.add(rb)`, добавить в панель по одной в строку

**Строка "свободный ввод"** (если `options.isEmpty()`):
- `JTextField field` — для свободного ввода; на Enter — `submitAnswer(field.getText().trim())`

**Нижняя строка — Send + Cancel** (всегда):
- `JButton sendBtn` — показывается только если есть радиокнопки или свободный ввод; отправляет `response` выбранной радиокнопки (или текст поля)
- `JButton cancelBtn` — **всегда присутствует**; вызывает `cancel()`; выровнен по правому краю через `Box.createHorizontalGlue()`
- Если есть только Yes/No (нет otherOptions и нет free-form): показывается только Cancel (без Send)

### Логика дефолтного фокуса
```
if defaultOptionIndex == -1 → нет дефолта
else if options[defaultOptionIndex] is Yes/No → requestFocusInWindow() на соответствующей кнопке
else → setSelected(true) на радиокнопке + requestFocusInWindow() на sendBtn
if free-form → requestFocusInWindow() на field
```

---

## Шаг 5. Тесты

### `PromptResponsePanelTest` — обновить + добавить:
- Обновить все `new PromptRequest(text, options)` → добавить `-1` или `0`
- `testShowWithOptionsCreatesButtons` → использовать "Yes"/"No", проверять JButton (не радио)
- `testYesButtonIsGreen` — проверить `btn.getBackground().equals(new Color(34,139,34))`
- `testNoButtonIsRed` — проверить красный цвет
- `testRadioButtonForNonYesNoOption` — для опции `"Maybe"` ожидать JRadioButton
- `testCancelAlwaysPresent` — Cancel есть при любом наборе опций (Yes/No, радио, free-form)
- `testSendAbsentWhenOnlyYesNo` — нет кнопки Send если только Yes+No
- `testSendPresentWhenOtherOptionsExist` — Send есть при наличии "других" опций
- `testDefaultRadioSelected` — `defaultOptionIndex=0`, опция "Maybe" → `JRadioButton.isSelected()`

### `TtyPromptDetectorTest` — обновить:
- Все `req.get()` проверки: добавить `assertEquals(0, req.get().defaultOptionIndex())` для numbered
- `assertEquals(-1, req.get().defaultOptionIndex())` для inline и JSON

---

## Критические файлы
- `src/main/java/io/github/nbclaudecodegui/ui/PromptResponsePanel.java`
- `src/main/java/io/github/nbclaudecodegui/process/TtyPromptDetector.java`
- `src/test/java/io/github/nbclaudecodegui/ui/PromptResponsePanelTest.java`
- `src/test/java/io/github/nbclaudecodegui/process/TtyPromptDetectorTest.java`
- `pom.xml`

---

## Верификация

### Автоматические тесты
```bash
mvn test -Dtest=PromptResponsePanelTest,TtyPromptDetectorTest
mvn package
```

### Ручное тестирование — промпты для Claude Code

Проект для всех сценариев: `/home/oleg/my-projects/grandorgue/GrandOrgue`
(открыть через Actions → Claude Code → выбрать этот проект).

**Сценарий 1: Yes + No + "always allow" (основной — 3 опции)**
Предусловие: нет постоянного разрешения (удалить `.claude/settings.json` или
запустить в первый раз для этого проекта).
```
write a file called /tmp/cc_panel_test.txt with content hello
```
Ожидается: крупный вопрос "Do you want to proceed?"; строка 2: [Yes] зелёный + [No] красный;
строка 3: ○ "Yes, and always allow access to GrandOrgue/…"; строка 4: [Send] [Cancel];
фокус на кнопке Yes (дефолт).

**Сценарий 2: Bash-команда (проверить число опций)**
```
run the command: echo hello_cc > /tmp/cc_bash_test.txt
```
Ожидается: аналогичный диалог (уточнить — 2 или 3 опции для bash).
Если 2: только [Yes] + [No] + [Cancel] (без Send).

**Сценарий 3: Нажать Yes — файл создаётся**
В диалоге сценария 1 нажать [Yes].
Проверить: `cat /tmp/cc_panel_test.txt` выводит `hello`.

**Сценарий 4: Нажать No — операция отменяется**
В диалоге сценария 1 нажать [No].
Проверить: файл `/tmp/cc_panel_test.txt` не создан.

**Сценарий 5: Нажать Cancel — панель скрывается**
В любом активном диалоге нажать [Cancel].
Ожидается: панель скрывается, inputPanel возвращается,
Claude ждёт следующего ввода (не блокируется).

**Сценарий 6: "Always allow" — радиокнопка + Send**
В диалоге сценария 1 выбрать радиокнопку "Yes, and always allow…", нажать [Send].
Ожидается: PTY получает "2\r"; файл создаётся; следующий запрос на запись
не требует подтверждения.

---

### Python-скрипты в `claude-launch-tests/`

Все скрипты запускают `claude` через PTY (модуль `pty`), как это делает плагин.
Проект: `/home/oleg/my-projects/grandorgue/GrandOrgue`.

**`test_panel_scenario1_write_file.py`**
- Запускает `claude` в PTY, отправляет:
  `write a file called /tmp/cc_panel_test.txt with content hello\n`
- Читает PTY-вывод, применяет ESC[NC→пробелы + ANSI-стрипинг
- Детектирует меню Python-паттернами (аналог `tty-patterns.properties`)
- Assert: найден PromptRequest, 3 опции, опция[0] содержит "Yes", опция[2] содержит "No"
- Отправляет `3\n` (No) в PTY, завершает

**`test_panel_scenario2_bash.py`**
- Промпт: `run the command: echo hello_cc > /tmp/cc_bash_test.txt\n`
- Фиксирует количество опций (2 или 3) — вывод для документирования
- Отправляет `1\n` (Yes), проверяет что файл создан

**`test_panel_detector_replay.py`** *(без запуска claude)*
- Содержит константу `RAW_LINES` — сохранённые строки из `messages.log`
  (реальный PTY-сеанс с меню)
- Применяет ESC[NC→пробелы + ANSI-стрипинг
- Прогоняет через Python-эмуляцию TtyPromptDetector
- Assert: PromptRequest обнаружен с правильными опциями
- Запускается мгновенно, не требует claude
