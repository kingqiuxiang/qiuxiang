import fs from "node:fs";
import path from "node:path";
import { getConfig } from "../config.js";

const IGNORE_DIRS = new Set([
  "node_modules",
  ".git",
  "dist",
  "build",
  "out",
  "target",
  ".next",
  ".nuxt",
  "coverage",
  ".idea",
  ".cache",
  "vendor",
]);

const CODE_EXT = new Set([
  ".ts", ".tsx", ".js", ".jsx", ".vue", ".java", ".kt", ".go", ".py",
  ".rb", ".php", ".cs", ".rs", ".json", ".yml", ".yaml",
]);

export interface CodeFile {
  path: string;
  rel: string;
  size: number;
  ext: string;
}

export interface CodeMatch {
  rel: string;
  line: number;
  text: string;
}

function projectRoot(): string {
  const root = getConfig().project.rootPath;
  if (!root || !fs.existsSync(root)) {
    throw new Error("PROJECT_ROOT_NOT_SET");
  }
  return root;
}

export function projectAvailable(): boolean {
  const root = getConfig().project.rootPath;
  return Boolean(root && fs.existsSync(root));
}

export function scan(maxFiles = 4000): { root: string; files: CodeFile[]; truncated: boolean } {
  const root = projectRoot();
  const files: CodeFile[] = [];
  let truncated = false;
  const walk = (dir: string) => {
    if (files.length >= maxFiles) {
      truncated = true;
      return;
    }
    let entries: fs.Dirent[];
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      if (files.length >= maxFiles) {
        truncated = true;
        return;
      }
      if (entry.name.startsWith(".") && entry.name !== ".env.example") {
        if (entry.isDirectory()) continue;
      }
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (IGNORE_DIRS.has(entry.name)) continue;
        walk(full);
      } else {
        const ext = path.extname(entry.name).toLowerCase();
        if (!CODE_EXT.has(ext)) continue;
        let size = 0;
        try {
          size = fs.statSync(full).size;
        } catch {
          continue;
        }
        files.push({ path: full, rel: path.relative(root, full), size, ext });
      }
    }
  };
  walk(root);
  return { root, files, truncated };
}

export function search(query: string, limit = 40): CodeMatch[] {
  if (!query.trim()) return [];
  const { files } = scan();
  const needle = query.toLowerCase();
  const matches: CodeMatch[] = [];
  for (const file of files) {
    if (file.size > 400_000) continue;
    let content: string;
    try {
      content = fs.readFileSync(file.path, "utf-8");
    } catch {
      continue;
    }
    const lines = content.split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].toLowerCase().includes(needle)) {
        matches.push({ rel: file.rel, line: i + 1, text: lines[i].trim().slice(0, 240) });
        if (matches.length >= limit) return matches;
      }
    }
  }
  return matches;
}

export function readFileSnippet(rel: string, maxBytes = 60_000): { rel: string; content: string } {
  const root = projectRoot();
  const full = path.resolve(root, rel);
  if (!full.startsWith(root)) throw new Error("Path traversal blocked");
  const content = fs.readFileSync(full, "utf-8");
  return { rel, content: content.slice(0, maxBytes) };
}

/**
 * Find code snippets relevant to a given API path/title to use as the
 * "baseline" context the AI grounds its parameter values on.
 */
export function relevantSnippets(apiPath: string, title: string): CodeMatch[] {
  const tokens = new Set<string>();
  apiPath
    .split(/[/{}?&=:.\-]/)
    .map((t) => t.trim())
    .filter((t) => t.length >= 3 && !["api", "v1", "v2"].includes(t.toLowerCase()))
    .forEach((t) => tokens.add(t));
  // last meaningful path segment is usually the strongest signal
  const out: CodeMatch[] = [];
  const seen = new Set<string>();
  for (const token of tokens) {
    for (const m of search(token, 8)) {
      const key = m.rel + ":" + m.line;
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(m);
      if (out.length >= 24) return out;
    }
  }
  return out;
}
