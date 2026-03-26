# Claude Code GUI — NetBeans Plugin

A NetBeans IDE plugin that embeds the [Claude Code](https://claude.ai/code) CLI as a PTY-based terminal session directly inside the IDE. Each session runs in its own dockable window with a full JediTerm terminal widget — Claude's TUI renders natively including permission prompts and progress indicators.

Current version: **0.13.12-SNAPSHOT**

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
target/netbeans-claude-code-gui-1.0-SNAPSHOT.nbm
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

### Process startup

When the user opens a session, `ClaudeProcess.start()`:

1. Looks up `ClaudeCodeStatusService` to get the running MCP SSE server port (default **28991**, configurable in Tools → Options → Claude Code).
2. Merges two entries into `{workingDir}/.claude/settings.local.json`:
   - `mcpServers.netbeans` — SSE transport pointing to `http://localhost:{port}/sse`
   - `hooks.PreToolUse[matcher=Edit|Write|MultiEdit]` — HTTP hook pointing to `http://localhost:{port}/hook`

   The write is a **merge**, not an overwrite: any other MCP servers, hooks, or keys the user has placed in the file are left untouched; only the plugin's own keys are added or updated.
3. Launches `claude` (no `--print`) as a PTY process via `pty4j` with `TERM=xterm-256color`.
4. Bridges the PTY to a JediTerm widget via `PtyTtyConnector`. The full Claude TUI renders natively inside the IDE.

When the session **stops**, the plugin removes its own keys from `settings.local.json`. If the file becomes empty after cleanup (the common case when it was created fresh by the plugin) the file is deleted. If the file contained user-provided content only the plugin's keys are removed and the file is written back.

The MCP SSE server (`MCPSseServer`, Jetty) starts when the plugin module is installed (`ClaudeCodeInstaller`) and runs for the lifetime of the IDE session. It is shared across all Claude sessions.

### Communication channels

Three independent channels connect the plugin and Claude:

#### 1. PTY — terminal I/O

- **Claude → plugin:** raw TUI bytes (ANSI escape sequences). JediTerm renders them. `TtyPromptDetector` / `StreamJsonParser` pattern-match the stream to detect interactive prompts (numbered menus, yes/no questions).
- **Plugin → Claude:** raw bytes written to the PTY master. User text from the input bar is sent as-is. Option selection sends the option number + `\r`. Ctrl+C (interrupt) sends byte `0x03`.

#### 2. MCP SSE — IDE tools and resources

Claude connects to `GET /sse` immediately after startup (it reads the URL from `settings.local.json`). The SSE stream is kept open for the entire session.

- **Claude → plugin:** JSON-RPC 2.0 requests are `POST`-ed to `/messages`. The HTTP response is always `202 Accepted` (empty body). `NetBeansMCPHandler` dispatches the call to the appropriate tool class.
- **Plugin → Claude:** JSON-RPC responses are pushed back over the SSE stream (`data:` frames). Synchronous tools return immediately; asynchronous tools (`permission_prompt`) call `sendAsyncToolResponse()` later, which enqueues the JSON-RPC response into the SSE queue.
- Keep-alive: the server sends `ping` comments every 5 s if the queue is idle.

Available MCP tools: `openFile`, `getWorkspaceFolders`, `getOpenEditors`, `getCurrentSelection`, `getDiagnostics`, `checkDocumentDirty`, `saveDocument`, `close_tab`, `closeAllDiffTabs`, `openDiff`, `permission_prompt`.

The server also pushes `selection_changed` notifications to Claude whenever the caret moves in an editor.

#### 3. PreToolUse HTTP hook — file-change interception

Configured via `hooks.PreToolUse` in `settings.local.json`. Before executing `Edit`, `Write`, or `MultiEdit`, Claude POSTs the tool call JSON to `POST /hook`.

- **Claude → plugin:** hook JSON body with `tool_name` and `tool_input` (file path + change).
- **Plugin:** `HookServlet` computes the before/after file content, opens a `FileDiffTab`, and **blocks** on a `CompletableFuture` (timeout 590 s, just under Claude's 600 s limit).
- **Plugin → Claude:** when the user clicks Accept/Reject/Cancel, `DiffTabTracker.resolveHook()` completes the future with one of:
  - `{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow"}}`
  - `{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny",...}}`
  - `{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"ask"}}` (fallback/timeout → Claude shows its own built-in prompt)

### MVC architecture

The session UI follows a three-layer MVC structure:

| Layer | Package | Key class | Role |
|-------|---------|-----------|------|
| Model | `model/` | `ClaudeSessionModel` | Owns all session state (`SessionLifecycle`, edit mode, model list, choice menu, prompt history). Fires `ClaudeSessionModelListener` notifications on the EDT. Hosts the static `EDIT_MODE_REGISTRY` for cross-thread access by `NetBeansMCPHandler`. |
| Controller | `controller/` | `ClaudeSessionController` | Process lifecycle, PTY writes, screen-poll timers, model/edit-mode detection, `parseModelDiscovery`. Has no layout components; communicates state changes through the model only. |
| View | `ui/` | `ClaudePromptPanel` | Passive View: builds Swing layout, implements `ClaudeSessionModelListener` to keep UI in sync, delegates user gestures to the controller. |

`SessionLifecycle` state machine:

```
showChatUI()                    → STARTING
detectInputPromptReady() = true → READY   (one-shot, from STARTING)
sendPrompt() / discoverModels() → WORKING
onClaudeIdle()                  → READY
```

### Component map

| Package | Responsibility |
|---------|---------------|
| `model/` | `ClaudeSessionModel` — session state + listener dispatch; `SessionLifecycle` — state enum; `ChoiceMenuModel` — interactive-prompt data |
| `controller/` | `ClaudeSessionController` — PTY lifecycle, screen polling, model switching, `parseModelDiscovery` |
| `process/` | `ClaudeProcess` — PTY lifecycle + `settings.local.json` generation; `PtyTtyConnector` — PTY↔JediTerm bridge; `StreamJsonParser` — lightweight NDJSON parser |
| `ui/` | `ClaudeSessionTopComponent` — one TC per session; `ClaudePromptPanel` — passive View, implements `ClaudeSessionModelListener`; `ChoiceMenuPanel` — interactive choice UI; `FileDiffTab` + `FileDiffPermissionPanel` — diff TopComponent with permission bar; `MarkdownRenderer` — markdown→HTML |
| `settings/` | `ClaudeCodePreferences` (default MCP port 28991); `ClaudeCodeOptionsPanelController` / `ClaudeCodeOptionsPanel` — Tools→Options (General + Profiles tabs); `ClaudeProfile`, `ClaudeProfileStore` — named profiles with isolated `CLAUDE_CONFIG_DIR`, auth credentials, proxy settings, and extra env vars; `ClaudeProjectProperties` — per-project profile assignment |
| `actions/` | `ClaudeCodeAction` — toolbar button; `OpenWithClaudeAction` — project context menu |
| `org.openbeans.claude.netbeans` | `MCPSseServer` — Jetty HTTP server (`/sse`, `/messages`, `/hook`); `NetBeansMCPHandler` — MCP dispatcher + PreToolUse hook handler; reads edit mode via `ClaudeSessionModel.EDIT_MODE_REGISTRY`; `tools/` — `PermissionPromptTool`, `DiffTabTracker`, `OpenDiff`, and other IDE tools |

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

### File-change permissions

When Claude wants to edit a file, the plugin can intercept the request and show a diff view before allowing it. A permission bar at the bottom of the diff tab lets you:

- **✓ Accept** — allow the change
- **✗ Reject** — deny the change (with an optional reason sent to Claude)
- **Cancel** — deny and interrupt Claude's running prompt (sends Ctrl+C to the PTY)
- **× (close tab)** — treated as Reject

This works via two integration paths that share the same UI (`FileDiffTab` + `PermissionPanel`):
1. **PreToolUse HTTP hook** — Claude calls the plugin's endpoint before each write.
2. **`permission_prompt` MCP tool** — Claude invokes the tool directly.

After any action, the Claude session window is re-activated automatically.

### Profiles

Multiple **named profiles** can be configured in Tools → Options → Claude Code → **Profiles**. Each profile has:

- **Connection type** — Claude managed (no extra vars), Subscription (`CLAUDE_CODE_OAUTH_TOKEN`), Claude API (`ANTHROPIC_API_KEY`), or Other API (`ANTHROPIC_API_KEY` + `ANTHROPIC_BASE_URL`).
- **Proxy settings** — inherit system env, force no proxy, or supply custom `HTTP_PROXY`/`HTTPS_PROXY`/`NO_PROXY` values.
- **Extra env vars** — arbitrary key/value pairs for Bedrock, Vertex, etc.
- **Isolated config dir** — each non-Default profile gets its own `CLAUDE_CONFIG_DIR` under the configured profiles directory (default: `~/.netbeans/claude-profiles/<name>/`), so history, settings, and credentials are fully separated.

Profiles can be assigned per-project via **right-click → Properties → Claude Code**, or selected per-session in the control bar combo before clicking **Open**.

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
      ui/               ClaudeSessionTopComponent, ClaudePromptPanel,
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
| 10 | MCP SSE server, NetBeans IDE tools for Claude CLI — [test plan](docs/manual-test-mcp.md) | ✅ |
| 11 | Unified diff viewer with Accept/Reject/Cancel (`FileDiffTab`, `PermissionPanel`) | ✅ |
| 12 | Model picker, edit-mode selector, split pane input, status bar | ✅ |
| 13 | Named profiles: isolated `CLAUDE_CONFIG_DIR`, auth credentials, proxy, extra env vars; per-project assignment | ✅ |
| 14 | Prompt history | planned |

---

## Third-party code

The MCP server integration (package `org.openbeans.claude.netbeans`) is based on
[claude-code-netbeans](https://github.com/emilianbold/claude-code-netbeans)
by Emilian Marius Bold, used under the **ISC License**:

> Copyright (c) 2025 Emilian Marius Bold
>
> Permission to use, copy, modify, and distribute this software for any purpose
> with or without fee is hereby granted, provided that the above copyright notice
> and this permission notice appear in all copies.
>
> THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
> REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND
> FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
> INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
> LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
> OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
> PERFORMANCE OF THIS SOFTWARE.

**Changes made:** updated target NetBeans version from RELEASE190 to RELEASE230;
integrated into the `netbeans-claude-code-gui` plugin build alongside the PTY terminal component.

---

## License

Apache License 2.0
