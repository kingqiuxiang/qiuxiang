#!/usr/bin/env node

import { spawn } from "node:child_process";
import { promises as fs } from "node:fs";
import os from "node:os";
import path from "node:path";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import JSZip from "jszip";
import { z } from "zod/v4";

const SERVER_VERSION = "1.0.0";
const DEFAULT_OUTPUT_DIR = path.join(os.homedir(), "Documents", "Cursor-XMind");

type TopicInput = {
  title: string;
  notes?: string;
  labels?: string[];
  children?: TopicInput[];
};

type XMindTopic = {
  id: string;
  class: "topic";
  title: string;
  notes?: {
    plain: {
      content: string;
    };
  };
  labels?: string[];
  children?: {
    attached: XMindTopic[];
  };
};

const TopicInputSchema: z.ZodType<TopicInput> = z.lazy(() =>
  z.object({
    title: z.string().min(1).describe("Topic title shown in XMind."),
    notes: z.string().optional().describe("Optional notes attached to this topic."),
    labels: z.array(z.string().min(1)).optional().describe("Optional labels for this topic."),
    children: z.array(TopicInputSchema).optional().describe("Child topics."),
  }),
);

const CreateMapInputSchema = z.object({
  title: z.string().min(1).describe("XMind sheet title and default file name."),
  rootTopic: TopicInputSchema.describe("The central topic and all nested child topics."),
  outputDir: z
    .string()
    .optional()
    .describe("Optional output directory. Defaults to %USERPROFILE%\\Documents\\Cursor-XMind on Windows."),
  fileName: z
    .string()
    .optional()
    .describe("Optional file name. The .xmind extension is added automatically when omitted."),
  openInXMind: z
    .boolean()
    .default(true)
    .describe("Whether to launch the generated file in local XMind after creation."),
  xmindExecutable: z
    .string()
    .optional()
    .describe("Optional absolute path to XMind.exe. If omitted, Windows opens the .xmind file association."),
});

const CreateFromOutlineInputSchema = z.object({
  title: z.string().min(1).describe("XMind sheet title and default file name."),
  outline: z
    .string()
    .min(1)
    .describe(
      "Markdown outline. Supports # headings, -, *, + bullets, and numbered list items. Indentation creates child topics.",
    ),
  outputDir: z
    .string()
    .optional()
    .describe("Optional output directory. Defaults to %USERPROFILE%\\Documents\\Cursor-XMind on Windows."),
  fileName: z
    .string()
    .optional()
    .describe("Optional file name. The .xmind extension is added automatically when omitted."),
  openInXMind: z
    .boolean()
    .default(true)
    .describe("Whether to launch the generated file in local XMind after creation."),
  xmindExecutable: z
    .string()
    .optional()
    .describe("Optional absolute path to XMind.exe. If omitted, Windows opens the .xmind file association."),
});

const OpenFileInputSchema = z.object({
  filePath: z.string().min(1).describe("Absolute or current-working-directory-relative .xmind file path."),
  xmindExecutable: z
    .string()
    .optional()
    .describe("Optional absolute path to XMind.exe. If omitted, Windows opens the .xmind file association."),
});

function createId(prefix: string): string {
  return `${prefix}-${cryptoRandom().slice(0, 12)}`;
}

function cryptoRandom(): string {
  return Math.random().toString(36).slice(2) + Date.now().toString(36);
}

function toXMindTopic(topic: TopicInput): XMindTopic {
  const children = (topic.children ?? []).filter((child) => child.title.trim().length > 0);
  const xmindTopic: XMindTopic = {
    id: createId("topic"),
    class: "topic",
    title: topic.title.trim(),
  };

  if (topic.notes?.trim()) {
    xmindTopic.notes = {
      plain: {
        content: topic.notes.trim(),
      },
    };
  }

  const labels = topic.labels?.map((label) => label.trim()).filter(Boolean);
  if (labels?.length) {
    xmindTopic.labels = labels;
  }

  if (children.length > 0) {
    xmindTopic.children = {
      attached: children.map(toXMindTopic),
    };
  }

  return xmindTopic;
}

