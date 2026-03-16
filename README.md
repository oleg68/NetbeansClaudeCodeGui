# Claude Code GUI — NetBeans Plugin

A NetBeans IDE plugin that provides a graphical interface for the [Claude Code](https://claude.ai/code) CLI tool.
Run `claude` code sessions, send prompts, attach files, and review AI-suggested file changes without leaving the IDE.

---

## Requirements

- Apache NetBeans 23 (RELEASE230) or later
- Java 17+
- Maven 3.8+
- `claude` CLI installed and available in PATH (required from Stage 5 onward)

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

After restart the plugin is listed under **Tools → Plugins → Installed** as **Claude Code GUI**.

### Run unit tests

```bash
mvn test
```

---

## Project Structure

```
src/
  main/
    java/io/github/nbclaudecodegui/   # Plugin sources
    resources/io/github/nbclaudecodegui/
      layer.xml                        # NetBeans layer registration
      Bundle.properties                # Localisation strings
  test/
    java/io/github/nbclaudecodegui/   # Tests
```

---

## Development Stages

| Stage | Feature |
|-------|---------|
| 1 | Dummy NBM — installable plugin skeleton *(current)* |
| 2 | Settings page with Claude icon |
| 3 | Toolbar button + empty window |
| 4 | Per-project tabbed window + Settings navigation |
| 5 | First working chat (subprocess, multi-line input) |
| 6 | File attachments (`@path` syntax) |
| 7 | Stream-JSON parsing + formatted output |
| 8 | Diff viewer with Accept/Reject |
| 9 | Full settings integration + session persistence |
| 10 | MCP integration (optional) |

---

## License

Apache License 2.0

---

## Sessions

The plugin uses a tabbed window where each tab represents a Claude Code session
bound to a working directory.

### Opening a session

**From the toolbar:** click the Claude Code button in the Build toolbar —
the window opens with an empty "New Session" tab.

**From the context menu:** right-click any project node in the **Projects
Explorer** and choose **Open with Claude Code**. A new locked tab is created
with the project's root directory pre-filled.

**Manually:** click the **+** tab to open a new session, then type or browse
to any directory in the directory selector and click **Open**.

> Non-project directories (arbitrary filesystem folders) can only be opened
> manually via the "New Session" tab. The context menu is available on
> project nodes only.

### Directory selector

Each tab has a directory control at the top:

- The drop-down lists all currently open projects for quick selection.
- The **Browse…** button opens a directory chooser.
- Once a valid directory is confirmed (**Open**), the control locks and the
  tab label updates to the project name (if the path matches an open project
  root) or the directory basename.
- The **⚙** button opens the plugin settings.

### Closing sessions

Closing the last tab closes the whole Claude Code window. If any session has
a confirmed (locked) directory, a confirmation dialog is shown first.
