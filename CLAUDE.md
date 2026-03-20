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

A NetBeans IDE plugin that embeds Claude Code CLI as a PTY-based terminal session connected to the IDE via three channels: PTY, MCP SSE, and an HTTP hook.

### Process startup

`ClaudeProcess.start(workingDir)`:
1. Looks up `ClaudeCodeStatusService` → gets the MCP SSE server port (default **28991**, configurable in Tools → Options).
2. Writes `{workingDir}/.claude/settings.local.json`:
   ```json
   {
     "mcpServers": {"netbeans": {"type":"sse","url":"http://localhost:{port}/sse"}},
     "hooks": {"PreToolUse": [{"matcher":"Edit|Write|MultiEdit","hooks":[{"type":"http","url":"http://localhost:{port}/hook"}]}]}
   }
   ```
3. Launches `claude` (no `--print`, `TERM=xterm-256color`) via `pty4j` `PtyProcessBuilder`.
4. `PtyTtyConnector` bridges the PTY master to JediTerm's `TtyConnector`. The full Claude TUI renders natively.

`MCPSseServer` (Jetty) starts at IDE startup (`ClaudeCodeInstaller`) and is shared across all sessions.

### Communication channels

#### PTY — terminal I/O
- **Claude → plugin:** ANSI TUI bytes rendered by JediTerm. `TtyPromptDetector` / `StreamJsonParser` pattern-match the stream to detect interactive prompts → `PromptResponsePanel` shown.
- **Plugin → Claude:** raw bytes to the PTY master. Text input from the input bar sent as-is. Option selected → number + `\r`. Cancel → byte `0x03` (Ctrl+C / SIGINT).

#### MCP SSE — IDE tools (`/sse` + `/messages`)
Claude keeps `GET /sse` open (long-lived SSE stream). JSON-RPC requests arrive as `POST /messages` (HTTP response is always `202 Accepted`). JSON-RPC responses go back **via SSE** (not the POST body — required by the MCP SSE spec).
- **Sync tools** — handler returns result, enqueued to SSE immediately.
- **Async tools** (`permission_prompt`) — handler returns `AsyncResponse`; when the user decides, `sendAsyncToolResponse()` enqueues the JSON-RPC response.
- **Notifications** — `selection_changed` pushed to Claude on every caret move in an editor.
- Keep-alive: `: ping` SSE comments every 5 s when queue is idle.

#### PreToolUse HTTP hook (`/hook`)
Configured via `settings.local.json`. Fires before `Edit`/`Write`/`MultiEdit`.
- Claude POSTs hook JSON → `HookServlet.doPost()` computes before/after content, calls `FileDiffTab.open()`, then **blocks** on `CompletableFuture` (590 s timeout, just under Claude's 600 s limit).
- User action → `DiffTabTracker.resolveHook()` completes the future → HTTP response body:
  - `allow` → `{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow"}}`
  - `deny` (+ optional reason) → same with `"permissionDecision":"deny"`
  - `ask` (timeout/close) → Claude falls back to its built-in PTY permission dialog

### Component Map

| Package | Responsibility |
|---------|---------------|
| `process/` | `ClaudeProcess` — PTY lifecycle; `PtyTtyConnector` — PTY↔JediTerm bridge; `StreamJsonParser` — lightweight NDJSON parser (no external JSON lib) |
| `ui/` | `ClaudeSessionTopComponent` — one TC per session; `ClaudeSessionPanel` — terminal + top bar; `PromptResponsePanel` — interactive question panel (Yes/No buttons, radio buttons, free-form input); `PermissionPanel` — Accept/Reject/Cancel bar for file-change diffs; `FileDiffTab` — opens a diff TopComponent with `PermissionPanel`; `MarkdownRenderer` — markdown→HTML for output panes |
| `settings/` | `ClaudeCodePreferences` — NbPreferences wrapper; `ClaudeCodeOptionsPanelController` / `ClaudeCodeOptionsPanel` — Tools→Options integration |
| `actions/` | `ClaudeCodeAction` — toolbar button; `OpenWithClaudeAction` — project node context menu |
| `org.openbeans.claude.netbeans` | `MCPSseServer` — SSE/HTTP server Claude connects to; `NetBeansMCPHandler` — handles MCP requests including PreToolUse hook; `tools/` — `OpenDiff`, `PermissionPromptTool`, `DiffTabTracker` |

### Session lifecycle
1. User clicks toolbar → `ClaudeSessionTopComponent` opens; user picks a directory → `ClaudeProcess.start()` writes `settings.local.json` and launches PTY.
2. PTY output renders in JediTerm; `TtyPromptDetector`/`StreamJsonParser` detect interactive prompts → `PromptResponsePanel` shown.
3. Claude connects to `GET /sse`; subsequent tool calls arrive as `POST /messages`; file edits trigger `POST /hook`.
4. Each session = isolated PTY process; closing window confirms if the process is running.

### File-change permission flow
Claude Code can ask permission before editing files via two paths, both using `FileDiffTab` + `PermissionPanel`:

1. **PreToolUse HTTP hook** (`NetBeansMCPHandler.handlePreToolUse`) — Claude calls the plugin's SSE endpoint before each write; plugin opens a diff tab; response is a CompletableFuture resolved by `DiffTabTracker.resolveHook()`.
2. **`permission_prompt` MCP tool** (`PermissionPromptTool`) — Claude invokes the tool directly; plugin opens the same diff tab; result is returned via `AsyncHandler` stored in `DiffTabTracker`.

Both paths show the same UI: `[✓ Accept] [✗ Reject] [Reject reason (Optional)] [Cancel]`.
- **Accept** — allows the change
- **Reject** — denies the change with an optional reason sent to Claude
- **Cancel** — denies the change and sends Ctrl+C (byte `0x03`) to the PTY, interrupting Claude's running prompt
- **× (close tab)** — treated as Reject (hook returns "ask", MCP returns deny)

After any action the originating `ClaudeSessionTopComponent` is re-activated automatically.

### Registration
`layer.xml` registers the Options category (position 1500) and toolbar action. Icon PNGs are generated at build time from `cc-gui-icon.svg` via the Groovy script in `pom.xml` using Apache Batik.

## Testing
- Unit tests: JUnit 5 in `src/test/java/`
- Integration test: `ClaudeCodePluginIT` uses `NbModuleSuite` for full IDE lifecycle
- Test fixtures (JSON): `src/test/resources/fixtures/`
- `ClaudeProcessTest` skips on Windows; uses a fake `claude` shell script
