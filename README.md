# Claude Code GUI — NetBeans Plugin

A NetBeans IDE plugin that embeds the [Claude Code](https://claude.ai/code) CLI as a PTY-based terminal session directly inside the IDE. Each session runs in its own dockable window with a full JediTerm terminal widget — Claude's TUI renders natively including permission prompts and progress indicators.

Current version: **0.15.24-SNAPSHOT**

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
target/netbeans-claude-code-gui-0.15.24-SNAPSHOT.nbm
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

### Prompt history & favorites

The input bar maintains a persistent prompt history per project:
- **Ctrl+Up / Ctrl+Down** — navigate history entries in the input field
- **History** button (or Ctrl+H) — open the history popup: select an entry to load into the input bar, star it as a favorite, or delete it

**Favorites** can be global or per-project:
- **Favorites** button — open the favorites panel: Send, Move (global ↔ per-project), Rename, Reorder (↑↓), Delete
- Assign a keyboard shortcut to any favorite via **right-click → Assign shortcut…**

### Closing sessions

Closing a session window while a PTY process is running shows a confirmation dialog. Confirmed close stops the process. PTY processes are children of the IDE JVM — they receive SIGHUP automatically if the IDE exits unexpectedly.

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
| 14 | Prompt history & favorites: persistent history (Ctrl+Up/Down), popup list, global/per-project favorites, hotkey assignment | ✅ |
| 15 | File attachments: @path token insertion, DnD + Ctrl+V, @-completion popup, blue token highlight, `ui/common/` shared components | ✅ |
| 16 | FileDiff location config: inline panel or separate tab (boolean preference) | ✅ |
| 17 | Settings + full integration (auto-start, CLI path, send key) | planned |
| 18 | GitHub CI/CD + NBM publishing | planned |
| 19 | Help + user documentation | planned |

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
