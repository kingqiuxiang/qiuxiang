import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { randomUUID } from "node:crypto";
import { promises as fsp } from "node:fs";
import { spawn } from "node:child_process";
import JSZip from "jszip";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const SERVER_NAME = "cursor-xmind-mcp";
const SERVER_VERSION = "1.0.0";

/**
 * @typedef {{ title: string, children: TopicNode[] }} TopicNode
 */

function truncateText(value, max = 40) {
  const text = String(value || "").trim().replace(/\s+/g, " ");
  if (text.length <= max) return text;
  return `${text.slice(0, max - 1)}…`;
}

function slugifyFilename(input) {
  const base = String(input || "mindmap")
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5-_]+/gi, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 50);
  return base || "mindmap";
}

function ensureXmindExt(name) {
  if (name.toLowerCase().endsWith(".xmind")) return name;
  return `${name}.xmind`;
}

function normalizeOutputDir(outputDir) {
  if (!outputDir) return path.resolve(process.cwd(), "generated-xmind");
  return path.resolve(outputDir);
}

function parseMarkdownTree(markdown, title) {
  /** @type {TopicNode} */
  const root = { title: truncateText(title, 80), children: [] };
  const lines = String(markdown || "").replace(/\r\n/g, "\n").split("\n");
  const stack = [{ level: -1, node: root }];
  let hasListItems = false;

  for (const rawLine of lines) {
    const normalized = rawLine.replace(/\t/g, "  ");
    const listMatch = normalized.match(/^(\s*)([-*+]|\d+[.)])\s+(.+)$/);
    if (!listMatch) continue;
    hasListItems = true;
    const spaces = listMatch[1].length;
    const level = Math.floor(spaces / 2);
    const text = truncateText(listMatch[3], 80);
    if (!text) continue;

    const node = { title: text, children: [] };
    while (stack.length > 1 && stack[stack.length - 1].level >= level) {
      stack.pop();
    }
    const parent = stack[stack.length - 1]?.node || root;
    parent.children.push(node);
    stack.push({ level, node });
  }

  if (!hasListItems) {
    const fallbackLines = lines
      .map((line) => line.trim())
      .filter(Boolean)
      .slice(0, 12)
      .map((line) => ({ title: truncateText(line, 80), children: [] }));
    root.children.push(...fallbackLines);
  }

  return root;
}

function splitSentences(input) {
  return String(input || "")
    .replace(/\r\n/g, "\n")
    .split(/\n+|[。！？!?;；]+/g)
    .map((s) => s.trim())
    .filter(Boolean);
}

function inferTreeFromRequirements(requirement, title) {
  /** @type {TopicNode} */
  const root = { title: truncateText(title, 80), children: [] };
  const rows = splitSentences(requirement).slice(0, 40);
  if (rows.length === 0) return root;

  const groups = new Map([
    ["目标", []],
    ["功能", []],
    ["流程", []],
    ["非功能", []],
    ["风险与依赖", []],
    ["其他", []],
  ]);

  const rules = [
    { key: "目标", re: /(目标|目的|愿景|value|objective|why)/i },
    { key: "功能", re: /(功能|需求|feature|模块|页面|接口|能力)/i },
    { key: "流程", re: /(流程|步骤|交互|状态|时序|触发|审批)/i },
    { key: "非功能", re: /(性能|安全|稳定|并发|可用性|审计|监控|non-functional)/i },
    { key: "风险与依赖", re: /(风险|依赖|约束|限制|兼容|成本)/i },
  ];

  for (const row of rows) {
    const matched = rules.find((rule) => rule.re.test(row));
    const key = matched?.key || "其他";
    groups.get(key).push(truncateText(row, 80));
  }

  for (const [key, items] of groups.entries()) {
    if (items.length === 0) continue;
    root.children.push({
      title: key,
      children: items.slice(0, 8).map((item) => ({ title: item, children: [] })),
    });
  }

  if (root.children.length === 0) {
    root.children.push(
      ...rows.slice(0, 12).map((item) => ({ title: truncateText(item, 80), children: [] })),
    );
  }
  return root;
}

function topicToXmindTopic(node) {
  const xmindNode = {
    id: randomUUID(),
    title: truncateText(node.title, 120) || "未命名主题",
  };
  if (Array.isArray(node.children) && node.children.length > 0) {
    xmindNode.children = {
      attached: node.children.map((child) => topicToXmindTopic(child)),
    };
  }
  return xmindNode;
}

