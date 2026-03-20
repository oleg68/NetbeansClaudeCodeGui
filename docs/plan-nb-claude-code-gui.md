# Plan: Claude Code GUI — NetBeans Plugin

## Context

Build a new NetBeans plugin **Claude Code GUI** that combines:
- From **JeddictAi**: NetBeans patterns (`OptionsPanelController` settings, toolbar action, TopComponent window, navigation to settings)
- From **IdeaClaudeCodeGui**: launching Claude Code CLI as a subprocess, UI for it, Claude Code icons

The plugin is built incrementally — each stage produces a working, installable NBM.

> **Versioning:** scheme `0.<stage>.<fix>-SNAPSHOT`. First build of a stage: fix = 0. Each reinstall-requiring fix: increment fix. Examples: stage 1 → `0.1.0-SNAPSHOT`, stage 2 → `0.2.0-SNAPSHOT`, second fix of stage 2 → `0.2.1-SNAPSHOT`.

## Source Files (for reuse)

| What | Where |
|------|-------|
| Settings pattern | `submodules/JeddictAi/src/main/java/io/github/jeddict/ai/settings/AIAssistanceOptionsPanelController.java` |
| Toolbar action pattern | `submodules/JeddictAi/src/main/java/io/github/jeddict/ai/actions/ToolbarAction.java` |
| TopComponent pattern | `submodules/JeddictAi/src/main/java/io/github/jeddict/ai/components/AssistantChat.java` |
| Process launch | `submodules/IdeaClaudeCodeGui/src/main/java/com/github/claudecodegui/bridge/ProcessManager.java` |
| Claude icons | `submodules/IdeaClaudeCodeGui/src/main/resources/icons/` (cc-gui-icon.svg, logo-16.png, logo.png) |

---

## Plugin Structure

```
NetbeansClaudeCodePlugin/          ← repository root
├── pom.xml                        # Maven + nbm-maven-plugin
├── submodules/                    # existing submodules
│   ├── JeddictAi/
│   └── IdeaClaudeCodeGui/
└── src/main/
    ├── java/io/github/nbclaudecodegui/
    │   ├── actions/
    │   │   ├── ClaudeCodeAction.java          # Toolbar action
    │   │   └── OpenWithClaudeAction.java      # Context menu action
    │   ├── settings/
    │   │   ├── ClaudeCodeOptionsPanelController.java
    │   │   ├── ClaudeCodeOptionsPanel.java
    │   │   └── ClaudeCodePreferences.java
    │   ├── ui/
    │   │   ├── ClaudeSessionTopComponent.java  # One TC = one session; cancelCurrentPrompt()
    │   │   ├── ClaudeSessionPanel.java         # Session UI (terminal + top bar)
    │   │   ├── PromptResponsePanel.java        # Interactive questions (Yes/No, radio, free-form)
    │   │   ├── PermissionPanel.java            # [✓ Accept][✗ Reject][reason][Cancel]
    │   │   ├── FileDiffTab.java                # Diff TopComponent + PermissionPanel; shared by hook and MCP
    │   │   └── MarkdownRenderer.java           # Markdown → HTML
    │   └── process/
    │       ├── ClaudeProcess.java              # PTY lifecycle + settings.local.json merge/cleanup
    │       ├── PtyTtyConnector.java            # PTY ↔ JediTerm bridge
    │       └── StreamJsonParser.java           # NDJSON parser
    ├── java/org/openbeans/claude/netbeans/
    │   ├── MCPSseServer.java                   # Jetty: /sse /messages /hook
    │   ├── NetBeansMCPHandler.java             # JSON-RPC dispatcher + PreToolUse hook handler
    │   ├── ClaudeCodeInstaller.java            # Module lifecycle: starts MCPSseServer
    │   ├── ClaudeCodeStatusService.java        # Interface: isServerRunning(), getServerPort()
    │   └── tools/
    │       ├── DiffTabTracker.java             # Registry of pending diff tabs
    │       ├── PermissionPromptTool.java       # MCP tool: shows FileDiffTab, async response
    │       ├── OpenDiff.java
    │       ├── OpenFile.java
    │       ├── GetWorkspaceFolders.java
    │       ├── GetOpenEditors.java
    │       ├── GetCurrentSelection.java
    │       ├── GetDiagnostics.java
    │       ├── CheckDocumentDirty.java
    │       ├── SaveDocument.java
    │       ├── CloseTab.java
    │       └── CloseAllDiffTabs.java
    └── resources/io/github/nbclaudecodegui/
        ├── layer.xml
        ├── Bundle.properties
        └── icons/
            ├── claude-icon.png               # 16px (toolbar/tabs)
            └── claude-icon-32.png            # 32px (settings)
```

