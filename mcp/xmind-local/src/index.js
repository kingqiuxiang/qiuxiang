import fs from "node:fs/promises";
import fsSync from "node:fs";
import path from "node:path";
import os from "node:os";
import { spawn } from "node:child_process";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { Workbook, RootTopic, Topic, writeLocalFile } from "xmind-generator";

const DEFAULT_OUTPUT_DIR = process.env.XMIND_OUTPUT_DIR
  ? path.resolve(process.env.XMIND_OUTPUT_DIR)
  : path.join(os.homedir(), "Documents", "xmind-mcp");
const MAX_TOPIC_LENGTH = 120;

const topicNodeSchema = z.lazy(() =>
  z.object({
    title: z.string().min(1).max(MAX_TOPIC_LENGTH),
    children: z.array(topicNodeSchema).optional(),
  }),
);

const server = new McpServer({
  name: "xmind-local-mcp",
  version: "1.0.0",
});

server.registerTool(
  "draw_xmind",
  {
    description:
      "根据需求文本或结构化大纲生成 .xmind 脑图文件，并在 Windows 本地自动打开 XMind。",
    inputSchema: {
      title: z.string().min(1).max(MAX_TOPIC_LENGTH).describe("脑图中心主题"),
      requirement: z
        .string()
        .optional()
        .describe("自然语言需求描述。未提供 outline 时将自动解析成脑图分支。"),
      outline: z
        .array(topicNodeSchema)
        .optional()
        .describe("可选：结构化脑图。提供后会优先使用它。"),
      outputPath: z
        .string()
        .optional()
        .describe("可选：输出路径。可传绝对路径或相对路径。"),
      openAfterCreate: z
        .boolean()
        .optional()
        .default(true)
        .describe("是否在生成后自动打开 XMind。默认 true。"),
      xmindExePath: z
        .string()
        .optional()
        .describe("可选：XMind.exe 绝对路径（Windows）。"),
    },
  },
  async ({ title, requirement, outline, outputPath, openAfterCreate, xmindExePath }) => {
    if (!requirement && (!outline || outline.length === 0)) {
      return {
        isError: true,
        content: [
          {
            type: "text",
            text: "缺少脑图内容：请至少提供 requirement 或 outline。",
          },
        ],
      };
    }

    const nodes = normalizeOutline(outline ?? parseRequirementToOutline(requirement));
    if (nodes.length === 0) {
      return {
        isError: true,
        content: [{ type: "text", text: "无法从输入内容中提取有效脑图节点。" }],
      };
    }

    const absoluteOutputPath = resolveOutputPath(outputPath, title);
    await fs.mkdir(path.dirname(absoluteOutputPath), { recursive: true });

    const workbook = Workbook(
      RootTopic(cleanTopicText(title)).children(buildTopicBuilders(nodes)),
    );
    await writeLocalFile(workbook, absoluteOutputPath);

    let openMessage = "已生成文件，未尝试打开。";
    if (openAfterCreate ?? true) {
      const openResult = await openXmindFileOnWindows(absoluteOutputPath, xmindExePath);
      openMessage = openResult.message;
    }

    return {
      content: [
        {
          type: "text",
          text: [
            `脑图已生成：${absoluteOutputPath}`,
            openMessage,
            `一级分支数量：${nodes.length}`,
          ].join("\n"),
        },
      ],
    };
  },
);

server.registerTool(
  "open_xmind_file",
  {
    description: "打开已存在的 .xmind 文件（Windows 本地）。",
    inputSchema: {
      filePath: z.string().min(1).describe(".xmind 文件路径"),
      xmindExePath: z.string().optional().describe("可选：XMind.exe 绝对路径"),
    },
  },
  async ({ filePath, xmindExePath }) => {
    const absolutePath = path.isAbsolute(filePath)
      ? filePath
      : path.resolve(process.cwd(), filePath);

    try {
      await fs.access(absolutePath);
    } catch {
      return {
        isError: true,
        content: [{ type: "text", text: `文件不存在：${absolutePath}` }],
      };
    }

    const result = await openXmindFileOnWindows(absolutePath, xmindExePath);
    return {
      content: [{ type: "text", text: `${result.message}\n文件：${absolutePath}` }],
      isError: !result.opened,
    };
  },
);

