import fs from 'node:fs';
import path from 'node:path';
import type { ApiInterface, Project } from '../types.js';

const IGNORE_DIRS = new Set([
  'node_modules', '.git', 'dist', 'build', 'out', 'target', '.idea', '.vscode',
  'coverage', '.next', '.nuxt', 'vendor', '__pycache__', '.gradle',
]);

const CODE_EXT = new Set([
  '.js', '.ts', '.jsx', '.tsx', '.vue', '.java', '.kt', '.go', '.py', '.rb',
  '.php', '.cs', '.rs', '.scala', '.c', '.cpp', '.h',
]);

export interface CodeSnippet {
  file: string;
  line: number;
  preview: string;
}

export interface CodeContext {
  available: boolean;
  root: string;
  fileCount: number;
  snippets: CodeSnippet[];
  note?: string;
}

function walk(dir: string, depth: number, acc: string[], limit: number) {
  if (depth > 12 || acc.length >= limit) return;
  let entries: fs.Dirent[] = [];
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const e of entries) {
    if (acc.length >= limit) return;
    if (e.name.startsWith('.') && e.name !== '.') continue;
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (IGNORE_DIRS.has(e.name)) continue;
      walk(full, depth + 1, acc, limit);
    } else if (CODE_EXT.has(path.extname(e.name))) {
      acc.push(full);
    }
  }
}

/** 扫描项目源码，定位与某接口 path 相关的代码片段，供 AI 作为上下文 */
export function findCodeContext(project: Project, api: ApiInterface, maxSnippets = 6): CodeContext {
  const root = project.codePath?.trim();
  if (!root || !fs.existsSync(root)) {
    return {
      available: false,
      root: root || '',
      fileCount: 0,
      snippets: [],
      note: root ? '代码路径不存在' : '未配置项目代码路径',
    };
  }

  const files: string[] = [];
  walk(root, 0, files, 4000);

  // 构造搜索关键词：路径片段（去掉 {param}）、接口标题
  const cleanedPath = api.path.replace(/\{[^}]+\}/g, '').replace(/\/+/g, '/');
  const segments = cleanedPath.split('/').filter((s) => s.length >= 3);
  const lastSeg = segments[segments.length - 1] || '';
  const keywords = Array.from(
    new Set([api.path, cleanedPath, lastSeg, ...segments].filter(Boolean))
  );

  const snippets: CodeSnippet[] = [];
  for (const file of files) {
    if (snippets.length >= maxSnippets) break;
    let content = '';
    try {
      const stat = fs.statSync(file);
      if (stat.size > 600_000) continue;
      content = fs.readFileSync(file, 'utf-8');
    } catch {
      continue;
    }
    const lines = content.split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (keywords.some((k) => line.includes(k))) {
        const start = Math.max(0, i - 4);
        const end = Math.min(lines.length, i + 12);
        snippets.push({
          file: path.relative(root, file),
          line: i + 1,
          preview: lines.slice(start, end).join('\n'),
        });
        break;
      }
    }
  }

  return {
    available: true,
    root,
    fileCount: files.length,
    snippets,
    note: snippets.length === 0 ? '未在源码中匹配到该接口路径，AI 将仅依据接口定义生成参数' : undefined,
  };
}

/** 项目源码概览（文件树统计），用于「快捷启动」前的项目识别 */
export function describeProject(project: Project) {
  const root = project.codePath?.trim();
  if (!root || !fs.existsSync(root)) {
    return { available: false, root: root || '', techHints: [] as string[] };
  }
  const hints: string[] = [];
  const check = (f: string, label: string) => {
    if (fs.existsSync(path.join(root, f))) hints.push(label);
  };
  check('package.json', 'Node.js');
  check('pom.xml', 'Maven (Java)');
  check('build.gradle', 'Gradle (Java)');
  check('requirements.txt', 'Python');
  check('go.mod', 'Go');
  check('Cargo.toml', 'Rust');
  check('composer.json', 'PHP');
  check('vite.config.ts', 'Vite');
  check('vite.config.js', 'Vite');
  check('next.config.js', 'Next.js');
  return { available: true, root, techHints: hints };
}