---

## Claude Code Communication Protocols

### Primary protocol: PTY + native TUI

Claude is launched **without `--print`** via PTY (pty4j); the full TUI renders inside JediTermWidget:

```
claude   (no flags, working directory = project root)
```

- PTY is created via `pty4j`; bridge to JediTerm is `PtyTtyConnector`
- `TERM=xterm-256color` is passed in the process environment
- The user interacts directly through the embedded terminal

`StreamJsonParser`, `MarkdownRenderer`, and `PromptResponsePanel` are retained for a possible hybrid mode in the future.

### MCP (Model Context Protocol) — implemented in stage 10

**MCP does not replace or modify the primary PTY protocol.** It runs alongside it via a separate HTTP server (Jetty, port 28991).

**Three communication channels:**

1. **PTY** — TUI bytes in both directions; `TtyPromptDetector` detects interactive questions → `PromptResponsePanel`
2. **MCP SSE** (`GET /sse` + `POST /messages`) — JSON-RPC 2.0; Claude POSTs requests, responses go over the SSE stream; async tools (`permission_prompt`) respond later via the same SSE
3. **PreToolUse HTTP hook** (`POST /hook`) — Claude POSTs before Edit/Write/MultiEdit; the servlet blocks until the user decides (up to 590 s); response is `allow`/`deny`/`ask`

**settings.local.json** is written to `{workingDir}/.claude/` before the PTY starts using merge logic (not overwrite): existing user keys are preserved. When the session stops, the plugin removes its own keys; if the file becomes empty it is deleted entirely.

---

## Stages

### Stage 1 — Dummy plugin (installable NBM) ✅

**Result:** minimal plugin installs in NetBeans without errors.

---

### Stage 2 — Settings page with Claude icon ✅

**Result:** Tools → Options → "Claude Code" tab with icon and CLI path field.

---

### Stage 3 — Toolbar button opening an empty window ✅

**Result:** toolbar button with Claude icon → click → empty window appears.

---

### Stage 4 — Window with tabs per working directory ✅

**Result:** `ClaudeCodeTopComponent` with `JTabbedPane`; session tabs; "Open with Claude Code" context menu on projects; "+" button; path persistence via `writeExternal/readExternal`.

> **Note:** The JTabbedPane architecture was replaced in stage 7b. All functionality is preserved in the new scheme.

---

### Stage 5 — First working chat with Claude Code ✅

**Result:** subprocess (stream-json + stdin/stdout), multi-line input, configurable send key, slash commands.

---

### Stage 6 — Stream-JSON parsing + formatted output + markdown ✅

**Result:** `StreamJsonParser` with no external dependencies; `MarkdownRenderer` (headings, code, lists, bold/italic); `session_id` → `--resume`; `PromptResponsePanel` for interactive questions.

---

### Stage 7 — Embedded terminal (JediTerm) for full Claude TUI ✅

**Result:** `PtyTtyConnector` + `JediTermWidget` inside `ClaudeSessionPanel`; Claude TUI renders natively including permission prompts and progress indicators.