function parseRequirementToOutline(requirement = "") {
  const raw = requirement.replace(/\r/g, "");
  if (!raw.trim()) {
    return [];
  }

  const bulletRegex = /^(\s*)(?:[-*+]|(?:\d+[.)、]))\s+(.+)$/;
  const lines = raw.split("\n").filter((line) => line.trim().length > 0);
  const hasBullet = lines.some((line) => bulletRegex.test(line));
  if (hasBullet) {
    const root = [];
    const stack = [{ indent: -1, children: root }];

    for (const line of lines) {
      const match = line.match(bulletRegex);
      if (!match) {
        continue;
      }

      const indent = countIndent(match[1]);
      const title = cleanTopicText(match[2]);
      if (!title) {
        continue;
      }

      while (stack.length > 1 && indent <= stack[stack.length - 1].indent) {
        stack.pop();
      }

      const node = { title, children: [] };
      stack[stack.length - 1].children.push(node);
      stack.push({ indent, children: node.children });
    }
    return root;
  }

  return raw
    .split(/\n+/)
    .flatMap((line) => line.split(/[。；;!?！？]/))
    .map((segment) => cleanTopicText(segment))
    .filter(Boolean)
    .slice(0, 20)
    .map((title) => ({ title, children: [] }));
}

function countIndent(indentText) {
  return indentText.replace(/\t/g, "    ").length;
}

function cleanTopicText(text) {
  return text.replace(/\s+/g, " ").trim().slice(0, MAX_TOPIC_LENGTH);
}

function normalizeOutline(nodes = []) {
  return nodes
    .map((node) => ({
      title: cleanTopicText(node.title ?? ""),
      children: normalizeOutline(node.children ?? []),
    }))
    .filter((node) => node.title.length > 0);
}

function buildTopicBuilders(nodes) {
  return nodes.map((node) => {
    const topic = Topic(node.title);
    if (node.children.length > 0) {
      return topic.children(buildTopicBuilders(node.children));
    }
    return topic;
  });
}

function resolveOutputPath(outputPath, title) {
  const resolved = outputPath
    ? path.isAbsolute(outputPath)
      ? outputPath
      : path.resolve(process.cwd(), outputPath)
    : path.join(DEFAULT_OUTPUT_DIR, `${safeFileName(title)}-${Date.now()}.xmind`);

  if (resolved.toLowerCase().endsWith(".xmind")) {
    return resolved;
  }
  return `${resolved}.xmind`;
}

function safeFileName(name) {
  const cleaned = name
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, "-")
    .replace(/[.\s]+$/g, "")
    .trim();

  return cleaned || "mindmap";
}

function findXmindExecutable(customPath) {
  const candidates = [
    customPath,
    process.env.XMIND_EXECUTABLE,
    "C:\\Program Files\\Xmind\\Xmind.exe",
    "C:\\Program Files\\Xmind ZEN\\XMind ZEN.exe",
    "C:\\Program Files (x86)\\XMind\\XMind.exe",
    process.env.LOCALAPPDATA
      ? path.join(process.env.LOCALAPPDATA, "Programs", "Xmind", "Xmind.exe")
      : null,
  ].filter(Boolean);

  for (const executable of candidates) {
    if (isFileSync(executable)) {
      return executable;
    }
  }
  return null;
}

function isFileSync(filePath) {
  try {
    return fsSync.statSync(filePath).isFile();
  } catch {
    return false;
  }
}

async function openXmindFileOnWindows(filePath, xmindExePath) {
  if (process.platform !== "win32") {
    return {
      opened: false,
      message: "当前系统不是 Windows，已跳过自动打开。请在 Windows 本机运行该 MCP。",
    };
  }

  const executable = findXmindExecutable(xmindExePath);
  if (executable) {
    await spawnDetached(executable, [filePath]);
    return { opened: true, message: `已通过 XMind 可执行文件打开：${executable}` };
  }

  await spawnDetached("explorer.exe", [filePath]);
  return {
    opened: true,
    message:
      "未找到 XMind.exe，已使用系统文件关联打开。若失败，请设置 XMIND_EXECUTABLE 环境变量。",
  };
}

function spawnDetached(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { detached: true, stdio: "ignore" });
    child.once("error", reject);
    child.once("spawn", () => {
      child.unref();
      resolve();
    });
  });
}

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((error) => {
  console.error("xmind-local-mcp 启动失败:", error);
  process.exit(1);
});
