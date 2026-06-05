# LingCe AI File Cleaner IDEA Plugin

An IntelliJ IDEA plugin for keeping local projects clean:

- Detects temporary files and deletes them automatically when enabled.
- Detects AI-generated scratch/output files using local rules and an optional OpenAI-compatible API.
- Adds IDE/project configuration and AI-assistant configuration files to `.gitignore`; directories are also excluded from IDE indexing.
- Sends suspicious or unpredictable files to an "AI Cleaner" tool window with one-click actions:
  - Move to a configured quarantine directory.
  - Delete.
  - Add to ignore/exclude.
  - Open for review.

## Build

Requires JDK 17+ and Gradle 9+.

From this directory:

```bash
gradle buildPlugin
```

The installable plugin ZIP is generated under:

```text
idea-plugin/build/distributions/
```

Install it in IDEA via:

```text
Settings / Preferences -> Plugins -> Install Plugin from Disk...
```

## Configure

Open:

```text
Settings / Preferences -> AI File Cleaner
```

Available options:

| Option | Purpose |
| --- | --- |
| Base URL | OpenAI-compatible base URL, for example `https://api.openai.com/v1` or a private gateway. |
| API Key | Stored in the IDE password safe. If empty, the plugin uses local heuristics only. |
| Model | Chat completion model used for suspicious AI-related files. |
| Quarantine directory | Directory for one-click transfer of unpredictable files. Relative paths are resolved under the project root. |
| Automatically delete temporary files | Deletes files such as `*.tmp`, `*.bak`, `*.swp`, and files under temp/scratch paths. |
| Automatically delete obvious AI scratch/output files | Disabled by default for safety. Enable if you want clear AI scratch artifacts removed immediately. |
| Add project/AI config files to `.gitignore` and exclude directories | Keeps files such as `.idea/`, `.vscode/`, `.cursor/`, `.continue/`, `CLAUDE.md`, and `AGENTS.md` out of source control/indexing. |
| Scan project when it opens | Runs a background scan after project startup. |
| Scan new and changed files | Watches VFS changes and classifies new/modified files. |

## Usage

- Use `Tools -> AI File Cleaner -> Scan Project for AI/Tmp Files` to scan the full project.
- Use `Tools -> AI File Cleaner -> Scan Selected Files` to classify selected files.
- Open the `AI Cleaner` tool window to review suspicious files and apply one-click actions.

## Safety model

The plugin is intentionally conservative:

- Temporary files can be cleaned automatically.
- Project and AI configuration files are ignored/excluded rather than deleted.
- Source files with AI markers are not deleted by default; they require confirmation.
- OpenAI-compatible classification is only used after local rules detect AI/suspicious signals, avoiding broad API calls across normal source files.