**Implemented:**
- `PtyTtyConnector.java` — PTY ↔ JediTerm bridge (`TtyConnector` interface)
- `ClaudeProcess.java` simplified: `start(workingDir)` → `PtyProcess`; `claude` command with no flags
- `ClaudeSessionPanel.java` — `showChatUI()` creates `JediTermWidget` instead of pane/area

---

### Stage 8 — Refactor: each session = independent TopComponent ✅

**Goal:** fix three problems of the previous architecture:
1. Toolbar did not dock the window (`dockToRightIfNeeded` was not called)
2. Single `ClaudeCodeTopComponent` with JTabbedPane — sessions cannot be moved independently
3. `componentClosed()` killed the PTY on ResetWindows

**Implemented:**

| File | Action |
|------|--------|
| `ui/ClaudeSessionTopComponent.java` | **created** — one TC = one session |
| `ui/ClaudeSessionPanel.java` | added `detachTerminal`, `reattachTerminal`, `hasLiveProcess`, `autoStart` |
| `ui/ClaudeCodeTopComponent.java` | **removed** |
| `ui/TabHeader.java` | **removed** |
| `actions/ClaudeCodeAction.java` | finds a free TC or creates a new one |
| `actions/OpenWithClaudeAction.java` | finds TC by directory or creates a new one |

**Key decisions:**
- `componentClosed()` → `panel.detachTerminal()` — removes the widget, PTY stays alive
- `canClose()` → `panel.canClose()` — confirmation dialog + `stopProcess()` on confirm
- PTY processes are children of the IDE JVM: they receive SIGHUP on IDE crash, no zombies
- `writeExternal/readExternal` — persists directory path; `componentOpened` restores the session or reattaches a live terminal

**Version:** `0.8.0-SNAPSHOT`

---

### Stage 9 — PromptResponsePanel: response panel for Claude questions ✅

**Result:** `PromptResponsePanel` is shown when Claude waits for a response; fixed panel visibility, flush timer, and ESC on Cancel.

**Version:** `0.9.16-SNAPSHOT`

---

### Stage 10 — MCP SSE server + IDE tools ✅

**Version:** `0.10.x-SNAPSHOT`

**Implementation:**

- `org.openbeans.claude.netbeans.MCPSseServer` — Jetty HTTP server; three endpoints:
  - `GET /sse` — long-lived SSE stream; sends `event:endpoint /messages` on connect; keep-alive pings every 5 s
  - `POST /messages` — receives JSON-RPC 2.0 from Claude, responds `202 Accepted`; responses go via SSE
  - `POST /hook` — PreToolUse HTTP hook; blocks until the user decides (timeout 590 s); returns `allow`/`deny`/`ask` JSON
- `org.openbeans.claude.netbeans.NetBeansMCPHandler` — MCP method dispatcher; supports `initialize`, `tools/list`, `tools/call`, `resources/list`, `resources/read`, `prompts/list`; manages selection tracking (`selection_changed` notifications → SSE)
- `tools/` — `OpenFile`, `GetWorkspaceFolders`, `GetOpenEditors`, `GetCurrentSelection`, `GetDiagnostics`, `CheckDocumentDirty`, `SaveDocument`, `CloseTab`, `CloseAllDiffTabs`, `OpenDiff`, `PermissionPromptTool`
- `tools/DiffTabTracker` — registry of pending diff tabs; separate maps for hook futures and MCP handlers
- `ClaudeCodeInstaller` — starts `MCPSseServer` at IDE startup; default port **28991**, configurable in Tools → Options

**Config written before PTY start:**
```json
{
  "mcpServers": {"netbeans": {"type": "sse", "url": "http://localhost:<PORT>/sse"}},
  "hooks": {"PreToolUse": [{"matcher": "Edit|Write|MultiEdit", "hooks": [{"type": "http", "url": "http://localhost:<PORT>/hook"}]}]}
}
```

**Manual test plan:** [`docs/manual-test-mcp.md`](manual-test-mcp.md)

---

### Stage 11 — Diff viewer: Accept/Reject/Cancel ✅

**Version:** `0.11.x-SNAPSHOT`

