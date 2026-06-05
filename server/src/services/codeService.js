import fs from 'node:fs';
import path from 'node:path';

const IGNORE_DIRS = new Set([
  'node_modules', '.git', 'dist', 'build', 'out', '.next', '.nuxt', 'coverage',
  '.idea', '.vscode', 'target', '__pycache__', 'venv', '.venv', 'vendor',
]);
const CODE_EXT = new Set([
  '.js', '.jsx', '.ts', '.tsx', '.vue', '.java', '.py', '.go', '.rb', '.php',
  '.cs', '.kt', '.json', '.yml', '.yaml', '.sql', '.proto',
]);
const MAX_FILE_BYTES = 200 * 1024;
const MAX_FILES = 4000;

function safeResolve(projectPath) {
  if (!projectPath) {
    const err = new Error('未配置项目路径（project.path）。');
    err.code = 'PROJECT_PATH_MISSING';
    throw err;
  }
  const abs = path.resolve(projectPath);
  if (!fs.existsSync(abs) || !fs.statSync(abs).isDirectory()) {
    const err = new Error(`项目路径不存在或不是目录：${abs}`);
    err.code = 'PROJECT_PATH_INVALID';
    throw err;
  }
  return abs;
}

function* walk(dir, root, count = { n: 0 }) {
  let entries;
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
  for (const entry of entries) {
    if (count.n >= MAX_FILES) return;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (IGNORE_DIRS.has(entry.name) || entry.name.startsWith('.')) continue;
      yield* walk(full, root, count);
    } else if (entry.isFile()) {
      const ext = path.extname(entry.name).toLowerCase();
      if (!CODE_EXT.has(ext)) continue;
      count.n += 1;
      yield { full, rel: path.relative(root, full), ext };
    }
  }
}

const FRAMEWORK_HINTS = [
  { dep: 'express', label: 'Express' },
  { dep: 'koa', label: 'Koa' },
  { dep: '@nestjs/core', label: 'NestJS' },
  { dep: 'next', label: 'Next.js' },
  { dep: 'vue', label: 'Vue' },
  { dep: 'react', label: 'React' },
  { dep: 'spring-boot', label: 'Spring Boot' },
  { dep: 'fastapi', label: 'FastAPI' },
  { dep: 'flask', label: 'Flask' },
  { dep: 'gin-gonic', label: 'Gin' },
];

export function scanProject(projectPath) {
  const root = safeResolve(projectPath);
  const byExt = {};
  let total = 0;
  const files = [];
  for (const f of walk(root, root)) {
    byExt[f.ext] = (byExt[f.ext] || 0) + 1;
    total += 1;
    if (files.length < 600) files.push(f.rel);
  }

  const frameworks = new Set();
  const pkgPath = path.join(root, 'package.json');
  if (fs.existsSync(pkgPath)) {
    try {
      const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
      const deps = { ...pkg.dependencies, ...pkg.devDependencies };
      for (const h of FRAMEWORK_HINTS) if (deps[h.dep]) frameworks.add(h.label);
    } catch { /* ignore */ }
  }
  for (const probe of ['pom.xml', 'build.gradle', 'requirements.txt', 'go.mod']) {
    if (fs.existsSync(path.join(root, probe))) {
      const content = fs.readFileSync(path.join(root, probe), 'utf-8').toLowerCase();
      for (const h of FRAMEWORK_HINTS) if (content.includes(h.dep)) frameworks.add(h.label);
    }
  }

  return {
    root,
    totalFiles: total,
    truncated: total >= MAX_FILES,
    byExt: Object.entries(byExt).sort((a, b) => b[1] - a[1]).map(([ext, n]) => ({ ext, count: n })),
    frameworks: [...frameworks],
    sampleFiles: files.slice(0, 120),
  };
}

/** Escape a string for use inside a RegExp. */
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/**
 * Collect code snippets relevant to a given interface so the AI can ground its
 * parameter generation in the real project ("以项目代码为基准").
 */
export function findContextForInterface(projectPath, iface, opts = {}) {
  const root = safeResolve(projectPath);
  const maxSnippets = opts.maxSnippets || 12;
  const contextLines = opts.contextLines || 6;

  // Build search terms: path segments + schema field names.
  const rawPath = (iface.path || '').replace(/:\w+/g, '').replace(/\{\w+\}/g, '');
  const segments = rawPath.split('/').filter((s) => s && s.length > 2);
  const fieldNames = new Set();
  collectFieldNames(iface.bodySchema, fieldNames);
  (iface.query || []).forEach((q) => fieldNames.add(q.name));
  (iface.bodyForm || []).forEach((q) => fieldNames.add(q.name));

  const pathRegex = segments.length
    ? new RegExp(segments.map(esc).join('[\\\\/"\\\'`]'), 'i')
    : null;
  const fullPathRegex = rawPath.length > 3 ? new RegExp(esc(rawPath.replace(/\/$/, '')), 'i') : null;

  const snippets = [];
  const fieldHits = new Set();

  for (const f of walk(root, root)) {
    if (snippets.length >= maxSnippets) break;
    let content;
    try {
      const stat = fs.statSync(f.full);
      if (stat.size > MAX_FILE_BYTES) continue;
      content = fs.readFileSync(f.full, 'utf-8');
    } catch { continue; }

    const lines = content.split(/\r?\n/);
    for (let i = 0; i < lines.length; i += 1) {
      const line = lines[i];
      const matchedPath = (fullPathRegex && fullPathRegex.test(line)) || (pathRegex && pathRegex.test(line));
      if (matchedPath) {
        const start = Math.max(0, i - contextLines);
        const end = Math.min(lines.length, i + contextLines + 1);
        snippets.push({
          file: f.rel,
          startLine: start + 1,
          endLine: end,
          code: lines.slice(start, end).join('\n'),
          reason: 'path-match',
        });
        i = end; // skip ahead to avoid dup overlap
        if (snippets.length >= maxSnippets) break;
      }
    }

    // Note field-name occurrences (enums/constants/types) lightly.
    if (fieldHits.size < 30) {
      for (const name of fieldNames) {
        if (!name || name.length < 3) continue;
        const re = new RegExp(`(enum|const|type|interface|class)[^\n]{0,40}${esc(name)}`, 'i');
        if (re.test(content)) fieldHits.add(`${f.rel}:${name}`);
      }
    }
  }

  return {
    root,
    interface: { method: iface.method, path: iface.path, title: iface.title },
    snippets,
    fieldReferences: [...fieldHits].slice(0, 30),
    matched: snippets.length > 0,
  };
}

function collectFieldNames(schema, set, depth = 0) {
  if (!schema || typeof schema !== 'object' || depth > 6) return;
  if (schema.properties) {
    for (const key of Object.keys(schema.properties)) {
      set.add(key);
      collectFieldNames(schema.properties[key], set, depth + 1);
    }
  }
  if (schema.items) collectFieldNames(schema.items, set, depth + 1);
}

/** Build a compact text blob of context for prompting the AI. */
export function contextToPrompt(ctx) {
  if (!ctx || !ctx.snippets?.length) return '（未在项目代码中找到与该接口直接相关的片段）';
  const parts = ctx.snippets.slice(0, 8).map(
    (s) => `// ${s.file}:${s.startLine}-${s.endLine}\n${s.code}`,
  );
  if (ctx.fieldReferences?.length) {
    parts.push('// 相关字段定义引用:\n' + ctx.fieldReferences.join('\n'));
  }
  return parts.join('\n\n').slice(0, 6000);
}
