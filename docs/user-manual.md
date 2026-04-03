# NetBeans Claude Code GUI — User Manual

<!-- TODO: screenshot: plugin overview with session tab open -->

## Table of Contents

1. [Overview](#1-overview)
2. [Requirements & Installation](#2-requirements--installation)
3. [Quick Start](#3-quick-start)
4. [Session Window](#4-session-window)
5. [Sending Prompts](#5-sending-prompts)
6. [Favorites](#6-favorites)
7. [Prompt History](#7-prompt-history)
8. [File Attachments](#8-file-attachments)
9. [Interactive Prompts (Choice Menu)](#9-interactive-prompts-choice-menu)
10. [File-Change Permissions (Diff Panel)](#10-file-change-permissions-diff-panel)
11. [Settings (Tools → Options → Claude Code)](#11-settings)
12. [Profiles](#12-profiles)
13. [Troubleshooting](#13-troubleshooting)


---

## 1. Overview

NetBeans Claude Code GUI is a NetBeans IDE plugin that embeds the Claude Code CLI as a full interactive terminal session directly inside the IDE. You type prompts in a dedicated session tab, Claude reads and edits your project files, and the plugin provides:

- a graphical file diff (using NetBeans' built-in diff viewer) before any change is written to disk
- a graphical panel for responding to Claude's interactive questions

IDE integration (open editors, diagnostics, current selection) is exposed to Claude via the MCP protocol so that Claude always has full context about your work.

---

## 2. Requirements & Installation

See [Installation & Build](installation.md) for requirements, installation steps, and build instructions.

---

## 3. Quick Start

1. Right-click your project in the **Projects** window → **Open with Claude Code**.
2. A session tab opens and Claude Code starts. Wait for the Claude Code prompt to appear in the terminal.
3. If this is the first time running Claude Code (or the first time for this project), answer Claude's initial setup questions in the terminal or the choice panel.
4. Type your first prompt in the input area at the bottom (e.g., `Explain what this project does`).
5. Press **Ctrl+Enter** (default) to send. Claude's response appears in the terminal.

<!-- TODO: screenshot: session tab with first prompt -->

---

## 4. Session Window

<!-- TODO: screenshot: annotated session window -->

The session tab contains the following areas:

### Terminal area (center)
Displays Claude Code output. You can also click into the terminal and interact with Claude Code directly — as if it were running in a standalone terminal. Use this when the plugin fails to detect Claude's state correctly, for example if an interactive choice panel was not shown.

### Prompt panel (bottom)
Contains the input area, **▶ Send** / **✖ Cancel** buttons, and **☰ History** / **★ Favorites** buttons.

### Status bar (below the prompt panel, visible during an active session)
Shows the current **edit mode** selector and the **model** selector.

---

## 5. Sending Prompts

### Input area
A multi-line text area. Wrap long prompts naturally — newlines are preserved. `@path` tokens are highlighted in blue (see [File Attachments](#8-file-attachments)).

### Sending
Press the configured **send key** (default: **Ctrl+Enter**) or click the **▶ Send** button. The input area is cleared after sending.

You can change the send key in **Tools → Options → Claude Code → General** (choices: Enter, Shift+Enter, Ctrl+Enter, Alt+Enter).

### Cancelling
Press **Escape** in the input area, or click the **✖ Cancel** button to interrupt Claude's running task.

### Edit mode
Press **Shift+Tab** anywhere in the prompt panel to cycle Claude Code's edit mode. The current mode is shown in the status bar.

### Button states
- **▶ Send** is active only when Claude is idle and ready to accept input.
- **✖ Cancel** is active only when Claude is working.

If the states appear out of sync:
- Send is inactive but Claude is ready — click **✖ Cancel** to reset the state.
- Cancel is inactive but Claude is working — click into the terminal area and interact directly.

### Context menu (right-click in the input area)

| Item | Description |
|------|-------------|
| Cut / Copy / Paste / Select All / Clear | Standard text editing actions |
| **Add to Favorites** | Saves the current input text as a project favorite (active only when the input is non-empty and a working directory is set) |
| **Favorites...** | Opens the Favorites dialog |

---

## 6. Favorites

Favorites are saved prompts you can reuse with a button click or a keyboard shortcut.

### Scope
- **Global favorites** — available in every project. Managed in **Tools → Options → Claude Code → Favorites**.
- **Project favorites** — available only for a specific working directory.

### Adding a favorite
Right-click the input area → **Add to Favorites**. This always adds to **project** favorites. To promote a project favorite to global, use the **To Global** button in the Favorites dialog.

### Favorites dialog

Click **★ Favorites** in the prompt panel to open the dialog.

<!-- TODO: screenshot: Favorites dialog -->

The dialog shows a table with the following columns:

| Column | Description |
|--------|-------------|
| ☐ | Checkbox for multi-select |
| **Text** | Prompt text (truncated to 100 characters) |
| **Shortcut** | Assigned keyboard shortcut, if any |
| **Scope** | `PROJECT` or `GLOBAL` |

**Buttons:**

| Button | Effect |
|--------|--------|
| **Send** | Loads the selected favorite into the input area and closes the dialog (also triggered by double-click) |
| **Edit** | Edit the text of the selected favorite |
| **To Global** | Move selected PROJECT favorite(s) to global scope |
| **Assign Shortcut** | Assign a keyboard shortcut to the selected favorite |
| **↑ / ↓** | Change the display order of favorites |
| **Delete** | Delete selected favorite(s) (PROJECT only — global favorites can only be deleted via **Tools → Options → Claude Code → Favorites**) |

The search field filters entries by text.

### Assigning keyboard shortcuts

Click **Assign Shortcut** to open the shortcut capture dialog. Press the desired key combination(s) — they are displayed in the field. Multiple key combos can be chained (e.g., `Ctrl+K Ctrl+F`).

If the combination is already used by another favorite, a conflict warning is shown and **OK** is disabled.

**Note:** All key presses are captured as shortcut input — to close the dialog use the **OK**, **Clear**, or **Cancel** buttons with the mouse.

Shortcut conflicts with other NetBeans IDE actions are not checked automatically. If a shortcut does not work, check for conflicts in **Tools → Keymap** (search "Claude Favorite").

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

Click the **☰ History** button to open the History dialog.

<!-- TODO: screenshot: History dialog -->

The dialog shows history for the current working directory. The table has the following columns:

| Column | Description |
|--------|-------------|
| ☐ | Checkbox for multi-select |
| **Prompt** | Prompt text (truncated to 120 characters) |
| **Time** | Date and time the prompt was sent (`yyyy-MM-dd HH:mm`) |

**Buttons:**

| Button | Effect |
|--------|--------|
| **Send** | Loads the selected entry into the input area and closes the dialog (also triggered by double-click) |
| **To Favorites** | Saves selected entries as project favorites |
| **Delete** | Deletes selected entries |
| **Clear older…** | Opens a date input dialog (`yyyy-MM-dd`); deletes all entries from that date and older (with confirmation) |

The search field filters entries by prompt text.

### History settings

Configure in **Tools → Options → Claude Code → General**:

| Setting | Default | Description |
|---------|---------|-------------|
| History max depth | 200 | Maximum entries kept per working directory |
| History TTL (days) | 0 (forever) | Entries older than this are purged automatically |

---

## 8. File Attachments

You can attach files to a prompt as `@path` tokens. Claude Code interprets these as file references.

### @-completion popup

Type `@` anywhere in the input area. A popup appears listing the contents of the current directory level. Hidden files (starting with `.`) are not shown. `..` is always present for navigating up.

<!-- TODO: screenshot: @-completion popup -->

| Key | Action |
|-----|--------|
| Up / Down | Navigate the list |
| Enter or Tab | File: insert and close popup. Directory: navigate into it. |
| Space | Insert the current item as-is (even if it is a directory) and close the popup |
| Escape | Dismiss the popup |
| Double-click | Same as Enter |

### Drag-and-drop and paste

You can drag items from the OS file manager or the NetBeans **Projects** tree and drop them onto the input area, or paste them via **Ctrl+V**, **Shift+Ins**, or **Paste** from the context menu.

Three types of content are supported:

| Content type | What is inserted |
|-------------|-----------------|
| **File** inside the working directory (from OS or Projects tree) | Relative path without `@` (e.g. `src/Main.java`) |
| **File** outside the working directory | `@/absolute/path` |
| **Package directory** (under a source root such as `src/main/java/`) | Fully-qualified package name (e.g. `com.example.util`) |
| **Directory** inside the working directory (not a source root) | Relative path without `@` |
| **Directory** outside the working directory | `@/absolute/path` |
| **Image** from clipboard | Saved as a temporary PNG file; `@/tmp/....png` inserted |
| **Plain text** | Inserted as-is |

---

## 9. Interactive Prompts (Choice Menu)

Claude Code sometimes presents interactive prompts (Yes/No or multiple choice). The plugin detects these and shows a **Choice Menu** panel **in place of** the prompt input area.

<!-- TODO: screenshot: choice menu panel -->

**Yes / No options** appear as buttons. Clicking one sends the answer immediately and closes the panel.

**Other options** appear as radio buttons. Some options may include a text field for additional input. Click the desired option (and optionally fill in the text field), then press **Enter** or click **Send** to submit. Press **Escape** to cancel.

After submitting or cancelling, the input area is restored automatically.

**Note:** If the choice panel does not appear when Claude asks a question, or appears unexpectedly without a question — click into the terminal area and interact with Claude directly. The panel will disappear automatically once Claude's state changes.

---

## 10. File-Change Permissions (Diff Panel)

When Claude Code is about to edit or create a file, the plugin intercepts the operation and shows a diff panel **before** the change is written to disk.

The diff panel appears either **embedded in the session tab** (replacing the input area) or in a **separate IDE tab**, depending on the setting in **Tools → Options → Claude Code → General → Open diff in a separate tab**.

<!-- TODO: screenshot: diff panel with permission bar -->

### Permission bar

```
[✓✓ Accept All]  [✓ Accept]   [_Decline reason (Optional)_]  [✗ Decline]   [Cancel]
```

| Button | Effect |
|--------|--------|
| **✓✓ Accept All** | Accept this change **and** switch Claude's edit mode to "Accept on Edit" for this session — all subsequent edits are accepted automatically without showing a diff |
| **✓ Accept** | Allow this single change; Claude continues |
| **✗ Decline** | Reject this change; optionally send a reason to Claude (type it in the text field first) |
| **Cancel** | Reject this change **and** interrupt Claude's current task |
| Close tab (×) | Treated as Decline without a reason |

### Decline reason
Type a reason in the text field between the Accept and Decline buttons. When you click Decline, the reason is sent to Claude. Pressing Enter in a non-empty reason field automatically clicks Decline.

If you type a decline reason but click Accept, a confirmation dialog warns you that the reason will not be sent.

### Keyboard navigation
Tab / Shift+Tab cycles focus between: Accept All → Accept → reason field → Decline → Cancel.
Press Escape to click Cancel.

### Markdown preview

For `.md` files, a rendered markdown preview is shown alongside the raw diff. Toggle it via **right-click on the diff → Preview Markdown** (checkbox). The default state is configured in **Tools → Options → Claude Code → General → Show markdown preview for .md files in diff**. Toggling in the context menu does not change the global setting.

**Pin Preview** — opens the rendered markdown (proposed content) in a separate IDE tab that remains open after the diff is closed.

Both features are especially useful in Claude's plan mode: they let you read the formatted plan before deciding whether to accept it.

If the file being edited is outside the current project directory, a warning ⚠ is shown in the diff panel.

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
| Insert newline key | Enter | Key combination that inserts a newline in the input area. The send and newline keys are configured independently but cannot be set to the same value. |
| Debug mode | Off | Enables verbose logging of all Claude I/O to the NetBeans log file and the Output window. |
| Open diff in a separate tab | Off | Opens the diff panel in a new IDE tab instead of embedding it in the session tab. |
| Show markdown preview for .md files in diff | On | Shows a rendered markdown preview alongside the raw diff for `.md` files. |

### Favorites tab

Manages global favorites — prompts available in every project.

The table shows all global favorites with columns: ☐ (checkbox for multi-select), **Text**, **Shortcut**.

**Buttons:**

| Button | Effect |
|--------|--------|
| **Add** | Add a new global favorite (enter text in the dialog) |
| **Edit** | Edit the text of the selected favorite |
| **Assign Shortcut** | Assign a keyboard shortcut to the selected favorite |
| **↑ / ↓** | Change the display order |
| **Delete** | Delete selected favorite(s) |

The search field filters entries by text.

---

## 12. Profiles

Profiles allow you to run Claude Code under different accounts for different projects. Each profile has an isolated `CLAUDE_CONFIG_DIR` so that authentication and settings do not interfere with each other.

<!-- TODO: screenshot: Profiles tab -->

Open **Tools → Options → Claude Code → Profiles**.

The **Default** profile is always present and cannot be renamed or deleted. Named profiles can be freely created, copied, renamed, and deleted.

### Profiles directory

Named profiles are stored under a common base directory (default: `~/.netbeans/claude-profiles/`). Each profile occupies a subdirectory named after the profile.

The base directory only affects newly created profiles. To move existing profiles to a new location, manually move their subdirectories to the new location first, then update the directory via the **Profiles directory** field (click **Change…**).

The read-only **Config directory** field shows the storage path for the selected profile. For the Default profile it shows `~/.claude (not overridden)`.

### Managing profiles

| Button | Effect |
|--------|--------|
| **New** | Create a new named profile |
| **Copy** | Duplicate the currently selected profile |
| **Rename** | Rename the currently selected profile |
| **Delete** | Delete the currently selected profile |
| **Change…** | Change the profile Config directory |

### Authentication

Select the authentication type using the radio buttons:

| Type | Fields to fill in | How to authenticate |
|------|------------------|-------------------|
| **Claude Managed** | None | Claude manages authentication itself. Run `claude` in a terminal once to set it up, or Claude will prompt you on first launch. Use `/login` in the session terminal to re-authenticate. |
| **Subscription** | OAuth token | Obtain the token at [claude.ai](https://claude.ai) (requires Pro, Max, Team, or Enterprise subscription). **Note:** OAuth tokens expire periodically and must be regenerated and re-entered manually. For a smoother experience, prefer **Claude Managed** instead. |
| **Claude API** | API key | Obtain the key at [console.anthropic.com](https://console.anthropic.com) (requires an Anthropic Console account with API access). |
| **Other API** | API key + Base URL | Use the API key and base URL provided by your third-party provider. |

### Model aliases (Other API only)

If your provider names models differently from Anthropic's standard names, the plugin cannot match them to the `sonnet`, `opus`, and `haiku` aliases used by Claude Code. In that case, set the alias to the actual model ID used by your provider (for example, with an `anthropic/` prefix).

Fill in only the aliases for models you plan to use and whose IDs do not start with the standard name (`sonnet`, `opus`, or `haiku`). If no matching models are found and no aliases are set, model selection will be unavailable.

### Proxy settings

| Mode | Description |
|------|-------------|
| **System managed** (default) | Claude Code inherits proxy settings from the IDE environment. Use this when a proxy is already configured in the system. |
| **No proxy** | Forces Claude Code to connect directly, bypassing any system proxy. |
| **Custom** | Specify proxy settings manually. Use this when Claude Code needs a proxy different from the system default. |

**Custom proxy fields:**

| Field | Syntax | Example |
|-------|--------|---------|
| **HTTP Proxy** | `http://host:port` | `http://proxy.example.com:8080` |
| **HTTPS Proxy** | `http://host:port` | `http://proxy.example.com:8080` |
| **NO_PROXY** | Empty or a comma-separated list of host patterns | `localhost,127.0.0.1,.example.com` |

**Note:** Claude Code communicates with the plugin via `localhost`. External proxies are not aware of this. If you fill in the **NO_PROXY** field, make sure it includes `localhost` — otherwise the plugin integration will stop working. If you leave the field empty, the plugin adds `localhost` automatically.

### Extra environment variables

Arbitrary `KEY=VALUE` pairs injected into the Claude process environment. Applied last — they override all other profile variables. Use this for provider-specific configuration. <!-- TODO: add links to Claude Code env vars documentation and list of compatible providers -->

### Per-project profile assignment

By default, Claude Code sessions use the **Default** profile.

#### Persistent profile for a project (recommended)

To permanently assign a profile to a project:
1. Right-click the project in the **Projects** window → **Properties**.
2. Go to the **Claude Code** category.
3. Select the desired profile from the **Profile** drop-down and click **OK**.

The selected profile is used automatically when a session is started for that project via **Open with Claude Code**. Selecting **Default** removes any project-specific assignment.

If the assigned profile no longer exists, the plugin falls back to the Default profile automatically.

#### Temporary profile for one session

Click the **Claude Code** button in the toolbar to open the session selector. In the selector bar, choose a project or a working directory and select a profile from the **Profile** combo, then click **Open**. The selected profile is used for that session only and does not affect project settings.

---

## 13. Troubleshooting

### Enable debug mode
Go to **Tools → Options → Claude Code → General** and check **Debug mode**. This writes detailed logs of all Claude I/O (PTY bytes, MCP messages, hook calls) to the NetBeans log file.

### Log file location
```
~/.netbeans/<version>/var/log/messages.log
```
For example: `~/.netbeans/28/var/log/messages.log`

### Common issues

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Session tab does not open | `claude` not found on PATH | Set the full path in **Tools → Options → Claude Code → General → Claude CLI path** |
| Diff panel shows but Accept/Decline have no effect | Claude timed out waiting for the hook response (600 s limit) | Respond to the diff panel within ~9 minutes; if this happens regularly, check for blocking processes |
| Claude asked a question but the Choice Menu panel did not appear | The plugin did not recognise the prompt format | Switch to the terminal area and answer directly by typing |
| Choice Menu appeared but Claude did not ask anything (false trigger) | The plugin mis-detected a numbered list as a menu | Dismiss the panel with **Esc** or ignore it; Claude will continue on its own |
| OAuth login prompt not appearing | The `claude` process cannot open a browser, or proxy settings mismatch | Run `claude` manually in a terminal once to complete authentication, then restart the session. If you use a proxy, make sure the proxy settings in the plugin profile match the system/browser proxy settings. |
| Profile not applied to a project | Project properties not saved | Re-open the project properties, select the profile, and click OK. Alternatively, open the session via the toolbar and select the profile explicitly in the session selector. |
| Favorites shortcut not working | Shortcut conflicts with another IDE action | Re-assign the shortcut in **Tools → Keymap** (search "Claude Favorite") |

### Reporting bugs
Please open an issue at the project's GitHub repository and attach the relevant section of `messages.log` with debug mode enabled.