**Implementation:** two file-change interception paths share a common UI.

**`ui/PermissionPanel`** — single row `[✓ Accept] [✗ Reject] [Reject reason (Optional)] [Cancel]`
- `ICON_ACCEPT = "\u2713"`, `ICON_REJECT = "\u2717"` — same constants used in `PromptResponsePanel` for Yes/No buttons
- **Accept** → allow; **Reject** → deny + optional reason; **Cancel** → deny + Ctrl+C to PTY (byte `0x03`); **× (close tab)** → hook: ask, MCP: deny

**`ui/FileDiffTab`** — static `open(filePath, before, after, tabName, onAccept, onReject, onCancel, onClose)`:
- Opens a `TopComponent` with the NetBeans Diff API
- `AtomicBoolean decided` — prevents double-firing of `onClose` after a button click
- After any action, activates the `ClaudeSessionTopComponent` for the matching `workingDir`
- `public static cancelCurrentPromptForFile(filePath)` — finds the session TC and calls `cancelCurrentPrompt()`

**Path 1 — PreToolUse hook** (`NetBeansMCPHandler.handlePreToolUse`):
- Claude POSTs to `/hook`; servlet blocks on a `CompletableFuture`
- Decision is recorded via `DiffTabTracker.resolveHook()` → HTTP response goes to Claude

**Path 2 — MCP tool `permission_prompt`** (`PermissionPromptTool`):
- Claude calls the tool via JSON-RPC
- Result is sent via `AsyncHandler.sendResponse()` → SSE

**Dependency:** `org-netbeans-modules-diff`

**before/after computation** (in `NetBeansMCPHandler.handlePreToolUse`):
- `Edit` — reads file, applies `old_string → new_string`
- `Write` — before = current file or `""`, after = `content`
- `MultiEdit` — applies edits sequentially

---

### Stage 11 (fix) — settings.local.json merge + cleanup ✅

**Version:** `0.11.12-SNAPSHOT`

**Problem:** `writeSettingsLocalJson` did a full overwrite (destroying user content); the file was not deleted after the session.

**Implementation in `ClaudeProcess`:**
- `mergeSettingsJson(existingJson, port)` — targeted merge via Jackson: adds/updates `mcpServers.netbeans` and the `PreToolUse[matcher=Edit|Write|MultiEdit]` entry; all other keys are left untouched
- `cleanedSettingsJson(existingJson)` — removes our keys; returns `null` if the file became empty
- `cleanupSettingsLocalJson(workingDir)` — deletes the file on `null` result, otherwise writes it back
- `stop()` triggers cleanup automatically via the stored `workingDir` field

---

### Stage 12 — Prompt panel improvements (planned)

**Goal:** improve the UX of the prompt input area and add a session status bar.

**What to add:**

*Input area:*
- `inputArea` in `JScrollPane` with resizable split (`JSplitPane` between terminal and input field)
- Context menu on `inputArea`: Cut / Copy / Paste + "History", "Favorites", "Clear"
- Ctrl+Up / Ctrl+Down — in-memory command history navigation for the current session
- Icons on Send and Cancel buttons (reuse pattern from `PermissionPanel`)

*Status bar (bottom of `ClaudeSessionPanel`):*
- **Model** combo box — lists available Claude models; selected value passed to `claude` CLI via `--model` flag
- **Edit mode** combo box — e.g. `auto` / `edit` / `diff`; passed via `--edit-mode` flag (or equivalent)
- **Claude version** label — populated once at session start by running `claude --version` and parsing stdout
- **Current plan** label — shows the active plan name parsed from Claude's PTY output (e.g. from `CLAUDE.md` plan header or a `plan:` line in the stream)

**Files:** `ClaudeSessionPanel.java`, `ClaudeProcess.java`, `ClaudeCodePreferences.java`

---

### Stage 13 — Claude Code profiles (planned)

**Goal:** support multiple API keys / proxy settings via named profiles.

