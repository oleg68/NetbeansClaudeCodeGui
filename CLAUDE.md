# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
mvn package                 # Full build with tests
mvn package -DskipTests     # Build without tests
mvn nbm:nbm                 # Package as installable NBM file
mvn test                    # Run all unit tests
mvn test -Dtest=ClassName   # Run a single test class
```

After every fix or change, increment the patch version in `pom.xml` and rebuild.

## Architecture

A NetBeans IDE plugin that embeds Claude Code CLI as a PTY-based terminal session.

### Key Design: PTY, not pipes
Claude is launched **without** `--print` so its full TUI runs inside a JediTerm terminal widget. `pty4j` creates the PTY, `PtyTtyConnector` bridges it to JediTerm's `TtyConnector` interface. This is why pty4j + JediTerm are required dependencies.

### Component Map

| Package | Responsibility |
|---------|---------------|
| `process/` | `ClaudeProcess` — PTY lifecycle; `PtyTtyConnector` — PTY↔JediTerm bridge; `StreamJsonParser` — lightweight NDJSON parser (no external JSON lib) |
| `ui/` | `ClaudeCodeTopComponent` — main window with `JTabbedPane`; `ClaudeSessionPanel` — per-tab UI + JediTermWidget; `PromptResponsePanel` — shown when Claude asks interactive questions; `MarkdownRenderer` — markdown→HTML for output panes |
| `settings/` | `ClaudeCodePreferences` — NbPreferences wrapper; `ClaudeCodeOptionsPanelController` / `ClaudeCodeOptionsPanel` — Tools→Options integration |
| `actions/` | `ClaudeCodeAction` — toolbar button; `OpenWithClaudeAction` — project node context menu |

### Session lifecycle
1. User clicks toolbar → `ClaudeCodeTopComponent` opens
2. User picks a directory in `ClaudeSessionPanel` → `ClaudeProcess.start()` launches PTY
3. PTY output renders in embedded `JediTermWidget`
4. `StreamJsonParser` detects interactive prompts → `PromptResponsePanel` surfaces option buttons
5. Each tab = isolated PTY process; closing window confirms if processes are running

### Registration
`layer.xml` registers the Options category (position 1500) and toolbar action. Icon PNGs are generated at build time from `cc-gui-icon.svg` via the Groovy script in `pom.xml` using Apache Batik.

## Testing
- Unit tests: JUnit 5 in `src/test/java/`
- Integration test: `ClaudeCodePluginIT` uses `NbModuleSuite` for full IDE lifecycle
- Test fixtures (JSON): `src/test/resources/fixtures/`
- `ClaudeProcessTest` skips on Windows; uses a fake `claude` shell script