async function writeXmindFile(tree, title, outputDir, filename) {
  const finalOutputDir = normalizeOutputDir(outputDir);
  await fsp.mkdir(finalOutputDir, { recursive: true });

  const baseFilename = filename ? ensureXmindExt(filename) : ensureXmindExt(`${slugifyFilename(title)}-${Date.now()}`);
  const filePath = path.join(finalOutputDir, baseFilename);

  const workbook = [
    {
      id: randomUUID(),
      class: "sheet",
      title: "Sheet 1",
      rootTopic: topicToXmindTopic(tree),
    },
  ];
  const metadata = {
    creator: { name: SERVER_NAME, version: SERVER_VERSION },
    modifier: { name: SERVER_NAME, version: SERVER_VERSION },
    created: Date.now(),
    modified: Date.now(),
  };
  const manifest = {
    "file-entries": {
      "content.json": {},
      "metadata.json": {},
      "manifest.json": {},
    },
  };

  const zip = new JSZip();
  zip.file("content.json", JSON.stringify(workbook, null, 2));
  zip.file("metadata.json", JSON.stringify(metadata, null, 2));
  zip.file("manifest.json", JSON.stringify(manifest, null, 2));
  const buffer = await zip.generateAsync({ type: "nodebuffer", compression: "DEFLATE" });
  await fsp.writeFile(filePath, buffer);
  return path.resolve(filePath);
}

function detectXmindExecutable() {
  if (process.platform !== "win32") return null;
  const home = process.env.USERPROFILE || os.homedir();
  const candidates = [
    process.env.XMIND_EXE || "",
    path.join(home, "AppData", "Local", "Programs", "Xmind", "Xmind.exe"),
    path.join(home, "AppData", "Local", "Programs", "Xmind", "resources", "app", "Xmind.exe"),
    "C:\\Program Files\\Xmind\\Xmind.exe",
    "C:\\Program Files\\Xmind ZEN\\Xmind.exe",
    "C:\\Program Files (x86)\\Xmind\\Xmind.exe",
  ].filter(Boolean);
  return candidates.find((candidate) => fs.existsSync(candidate)) || null;
}

function escapePowershell(value) {
  return String(value).replace(/'/g, "''");
}

function openOnWindows(filePath, xmindExecutable) {
  if (process.platform !== "win32") {
    return {
      opened: false,
      message: `当前平台为 ${process.platform}，已生成文件但未自动拉起 Xmind。`,
    };
  }

  const finalExe = xmindExecutable || detectXmindExecutable();
  const psScript = finalExe
    ? `Start-Process -FilePath '${escapePowershell(finalExe)}' -ArgumentList @('${escapePowershell(filePath)}')`
    : `Start-Process -FilePath '${escapePowershell(filePath)}'`;

  const child = spawn("powershell.exe", ["-NoProfile", "-Command", psScript], {
    detached: true,
    stdio: "ignore",
  });
  child.unref();
  return {
    opened: true,
    message: finalExe
      ? `已通过 Xmind 可执行文件启动：${finalExe}`
      : "已通过系统默认文件关联打开 .xmind 文件。",
  };
}

function buildSuccessText(label, details) {
  return [
    `${label}成功`,
    `文件: ${details.filePath}`,
    `节点总数: ${details.nodeCount}`,
    `自动打开: ${details.opened ? "是" : "否"}`,
    details.message ? `说明: ${details.message}` : "",
  ]
    .filter(Boolean)
    .join("\n");
}

function countNodes(root) {
  let count = 1;
  for (const child of root.children || []) count += countNodes(child);
  return count;
}

function parseJsonTree(raw, title) {
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    throw new Error(`treeJson 不是合法 JSON: ${error instanceof Error ? error.message : String(error)}`);
  }
  if (!parsed || typeof parsed !== "object") {
    throw new Error("treeJson 必须是对象，格式: {\"title\":\"中心主题\",\"children\":[...]}");
  }
  const rootTitle = parsed.title ? String(parsed.title) : title;
  return normalizeTreeShape({ title: rootTitle, children: parsed.children || [] }, 0);
}

function normalizeTreeShape(node, depth) {
  if (depth > 8) {
    return { title: truncateText(node.title || "节点", 80), children: [] };
  }
  const rawChildren = Array.isArray(node.children) ? node.children : [];
  return {
    title: truncateText(node.title || "节点", 80),
    children: rawChildren
      .slice(0, 20)
      .map((child) => normalizeTreeShape(child || {}, depth + 1)),
  };
}

const server = new McpServer({
  name: SERVER_NAME,
  version: SERVER_VERSION,
});

server.registerTool(
  "xmind_generate_from_markdown",
  {
    title: "Generate Xmind from markdown",
    description: "根据 markdown 列表结构生成 .xmind 脑图，并在 Windows 自动打开。",
    inputSchema: {
      title: z.string().min(1).describe("脑图中心主题"),
      markdownTree: z.string().min(1).describe("列表结构，例如：- 功能\\n  - 登录\\n  - 注册"),
      outputDir: z.string().optional().describe("输出目录，默认 <cwd>/generated-xmind"),
      filename: z.string().optional().describe("输出文件名，可不带 .xmind"),
      openAfterCreate: z.boolean().optional().describe("是否自动打开，默认 true"),
      xmindExecutable: z.string().optional().describe("可选：Xmind.exe 绝对路径（Windows）"),
    },
  },
  async ({ title, markdownTree, outputDir, filename, openAfterCreate = true, xmindExecutable }) => {
    const tree = parseMarkdownTree(markdownTree, title);
    if (tree.children.length === 0) {
      throw new Error("markdownTree 没有可解析内容，请至少提供一个列表项。");
    }
    const filePath = await writeXmindFile(tree, title, outputDir, filename);
    let opened = false;
    let message = "";
    if (openAfterCreate) {
      const openResult = openOnWindows(filePath, xmindExecutable);
      opened = openResult.opened;
      message = openResult.message;
    }
    const nodeCount = countNodes(tree);
    return {
      content: [{ type: "text", text: buildSuccessText("生成脑图", { filePath, nodeCount, opened, message }) }],
      structuredContent: { filePath, nodeCount, opened, message, title },
    };
  },
);

