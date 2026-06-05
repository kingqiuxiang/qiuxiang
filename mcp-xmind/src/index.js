#!/usr/bin/env node
// XMind MCP 服务入口（stdio）。
// 让 Cursor 通过 MCP 调用本地工具：根据需求生成 .xmind 思维导图并用本地 XMind 打开。

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { writeFile, mkdir } from "node:fs/promises";
import path from "node:path";
import os from "node:os";

import { generateXmindBuffer, parseOutline, STRUCTURES } from "./xmind.js";
import { openWithXmind, launchXmind, defaultOutputDir } from "./open.js";

const VERSION = "1.0.0";

// ---- 递归节点 schema ----
const nodeSchema = z.lazy(() =>
  z.object({
    title: z.string().describe("主题文字"),
    note: z.string().optional().describe("备注（XMind 的笔记）"),
    labels: z.array(z.string()).optional().describe("标签"),
    markers: z
      .array(z.string())
      .optional()
      .describe("图标 marker，可用别名(如 优先级1/done/star/flag/yes/question)或原生 markerId(如 priority-1)"),
    href: z.string().optional().describe("超链接 URL"),
    children: z.array(nodeSchema).optional().describe("子主题列表（可无限嵌套）"),
  }),
);

const structureKeys = Object.keys(STRUCTURES).join(", ");