**Profile fields:**
- Profile name
- `ANTHROPIC_API_KEY`, `ANTHROPIC_BASE_URL`, `HTTPS_PROXY`, `HTTP_PROXY`
- Type: `claude-subscription` / `claude-api` / `third-party-api`

**Two ways to assign a profile:**
1. **Project Properties** (right-click → Properties → Claude Code) — persistent per-project assignment
2. **When opening a new session** (toolbar or context menu) — picker dialog

**Components:**
- `settings/ClaudeProfile.java` — POJO
- `settings/ClaudeProfileStore.java` — stored in `NbPreferences`
- `settings/ClaudeCodeOptionsPanel.java` — profile table with Add / Edit / Delete
- `settings/ClaudeProjectProperties.java` — Project Properties panel
- `actions/ClaudeCodeAction.java` — profile picker when opening a session
- `process/ClaudeProcess.java` — pass profile env vars to PTY

---

### Stage 14 — Prompt history (planned)

**Goal:** persistent per-project prompt history.

**What to add:**
- Store history in `NbPreferences` as a JSON array with timestamps
- Settings: max depth (default 200), TTL
- Popup list UI with columns: time, prompt preview
- Actions on an entry: **Send to input area**, **Add to favorites**, **Delete**
- Force-clear: "Delete entries older than…" (dialog with date picker)
- Ctrl+Up / Ctrl+Down in `inputArea` — navigate persistent history

**Components:**
- `history/PromptHistoryStore.java` — read / write / trim
- `ui/PromptListPanel.java` — reusable control (also used in stage 15)

---

### Stage 15 — Prompt favorites (planned)

**Goal:** save frequently used prompts with optional hotkey support.

**Two levels:**
- Global (file in `~/.netbeans/`)
- Per-project (`NbPreferences`)

**What to add:**
- `PromptListPanel` (from stage 14) — tabbed "History | Favorites" view
- Actions on a favorite: **Send**, **Move to global**, **Rename**, **Delete**, **Reorder** (↑↓ buttons + drag & drop)
- Assign a hotkey to a specific favorite (NetBeans Keymap API)

**Components:**
- `favorites/PromptFavoritesStore.java`
- `ui/PromptListPanel.java` — extension from stage 14
- `actions/PromptFavoriteAction.java` — dynamically registered Actions for hotkeys

---

### Stage 16 — File attachments in prompt (planned)

**Goal:** attach files to a prompt — prepended as `@/absolute/path` before the prompt text on send.

**UI:** horizontal chip panel above `inputArea` (appears when the first file is added):
```
┌──────────────────────────────────────────┐
│ [src/Foo.java ×]  [README.md ×]          │
├──────────────────────────────────────────┤
│ inputArea...                             │
└──────────────────[Attach][Send][Cancel]──┘
```

**Ways to add files:**
1. **Attach** button → JFileChooser
2. Context menu in the Projects tree: "Add to Claude Prompt"
3. Drag & drop a file onto `inputPanel`

**Components:**
- `ui/AttachedFilesPanel.java` — chip panel (filename + "×")
- `actions/AddToClaudePromptAction.java` — context menu on file/folder nodes
- `ui/ClaudeSessionPanel.java` — Attach button, DnD handler; prepend `@path` in `sendPrompt()`
- `layer.xml` — register `AddToClaudePromptAction`

**Tests:**
- `AttachedFilesPanelTest.java` — add file → chip; "×" → chip removed; `clearFiles()` → empty
- `FileAttachmentPromptTest.java` — two files + text → `@path1\n@path2\ntext\r` in connector

---

### Stage 17 — FileDiff location config (planned)

**Goal:** allow the user to choose whether FileDiff appears embedded in the session window or in a separate TopComponent (current behaviour).

**What to add:**
- New key in `ClaudeCodePreferences`: `diffLocation = SESSION | TOPLEVEL`
- `FileDiffTab.open()` reads the setting and picks the display mode accordingly

