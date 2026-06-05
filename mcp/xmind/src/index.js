#!/usr/bin/env node

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { zipSync, strToU8 } from "fflate";
import { randomUUID } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { z } from "zod";

const SERVER_NAME = "xmind-local-mcp";
const SERVER_VERSION = "1.0.0";

const topicSchema = z.object({
  title: z.string().min(1, "Topic title is required"),
  notes: z.string().optional(),
  labels: z.array(z.string()).optional(),
  children: z.array(z.unknown()).optional()
});

const createXmindInput = {
  title: z.string().min(1, "Root title is required"),
  topics: z.array(topicSchema).optional().describe("Top-level child topics. Each item can contain nested children."),
  outputDirectory: z.string().optional().describe("Directory where the .xmind file should be written."),
  fileName: z.string().optional().describe("Optional file name. .xmind is added automatically when missing."),
  openInXmind: z.boolean().default(true).describe("Open the generated file with XMind or the OS default app."),
  sheetTitle: z.string().optional().describe("Optional XMind sheet title.")
};

const createFromMarkdownInput = {
  markdown: z.string().min(1, "Markdown outline is required"),
  title: z.string().optional().describe("Root title. If omitted, the first Markdown heading is used when present."),
  outputDirectory: z.string().optional().describe("Directory where the .xmind file should be written."),
  fileName: z.string().optional().describe("Optional file name. .xmind is added automatically when missing."),
  openInXmind: z.boolean().default(true).describe("Open the generated file with XMind or the OS default app."),
  sheetTitle: z.string().optional().describe("Optional XMind sheet title.")
};

const server = new McpServer({
  name: SERVER_NAME,
  version: SERVER_VERSION
});

server.tool(
  "create_xmind",
  "Create a local .xmind file from a structured topic tree and optionally open it in XMind.",
  createXmindInput,
  async (input) => {
    const topics = normalizeTopics(input.topics ?? []);
    const result = await createXmindFile({
      rootTitle: input.title,
      children: topics,
      outputDirectory: input.outputDirectory,
      fileName: input.fileName,
      openInXmind: input.openInXmind,
      sheetTitle: input.sheetTitle
    });

    return toolText(formatResult(result));
  }
);

server.tool(
  "create_xmind_from_markdown",
  "Create a local .xmind file from a Markdown heading or list outline and optionally open it in XMind.",
  createFromMarkdownInput,
  async (input) => {
    const outline = parseMarkdownOutline(input.markdown, input.title);
    const result = await createXmindFile({
      rootTitle: outline.title,
      children: outline.children,
      outputDirectory: input.outputDirectory,
      fileName: input.fileName,
      openInXmind: input.openInXmind,
      sheetTitle: input.sheetTitle
    });

    return toolText(formatResult(result, outline.warnings));
  }
);

await server.connect(new StdioServerTransport());

function normalizeTopics(topics, trail = "topics") {
  if (!Array.isArray(topics)) {
    throw new Error(`${trail} must be an array.`);
  }

  return topics.map((topic, index) => {
    if (!topic || typeof topic !== "object" || Array.isArray(topic)) {
      throw new Error(`${trail}[${index}] must be an object with a title.`);
    }

    const title = typeof topic.title === "string" ? topic.title.trim() : "";
    if (!title) {
      throw new Error(`${trail}[${index}].title is required.`);
    }

    return {
      title,
      notes: typeof topic.notes === "string" ? topic.notes.trim() : undefined,
      labels: Array.isArray(topic.labels)
        ? topic.labels.filter((label) => typeof label === "string" && label.trim()).map((label) => label.trim())
        : undefined,
      children: normalizeTopics(topic.children ?? [], `${trail}[${index}].children`)
    };
  });
}

async function createXmindFile(options) {
  const outputDirectory = resolveOutputDirectory(options.outputDirectory);
  const fileName = normalizeFileName(options.fileName || options.rootTitle);
  const filePath = path.resolve(outputDirectory, fileName);
  const content = buildXmindArchive({
    rootTitle: options.rootTitle,
    children: options.children,
    sheetTitle: options.sheetTitle
  });

  await mkdir(outputDirectory, { recursive: true });
  await writeFile(filePath, content);

  const openResult = options.openInXmind ? openFile(filePath) : { attempted: false };

  return {
    filePath,
    openResult,
    topicCount: countTopics(options.children) + 1
  };
}

function buildXmindArchive({ rootTitle, children, sheetTitle }) {
  const sheetId = createId();
  const now = new Date().toISOString();
  const content = [
    {
      id: sheetId,
      class: "sheet",
      title: sheetTitle || rootTitle,
      rootTopic: toXmindTopic({
        title: rootTitle,
        children
      }),
      topicPositioning: "fixed",
      extensions: []
    }
  ];

  const metadata = {
    creator: {
      name: SERVER_NAME,
      version: SERVER_VERSION
    },
    activeSheetId: sheetId,
    created: now,
    modified: now
  };

  const manifest = {
    "file-entries": {
      "content.json": {},
      "metadata.json": {}
    }
  };

  return zipSync({
    "content.json": strToU8(JSON.stringify(content, null, 2)),
    "metadata.json": strToU8(JSON.stringify(metadata, null, 2)),
    "manifest.json": strToU8(JSON.stringify(manifest, null, 2))
  });
}

