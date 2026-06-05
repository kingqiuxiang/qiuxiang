# AI File Cleaner IDEA Plugin

An IntelliJ IDEA plugin that helps keep local projects clean by detecting and handling:

- temporary files (`*.tmp`, editor swap files, backup/reject files, files under tmp/temp folders)
- clearly throwaway AI-generated files
- AI assistant configuration (`.cursor/`, `.windsurf/`, `.continue/`, `.claude/`, `CLAUDE.md`, `AGENTS.md`, etc.)
- IDE/project configuration (`.idea/`, `.vscode/`, `.env*`, build output folders, etc.)
- suspicious or unpredictable files

## Capabilities

- **Configurable AI classifier**: supports OpenAI-compatible `baseUrl`, `apiKey`, and `model`.
- **Safe automatic cleanup**: temporary files are moved to a quarantine directory by default instead of being permanently deleted.
- **Config handling**: project/AI config files can be appended to `.gitignore`; known config/build directories can be marked as IDEA excluded folders.
- **Manual review workflow**: suspicious files show up in the `AI Cleaner` tool window and notifications with one-click actions:
  - move to quarantine
  - delete
  - add to ignore/exclude
- **Project scan**: run `Tools -> Scan Project with AI File Cleaner` or use the `AI Cleaner` tool window.
- **Current file scan**: right-click a file/editor and choose `Scan with AI File Cleaner`.

## Build

```bash
cd idea-plugin
gradle buildPlugin
```

The plugin ZIP is generated under:

```text
idea-plugin/build/distributions/
```

## Install locally

1. Open IntelliJ IDEA.
2. Go to `Settings -> Plugins -> Gear Icon -> Install Plugin from Disk...`.
3. Select the generated ZIP from `idea-plugin/build/distributions/`.
4. Restart the IDE if prompted.

## Configure

Open `Settings -> Tools -> AI File Cleaner`:

- `OpenAI-compatible Base URL`: for example `https://api.openai.com/v1` or your internal proxy.
- `API Key`: your model provider key.
- `Model`: any chat-completions compatible model.
- `Quarantine directory`: defaults to `.ai-cleaner-quarantine`.
- Enable `Use AI API classifier` to call the API; otherwise only local rules are used.

By default the plugin automatically moves temporary files to quarantine and handles config files by updating `.gitignore`/excluded folders. Automatic cleanup of AI-generated throwaway files is disabled until you explicitly enable it.
