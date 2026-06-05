import fs from "node:fs";
import path from "node:path";
import { config } from "../config/index.js";
import { logger } from "../utils/logger.js";
import type { ApiInterface, CodeEvidence } from "../types/index.js";

const IGNORE_DIRS = new Set([
  "node_modules", ".git", "dist", "build", ".next", ".nuxt", "coverage",
  "vendor", "target", ".idea", ".vscode", "__pycache__", ".cache", "out",
]);

const CODE_EXT = new Set([
  ".ts", ".tsx", ".js", ".jsx", ".vue", ".java", ".kt", ".go", ".py",
  ".rb", ".php", ".cs", ".rs", ".json", ".yaml", ".yml",
]);

const MAX_FILES = 4000;
const MAX_FILE_SIZE = 512 * 1024; // 512KB

/**
 * 项目代码索引器：以「项目代码为基准」为 AI 提供上下文证据。
 * 通过匹配接口路径片段与字段名，定位最相关的代码片段。
 */
class CodeIndexer {
  get enabled() {
    return Boolean(config.project.root && fs.existsSync(config.project.root));
  }

  private *walk(dir: string, count = { n: 0 }): Generator<string> {
    if (count.n >= MAX_FILES) return;
    let entries: fs.Dirent[] = [];
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const e of entries) {
      if (count.n >= MAX_FILES) return;
      const full = path.join(dir, e.name);
      if (e.isDirectory()) {
        if (IGNORE_DIRS.has(e.name) || e.name.startsWith(".")) continue;
        yield* this.walk(full, count);
      } else if (e.isFile() && CODE_EXT.has(path.extname(e.name))) {
        count.n++;
        yield full;
      }
    }
  }

  /** 为某个接口收集相关代码证据 */
  searchForInterface(iface: ApiInterface, limit = 8): CodeEvidence[] {
    if (!this.enabled) return [];
    const root = config.project.root;
    const terms = this.buildSearchTerms(iface);
    const evidence: CodeEvidence[] = [];
    const seen = new Set<string>();

    try {
      for (const file of this.walk(root)) {
        if (evidence.length >= limit) break;
        let content: string;
        try {
          const stat = fs.statSync(file);
          if (stat.size > MAX_FILE_SIZE) continue;
          content = fs.readFileSync(file, "utf8");
        } catch {
          continue;
        }
        const lines = content.split(/\r?\n/);
        for (let i = 0; i < lines.length; i++) {
          if (evidence.length >= limit) break;
          const line = lines[i];
          if (!terms.some((t) => line.includes(t))) continue;
          const key = file + ":" + i;
          if (seen.has(key)) continue;
          seen.add(key);
          const start = Math.max(0, i - 2);
          const end = Math.min(lines.length, i + 3);
          evidence.push({
            file: path.relative(root, file),
            line: i + 1,
            snippet: lines.slice(start, end).join("\n"),
          });
        }
      }
    } catch (err) {
      logger.error("代码索引失败：", (err as Error).message);
    }
    // 路径完整匹配优先
    return evidence.sort((a, b) => Number(b.snippet.includes(iface.path)) - Number(a.snippet.includes(iface.path)));
  }

  private buildSearchTerms(iface: ApiInterface): string[] {
    const terms = new Set<string>();
    terms.add(iface.path);
    // 去掉路径参数与前导 api，提取有意义的片段
    for (const seg of iface.path.split("/")) {
      const clean = seg.replace(/[{}:]/g, "");
      if (clean && clean.length > 2 && !["api", "v1", "v2"].includes(clean.toLowerCase())) {
        terms.add(clean);
      }
    }
    for (const p of iface.params) {
      if (p.name && p.name.length > 2) terms.add(p.name);
    }
    return [...terms];
  }

  /** 列出项目根目录下的顶层结构，便于前端展示与 AI 概览 */
  overview(): { name: string; type: "dir" | "file" }[] {
    if (!this.enabled) return [];
    try {
      return fs
        .readdirSync(config.project.root, { withFileTypes: true })
        .filter((e) => !IGNORE_DIRS.has(e.name))
        .map((e) => ({ name: e.name, type: e.isDirectory() ? ("dir" as const) : ("file" as const) }))
        .slice(0, 100);
    } catch {
      return [];
    }
  }
}

export const codeIndexer = new CodeIndexer();