async function createXMindFile(options: {
  title: string;
  rootTopic: TopicInput;
  outputDir?: string;
  fileName?: string;
}): Promise<string> {
  const outputDir = resolveOutputDir(options.outputDir);
  await fs.mkdir(outputDir, { recursive: true });

  const filePath = await nextAvailablePath(outputDir, options.title, options.fileName);
  const rootTopic = toXMindTopic(options.rootTopic);
  const sheetTitle = options.title.trim();

  const zip = new JSZip();
  zip.file(
    "content.json",
    JSON.stringify(
      [
        {
          id: createId("sheet"),
          class: "sheet",
          title: sheetTitle,
          rootTopic,
          topicPositioning: "fixed",
          structureClass: "org.xmind.ui.map.unbalanced",
        },
      ],
      null,
      2,
    ),
  );
  zip.file(
    "metadata.json",
    JSON.stringify(
      {
        creator: {
          name: "Cursor XMind MCP",
          version: SERVER_VERSION,
        },
        schemaVersion: "1.0",
      },
      null,
      2,
    ),
  );
  zip.file(
    "manifest.json",
    JSON.stringify(
      {
        "file-entries": {
          "content.json": {},
          "metadata.json": {},
          "manifest.json": {},
        },
      },
      null,
      2,
    ),
  );

  const buffer = await zip.generateAsync({
    type: "nodebuffer",
    compression: "DEFLATE",
    compressionOptions: {
      level: 6,
    },
  });

  await fs.writeFile(filePath, buffer);
  return filePath;
}

function resolveOutputDir(outputDir?: string): string {
  if (!outputDir?.trim()) {
    return DEFAULT_OUTPUT_DIR;
  }

  return path.resolve(outputDir.trim());
}

async function nextAvailablePath(outputDir: string, title: string, fileName?: string): Promise<string> {
  const requestedName = fileName?.trim();
  const baseName = requestedName
    ? ensureXMindExtension(sanitizeFileName(requestedName))
    : `${timestampForFile()}-${sanitizeFileName(title)}.xmind`;

  let candidate = path.join(outputDir, baseName);
  const parsed = path.parse(baseName);

  for (let index = 2; await fileExists(candidate); index += 1) {
    candidate = path.join(outputDir, `${parsed.name}-${index}${parsed.ext || ".xmind"}`);
  }

  return candidate;
}

function ensureXMindExtension(fileName: string): string {
  return fileName.toLowerCase().endsWith(".xmind") ? fileName : `${fileName}.xmind`;
}

function sanitizeFileName(value: string): string {
  const sanitized = value
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, "-")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 100);

  return sanitized || "mind-map";
}

function timestampForFile(): string {
  const now = new Date();
  const pad = (value: number) => value.toString().padStart(2, "0");
  return [
    now.getFullYear(),
    pad(now.getMonth() + 1),
    pad(now.getDate()),
    "-",
    pad(now.getHours()),
    pad(now.getMinutes()),
    pad(now.getSeconds()),
  ].join("");
}

