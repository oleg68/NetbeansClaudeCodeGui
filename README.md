# Claude Code GUI — NetBeans Plugin

![Build](https://github.com/oleg68/NetbeansClaudeCodeGui/actions/workflows/build.yml/badge.svg)
[![Release](https://img.shields.io/github/v/release/oleg68/NetbeansClaudeCodeGui)](https://github.com/oleg68/NetbeansClaudeCodeGui/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/oleg68/NetbeansClaudeCodeGui/total)](https://github.com/oleg68/NetbeansClaudeCodeGui/releases)

> **Alpha:** The plugin is under active development. Some features may not work correctly.

NetBeans Claude Code GUI is a NetBeans IDE plugin that embeds the Claude Code CLI as a full interactive terminal session directly inside the IDE. You type prompts in a dedicated session tab, Claude reads and edits your project files, and the plugin provides:

- a graphical file diff (using NetBeans' built-in diff viewer) before any change is written to disk
- a graphical panel for responding to Claude's interactive questions

IDE integration (open editors, diagnostics, current selection) is exposed to Claude via the MCP protocol so that Claude always has full context about your work.

The plugin code was written entirely by [Claude Code](https://claude.ai/code) using **Claude Sonnet 4.6**, with the author acting as architect and reviewer.

---

## Download

Download the latest `.nbm` file from [GitHub Releases](https://github.com/oleg68/NetbeansClaudeCodeGui/releases/latest).

Intermediate builds between releases are available as artifacts on the [Actions](https://github.com/oleg68/NetbeansClaudeCodeGui/actions) page — open the latest successful workflow run and download the `nbm` artifact (delivered as a zip file; extract the `.nbm` before installing).

---

See [Installation & Build](docs/installation.md) for requirements, installation steps, and build instructions.

---

## Usage

See the [User Manual](docs/user-manual.md) for full documentation of all plugin features.

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
| 17 | GitHub CI/CD + NBM publishing | ✅ |
| 18 | User manual, installation guide, docs HTML artifact | ✅ |
| 19 | Choice menu improvements (checkbox menus, detection fixes) | 🔧 |

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
