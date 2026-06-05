import { randomUUID } from "node:crypto";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";

import JSZip from "jszip";
import { z } from "zod";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

const SERVER_VERSION = "1.0.0";
const DEFAULT_TITLE = "需求脑图";
const DEFAULT_OUTPUT_DIR = path.join(os.homedir(), "Documents", "xmind-maps");

function normalizeTopicTitle(title) {
  const normalized = `${title ?? ""}`.replace(/\s+/g, " ").trim();
  if (!normalized) {
    return "未命名主题";
  }
  return normalized.slice(0, 120);
}

function sanitizeFileName(input) {
  const cleaned = `${input ?? ""}`
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, "_")
    .replace(/\s+/g, " ")
    .trim();
  return cleaned || `mindmap-${Date.now()}.xmind`;
}

function ensureXmindExt(fileName) {
  return fileName.toLowerCase().endsWith(".xmind") ? fileName : `${fileName}.xmind`;
}

function createNode(title) {
  return {
    title: normalizeTopicTitle(title),
    children: [],
  };
}

function stripListPrefix(line) {
  return line
    .replace(/^\s*#{1,6}\s+/, "")
    .replace(/^\s*[-*+]\s+/, "")
    .replace(/^\s*\d+([.)、])\s+/, "")
    .trim();
}

function leadingSpaces(line) {
  return line.replace(/\t/g, "  ").match(/^\s*/)?.[0]?.length ?? 0;
}