server.registerTool(
  "xmind_generate_from_requirement",
  {
    title: "Generate Xmind from requirement text",
    description: "根据自然语言需求快速生成脑图骨架，并可自动拉起 Xmind。",
    inputSchema: {
      title: z.string().optional().describe("中心主题，默认：需求脑图"),
      requirement: z.string().min(1).describe("需求原文（中文/英文均可）"),
      outputDir: z.string().optional().describe("输出目录，默认 <cwd>/generated-xmind"),
      filename: z.string().optional().describe("输出文件名，可不带 .xmind"),
      openAfterCreate: z.boolean().optional().describe("是否自动打开，默认 true"),
      xmindExecutable: z.string().optional().describe("可选：Xmind.exe 绝对路径（Windows）"),
      preferMarkdownTree: z.string().optional().describe("可选：直接传 markdown 树以获得更精确结构"),
    },
  },
  async ({
    title = "需求脑图",
    requirement,
    outputDir,
    filename,
    openAfterCreate = true,
    xmindExecutable,
    preferMarkdownTree,
  }) => {
    const tree = preferMarkdownTree
      ? parseMarkdownTree(preferMarkdownTree, title)
      : inferTreeFromRequirements(requirement, title);
    if (tree.children.length === 0) {
      throw new Error("无法从 requirement 解析出有效节点，请补充更具体的需求描述。");
    }
    const filePath = await writeXmindFile(tree, title, outputDir, filename);
    let opened = false;
    let message = "";
    if (openAfterCreate) {
      const openResult = openOnWindows(filePath, xmindExecutable);
      opened = openResult.opened;
      message = openResult.message;
    }
    const nodeCount = countNodes(tree);
    return {
      content: [{ type: "text", text: buildSuccessText("按需求生成脑图", { filePath, nodeCount, opened, message }) }],
      structuredContent: { filePath, nodeCount, opened, message, title },
    };
  },
);

server.registerTool(
  "xmind_generate_from_json_tree",
  {
    title: "Generate Xmind from JSON tree",
    description: "根据 JSON 树结构生成 .xmind 文件并可自动打开。",
    inputSchema: {
      title: z.string().optional().describe("默认中心主题，JSON 未提供时使用"),
      treeJson: z.string().min(1).describe("JSON 字符串，格式：{\"title\":\"中心\",\"children\":[...]}"),
      outputDir: z.string().optional().describe("输出目录，默认 <cwd>/generated-xmind"),
      filename: z.string().optional().describe("输出文件名，可不带 .xmind"),
      openAfterCreate: z.boolean().optional().describe("是否自动打开，默认 true"),
      xmindExecutable: z.string().optional().describe("可选：Xmind.exe 绝对路径（Windows）"),
    },
  },
  async ({ title = "结构化脑图", treeJson, outputDir, filename, openAfterCreate = true, xmindExecutable }) => {
    const tree = parseJsonTree(treeJson, title);
    const finalTitle = tree.title || title;
    const filePath = await writeXmindFile(tree, finalTitle, outputDir, filename);
    let opened = false;
    let message = "";
    if (openAfterCreate) {
      const openResult = openOnWindows(filePath, xmindExecutable);
      opened = openResult.opened;
      message = openResult.message;
    }
    const nodeCount = countNodes(tree);
    return {
      content: [{ type: "text", text: buildSuccessText("按 JSON 生成脑图", { filePath, nodeCount, opened, message }) }],
      structuredContent: { filePath, nodeCount, opened, message, title: finalTitle },
    };
  },
);

server.registerTool(
  "xmind_open_file",
  {
    title: "Open existing Xmind file",
    description: "拉起本地 Xmind 打开已有 .xmind 文件。",
    inputSchema: {
      filePath: z.string().min(1).describe(".xmind 文件路径"),
      xmindExecutable: z.string().optional().describe("可选：Xmind.exe 绝对路径（Windows）"),
    },
  },
  async ({ filePath, xmindExecutable }) => {
    const absolutePath = path.resolve(filePath);
    if (!fs.existsSync(absolutePath)) {
      throw new Error(`文件不存在: ${absolutePath}`);
    }
    const result = openOnWindows(absolutePath, xmindExecutable);
    return {
      content: [
        {
          type: "text",
          text: `打开请求已执行\n文件: ${absolutePath}\n自动打开: ${result.opened ? "是" : "否"}\n说明: ${result.message}`,
        },
      ],
      structuredContent: { filePath: absolutePath, opened: result.opened, message: result.message },
    };
  },
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error(`[${SERVER_NAME}] started on stdio`);
}

main().catch((error) => {
  console.error(`[${SERVER_NAME}] fatal error`, error);
  process.exit(1);
});
