# Claude Code GUI — NetBeans Plugin

A NetBeans IDE plugin that embeds the [Claude Code](https://claude.ai/code) CLI as a PTY-based terminal session directly inside the IDE. Each session runs in its own dockable window with a full JediTerm terminal widget — Claude's TUI renders natively including permission prompts and progress indicators.

Current version: **0.9.16-SNAPSHOT** (Stage 9 complete)

---

## Requirements

- Apache NetBeans 23 (RELEASE230) or later
- Java 17+
- Maven 3.8+
- `claude` CLI installed and available in PATH

---

## Build & Install

### Build the NBM package

```bash
mvn nbm:nbm
```

The installable plugin file is created at:

```
target/nb-claude-code-gui-1.0-SNAPSHOT.nbm
```

### Install into NetBeans

1. Open NetBeans → **Tools → Plugins**
2. Switch to the **Downloaded** tab
3. Click **Add Plugins…** and select the `.nbm` file
4. Click **Install** and follow the wizard
5. Restart NetBeans when prompted

### Build commands

```bash
mvn package              # Full build with tests
mvn package -DskipTests  # Build without tests
mvn test                 # Run all unit tests
```

---

## Architecture

Claude is launched **without `--print`** so its full TUI runs inside a [JediTerm](https://github.com/JetBrains/jediterm) terminal widget. `pty4j` creates the PTY; `PtyTtyConnector` bridges it to JediTerm's `TtyConnector` interface.

Each session is an independent `ClaudeSessionTopComponent` (a NetBeans dockable window). Sessions can be moved, stacked, or floated independently. Closing a window while a PTY process is running shows a confirmation dialog; the PTY process receives SIGHUP on IDE exit.

### Component map

| Package | Responsibility |
|---------|---------------|
| `process/` | `ClaudeProcess` — PTY lifecycle; `PtyTtyConnector` — PTY↔JediTerm bridge; `StreamJsonParser` — lightweight NDJSON parser |
| `ui/` | `ClaudeSessionTopComponent` — one TC per session; `ClaudeSessionPanel` — terminal + top bar; `PromptResponsePanel` — interactive question panel; `MarkdownRenderer` — markdown→HTML |
| `settings/` | `ClaudeCodePreferences`; `ClaudeCodeOptionsPanelController` / `ClaudeCodeOptionsPanel` — Tools→Options |
| `actions/` | `ClaudeCodeAction` — toolbar button; `OpenWithClaudeAction` — project context menu |

---

## Usage

### Opening a session

**From the toolbar:** click the Claude Code button in the Build toolbar. If no free session window exists a new one is created.

**From the context menu:** right-click any project node in the Projects Explorer → **Open with Claude Code**. A session window bound to that project's root directory opens (or is focused if already open).

### Directory selector

Each session window has a directory bar at the top:

- The drop-down lists all currently open projects for quick selection.
- The **Browse…** button opens a directory chooser.
- Once confirmed (**Open**), the bar locks and the window title updates to the project name or directory basename.
- The **⚙** button opens the plugin settings (Tools → Options → Claude Code).

### Interactive prompts

When Claude asks a question (permission request, choice, confirmation), a `PromptResponsePanel` appears between the terminal and the input bar. Click a button or press **ESC** / **Cancel** to dismiss.

### Closing sessions

Closing a session window while a PTY process is running shows a confirmation dialog. Confirmed close stops the process. PTY processes are children of the IDE JVM — they receive SIGHUP automatically if the IDE exits unexpectedly.

---

## Project Structure

```
src/
  main/
    java/io/github/nbclaudecodegui/
      actions/          ClaudeCodeAction, OpenWithClaudeAction
      process/          ClaudeProcess, PtyTtyConnector, StreamJsonParser
      settings/         ClaudeCodePreferences, ClaudeCodeOptionsPanel(Controller)
      ui/               ClaudeSessionTopComponent, ClaudeSessionPanel,
                        PromptResponsePanel, MarkdownRenderer
    resources/io/github/nbclaudecodegui/
      layer.xml         NetBeans layer registration
      Bundle.properties Localisation strings
      icons/
  test/
    java/io/github/nbclaudecodegui/
    resources/fixtures/ JSON test fixtures
```

---

## Development Stages

| Stage | Feature | Status |
|-------|---------|--------|
| 1 | Dummy NBM — installable plugin skeleton | ✅ |
| 2 | Settings page with Claude icon | ✅ |
| 3 | Toolbar button + empty window | ✅ |
| 4 | Per-project tabbed window, context menu, session persistence | ✅ |
| 5 | First working chat (subprocess, multi-line input, send-key pref) | ✅ |
| 6 | Stream-JSON parsing + markdown output + PromptResponsePanel | ✅ |
| 7 | Embedded JediTerm terminal (PTY, full Claude TUI) | ✅ |
| 8 | Refactor: each session = independent TopComponent | ✅ |
| 9 | PromptResponsePanel fixes (visibility, flush timer, ESC/Cancel) | ✅ |
| 10 | Diff viewer with Accept/Reject | planned |
| 11 | File attachments (`@path` syntax) | planned |
| 12 | Full settings integration + session persistence | planned |
| 13 | MCP integration (optional) | planned |

---

## License

Apache License 2.0
