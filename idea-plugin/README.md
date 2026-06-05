# LingCe AI File Cleaner IDEA Plugin

IntelliJ IDEA plugin for keeping local projects clean when AI assistants create
temporary files, disposable generated drafts, IDE/project metadata, or unknown
suspicious files.

## Features

- Scans the project after it opens and watches newly created files.
- Deletes obvious temporary files and temporary directories.
- Detects disposable AI output by filename/content heuristics.
- Optionally calls an OpenAI-compatible `/chat/completions` API for suspicious
  files when `baseUrl`, `apiKey`, and `model` are configured.
- Adds IDE/project/AI configuration files to the configured ignore file
  (`.gitignore` by default) instead of deleting them.
- Shows a notification for suspicious files with one-click actions:
  - **Move to quarantine**: moves the file into the configured directory.
  - **Delete**: removes the file from the project.

## Settings

Open **Settings/Preferences > Tools > LingCe AI File Cleaner**.

| Setting | Purpose |
| --- | --- |
| Base URL | OpenAI-compatible API base URL, for example `https://api.openai.com/v1`. |
| API Key | Bearer token for the compatible API. Stored in IDE settings. |
| Model | Model name used by the remote classifier. |
| Quarantine directory | Destination for one-click transfer. Relative paths are resolved from the project root. |
| Ignore file name | File updated for project/AI config ignores, usually `.gitignore`. |
| Max scanned file size | Prevents large/binary files from being read or sent to the classifier. |
| Custom temporary file regex patterns | Additional cleanup rules, one regex per line. |

Remote detection is conservative: it is only used for files that already have a
path/content signal such as `prompt`, `draft`, `generated`, `scratch`, `ai`, or
`llm`, so opening a large project does not send ordinary source files in bulk.

## Build and run

```bash
cd idea-plugin
gradle runIde
gradle buildPlugin
```

If you import the folder into IntelliJ IDEA, Gradle will resolve the IntelliJ
Platform SDK and expose the usual `runIde`/`buildPlugin` tasks.
