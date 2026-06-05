import fs from "node:fs/promises";
import path from "node:path";

const DEFAULT_SCAN_EXTENSIONS = new Set([
  ".js",
  ".ts",
  ".jsx",
  ".tsx",
  ".json",
  ".java",
  ".py",
  ".go",
  ".vue",
  ".yml",
  ".yaml",
  ".env",
  ".md"
]);

const IGNORE_DIRS = new Set([
  "node_modules",
  ".git",
  "dist",
  "build",
  ".next",
  ".idea",
  ".vscode",
  ".cursor",
  "coverage"
]);

async function walkFiles(rootDir, output = []) {
  const entries = await fs.readdir(rootDir, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name.startsWith(".") && entry.name !== ".env") {
      continue;
    }

    if (IGNORE_DIRS.has(entry.name)) {
      continue;
    }

    const absolute = path.join(rootDir, entry.name);
    if (entry.isDirectory()) {
      await walkFiles(absolute, output);
      continue;
    }

    if (DEFAULT_SCAN_EXTENSIONS.has(path.extname(entry.name))) {
      output.push(absolute);
    }
  }
  return output;
}

function extractValueHints(text, paramName) {
  const escaped = paramName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const regexes = [
    new RegExp(`["'\`]${escaped}["'\`]\\s*[:=]\\s*["'\`]([^"'\\n\\r]+)["'\`]`, "gi"),
    new RegExp(`${escaped}\\s*[:=]\\s*["'\`]([^"'\\n\\r]+)["'\`]`, "gi"),
    new RegExp(`["'\`]${escaped}["'\`]\\s*[:=]\\s*(\\d+)`, "gi")
  ];
  const hints = new Set();

  for (const regex of regexes) {
    for (const match of text.matchAll(regex)) {
      if (match[1]) {
        hints.add(match[1]);
      }
      if (hints.size >= 3) {
        return [...hints];
      }
    }
  }

  return [...hints];
}

export async function collectProjectContext(projectPath, targetParams = []) {
  const absoluteProjectPath = path.isAbsolute(projectPath)
    ? projectPath
    : path.resolve(process.cwd(), projectPath);
  const files = await walkFiles(absoluteProjectPath);

  const paramHints = {};
  targetParams.forEach((name) => {
    paramHints[name] = [];
  });

  const fileSnippets = [];

  for (const file of files.slice(0, 120)) {
    const raw = await fs.readFile(file, "utf-8");
    const relative = path.relative(absoluteProjectPath, file);
    if (fileSnippets.length < 25) {
      fileSnippets.push({
        file: relative,
        sample: raw.slice(0, 350)
      });
    }

    targetParams.forEach((paramName) => {
      if (!raw.includes(paramName)) {
        return;
      }
      const hints = extractValueHints(raw, paramName);
      if (hints.length > 0) {
        const existing = new Set(paramHints[paramName]);
        hints.forEach((hint) => existing.add(hint));
        paramHints[paramName] = [...existing].slice(0, 5);
      }
    });
  }

  return {
    projectPath: absoluteProjectPath,
    filesScanned: files.length,
    snippets: fileSnippets,
    paramHints
  };
}