function sanitizeFileName(name) {
  return (
    String(name || "mindmap")
      .replace(/[\\/:*?"<>|\n\r\t]/g, "_")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 80) || "mindmap"
  );
}

function resolveOutputPath(outputPath, title) {
  if (outputPath && outputPath.trim()) {
    let p = outputPath.trim();
    if (!p.toLowerCase().endsWith(".xmind")) {
      p = p + ".xmind";
    }
    return path.isAbsolute(p) ? p : path.resolve(defaultOutputDir(), p);
  }
  const fname = sanitizeFileName(title) + ".xmind";
  return path.join(defaultOutputDir(), fname);
}

async function writeAndMaybeOpen({ buffer, outPath, open, xmindPath }) {
  await mkdir(path.dirname(outPath), { recursive: true });
  await writeFile(outPath, buffer);

  let openResult = { opened: false, via: "skipped" };
  if (open !== false) {
    openResult = await openWithXmind(outPath, xmindPath);
  }
  return openResult;
}

function resultText(outPath, openResult, extra = "") {
  const lines = [
    `已生成 XMind 文件：${outPath}`,
    openResult.opened
      ? `已尝试用本地 XMind 打开（方式：${openResult.via}）。若未自动弹出，请手动双击该文件。`
      : `未自动打开（${openResult.via}${openResult.detail ? ": " + openResult.detail : ""}）。请手动双击该文件，或在 open_xmind 工具中指定 xmindPath。`,
  ];
  if (extra) lines.push(extra);
  return lines.join("\n");
}

const server = new McpServer({ name: "xmind-mcp", version: VERSION });

// ---- 工具 1：从结构化主题树生成 ----
server.registerTool(
  "create_mindmap",
  {
    title: "创建思维导图（结构化）",
    description:
      "根据结构化的主题树生成 .xmind 文件并用本地 XMind 打开。适合由 AI 把需求整理成层级结构后直接绘图。",
    inputSchema: {
      title: z.string().describe("中心主题（根节点）"),
      children: z.array(nodeSchema).optional().describe("子主题树，可无限嵌套"),
      structure: z
        .string()
        .optional()
        .describe(`画布结构类型，可选：${structureKeys}（默认 map 思维导图）`),
      sheetTitle: z.string().optional().describe("画布（Sheet）名称，默认取中心主题"),
      outputPath: z
        .string()
        .optional()
        .describe("输出文件路径（绝对或相对）。省略则保存到桌面，以中心主题命名"),
      open: z.boolean().optional().describe("是否生成后自动用 XMind 打开，默认 true"),
      xmindPath: z.string().optional().describe("XMind 可执行文件路径（无文件关联时使用）"),
    },
  },
  async (args) => {
    const outPath = resolveOutputPath(args.outputPath, args.title);
    const buffer = await generateXmindBuffer({
      title: args.title,
      children: args.children || [],
      structure: args.structure,
      sheetTitle: args.sheetTitle,
    });
    const openResult = await writeAndMaybeOpen({
      buffer,
      outPath,
      open: args.open,
      xmindPath: args.xmindPath,
    });
    return { content: [{ type: "text", text: resultText(outPath, openResult) }] };
  },
);

// ---- 工具 2：从 Markdown / 缩进大纲生成 ----
server.registerTool(
  "create_mindmap_from_outline",
  {
    title: "创建思维导图（大纲/Markdown）",
    description:
      "根据 Markdown 标题(#)或缩进列表(- /缩进)的大纲文本，自动解析层级并生成 .xmind 文件后用本地 XMind 打开。",
    inputSchema: {
      outline: z
        .string()
        .describe(
          "大纲文本。支持 # 标题层级与 - 列表/缩进。第一行作为中心主题。例：\n# 项目计划\n## 需求\n- 调研\n- 评审\n## 开发",
        ),
      structure: z.string().optional().describe(`画布结构类型：${structureKeys}（默认 map）`),
      title: z.string().optional().describe("覆盖中心主题（默认取大纲首行）"),
      outputPath: z.string().optional().describe("输出文件路径，省略则保存到桌面"),
      open: z.boolean().optional().describe("是否自动打开，默认 true"),
      xmindPath: z.string().optional().describe("XMind 可执行文件路径"),
    },
  },
  async (args) => {
    const tree = parseOutline(args.outline);
    const title = args.title || tree.title || "中心主题";
    const outPath = resolveOutputPath(args.outputPath, title);
    const buffer = await generateXmindBuffer({
      title,
      children: tree.children || [],
      structure: args.structure,
    });
    const openResult = await writeAndMaybeOpen({
      buffer,
      outPath,
      open: args.open,
      xmindPath: args.xmindPath,
    });
    const count = countNodes(tree);
    return {
      content: [
        { type: "text", text: resultText(outPath, openResult, `共解析 ${count} 个主题节点。`) },
      ],
    };
  },
);

// ---- 工具 3：打开已有文件 / 拉起 XMind ----
server.registerTool(
  "open_xmind",
  {
    title: "用本地 XMind 打开",
    description: "用本地 XMind 打开一个已存在的 .xmind 文件；若不提供 path，则直接拉起 XMind 应用。",
    inputSchema: {
      path: z.string().optional().describe(".xmind 文件路径；省略则只启动 XMind 应用"),
      xmindPath: z.string().optional().describe("XMind 可执行文件路径（可选）"),
    },
  },
  async (args) => {
    if (args.path && args.path.trim()) {
      const r = await openWithXmind(args.path.trim(), args.xmindPath);
      return {
        content: [
          {
            type: "text",
            text: r.opened
              ? `已尝试打开：${args.path}（方式：${r.via}）`
              : `打开失败：${r.via}${r.detail ? ": " + r.detail : ""}`,
          },
        ],
      };
    }
    const r = await launchXmind(args.xmindPath);
    return {
      content: [
        {
          type: "text",
          text: r.opened ? `已拉起 XMind：${r.via}` : `未能启动 XMind：${r.detail || "未知错误"}`,
        },
      ],
    };
  },
);

// ---- 资源 / 提示：环境信息（便于排查路径问题）----
server.registerTool(
  "xmind_env_info",
  {
    title: "环境信息",
    description: "返回当前 MCP 运行环境（操作系统、默认输出目录、临时目录），便于排查文件路径问题。",
    inputSchema: {},
  },
  async () => {
    return {
      content: [
        {
          type: "text",
          text: [
            `平台: ${os.platform()} ${os.arch()}`,
            `Node: ${process.version}`,
            `XMind MCP: v${VERSION}`,
            `默认输出目录: ${defaultOutputDir()}`,
            `临时目录: ${os.tmpdir()}`,
            `可用结构类型: ${structureKeys}`,
          ].join("\n"),
        },
      ],
    };
  },
);

function countNodes(node) {
  let n = 1;
  for (const c of node.children || []) n += countNodes(c);
  return n;
}

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  // stderr 仅用于日志，不污染 stdio 协议通道
  process.stderr.write(`[xmind-mcp] v${VERSION} 已启动（${os.platform()}）\n`);
}

main().catch((err) => {
  process.stderr.write(`[xmind-mcp] 启动失败: ${err?.stack || err}\n`);
  process.exit(1);
});