**Files:** `ClaudeCodePreferences.java`, `FileDiffTab.java`, `ClaudeCodeOptionsPanel.java`

---

### Stage 18 — Settings + full integration (planned)

**Goal:** settings drive plugin behaviour; session lifecycle management.

**What to add:**
- `ClaudeCodeOptionsPanel` — extend: Claude CLI path (Browse), auto-start (checkbox), send key
- Auto-start when `ClaudeSessionTopComponent` opens if `autoStart=true`
- On project close — stop associated processes

**Tests:**
- `ClaudeCodePreferencesAutoStartTest.java` — `autoStart=true` → `componentOpened` calls `panel.autoStart()`
- `FullIntegrationIT.java` (NbModuleSuite) — end-to-end

---

### Stage 19 — GitHub CI/CD + NBM publishing (planned)

**Goal:** automated build and release publishing.

**What to add:**
- `.github/workflows/build.yml`: `mvn package` on push to main; artifact — `.nbm`
- `.github/workflows/release.yml`: triggered by tag `v*.*.*`; GitHub Release + NBM; release notes from `CHANGELOG.md`
- `CHANGELOG.md` — maintained manually
- Badges in README

---

### Stage 20 — Help + user documentation (planned)

**Goal:** built-in help and end-user documentation.

**What to add:**
- NetBeans Help integration (JavaHelp)
- `docs/user-guide.md` or GitHub Wiki: installation, profiles, history, favorites, keyboard shortcuts
- External announcements on releases: posts in NetBeans community forum and Anthropic/Claude community (manual)

---

## Plugin Location

`/home/oleg/my-projects/NetbeansClaudeCodePlugin/` — `pom.xml` and `src/` alongside `submodules/`.

## Key NetBeans Dependencies (pom.xml)

```xml
org-openide-util, org-openide-util-lookup        <!-- Stage 1 -->
org-netbeans-modules-options-api                  <!-- Stage 2 -->
org-openide-windows, org-openide-awt              <!-- Stage 3 -->
org-netbeans-modules-projectapi                   <!-- Stage 4 -->
org-openide-filesystems                           <!-- Stage 5 -->
org-netbeans-modules-diff                         <!-- Stage 11: Diff API -->
```

JediTerm / pty4j (stage 7):
```xml
org.jetbrains.jediterm:jediterm-core:3.63
org.jetbrains.jediterm:jediterm-ui:3.63
org.jetbrains.pty4j:pty4j:0.12.4
```

NBM plugin NetBeans version: `RELEASE230` (NetBeans 23)

## Plugin Settings Location

Settings are stored via `NbPreferences.forModule(ClaudeCodePreferences.class)`:

| OS | Path |
|----|------|
| Linux / macOS | `~/.netbeans/23/config/Preferences/io/github/nbclaudecodegui.properties` |
| Windows | `%APPDATA%\NetBeans\23\config\Preferences\io\github\nbclaudecodegui.properties` |

**Settings keys:**

| Key | Type | Default | Stage |
|-----|------|---------|-------|
| `claudeExecutablePath` | String | `""` (search in PATH) | 2 |
| `sendKey` | String | `CTRL_ENTER` | 5 |
| `mcpPort` | int | `28991` | 10 |
| `autoStart` | boolean | `false` | 12 |
| `commandHistory` | String | `""` (JSON array) | 12 |

---

## Javadoc Policy

- **All public classes and methods** must have Javadoc comments.
- **`maven-javadoc-plugin`** with `-Xdoclint:all` and `failOnError=true` is configured in `pom.xml`.
- Generate: `mvn javadoc:javadoc` → `target/site/apidocs/`

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.6.3</version>
    <configuration>
        <additionalJOption>-Xdoclint:all</additionalJOption>
        <failOnError>true</failOnError>
    </configuration>
    <executions>
        <execution>
            <id>javadoc-jar</id>
            <phase>package</phase>
            <goals><goal>jar</goal></goals>
        </execution>
    </executions>
</plugin>
```
