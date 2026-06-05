#!/usr/bin/env node
import os from "node:os";
import path from "node:path";
import { promises as fs } from "node:fs";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

import { writeXmindFile, SUPPORTED_STRUCTURES } from "./xmind.js";
import { outlineToTree } from "./outline.js";
import { openInXmind } from "./open.js";

/** 默认输出目录：环境变量 XMIND_OUTPUT_DIR > 用户主目录下的 XMind-MCP。 */
function defaultOutputDir() {
  const fromEnv = process.env.XMIND_OUTPUT_DIR?.trim();
  if (fromEnv) return fromEnv;
  return path.join(os.homedir(), "XMind-MCP");
}

/** 清洗文件名中的非法字符。 */
function sanitizeFileName(name) {
  return String(name || "mindmap")
    .replace(/[\\/:*?"<>|\r\n]+/g, "_")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 120) || "mindmap";
}

/** 计算最终写出的绝对路径。 */
function resolveFilePath(filePath, title) {
  if (filePath && filePath.trim()) {
    let p = filePath.trim();
    if (!p.toLowerCase().endsWith(".xmind")) p += ".xmind";
    return path.isAbsolute(p) ? p : path.resolve(defaultOutputDir(), p);
  }
  const stamp = new Date()
    .toISOString()
    .replace(/[:T]/g, "-")
    .replace(/\..+/, "");
  const base = `${sanitizeFileName(title)}-${stamp}.xmind`;
  return path.join(defaultOutputDir(), base);
}

/** 统计节点数量（含根节点），用于反馈。 */
function countNodes(node) {
  if (!node) return 0;
  const children = node.children || node.subtopics || [];
  return 1 + (Array.isArray(children) ? children.reduce((s, c) => s + countNodes(c), 0) : 0);
}

const server = new McpServer({
  name: "mcp-xmind",
  version: "1.0.0",
});

const structureEnum = z
  .enum(SUPPORTED_STRUCTURES)
  .describe(
    "图形结构：map=思维导图(默认) / logic=逻辑图 / org=组织结构图 / tree=树形图"
  );

// 递归的节点 schema（用于结构化输入）
const nodeSchema = z.lazy(() =>
  z.object({
    title: z.string().describe("节点文字"),
    note: z.string().optional().describe("节点备注（可选）"),
    children: z.array(nodeSchema).optional().describe("子节点数组（可选）"),
  })
);

// ---------------------------------------------------------------------------
// 工具 1：根据 Markdown 大纲创建 XMind 并自动打开（最常用）
// ---------------------------------------------------------------------------
server.registerTool(
  "create_mind_map",
  {
    title: "创建思维导图(从大纲)",
    description:
      "根据 Markdown 缩进大纲生成 .xmind 思维导图文件，并默认用本地 XMind 打开。" +
      "适合 Cursor 把需求/方案直接整理成大纲后一键出图。\n" +
      "大纲示例：\n# 中心主题\n- 分支A\n  - 子节点A1\n  - 子节点A2\n- 分支B",
    inputSchema: {
      outline: z
        .string()
        .describe(
          "Markdown 缩进大纲。用 # 或缩进表示层级；首个顶层节点作为中心主题。"
        ),
      title: z
        .string()
        .optional()
        .describe("中心主题标题。当大纲存在多个顶层节点时作为根节点；也用于默认文件名。"),
      file_path: z
        .string()
        .optional()
        .describe(
          "输出路径（绝对或相对）。可省略，默认写入输出目录并自动命名。Windows 例：D:\\\\maps\\\\需求.xmind"
        ),
      structure: structureEnum.optional(),
      open_after: z
        .boolean()
        .optional()
        .describe("生成后是否用本地 XMind 自动打开，默认 true"),
    },
  },
  async ({ outline, title, file_path, structure, open_after }) => {
    const tree = outlineToTree(outline, title || "中心主题");
    const target = resolveFilePath(file_path, title || tree.title);
    const { filePath, bytes } = await writeXmindFile({
      tree,
      filePath: target,
      structure,
    });

    let openInfo = "未自动打开";
    const shouldOpen = open_after !== false;
    if (shouldOpen) {
      try {
        const r = await openInXmind(filePath);
        openInfo = `已通过 ${r.via} 打开`;
      } catch (e) {
        openInfo = `自动打开失败：${e?.message || e}（可手动打开该文件）`;
      }
    }

    const summary = {
      file: filePath,
      sizeBytes: bytes,
      nodes: countNodes(tree),
      structure: structure || "map",
      opened: shouldOpen,
      openInfo,
    };
    return {
      content: [
        {
          type: "text",
          text:
            `✅ 已生成思维导图\n路径：${filePath}\n节点数：${summary.nodes}\n结构：${summary.structure}\n${openInfo}`,
        },
      ],
      structuredContent: summary,
    };
  }
);

// ---------------------------------------------------------------------------
// 工具 2：根据结构化 JSON 树创建 XMind 并打开（更精确的程序化输入）
// ---------------------------------------------------------------------------
server.registerTool(
  "create_mind_map_from_tree",
  {
    title: "创建思维导图(从JSON树)",
    description:
      "根据结构化 JSON 节点树生成 .xmind 并默认打开。节点格式：{title, note?, children?[]}。" +
      "适合需要精确控制每个节点（含备注）的场景。",
    inputSchema: {
      root: nodeSchema.describe("根节点(中心主题)"),
      file_path: z.string().optional().describe("输出路径，可省略自动命名"),
      structure: structureEnum.optional(),
      open_after: z.boolean().optional().describe("生成后是否自动打开，默认 true"),
    },
  },
  async ({ root, file_path, structure, open_after }) => {
    const target = resolveFilePath(file_path, root.title);
    const { filePath, bytes } = await writeXmindFile({
      tree: root,
      filePath: target,
      structure,
    });

    let openInfo = "未自动打开";
    const shouldOpen = open_after !== false;
    if (shouldOpen) {
      try {
        const r = await openInXmind(filePath);
        openInfo = `已通过 ${r.via} 打开`;
      } catch (e) {
        openInfo = `自动打开失败：${e?.message || e}`;
      }
    }

    const summary = {
      file: filePath,
      sizeBytes: bytes,
      nodes: countNodes(root),
      structure: structure || "map",
      opened: shouldOpen,
      openInfo,
    };
    return {
      content: [
        {
          type: "text",
          text: `✅ 已生成思维导图\n路径：${filePath}\n节点数：${summary.nodes}\n结构：${summary.structure}\n${openInfo}`,
        },
      ],
      structuredContent: summary,
    };
  }
);

// ---------------------------------------------------------------------------
// 工具 3：打开已存在的 .xmind 文件
// ---------------------------------------------------------------------------
server.registerTool(
  "open_xmind",
  {
    title: "打开XMind文件",
    description: "用本地 XMind（或系统默认关联程序）打开一个已存在的 .xmind 文件。",
    inputSchema: {
      file_path: z.string().describe(".xmind 文件的绝对路径"),
    },
  },
  async ({ file_path }) => {
    try {
      const r = await openInXmind(file_path);
      return {
        content: [{ type: "text", text: `✅ 已通过 ${r.via} 打开：${file_path}` }],
        structuredContent: { opened: true, via: r.via, file: file_path },
      };
    } catch (e) {
      return {
        isError: true,
        content: [{ type: "text", text: `❌ 打开失败：${e?.message || e}` }],
      };
    }
  }
);

// ---------------------------------------------------------------------------
// 工具 4：查看当前配置（输出目录 / XMind 路径 / 平台）
// ---------------------------------------------------------------------------
server.registerTool(
  "xmind_info",
  {
    title: "查看XMind MCP配置",
    description: "返回当前默认输出目录、XMIND_PATH、运行平台等信息，便于排查。",
    inputSchema: {},
  },
  async () => {
    const dir = defaultOutputDir();
    let dirExists = false;
    try {
      await fs.access(dir);
      dirExists = true;
    } catch {
      dirExists = false;
    }
    const info = {
      platform: process.platform,
      outputDir: dir,
      outputDirExists: dirExists,
      xmindPath: process.env.XMIND_PATH || "(未设置，使用系统默认关联程序)",
      supportedStructures: SUPPORTED_STRUCTURES,
    };
    return {
      content: [{ type: "text", text: JSON.stringify(info, null, 2) }],
      structuredContent: info,
    };
  }
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  // 注意：stdio 模式下日志只能写 stderr，避免污染协议通道
  console.error("[mcp-xmind] server started on stdio");
}

main().catch((err) => {
  console.error("[mcp-xmind] fatal:", err);
  process.exit(1);
});
