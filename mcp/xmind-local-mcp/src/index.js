import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { Workbook, RootTopic, Topic, writeLocalFile } from "xmind-generator";
import { z } from "zod";

const server = new McpServer({
  name: "xmind-local-mcp",
  version: "1.0.0"
});

const OutlineNode = z.lazy(() =>
  z.object({
    title: z.string().min(1).describe("节点标题"),
    note: z.string().optional().describe("节点备注（可选）"),
    children: z.array(OutlineNode).optional().describe("子节点（可选）")
  })
);

function sanitizeFileName(name) {
  return name
    .trim()
    .replace(/[<>:"/\\|?*]/g, "-")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
    .slice(0, 60)
    .replace(/^-|-$/g, "") || "mindmap";
}

function normalizeOutputPath(outputPath, title) {
  let resolvedPath = outputPath?.trim();
  if (!resolvedPath) {
    const fileName = `${sanitizeFileName(title)}-${Date.now()}.xmind`;
    resolvedPath = path.join(os.homedir(), "Documents", "xmind-mcp", fileName);
  }

  if (!path.isAbsolute(resolvedPath)) {
    resolvedPath = path.resolve(process.cwd(), resolvedPath);
  }

  if (!resolvedPath.toLowerCase().endsWith(".xmind")) {
    resolvedPath = `${resolvedPath}.xmind`;
  }

  fs.mkdirSync(path.dirname(resolvedPath), { recursive: true });
  return resolvedPath;
}

function normalizeNodes(nodes) {
  return nodes
    .map((node) => ({
      title: node.title.trim(),
      note: node.note?.trim(),
      children: node.children ? normalizeNodes(node.children) : []
    }))
    .filter((node) => node.title.length > 0);
}

function parseTextToNodes(text) {
  const cleaned = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  const bulletItems = cleaned
    .map((line) => line.match(/^[-*+]\s+(.+)$/) || line.match(/^\d+[.)]\s+(.+)$/))
    .filter(Boolean)
    .map((match) => match[1].trim())
    .filter(Boolean);

  if (bulletItems.length >= 2) {
    return bulletItems.map((title) => ({ title }));
  }

  const parts = text
    .split(/[。！？!?；;\n]/)
    .map((part) => part.trim())
    .filter(Boolean);

  if (parts.length > 0) {
    return parts.map((title) => ({ title: title.slice(0, 80) }));
  }

  return [{ title: "待补充需求" }];
}

function toTopic(node) {
  let topic = Topic(node.title);
  if (node.note) {
    topic = topic.note(node.note);
  }
  if (node.children && node.children.length > 0) {
    topic = topic.children(node.children.map(toTopic));
  }
  return topic;
}

function countTopics(nodes) {
  return nodes.reduce((acc, node) => acc + 1 + countTopics(node.children || []), 0);
}

function tryOpenInXmind(filePath, xmindExecutable) {
  const executableFromEnv = process.env.XMIND_EXECUTABLE;
  const executable = xmindExecutable?.trim() || executableFromEnv?.trim();

  try {
    if (executable) {
      const child = spawn(executable, [filePath], {
        detached: true,
        stdio: "ignore",
        windowsHide: true
      });
      child.unref();
      return `已通过指定程序启动：${executable}`;
    }

    if (process.platform === "win32") {
      const child = spawn("cmd.exe", ["/c", "start", "", filePath], {
        detached: true,
        stdio: "ignore",
        windowsHide: true
      });
      child.unref();
      return "已通过 Windows 文件关联打开 .xmind 文件";
    }

    if (process.platform === "darwin") {
      const child = spawn("open", [filePath], {
        detached: true,
        stdio: "ignore"
      });
      child.unref();
      return "已通过 macOS open 命令打开文件";
    }

    const child = spawn("xdg-open", [filePath], {
      detached: true,
      stdio: "ignore"
    });
    child.unref();
    return "已通过 xdg-open 打开文件";
  } catch (error) {
    return `文件已生成，但自动打开失败：${error instanceof Error ? error.message : String(error)}`;
  }
}

async function createXmindFile({ title, nodes, outputPath, openAfterCreate, xmindExecutable }) {
  const normalizedTitle = title.trim() || "需求脑图";
  const normalizedNodes = normalizeNodes(nodes);
  const safeNodes = normalizedNodes.length > 0 ? normalizedNodes : [{ title: "待补充需求" }];
  const finalPath = normalizeOutputPath(outputPath, normalizedTitle);

  const workbook = Workbook(
    RootTopic(normalizedTitle).children(safeNodes.map(toTopic))
  );

  await writeLocalFile(workbook, finalPath);

  const openResult = openAfterCreate ? tryOpenInXmind(finalPath, xmindExecutable) : "已跳过自动打开";
  const topicCount = countTopics(safeNodes) + 1;

  return {
    filePath: finalPath,
    title: normalizedTitle,
    topicCount,
    openResult
  };
}

server.tool(
  "create_xmind_from_outline",
  {
    title: z.string().min(1).describe("脑图根节点标题"),
    outline: z.array(OutlineNode).min(1).describe("结构化大纲（数组）"),
    outputPath: z.string().optional().describe("输出路径（可选，支持相对路径）"),
    openAfterCreate: z.boolean().default(true).describe("是否创建后自动打开 XMind"),
    xmindExecutable: z
      .string()
      .optional()
      .describe("XMind 可执行文件路径，例如 C:\\Program Files\\Xmind\\Xmind.exe")
  },
  async ({ title, outline, outputPath, openAfterCreate, xmindExecutable }) => {
    const result = await createXmindFile({
      title,
      nodes: outline,
      outputPath,
      openAfterCreate,
      xmindExecutable
    });

    return {
      content: [
        {
          type: "text",
          text: [
            "XMind 脑图创建成功",
            `- 标题: ${result.title}`,
            `- 节点总数: ${result.topicCount}`,
            `- 文件路径: ${result.filePath}`,
            `- 打开结果: ${result.openResult}`
          ].join("\n")
        }
      ]
    };
  }
);

server.tool(
  "create_xmind_from_text",
  {
    title: z.string().default("需求脑图").describe("脑图根节点标题"),
    requirementText: z.string().min(1).describe("原始需求文本"),
    outputPath: z.string().optional().describe("输出路径（可选，支持相对路径）"),
    openAfterCreate: z.boolean().default(true).describe("是否创建后自动打开 XMind"),
    xmindExecutable: z
      .string()
      .optional()
      .describe("XMind 可执行文件路径，例如 C:\\Program Files\\Xmind\\Xmind.exe")
  },
  async ({ title, requirementText, outputPath, openAfterCreate, xmindExecutable }) => {
    const nodes = parseTextToNodes(requirementText);
    const result = await createXmindFile({
      title,
      nodes,
      outputPath,
      openAfterCreate,
      xmindExecutable
    });

    return {
      content: [
        {
          type: "text",
          text: [
            "已根据需求文本生成 XMind 脑图",
            `- 标题: ${result.title}`,
            `- 节点总数: ${result.topicCount}`,
            `- 文件路径: ${result.filePath}`,
            `- 打开结果: ${result.openResult}`,
            "",
            "提示：若你希望节点层级更精细，建议让 Cursor 先整理结构化大纲，再调用 create_xmind_from_outline。"
          ].join("\n")
        }
      ]
    };
  }
);

const transport = new StdioServerTransport();
await server.connect(transport);