function toXmindTopic(topic) {
  const xmindTopic = {
    id: createId(),
    class: "topic",
    title: topic.title
  };

  if (topic.notes) {
    xmindTopic.notes = {
      plain: {
        content: topic.notes
      }
    };
  }

  if (topic.labels?.length) {
    xmindTopic.labels = topic.labels;
  }

  if (topic.children?.length) {
    xmindTopic.children = {
      attached: topic.children.map(toXmindTopic)
    };
  }

  return xmindTopic;
}

function parseMarkdownOutline(markdown, explicitTitle) {
  const warnings = [];
  const roots = [];
  const stack = [];
  let title = explicitTitle?.trim() || "";
  let consumedHeadingAsTitle = false;

  for (const rawLine of markdown.split(/\r?\n/)) {
    const line = rawLine.replace(/\s+$/, "");
    if (!line.trim()) {
      continue;
    }

    const heading = line.match(/^(#{1,6})\s+(.+)$/);
    if (heading) {
      const headingText = cleanMarkdownText(heading[2]);
      if (!title) {
        title = headingText;
        consumedHeadingAsTitle = true;
        continue;
      }

      const level = heading[1].length - (consumedHeadingAsTitle ? 1 : 0);
      addOutlineNode(roots, stack, Math.max(0, level), headingText);
      continue;
    }

    const listItem = line.match(/^(\s*)(?:[-*+]\s+|\d+[.)]\s+)(.+)$/);
    if (listItem) {
      const indent = listItem[1].replace(/\t/g, "  ").length;
      const text = cleanMarkdownText(listItem[2]);
      addOutlineNode(roots, stack, indent, text);
      continue;
    }

    warnings.push(`Ignored non-outline line: ${line.trim()}`);
  }

  if (!title && roots.length === 1) {
    const [root] = roots;
    return {
      title: root.title,
      children: root.children,
      warnings
    };
  }

  if (!title) {
    title = "Cursor XMind";
  }

  return {
    title,
    children: roots,
    warnings
  };
}

function addOutlineNode(roots, stack, level, title) {
  const node = {
    title,
    children: []
  };

  while (stack.length && level <= stack[stack.length - 1].level) {
    stack.pop();
  }

  const parent = stack[stack.length - 1]?.node;
  if (parent) {
    parent.children.push(node);
  } else {
    roots.push(node);
  }

  stack.push({ level, node });
}

function cleanMarkdownText(value) {
  return value
    .trim()
    .replace(/^\[ \]\s+/, "")
    .replace(/^\[x\]\s+/i, "")
    .replace(/\*\*(.*?)\*\*/g, "$1")
    .replace(/__(.*?)__/g, "$1")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/\[(.*?)\]\((.*?)\)/g, "$1")
    .trim();
}

function openFile(filePath) {
  const executable = process.env.XMIND_PATH?.trim();

  if (executable) {
    return spawnDetached(executable, [filePath], "XMIND_PATH");
  }

  if (process.platform === "win32") {
    return spawnDetached("cmd.exe", ["/c", "start", "", filePath], "Windows file association");
  }

  if (process.platform === "darwin") {
    return spawnDetached("open", [filePath], "macOS open");
  }

  return spawnDetached("xdg-open", [filePath], "xdg-open");
}

function spawnDetached(command, args, method) {
  try {
    const child = spawn(command, args, {
      detached: true,
      stdio: "ignore",
      windowsHide: true
    });
    child.unref();
    return {
      attempted: true,
      ok: true,
      method
    };
  } catch (error) {
    return {
      attempted: true,
      ok: false,
      method,
      error: error instanceof Error ? error.message : String(error)
    };
  }
}

function resolveOutputDirectory(outputDirectory) {
  if (outputDirectory?.trim()) {
    return path.resolve(expandHome(outputDirectory.trim()));
  }

  if (process.env.XMIND_OUTPUT_DIR?.trim()) {
    return path.resolve(expandHome(process.env.XMIND_OUTPUT_DIR.trim()));
  }

  return path.join(os.homedir(), "Documents", "Cursor-XMind");
}

function expandHome(value) {
  if (value === "~") {
    return os.homedir();
  }

  if (value.startsWith("~/") || value.startsWith("~\\")) {
    return path.join(os.homedir(), value.slice(2));
  }

  return value;
}

function normalizeFileName(value) {
  const baseName = value
    .trim()
    .replace(/[<>:"/\\|?*\u0000-\u001F]/g, "-")
    .replace(/\s+/g, " ")
    .slice(0, 80)
    .replace(/[. ]+$/, "") || "cursor-xmind";

  return baseName.toLowerCase().endsWith(".xmind") ? baseName : `${baseName}.xmind`;
}

function countTopics(topics) {
  return topics.reduce((total, topic) => total + 1 + countTopics(topic.children ?? []), 0);
}

function createId() {
  return randomUUID().replace(/-/g, "");
}

function formatResult(result, warnings = []) {
  const lines = [
    `XMind file created: ${result.filePath}`,
    `Topics written: ${result.topicCount}`
  ];

  if (result.openResult.attempted) {
    if (result.openResult.ok) {
      lines.push(`Open command sent via ${result.openResult.method}.`);
    } else {
      lines.push(`Open command failed via ${result.openResult.method}: ${result.openResult.error}`);
    }
  } else {
    lines.push("Open command skipped.");
  }

  if (warnings.length) {
    lines.push("", "Warnings:");
    lines.push(...warnings.map((warning) => `- ${warning}`));
  }

  return lines.join("\n");
}

function toolText(text) {
  return {
    content: [
      {
        type: "text",
        text
      }
    ]
  };
}
