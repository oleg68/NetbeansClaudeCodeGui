- Fixed markdown pin preview tab jumping to the top on each content update — scroll position is now preserved relative to document height
- Added alpha-status notice to README
- Moved Requirements and Installation/Build instructions to docs/installation.md; README and user manual now link to it
- Fixed pin preview tab losing file-change tracking due to WeakHashMap key GC; now uses HashMap with proper cleanup
- Fixed pin preview not attaching file-change listener when tab is re-activated for a file that was unavailable at initial open time
- Added choice menu detection fixtures for interview menus with descriptions, submit/cancel menus, and login menus with blank-separated options (all detected correctly by existing algorithm)
- Added user manual draft covering all plugin features (docs/user-manual.md)
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
