import { readdir, readFile, stat } from "node:fs/promises";
import path from "node:path";

const ignoredDirectories = new Set([
  ".git",
  ".idea",
  ".ai-yapi-runner",
  "node_modules",
  "out",
  "dist",
  "build",
  "coverage",
  "target"
]);

const supportedExtensions = new Set([
  ".java",
  ".js",
  ".jsx",
  ".ts",
  ".tsx",
  ".vue",
  ".py",
  ".go",
  ".kt",
  ".json",
  ".yaml",
  ".yml",
  ".md",
  ".xml"
]);

const methodMap = {
  GetMapping: "GET",
  PostMapping: "POST",
  PutMapping: "PUT",
  DeleteMapping: "DELETE",
  PatchMapping: "PATCH"
};

async function walk(directory, root, results, options) {
  if (results.length >= options.maxFiles) {
    return;
  }

  const entries = await readdir(directory, { withFileTypes: true });
  for (const entry of entries) {
    if (results.length >= options.maxFiles) {
      return;
    }
    const fullPath = path.join(directory, entry.name);
    const relativePath = path.relative(root, fullPath);
    if (entry.isDirectory()) {
      if (!ignoredDirectories.has(entry.name)) {
        await walk(fullPath, root, results, options);
      }
      continue;
    }
    if (!entry.isFile() || !supportedExtensions.has(path.extname(entry.name))) {
      continue;
    }
    const fileStat = await stat(fullPath);
    if (fileStat.size > options.maxFileBytes) {
      continue;
    }
    results.push({ fullPath, relativePath, size: fileStat.size });
  }
}

function compactPreview(content) {
  return content
    .split(/\r?\n/)
    .filter((line) => line.trim() && !line.trim().startsWith("//"))
    .slice(0, 80)
    .join("\n")
    .slice(0, 6000);
}

function extractJavaEndpoints(content, filePath) {
  const endpoints = [];
  const classPrefixMatch = content.match(
    /@RequestMapping\s*\(\s*(?:"([^"]+)"|value\s*=\s*"([^"]+)")/
  );
  const classPrefix = classPrefixMatch?.[1] || classPrefixMatch?.[2] || "";
  const routeRegex =
    /@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\s*(?:\(\s*(?:"([^"]+)"|value\s*=\s*"([^"]+)"|path\s*=\s*"([^"]+)")?[^)]*\))?/g;

  for (const match of content.matchAll(routeRegex)) {
    const annotation = match[1];
    const rawPath = match[2] || match[3] || match[4] || "";
    const method =
      methodMap[annotation] ||
      (match[0].match(/method\s*=\s*RequestMethod\.([A-Z]+)/)?.[1] ?? "GET");
    endpoints.push({
      method,
      path: `${classPrefix}${rawPath || ""}` || "/",
      source: filePath,
      framework: "spring"
    });
  }
  return endpoints;
}

function extractJavaScriptEndpoints(content, filePath) {
  const endpoints = [];
  const expressRegex =
    /\b(?:app|router|server)\.(get|post|put|patch|delete)\s*\(\s*["'`]([^"'`]+)["'`]/gi;
  for (const match of content.matchAll(expressRegex)) {
    endpoints.push({
      method: match[1].toUpperCase(),
      path: match[2],
      source: filePath,
      framework: "express"
    });
  }

  const fetchRegex = /\bfetch\s*\(\s*["'`]([^"'`]+)["'`]/g;
  for (const match of content.matchAll(fetchRegex)) {
    endpoints.push({
      method: "FETCH",
      path: match[1],
      source: filePath,
      framework: "frontend"
    });
  }
  return endpoints;
}

function extractModels(content, filePath) {
  const models = [];
  const classRegex = /\b(?:class|interface|record)\s+([A-Za-z0-9_]*(?:Request|Response|DTO|Dto|Param|Form|Payload)[A-Za-z0-9_]*)\b/g;
  for (const match of content.matchAll(classRegex)) {
    const fields = [];
    const fieldRegex = /\b(?:private|public|protected)?\s*(?:final\s+)?([A-Za-z0-9_<>, ?]+)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*[;=]/g;
    for (const fieldMatch of content.matchAll(fieldRegex)) {
      fields.push({ type: fieldMatch[1].trim(), name: fieldMatch[2] });
      if (fields.length >= 20) {
        break;
      }
    }
    models.push({ name: match[1], source: filePath, fields });
  }
  return models;
}

export async function scanCodebase(projectRoot, options = {}) {
  const root = path.resolve(projectRoot);
  const maxFiles = options.maxFiles || 160;
  const maxFileBytes = options.maxFileBytes || 90_000;
  const files = [];
  await walk(root, root, files, { maxFiles, maxFileBytes });

  const snippets = [];
  const endpoints = [];
  const models = [];

  for (const file of files) {
    const content = await readFile(file.fullPath, "utf8");
    endpoints.push(...extractJavaEndpoints(content, file.relativePath));
    endpoints.push(...extractJavaScriptEndpoints(content, file.relativePath));
    models.push(...extractModels(content, file.relativePath));
    snippets.push({
      path: file.relativePath,
      extension: path.extname(file.relativePath).slice(1),
      preview: compactPreview(content)
    });
  }

  return {
    root,
    filesScanned: files.length,
    endpoints,
    models,
    snippets,
    summary: {
      endpointCount: endpoints.length,
      modelCount: models.length,
      languages: [...new Set(snippets.map((snippet) => snippet.extension).filter(Boolean))]
    }
  };
}

export function buildPromptContext(scanResult, limit = 12000) {
  const endpointLines = scanResult.endpoints
    .map((endpoint) => `${endpoint.method} ${endpoint.path} (${endpoint.source})`)
    .join("\n");
  const modelLines = scanResult.models
    .map((model) => {
      const fields = model.fields.map((field) => `${field.name}:${field.type}`).join(", ");
      return `${model.name} (${model.source}) ${fields}`;
    })
    .join("\n");
  const snippets = scanResult.snippets
    .slice(0, 24)
    .map((snippet) => `--- ${snippet.path}\n${snippet.preview}`)
    .join("\n");

  return [
    "# Detected endpoints",
    endpointLines || "None",
    "# Detected request/response models",
    modelLines || "None",
    "# Source snippets",
    snippets || "None"
  ]
    .join("\n\n")
    .slice(0, limit);
}
