# NetBeans Claude Code GUI — User Manual

<!-- TODO: screenshot: plugin overview with session tab open -->

## Table of Contents

1. [Overview](#1-overview)
2. [Requirements & Installation](#2-requirements--installation)
3. [Quick Start](#3-quick-start)
4. [Session Window](#4-session-window)
5. [Sending Prompts](#5-sending-prompts)
6. [File Attachments](#6-file-attachments)
7. [Prompt History](#7-prompt-history)
8. [Favorites](#8-favorites)
9. [File-Change Permissions (Diff Panel)](#9-file-change-permissions-diff-panel)
10. [Interactive Prompts (Choice Menu)](#10-interactive-prompts-choice-menu)
11. [Settings (Tools → Options → Claude Code)](#11-settings)
12. [Keyboard Shortcuts Reference](#12-keyboard-shortcuts-reference)
13. [MCP Tools Reference](#13-mcp-tools-reference)
14. [Troubleshooting](#14-troubleshooting)

---

## 1. Overview

NetBeans Claude Code GUI is a NetBeans IDE plugin that embeds the [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code) as a full interactive terminal session directly inside the IDE. You type prompts in a dedicated panel, Claude reads and edits your project files, and the plugin intercepts file-change requests to show you a diff before any change is applied. IDE integration (open editors, diagnostics, current selection) is exposed to Claude via the MCP protocol so that Claude always has full context about your work.

---

## 2. Requirements & Installation

See [Installation & Build](installation.md) for requirements, installation steps, and build instructions.

---

## 3. Quick Start

1. Open the Claude Code session: click the **Claude Code** button in the toolbar (or **Window → Claude Code**).
2. In the session tab, click the **directory bar** at the top and select (or type) the working directory — typically your project root.
3. Claude Code starts. Wait for the terminal area to show the Claude Code prompt.
4. Type your first prompt in the input area at the bottom (e.g., `Explain what this project does`).
5. Press **Ctrl+Enter** (default) to send. Claude's response appears in the terminal.

<!-- TODO: screenshot: session tab with first prompt -->

---

## 4. Session Window

<!-- TODO: screenshot: annotated session window -->

The session tab contains three main areas:

### Directory bar (top)
Shows the current working directory. Click it to change the directory before starting or after stopping a session. The working directory is passed to Claude as the project root.

### Terminal area (center)
A full PTY-backed terminal that renders the Claude Code TUI natively, including colors, cursor movement, and interactive menus.

### Prompt panel (bottom)
Contains the input area, Send/Cancel buttons, and History/Favorites buttons.

### Session state
The **Send** button is enabled only when Claude is idle (ready to accept input). The **Cancel** button is enabled only when Claude is working. The current state is also reflected by whether Claude's terminal shows a blinking prompt cursor.

---

## 5. Sending Prompts

### Input area
A multi-line text area. Wrap long prompts naturally — newlines are preserved. `@path` tokens are highlighted in blue (see [File Attachments](#7-file-attachments)).

### Sending
Press the configured **send key** (default: **Ctrl+Enter**) or click the **▶ Send** button. The input area is cleared after sending.

You can change the send key in **Tools → Options → Claude Code → General** (choices: Enter, Shift+Enter, Ctrl+Enter, Alt+Enter). The **insert newline** key is automatically set to a different combination to avoid conflicts.

### Cancelling
While Claude is working:
- Press **Escape** in the input area, or
- Click the **✖ Cancel** button.

Either action sends Ctrl+C (SIGINT) to the Claude process, interrupting the running task.

### Edit mode cycling
Press **Shift+Tab** anywhere in the prompt panel to cycle Claude Code's edit mode (e.g., between *auto*, *accept-edits*, etc.). This is equivalent to pressing Shift+Tab in the Claude TUI.

---

## 6. File Attachments

You can attach files to a prompt as `@path` tokens. Claude Code interprets these as file references.

### @-completion popup

Type `@` anywhere in the input area. A popup appears listing files relative to the working directory.

<!-- TODO: screenshot: @-completion popup -->

| Action | Key |
|--------|-----|
| Filter | Continue typing after `@` |
| Navigate | Up / Down arrow keys |
| Insert selected | Enter or Tab |
| Dismiss | Escape |
| Insert (mouse) | Double-click the entry |

The inserted token is a relative path (e.g., `@src/Main.java`) when the file is inside the working directory, or an absolute path otherwise.

### Drag-and-drop
Drag one or more files from the OS file manager or the NetBeans Projects tree and drop them onto the input area. Each file is inserted as an `@path` token at the drop position.

### Paste (Ctrl+V)
If the clipboard contains file paths (e.g., copied from a file manager), paste them with Ctrl+V — they are inserted as `@path` tokens. Plain text is pasted normally.

---

## 7. Prompt History

The plugin records every sent prompt per working directory.

### In-session keyboard navigation

| Action | Key |
|--------|-----|
| Previous prompt (older) | Ctrl+Up |
| Next prompt (newer) | Ctrl+Down |

These keys work inside the input area. At the most-recent position, Ctrl+Down clears the field.

### History dialog

Click the **☰ History** button or press **Ctrl+H** to open the History dialog.

<!-- TODO: screenshot: History dialog -->

The dialog shows a table with columns:

| Column | Description |
|--------|-------------|
| Timestamp | When the prompt was sent |
| Text | The prompt text (truncated) |
| Working directory | The directory associated with this entry |

**Actions:**
- Select a row and click **Use** (or double-click) to load the prompt into the input area.
- Click **Delete** to remove entries.
- The search field filters entries by text.

### History settings

Configure in **Tools → Options → Claude Code → General**:

| Setting | Default | Description |
|---------|---------|-------------|
| History max depth | 200 | Maximum entries kept per project |
| History TTL (days) | 0 (forever) | Entries older than this are purged automatically |

---

## 8. Favorites

Favorites are saved prompts you can reuse with a button click or a keyboard shortcut.

### Scope
- **Global favorites** — available in every project. Managed in **Tools → Options → Claude Code → Favorites**.
- **Project favorites** — available only for a specific working directory. Managed via the **★ Favorites** dialog in the session.

### Adding a favorite
1. Type (or load from history) the prompt text in the input area.
2. Right-click the input area → **Add to Favorites** — choose Global or Project scope.

### Using favorites
Click **★ Favorites** in the prompt panel to open the Favorites dialog. Select an entry and click **Send** (or double-click) to send it immediately, or **Use** to load it into the input area for editing.

<!-- TODO: screenshot: Favorites dialog -->

### Assigning keyboard shortcuts
1. Open **Tools → Options → Claude Code → Favorites** (for global) or the **★ Favorites** dialog (for project).
2. Select a favorite and click **Assign Shortcut**.
3. Press the desired key combination in the shortcut capture dialog and click **OK**.

Once assigned, the shortcut can also be managed via NetBeans' standard **Tools → Keymap** dialog (search for "Claude Favorite").

### Reordering favorites
Use the **↑** / **↓** buttons in the Favorites tab or dialog to reorder entries.

---

## 9. File-Change Permissions (Diff Panel)

When Claude Code is about to edit or create a file, the plugin intercepts the operation and shows a diff panel **before** the change is written to disk.

<!-- TODO: screenshot: diff panel with permission bar -->

### Layout
The diff panel shows a side-by-side (or inline) diff of the current file content vs. the proposed content. Below the diff is the permission bar:

```
[✓✓ AcceptAll]  [✓ Accept]   [_Decline reason (Optional)_]  [✗ Decline]   [Cancel]
```

### Actions

| Button | Effect |
|--------|--------|
| **✓✓ AcceptAll** | Accept this change **and** switch Claude's edit mode to "Accept on Edit" — all subsequent edits in this session are accepted automatically without showing a diff |
| **✓ Accept** | Allow this single change; Claude continues |
| **✗ Decline** | Reject this change; optionally send a reason to Claude (type it in the text field first) |
| **Cancel** | Reject this change **and** interrupt Claude's current task (sends Ctrl+C) |
| Close tab (×) | Treated as Decline; Claude's built-in permission dialog may appear as a fallback |

### Decline reason
Type a reason in the text field between the Accept and Decline buttons. When you click Decline, the reason is sent to Claude. Pressing Enter in a non-empty reason field automatically clicks Decline.

If you type a decline reason but click Accept, a confirmation dialog warns you that the reason will not be sent.

### Keyboard navigation in the diff panel
Tab / Shift+Tab cycles focus between: AcceptAll → Accept → reason field → Decline → Cancel.
Press Escape to click Cancel (if enabled).

### Diff location
By default the diff appears embedded inside the session tab. To open diffs in a separate IDE tab, enable **Tools → Options → Claude Code → General → Open diff in a separate tab**.

### Markdown preview
For `.md` files, a rendered markdown preview is shown alongside the raw diff. Disable it in **Tools → Options → Claude Code → General → Show markdown preview for .md files in diff**.

---

## 10. Interactive Prompts (Choice Menu)

Claude Code sometimes presents interactive prompts (Yes/No, multiple choice, or free-form input). The plugin detects these and shows a **Choice Menu** panel above the prompt input area.

<!-- TODO: screenshot: choice menu panel -->

| Prompt type | How it appears | How to respond |
|-------------|---------------|----------------|
| Yes/No | Two buttons: **Yes** / **No** | Click the button |
| Multiple choice | Numbered buttons for each option | Click an option, or type its number and press Enter |
| Free-form | Text field | Type your answer and press Enter |
| Cancel any prompt | — | Press **Ctrl+C** or click Cancel |

---

## 11. Settings

Open **Tools → Options → Claude Code** in NetBeans.

### General tab

| Setting | Default | Description |
|---------|---------|-------------|
| Claude CLI path | (empty) | Absolute path to the `claude` executable. Leave empty to use the system `PATH`. |
| MCP server port | 28991 | Port for the internal MCP SSE server. Change only if 28991 conflicts. Requires IDE restart. |
| History max depth | 200 | Maximum number of history entries kept per working directory. |
| History TTL (days) | 0 | Number of days after which history entries expire. 0 = keep forever. |
| Send prompt key | Ctrl+Enter | Key combination that sends the prompt from the input area. |
| Insert newline key | Enter | Key combination that inserts a newline in the input area. |
| Debug mode | Off | Enables verbose logging of all Claude I/O to the NetBeans log file and the Output window. |
| Open diff in a separate tab | Off | Opens the diff panel in a new IDE tab instead of embedding it in the session tab. |
| Show markdown preview for .md files in diff | On | Shows a rendered markdown preview alongside the raw diff for `.md` files. |

### Profiles tab

Profiles allow you to run Claude Code with different API keys, authentication methods, proxy settings, and model aliases. Each profile gets an isolated `CLAUDE_CONFIG_DIR` so that authentication and configuration do not interfere with each other.

<!-- TODO: screenshot: Profiles tab -->

#### Built-in Default profile
Always present. Uses Claude's own managed authentication (OAuth). No extra environment variables are injected. Uses `~/.claude/` as the config directory.

#### Creating a named profile
Click **Add** in the Profiles tab, enter a name (no spaces or special characters), then configure the following fields:

**Connection type** (derived automatically from the fields you fill in):

| Type | What to fill in | Environment variable injected |
|------|----------------|-------------------------------|
| Claude Managed | Nothing | none |
| Subscription | OAuth token | `CLAUDE_CODE_OAUTH_TOKEN` |
| Claude API | API key | `ANTHROPIC_API_KEY` |
| Other API | API key + Base URL | `ANTHROPIC_AUTH_TOKEN`, `ANTHROPIC_BASE_URL` |

**Proxy settings:**

| Mode | Effect |
|------|--------|
| System managed (default) | Inherits `HTTP_PROXY`/`HTTPS_PROXY` from the IDE environment |
| No proxy | Clears `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY` |
| Custom | Sets `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY` to your specified values |

**Model aliases:** Map alias names (`sonnet`, `opus`, `haiku`) to specific model IDs. These are set as `ANTHROPIC_DEFAULT_SONNET_MODEL` etc. environment variables.

**Extra environment variables:** Add any additional `KEY=VALUE` pairs to inject into the Claude process environment.

#### Config directory
Named profiles store their `CLAUDE_CONFIG_DIR` under:
```
~/.netbeans/claude-profiles/<profile-name>/
```
The base directory can be changed in the General tab (Profiles dir field, if shown).

### Per-project profile assignment

To assign a specific profile to a project:
1. Right-click the project in the **Projects** window → **Properties**.
2. Go to the **Claude Code** category.
3. Select the desired profile from the drop-down.

When a session is started in that project's directory, the selected profile's settings are applied automatically.

---

## 12. Keyboard Shortcuts Reference

### In the prompt input area

| Shortcut | Action |
|----------|--------|
| Ctrl+Enter *(default)* | Send prompt |
| Enter *(default)* | Insert newline |
| Ctrl+Up | Navigate to previous (older) prompt in history |
| Ctrl+Down | Navigate to next (newer) prompt in history |
| Escape | Cancel (when Claude is working) |
| Shift+Tab | Cycle Claude Code edit mode |
| @ | Trigger @-completion popup |

The send and newline keys are configurable in **Tools → Options → Claude Code → General**.

### In the @-completion popup

| Shortcut | Action |
|----------|--------|
| Up / Down | Navigate the file list |
| Enter or Tab | Insert selected path |
| Escape | Dismiss popup |
| Double-click | Insert selected path |

### History dialog

| Shortcut | Action |
|----------|--------|
| Ctrl+H | Open History dialog |
| Enter / Double-click | Load selected prompt into input area |

### Diff permission panel

| Shortcut | Action |
|----------|--------|
| Tab / Shift+Tab | Cycle focus between buttons |
| Enter (in reason field) | Click Decline (if reason is non-empty) |
| Escape | Click Cancel |

### Global (configurable via Tools → Keymap)

| Action | Default shortcut |
|--------|-----------------|
| Send a saved favorite | Assigned per favorite (none by default) |

---

## 13. MCP Tools Reference

The plugin exposes the following tools to Claude Code via the MCP protocol. Claude calls these automatically — you do not need to invoke them manually. This table is for power users and troubleshooting.

| Tool name | Description |
|-----------|-------------|
| `get_workspace_folders` | Returns the open project root paths |
| `get_open_editors` | Lists currently open editor tabs with file paths |
| `get_current_selection` | Returns the selected text and cursor position in the active editor |
| `get_diagnostics` | Returns IDE error and warning markers for a file |
| `open_file` | Opens a file in the IDE editor at a given line |
| `open_diff` | Shows a diff panel for two content strings |
| `permission_prompt` | Asks the user to accept or decline a proposed file change (alternative to the PreToolUse hook) |
| `check_document_dirty` | Checks whether a file has unsaved changes |
| `save_document` | Saves a file in the IDE |
| `close_tab` | Closes a specific editor tab |
| `close_all_diff_tabs` | Closes all open diff tabs |
| `selection_changed` *(notification)* | Pushed to Claude automatically on every cursor move |

The plugin also installs a **PreToolUse HTTP hook** that fires before every `Edit`, `Write`, and `MultiEdit` tool call, triggering the diff permission panel.

---

## 14. Troubleshooting

### Enable debug mode
Go to **Tools → Options → Claude Code → General** and check **Debug mode**. This writes detailed logs of all Claude I/O (PTY bytes, MCP messages, hook calls) to:
- The **Output** window inside the IDE (if the Claude Code output tab is open).
- The NetBeans log file.

### Log file location
```
~/.netbeans/<version>/var/log/messages.log
```
For example: `~/.netbeans/28/var/log/messages.log`

### Common issues

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Session tab opens but terminal stays blank | `claude` not found on PATH | Set the full path in **Tools → Options → Claude Code → General → Claude CLI path** |
| "MCP server failed to start" in the log | Port 28991 already in use | Change the MCP port in **Tools → Options → Claude Code → General** and restart the IDE |
| Diff panel shows but Accept/Decline have no effect | Claude timed out waiting for the hook response (600 s limit) | Respond to the diff panel within ~9 minutes; if this happens regularly, check for blocking processes |
| OAuth login prompt not appearing | The `claude` process cannot open a browser | Run `claude` manually in a terminal once to complete authentication, then restart the session |
| Profile not applied to a project | Project properties not saved | Re-open the project properties, select the profile, and click OK |
| Favorites shortcut not working | Shortcut conflicts with another IDE action | Re-assign the shortcut in **Tools → Keymap** (search "Claude Favorite") |

### Reporting bugs
Please open an issue at the project's GitHub repository and attach the relevant section of `messages.log` with debug mode enabled.
