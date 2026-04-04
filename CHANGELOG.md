- Added documentation artifact (`*-docs.html`) to build and release outputs — user manual and installation guide bundled as a single self-contained HTML file with screenshots
- Added "Copy URL" context menu item in Markdown Preview when hovering over a hyperlink (copies absolute path for .md links, URL for external links)
- Added hyperlinks to claude.ai and console.anthropic.com next to token/API key fields in profile settings
- Changed default profiles directory from ~/.netbeans/claude-profiles/ to ~/.netbeans/claude-plugin/profiles/
- Renamed labels in Profiles settings: "Profiles directory" → "New Profiles Directory", "Config directory" → "Profile Storage Directory"
- Fixed "Accept All" button label in diff panel (was "AcceptAll")
- Reordered settings tabs: Favorites now appears before Profiles
- Changed plugin display name in Tools → Plugins to "Claude Code GUI"
- Fixed Markdown Preview tabs not restoring after IDE restart; tabs now reopen with correct content and scroll position
- Added hyperlink navigation in Markdown Preview: click to open in same tab, right-click for same tab / new tab / browser; back and forward navigation
- Added "Preview Markdown" context menu item for .md files in Projects/Files trees
- Fixed markdown pin preview tab — scroll position now preserved on updates; file-change tracking no longer lost on re-activation
- Added alpha-status notice to README
- Moved Requirements and Installation/Build instructions to docs/installation.md; README and user manual now link to it
- Added user manual covering all plugin features (docs/user-manual.md)
- README: updated Requirements with links, clarified Claude CLI PATH setup, recommended download from GitHub Releases, added note that plugin is written by Claude Code

# 0.17

Initial public release.

- Stage 17: GitHub CI/CD + NBM publishing
- Stage 16: FileDiff location config (inline vs separate tab)
- Stage 15: File attachments in prompt (DnD, @-completion)
- Stage 14: Prompt history and favorites
- Stage 13: Claude Code profiles with isolated config directories
- Stage 12: Prompt panel improvements
- Stage 11: Diff viewer with Accept/Reject/Cancel + settings.local.json merge
- Stage 10: MCP SSE server + IDE tools
- Stage 9: PromptResponsePanel for Claude interactive questions
- Stage 8: Refactor: each session = independent TopComponent
- Stage 7: Embedded terminal (JediTerm) for full Claude TUI
- Stage 6: Stream-JSON parsing + formatted output + markdown
- Stage 5: First working chat with Claude Code
- Stage 4: Window with tabs per working directory
- Stage 3: Toolbar button opening an empty window
- Stage 2: Settings page with Claude icon
- Stage 1: Dummy plugin (installable NBM)
