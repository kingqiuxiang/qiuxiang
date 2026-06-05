#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { mkdir, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import os from "node:os";
import path from "node:path";

import { generateXmindBuffer } from "./xmind.js";
import { parseOutline, normalizeStructured } from "./outline.js";
import { openWithXmind } from "./open.js";

const VERSION = "1.0.0";

/** 默认输出目录：Windows 桌面 → 否则 用户主目录下 xmind-mcp 文件夹 */
function defaultOutputDir() {
  if (os.platform() === "win32") {
    const desktop = path.join(os.homedir(), "Desktop");
    if (existsSync(desktop)) return desktop;
    return os.homedir();
  }
  return process.env.XMIND_OUTPUT_DIR || path.join(os.homedir(), "xmind-mcp");
}

/** 清洗文件名，去除非法字符 */
function sanitizeFilename(name) {
  const base = String(name || "mindmap").replace(/\.xmind$/i, "");
  const cleaned = base.replace(/[\\/:*?"<>|\n\r\t]/g, "_").trim() || "mindmap";
  return cleaned.slice(0, 120);
}

/** 递归统计主题数量 */
function countTopics(node) {
  let n = 1;
  for (const c of node?.children || []) n += countTopics(c);
  return n;
}

// 结构化主题节点（递归）
const topicNode = z.object({
  title: z.string().describe("主题文字"),
  note: z.string().optional().describe("备注 / 说明"),
  marker: z
    .union([z.string(), z.array(z.string())])
    .optional()
    .describe("XMind 图标 markerId，如 priority-1 / task-done / flag-red / star-blue"),
  href: z.string().optional().describe("超链接 URL"),
  children: z.array(z.lazy(() => topicNode)).optional().describe("子主题"),
});

const server = new McpServer({ name: "xmind-mcp", version: VERSION });

server.registerTool(
  "create_mindmap",
  {
    title: "生成思维导图并用 XMind 打开",
    description:
      "根据需求生成一个 XMind 思维导图(.xmind) 文件并（默认）用本地 XMind 打开。\n" +
      "提供内容的两种方式（二选一）：\n" +
      "1) outline：Markdown / 缩进大纲文本（推荐，最自然）。例如:\n" +
      "   # 中心主题\\n## 分支一\\n- 要点A\\n  - 子要点\\n## 分支二\n" +
      "2) topics：结构化主题树（需要备注/图标/超链接等精细控制时使用）。\n" +
      "Windows 环境下会自动定位并调用本地 XMind 打开生成的文件。",
    inputSchema: {
      title: z.string().optional().describe("中心主题（标题）。使用 outline 且其首行为标题时可省略。"),
      outline: z
        .string()
        .optional()
        .describe("Markdown/缩进大纲文本。与 topics 二选一。"),
      topics: z
        .array(topicNode)
        .optional()
        .describe("结构化主题树，作为中心主题的下级分支。与 outline 二选一。"),
      filename: z.string().optional().describe("输出文件名（可不带 .xmind 后缀）"),
      directory: z
        .string()
        .optional()
        .describe("输出目录绝对路径。默认 Windows 桌面 / 其他系统为用户主目录下 xmind-mcp。"),
      open: z.boolean().optional().describe("是否生成后立即用 XMind 打开，默认 true"),
    },
  },
  async (args) => {
    try {
      const { outline, topics, filename, directory } = args;
      const shouldOpen = args.open !== false;

      // 1) 组装根节点
      let root;
      if (outline && outline.trim()) {
        root = parseOutline(outline);
        if (args.title && args.title.trim()) root.title = args.title.trim();
      } else if (topics && topics.length) {
        root = normalizeStructured({ title: args.title, topics });
      } else if (args.title && args.title.trim()) {
        root = { title: args.title.trim(), children: [] };
      } else {
        return {
          isError: true,
          content: [
            {
              type: "text",
              text: "缺少内容：请至少提供 outline（大纲文本）或 topics（结构化主题），或提供 title。",
            },
          ],
        };
      }

      // 2) 生成 .xmind 二进制
      const buffer = await generateXmindBuffer(root, {});

      // 3) 写文件
      const dir = directory && directory.trim() ? directory.trim() : defaultOutputDir();
      await mkdir(dir, { recursive: true });
      const name = sanitizeFilename(filename || root.title || "mindmap") + ".xmind";
      const filePath = path.join(dir, name);
      await writeFile(filePath, buffer);

      // 4) 打开
      let openResult = { opened: false, via: "skipped" };
      if (shouldOpen) openResult = openWithXmind(filePath);

      const total = countTopics(root);
      const lines = [
        `✅ 已生成思维导图：${filePath}`,
        `   中心主题：「${root.title}」 · 共 ${total} 个主题节点`,
      ];
      if (shouldOpen) {
        lines.push(
          openResult.opened
            ? `   已调用本地 XMind 打开（方式：${openResult.via}）。`
            : `   ⚠️ 自动打开失败（${openResult.detail || openResult.via}），请手动双击打开该文件。`
        );
      } else {
        lines.push("   （未自动打开，open=false）");
      }

      return {
        content: [{ type: "text", text: lines.join("\n") }],
        structuredContent: { filePath, directory: dir, filename: name, topicCount: total, ...openResult },
      };
    } catch (err) {
      return {
        isError: true,
        content: [{ type: "text", text: `生成失败：${String(err?.stack || err?.message || err)}` }],
      };
    }
  }
);

server.registerTool(
  "open_xmind",
  {
    title: "用本地 XMind 打开已有文件",
    description: "用本地 XMind 应用打开一个已存在的 .xmind 文件（Windows 优先）。",
    inputSchema: {
      filePath: z.string().describe(".xmind 文件的绝对路径"),
    },
  },
  async ({ filePath }) => {
    if (!filePath || !existsSync(filePath)) {
      return {
        isError: true,
        content: [{ type: "text", text: `文件不存在：${filePath}` }],
      };
    }
    const result = openWithXmind(filePath);
    return {
      content: [
        {
          type: "text",
          text: result.opened
            ? `已用本地 XMind 打开：${filePath}（方式：${result.via}）`
            : `打开失败：${result.detail || result.via}`,
        },
      ],
      structuredContent: { filePath, ...result },
    };
  }
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  // 日志输出到 stderr，避免污染 stdio 上的 JSON-RPC 通道
  console.error(`xmind-mcp v${VERSION} 已通过 stdio 启动`);
}

main().catch((err) => {
  console.error("xmind-mcp 启动失败:", err);
  process.exit(1);
});