function parseOutlineToTree(rootTitle, outlineMarkdown) {
  const root = createNode(rootTitle);
  const lines = outlineMarkdown.split(/\r?\n/);
  const stack = [{ indent: -1, node: root }];

  for (const rawLine of lines) {
    if (!rawLine.trim()) {
      continue;
    }

    const normalizedLine = rawLine.replace(/\t/g, "  ");
    const heading = normalizedLine.match(/^\s*(#{1,6})\s+(.+)$/);
    const indent = heading ? heading[1].length * 2 : leadingSpaces(normalizedLine);
    const text = heading ? heading[2].trim() : stripListPrefix(normalizedLine);
    if (!text) {
      continue;
    }

    const node = createNode(text);
    while (stack.length > 1 && indent <= stack[stack.length - 1].indent) {
      stack.pop();
    }
    stack[stack.length - 1].node.children.push(node);
    stack.push({ indent, node });
  }

  if (root.children.length === 0 && outlineMarkdown.trim()) {
    root.children.push(createNode(stripListPrefix(outlineMarkdown.trim())));
  }

  return root;
}

function parseRequirementToTree(requirement, title) {
  const looksLikeOutline = /(^|\n)\s*([-*+]|\d+[.)、]|#{1,6})\s+/.test(requirement);
  if (looksLikeOutline) {
    return parseOutlineToTree(title, requirement);
  }

  const root = createNode(title);
  const normalized = requirement.replace(/\r/g, "");
  const segments = normalized
    .split(/[。！？!?；;\n]+/)
    .map((segment) => segment.trim())
    .filter((segment) => segment.length > 0)
    .slice(0, 16);

  if (segments.length === 0) {
    root.children.push(createNode("请补充更具体的需求描述"));
    return root;
  }

  for (const segment of segments) {
    const [left, right] = segment.split(/[:：]/, 2);
    if (right && left.length <= 20) {
      const parent = createNode(left);
      parent.children.push(createNode(right));
      root.children.push(parent);
      continue;
    }
    root.children.push(createNode(segment));
  }

  return root;
}

function topicNodeToXMind(node) {
  const topic = {
    id: randomUUID(),
    title: normalizeTopicTitle(node.title),
  };
  if (node.children.length > 0) {
    topic.children = {
      attached: node.children.map(topicNodeToXMind),
    };
  }
  return topic;
}

async function buildXmindFile(filePath, tree) {
  const rootTopic = topicNodeToXMind(tree);
  const sheetId = randomUUID();
  const content = [
    {
      id: sheetId,
      class: "sheet",
      title: normalizeTopicTitle(tree.title),
      rootTopic,
    },
  ];

  const metadata = {
    creator: {
      name: "xmind-local-mcp",
      version: SERVER_VERSION,
    },
    activeSheetId: sheetId,
  };

  const manifest = {
    "file-entries": {
      "content.json": {},
      "metadata.json": {},
    },
  };

  const zip = new JSZip();
  zip.file("content.json", JSON.stringify(content, null, 2));
  zip.file("metadata.json", JSON.stringify(metadata, null, 2));
  zip.file("manifest.json", JSON.stringify(manifest, null, 2));

  const buffer = await zip.generateAsync({ type: "nodebuffer" });
  await fs.writeFile(filePath, buffer);
}

async function resolveOutputPath(fileName, title) {
  const outputDir = process.env.XMIND_OUTPUT_DIR
    ? path.resolve(process.env.XMIND_OUTPUT_DIR)
    : DEFAULT_OUTPUT_DIR;
  await fs.mkdir(outputDir, { recursive: true });

  const safeName = fileName
    ? sanitizeFileName(fileName)
    : `${new Date().toISOString().replace(/[:.]/g, "-")}-${sanitizeFileName(title)}`;

  return path.join(outputDir, ensureXmindExt(safeName));
}

function openFileWithDefaultApp(filePath) {
  if (process.platform === "win32") {
    const xmindExePath = process.env.XMIND_EXE_PATH?.trim();
    if (xmindExePath) {
      const child = spawn(xmindExePath, [filePath], {
        detached: true,
        stdio: "ignore",
        windowsHide: true,
      });
      child.unref();
      return;
    }

    const child = spawn("cmd.exe", ["/c", "start", "", filePath], {
      detached: true,
      stdio: "ignore",
      windowsHide: true,
    });
    child.unref();
    return;
  }

  if (process.platform === "darwin") {
    const child = spawn("open", [filePath], { detached: true, stdio: "ignore" });
    child.unref();
    return;
  }

  const child = spawn("xdg-open", [filePath], { detached: true, stdio: "ignore" });
  child.unref();
}

async function createXmindAndMaybeOpen({ title, tree, fileName, autoOpen }) {
  const outputPath = await resolveOutputPath(fileName, title);
  await buildXmindFile(outputPath, tree);

  let openMessage = "未自动打开。";
  if (autoOpen) {
    try {
      openFileWithDefaultApp(outputPath);
      openMessage = "已触发本地打开。";
    } catch (error) {
      openMessage = `自动打开失败：${error instanceof Error ? error.message : String(error)}`;
    }
  }

  return {
    outputPath,
    openMessage,
  };
}

const server = new McpServer({
  name: "xmind-local-mcp",
  version: SERVER_VERSION,
});

server.registerTool(
  "create_xmind_from_outline",
  {
    title: "Create XMind From Outline",
    description:
      "根据 Markdown 大纲生成 .xmind 文件，并可自动拉起本地 XMind 打开（Windows 优先）。",
    inputSchema: z.object({
      title: z.string().min(1).describe("脑图中心主题"),
      outline_markdown: z
        .string()
        .min(1)
        .describe("使用缩进列表或标题语法的 Markdown 大纲"),
      file_name: z.string().optional().describe("可选文件名，例如 login-flow.xmind"),
      auto_open: z.boolean().default(true).describe("是否立即在本机打开"),
    }),
  },
  async ({ title, outline_markdown: outlineMarkdown, file_name: fileName, auto_open: autoOpen }) => {
    const tree = parseOutlineToTree(title, outlineMarkdown);
    const result = await createXmindAndMaybeOpen({ title, tree, fileName, autoOpen });
    return {
      content: [
        {
          type: "text",
          text: `XMind 已生成：${result.outputPath}\n${result.openMessage}`,
        },
      ],
      structuredContent: {
        file_path: result.outputPath,
        opened: autoOpen,
      },
    };
  }
);

server.registerTool(
  "create_xmind_from_requirement",
  {
    title: "Create XMind From Requirement",
    description:
      "输入需求文本后自动拆分为脑图分支，生成 .xmind，并可自动打开本地 XMind。",
    inputSchema: z.object({
      requirement: z.string().min(1).describe("需求描述，可是自然语言或列表"),
      title: z.string().default(DEFAULT_TITLE).describe("中心主题，不填则使用“需求脑图”"),
      file_name: z.string().optional().describe("可选文件名"),
      auto_open: z.boolean().default(true).describe("是否立即在本机打开"),
    }),
  },
  async ({ requirement, title, file_name: fileName, auto_open: autoOpen }) => {
    const tree = parseRequirementToTree(requirement, title);
    const result = await createXmindAndMaybeOpen({ title, tree, fileName, autoOpen });
    return {
      content: [
        {
          type: "text",
          text: `XMind 已生成：${result.outputPath}\n${result.openMessage}`,
        },
      ],
      structuredContent: {
        file_path: result.outputPath,
        opened: autoOpen,
      },
    };
  }
);

server.registerTool(
  "open_xmind_file",
  {
    title: "Open Existing XMind File",
    description: "打开本地已有的 .xmind 文件（Windows 会优先使用 XMIND_EXE_PATH）。",
    inputSchema: z.object({
      file_path: z.string().min(1).describe("要打开的 .xmind 绝对路径"),
    }),
  },
  async ({ file_path: filePath }) => {
    const resolvedPath = path.resolve(filePath);
    await fs.access(resolvedPath);
    openFileWithDefaultApp(resolvedPath);

    return {
      content: [{ type: "text", text: `已触发打开：${resolvedPath}` }],
      structuredContent: {
        file_path: resolvedPath,
      },
    };
  }
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("xmind-local-mcp started");
}

main().catch((error) => {
  console.error("xmind-local-mcp failed to start:", error);
  process.exit(1);
});
