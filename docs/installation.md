# Installation & Build

## Requirements

| Requirement | Minimum version |
|-------------|-----------------|
| [Apache NetBeans IDE](https://netbeans.apache.org/front/main/download/) | 23 (RELEASE230) |
| Java | 17 |
| [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code/getting-started) (`claude`) | latest stable |

The `claude` executable must be on your system `PATH` **or** its absolute path must be configured in **Tools → Options → Claude Code → General → Claude CLI path** after installing the plugin.

---

## Installation

### Recommended: download from GitHub Releases

Download the latest `.nbm` file from the [Releases page](https://github.com/oleg68/NetbeansClaudeCodeGui/releases/latest).

### Install into NetBeans

1. Open NetBeans → **Tools → Plugins**
2. Switch to the **Downloaded** tab
3. Click **Add Plugins…** and select the `.nbm` file
4. Click **Install** and follow the wizard
5. Restart NetBeans when prompted

---

## Build from Source

```bash
mvn nbm:nbm
```

The installable plugin file is created at:

```
target/netbeans-claude-code-gui-*.nbm
```

### Other build commands

```bash
mvn package              # Full build with tests
mvn package -DskipTests  # Build without tests
mvn test                 # Run all unit tests
```