async function fileExists(filePath: string): Promise<boolean> {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function openInLocalXMind(filePath: string, xmindExecutable?: string): Promise<string> {
  const resolvedPath = path.resolve(filePath);
  if (!(await fileExists(resolvedPath))) {
    throw new Error(`XMind file does not exist: ${resolvedPath}`);
  }

  const executable = xmindExecutable?.trim() || process.env.XMIND_EXE?.trim() || process.env.XMIND_PATH?.trim();

  if (executable) {
    await spawnDetached(executable, [resolvedPath], { shell: false });
    return `Started XMind executable: ${executable}`;
  }

  if (process.platform === "win32") {
    await spawnDetached("cmd.exe", ["/c", "start", "", resolvedPath], { shell: false });
    return "Opened with the Windows .xmind file association.";
  }

  if (process.platform === "darwin") {
    await spawnDetached("open", [resolvedPath], { shell: false });
    return "Opened with macOS open.";
  }

  await spawnDetached("xdg-open", [resolvedPath], { shell: false });
  return "Opened with xdg-open.";
}

async function spawnDetached(command: string, args: string[], options: { shell: boolean }): Promise<void> {
  await new Promise<void>((resolve, reject) => {
    const child = spawn(command, args, {
      detached: true,
      shell: options.shell,
      stdio: "ignore",
      windowsHide: true,
    });

    child.once("error", reject);
    child.once("spawn", () => {
      child.unref();
      resolve();
    });
  });
}

function parseOutline(outline: string, fallbackTitle: string): TopicInput {
  const root: TopicInput = {
    title: fallbackTitle.trim(),
    children: [],
  };
  const stack: Array<{ level: number; topic: TopicInput }> = [{ level: 0, topic: root }];
  let rootTitleWasSetFromOutline = false;
  let inFence = false;

  for (const rawLine of outline.split(/\r?\n/)) {
    const line = rawLine.replace(/\t/g, "  ");
    const trimmed = line.trim();

    if (!trimmed) {
      continue;
    }

    if (trimmed.startsWith("```")) {
      inFence = !inFence;
      continue;
    }

    if (inFence) {
      continue;
    }

    const parsed = parseOutlineLine(line);
    if (!parsed) {
      continue;
    }

    if (parsed.kind === "heading" && parsed.level === 1 && !rootTitleWasSetFromOutline && root.children?.length === 0) {
      root.title = parsed.title;
      rootTitleWasSetFromOutline = true;
      continue;
    }

    const level = parsed.kind === "heading" ? Math.max(1, parsed.level - 1) : parsed.level;
    const topic: TopicInput = {
      title: parsed.title,
      children: [],
    };

    while (stack.length > 1 && stack[stack.length - 1].level >= level) {
      stack.pop();
    }

    const parent = stack[stack.length - 1].topic;
    parent.children ??= [];
    parent.children.push(topic);
    stack.push({ level, topic });
  }

  if (!root.children?.length) {
    root.children = [
      {
        title: outline.trim().slice(0, 120) || "需求",
      },
    ];
  }

  return root;
}

function parseOutlineLine(line: string):
  | {
      kind: "heading";
      level: number;
      title: string;
    }
  | {
      kind: "list";
      level: number;
      title: string;
    }
  | null {
  const heading = /^(#{1,6})\s+(.+)$/.exec(line.trim());
  if (heading) {
    return {
      kind: "heading",
      level: heading[1].length,
      title: stripTrailingMarkup(heading[2]),
    };
  }

  const list = /^(\s*)(?:[-*+]|\d+[.)])\s+(.+)$/.exec(line);
  if (list) {
    const indentLevel = Math.floor(list[1].length / 2);
    return {
      kind: "list",
      level: indentLevel + 1,
      title: stripTrailingMarkup(list[2]),
    };
  }

  const plain = line.trim();
  return plain
    ? {
        kind: "list",
        level: 1,
        title: stripTrailingMarkup(plain),
      }
    : null;
}

function stripTrailingMarkup(value: string): string {
  return value
    .replace(/\s+#+\s*$/g, "")
    .replace(/^\[[ xX]\]\s+/, "")
    .trim();
}

function resultText(filePath: string, openMessage?: string): string {
  return [
    "XMind mind map created successfully.",
    `File: ${filePath}`,
    openMessage ? `Open: ${openMessage}` : "Open: skipped by request.",
  ].join("\n");
}

const server = new McpServer({
  name: "xmind-mcp-server",
  version: SERVER_VERSION,
});

server.registerTool(
  "create_xmind_map",
  {
    title: "Create XMind mind map",
    description:
      "Create a .xmind mind map from a structured topic tree, then optionally launch local XMind. Use this after Cursor has converted the user's drawing request into a tree.",
    inputSchema: CreateMapInputSchema,
  },
  async (args) => {
    const filePath = await createXMindFile({
      title: args.title,
      rootTopic: args.rootTopic,
      outputDir: args.outputDir,
      fileName: args.fileName,
    });
    const openMessage = args.openInXMind ? await openInLocalXMind(filePath, args.xmindExecutable) : undefined;

    return {
      content: [
        {
          type: "text",
          text: resultText(filePath, openMessage),
        },
      ],
    };
  },
);

server.registerTool(
  "create_xmind_from_outline",
  {
    title: "Create XMind from Markdown outline",
    description:
      "Create a .xmind mind map from a Markdown outline, then optionally launch local XMind. Useful when the user describes a diagram in natural language and Cursor can first draft headings or bullets.",
    inputSchema: CreateFromOutlineInputSchema,
  },
  async (args) => {
    const rootTopic = parseOutline(args.outline, args.title);
    const filePath = await createXMindFile({
      title: args.title,
      rootTopic,
      outputDir: args.outputDir,
      fileName: args.fileName,
    });
    const openMessage = args.openInXMind ? await openInLocalXMind(filePath, args.xmindExecutable) : undefined;

    return {
      content: [
        {
          type: "text",
          text: resultText(filePath, openMessage),
        },
      ],
    };
  },
);

server.registerTool(
  "open_xmind_file",
  {
    title: "Open local XMind file",
    description:
      "Open an existing .xmind file in local XMind. On Windows, set xmindExecutable or XMIND_EXE for a specific XMind.exe path, otherwise the default .xmind app association is used.",
    inputSchema: OpenFileInputSchema,
  },
  async (args) => {
    const openMessage = await openInLocalXMind(args.filePath, args.xmindExecutable);

    return {
      content: [
        {
          type: "text",
          text: [`XMind open command sent.`, `File: ${path.resolve(args.filePath)}`, `Open: ${openMessage}`].join("\n"),
        },
      ],
    };
  },
);

async function main(): Promise<void> {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.stack || error.message : String(error);
  console.error(message);
  process.exit(1);
});
