#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { writeFileSync, mkdirSync, existsSync } from "node:fs";
import os from "node:os";
import path from "node:path";

import { buildXmindBuffer } from "./xmind.js";
import { parseOutline, normalizeTree } from "./outline.js";
import { openWithXmind } from "./opener.js";

const SERVER_NAME = "xmind-mcp";
const SERVER_VERSION = "1.0.0";

/**
 * 解析默认输出目录：
 *   1) 环境变量 XMIND_OUTPUT_DIR
 *   2) 桌面 / Desktop（Windows 上最常见）
 *   3) 系统临时目录
 */
function defaultOutputDir() {
  const env = process.env.XMIND_OUTPUT_DIR;
  if (env && env.trim()) return env.trim();

  const home = os.homedir();
  const desktopCandidates = [
    path.join(home, "Desktop"),
    path.join(home, "桌面"),
  ];
  for (const d of desktopCandidates) {
    if (existsSync(d)) return path.join(d, "XMind");
  }
  return path.join(os.tmpdir(), "xmind-mcp");
}

/** 把任意字符串清洗成合法文件名（去掉非法字符）。 */
function safeFileName(name) {
  const base = String(name || "mindmap")
    .replace(/[\\/:*?"<>|\n\r\t]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 80);
  return base || "mindmap";
}

/** 由输入参数构造根节点数组（每项一张 sheet）。 */
function resolveRoots({ title, outline, tree }) {
  // 1) 结构化树优先
  if (tree != null) {
    const arr = Array.isArray(tree) ? tree : [tree];
    const roots = arr.map((t) => normalizeTree(t));
    if (title && roots.length === 1) roots[0].title = String(title);
    return roots;
  }

  // 2) 大纲文本
  if (outline && String(outline).trim()) {
    const roots = parseOutline(outline);
    // 如果用户额外给了 title，且大纲解析出多个顶层节点，则用 title 作为统一中心主题
    if (title && roots.length > 1) {
      return [{ title: String(title), children: roots }];
    }
    if (title && roots.length === 1) roots[0].title = String(title);
    return roots;
  }

  // 3) 仅有标题
  if (title && String(title).trim()) {
    return [{ title: String(title), children: [] }];
  }

  throw new Error("请至少提供 outline（大纲文本）、tree（结构化树）或 title 之一");
}

async function main() {
  const server = new McpServer({
    name: SERVER_NAME,
    version: SERVER_VERSION,
  });

  server.registerTool(
    "create_mindmap",
    {
      title: "生成 XMind 思维导图",
      description:
        "根据需求生成 .xmind 思维导图文件，并默认用本地 XMind 打开。" +
        "请用 outline（推荐：Markdown 标题/缩进列表大纲）或 tree（结构化 JSON 树）描述内容。" +
        "outline 示例：\n# 中心主题\n## 分支A\n- 子项1\n- 子项2\n## 分支B",
      inputSchema: {
        outline: z
          .string()
          .optional()
          .describe(
            "Markdown/缩进大纲文本。# 表示中心主题，## / 缩进列表表示层级。与 tree 二选一。"
          ),
        tree: z
          .any()
          .optional()
          .describe(
            "结构化树（对象或数组）。节点字段：title、可选 note、可选 children[]。与 outline 二选一。"
          ),
        title: z
          .string()
          .optional()
          .describe("中心主题标题；可覆盖大纲/树的根标题，也用作默认文件名。"),
        filename: z
          .string()
          .optional()
          .describe("输出文件名（可不带 .xmind 后缀）。默认取 title。"),
        outputDir: z
          .string()
          .optional()
          .describe(
            "输出目录绝对路径。默认：环境变量 XMIND_OUTPUT_DIR > 桌面/XMind > 系统临时目录。"
          ),
        open: z
          .boolean()
          .optional()
          .describe("生成后是否用本地 XMind 打开，默认 true。"),
        xmindAppPath: z
          .string()
          .optional()
          .describe("可选：XMind 可执行文件绝对路径（文件关联缺失时兜底使用）。"),
      },
    },
    async (args) => {
      try {
        const roots = resolveRoots(args);
        const buffer = buildXmindBuffer(roots);

        const dir = args.outputDir?.trim() || defaultOutputDir();
        if (!existsSync(dir)) mkdirSync(dir, { recursive: true });

        const baseName = safeFileName(
          args.filename || args.title || roots[0]?.title || "mindmap"
        ).replace(/\.xmind$/i, "");
        const filePath = path.join(dir, `${baseName}.xmind`);
        writeFileSync(filePath, buffer);

        const shouldOpen = args.open !== false;
        let openResult = null;
        if (shouldOpen) {
          openResult = await openWithXmind(filePath, args.xmindAppPath);
        }

        const sheetCount = roots.length;
        const lines = [
          `✅ 已生成思维导图：${filePath}`,
          `   共 ${sheetCount} 张画布（sheet）。`,
        ];
        if (shouldOpen) {
          if (openResult?.ok) {
            lines.push(
              `🚀 已尝试用本地 XMind 打开（方式：${openResult.method}${
                openResult.detail ? `，${openResult.detail}` : ""
              }）。`
            );
          } else {
            lines.push(
              `⚠️ 自动打开失败：${openResult?.detail || "未知原因"}。` +
                `请手动双击该文件，或在调用时传入 xmindAppPath 指定 XMind.exe 路径。`
            );
          }
        } else {
          lines.push("ℹ️ 未自动打开（open=false）。");
        }

        return {
          content: [{ type: "text", text: lines.join("\n") }],
          structuredContent: {
            filePath,
            sheetCount,
            opened: shouldOpen ? !!openResult?.ok : false,
            openMethod: openResult?.method ?? null,
          },
        };
      } catch (err) {
        return {
          isError: true,
          content: [
            { type: "text", text: `❌ 生成失败：${err?.message || err}` },
          ],
        };
      }
    }
  );

  server.registerTool(
    "open_xmind",
    {
      title: "用本地 XMind 打开文件",
      description: "用本地 XMind（或系统默认关联程序）打开一个已存在的 .xmind 文件。",
      inputSchema: {
        filePath: z.string().describe(".xmind 文件的绝对路径。"),
        xmindAppPath: z
          .string()
          .optional()
          .describe("可选：XMind 可执行文件绝对路径。"),
      },
    },
    async (args) => {
      try {
        if (!existsSync(args.filePath)) {
          throw new Error(`文件不存在：${args.filePath}`);
        }
        const result = await openWithXmind(args.filePath, args.xmindAppPath);
        return {
          content: [
            {
              type: "text",
              text: result.ok
                ? `🚀 已打开：${args.filePath}（方式：${result.method}）`
                : `⚠️ 打开失败：${result.detail || "未知原因"}`,
            },
          ],
          structuredContent: { opened: result.ok, method: result.method },
        };
      } catch (err) {
        return {
          isError: true,
          content: [
            { type: "text", text: `❌ 打开失败：${err?.message || err}` },
          ],
        };
      }
    }
  );

  const transport = new StdioServerTransport();
  await server.connect(transport);
  // 通过 stderr 输出启动日志，避免污染 stdio 协议通道
  console.error(`[${SERVER_NAME}] v${SERVER_VERSION} 已启动（stdio）。`);
}

main().catch((err) => {
  console.error(`[${SERVER_NAME}] 启动失败：`, err);
  process.exit(1);
});
