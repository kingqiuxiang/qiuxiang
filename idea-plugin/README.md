# AI File Cleaner IDEA Plugin

AI File Cleaner is an IntelliJ IDEA plugin that keeps local projects clean by detecting:

- temporary/cache/swap files
- high-confidence AI-generated useless draft/output files
- IDE project configuration files
- AI assistant configuration files
- suspicious or unpredictable files that need one-click handling

## Features

- Configure an OpenAI-compatible `baseUrl`, `apiKey`, and model in `Settings | AI File Cleaner`.
- Automatically delete temporary files when confidence is high.
- Automatically quarantine or delete high-confidence AI-generated useless files.
- Add project/AI configuration files to `.gitignore` and mark directories as excluded when possible.
- Notify for suspicious files with one-click `Quarantine`, `Delete`, or `Ignore`.
- Right-click files in the Project view to `Quarantine Suspicious File(s)` or `Delete Suspicious File(s)`.
- Run `Tools | Scan Project for AI/Temp Files` to scan the current project on demand.

## Import and run

1. Open `idea-plugin/` as a Gradle project in IntelliJ IDEA.
2. Use the Gradle task `runIde` to launch a sandbox IDE.
3. Configure the plugin from `Settings | AI File Cleaner`.

The default quarantine directory is:

```text
.ai-file-cleaner/quarantine
```

You can set an absolute path if you want suspicious files moved outside the project.

## API compatibility

The plugin calls an OpenAI-compatible chat completions endpoint:

```text
{baseUrl}/chat/completions
```

The model is asked to return strict JSON:

```json
{
  "category": "KEEP",
  "confidence": 0.95,
  "reason": "normal source file"
}
```

If no API key is configured, or the API request fails, local heuristic classification is used.

## Safety defaults

- Temporary files are deleted automatically.
- AI-generated useless files are quarantined by default, not permanently deleted.
- Suspicious files are never auto-deleted; they produce a notification with one-click actions.
- Project and AI configuration files are ignored/excluded instead of deleted.
