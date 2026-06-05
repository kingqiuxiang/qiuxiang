#!/usr/bin/env node

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import * as z from "zod";
import { getXMindInstallHint, openInXMind } from "./launcher.js";
import type { TopicNode } from "./types.js";
import {
  createMindMapFile,
  getDefaultOutputDir,
  listMindMapFiles,
  readMindMapFile,
} from "./xmind-io.js";

const TopicNodeSchema: z.ZodType<TopicNode> = z.lazy(() =>
  z.object({
    title: z.string().min(1).describe("Topic title"),
    note: z.string().optional().describe("Plain-text note for this topic"),
    labels: z.array(z.string()).optional().describe("Labels/tags for this topic"),
    children: z.array(TopicNodeSchema).optional().describe("Child topics"),
  }),
);

const MindMapInputSchema = z.object({
  title: z.string().min(1).describe("Central topic / root title"),
  sheetTitle: z.string().optional().describe("Worksheet title inside the XMind file"),
  children: z
    .array(TopicNodeSchema)
    .optional()
    .describe("Main branches under the central topic"),
});

function textResult(payload: unknown) {
  return {
    content: [
      {
        type: "text" as const,
        text: typeof payload === "string" ? payload : JSON.stringify(payload, null, 2),
      },
    ],
  };
}

const server = new McpServer({
  name: "xmind-mcp",
  version: "1.0.0",
});

server.registerTool(
  "xmind_create_and_open",
  {
    title: "Create mind map and open in XMind",
    description:
      "Create a new .xmind file from a topic tree, then launch the local XMind app on Windows to display it. " +
      "Use this when the user asks to draw, create, or visualize a mind map in XMind.",
    inputSchema: {
      title: z.string().min(1).describe("Central topic / root title"),
      sheetTitle: z.string().optional().describe("Worksheet title"),
      children: z
        .array(TopicNodeSchema)
        .optional()
        .describe("Main branches and nested subtopics"),
      fileName: z
        .string()
        .optional()
        .describe("Output file name, e.g. project-plan.xmind. Defaults to a timestamped name."),
      outputDir: z
        .string()
        .optional()
        .describe(
          "Directory to save the file. Defaults to XMIND_OUTPUT_DIR or Documents/XMind.",
        ),
    },
  },
  async ({ title, sheetTitle, children, fileName, outputDir }) => {
    const dir = outputDir ?? getDefaultOutputDir();
    const safeName =
      fileName ??
      `${title.replace(/[<>:"/\\|?*]/g, "_").slice(0, 40)}-${Date.now()}.xmind`;
    const target = safeName.toLowerCase().endsWith(".xmind")
      ? safeName
      : `${safeName}.xmind`;

    const savedPath = await createMindMapFile(
      { title, sheetTitle, children },
      target,
      dir,
    );
    const openResult = await openInXMind(savedPath);

    return textResult({
      ok: true,
      savedPath,
      outputDir: dir,
      openResult,
      xmindHint: getXMindInstallHint(),
      tip: "If XMind was already open with this file, reload it to see updates.",
    });
  },
);

server.registerTool(
  "xmind_create_mindmap",
  {
    title: "Create mind map file",
    description: "Create a .xmind file without opening XMind.",
    inputSchema: {
      ...MindMapInputSchema.shape,
      filePath: z
        .string()
        .describe("Target .xmind path. Can be absolute or relative to outputDir."),
      outputDir: z.string().optional().describe("Base directory when filePath is relative"),
    },
  },
  async ({ title, sheetTitle, children, filePath, outputDir }) => {
    const savedPath = await createMindMapFile(
      { title, sheetTitle, children },
      filePath,
      outputDir ?? getDefaultOutputDir(),
    );

    return textResult({
      ok: true,
      savedPath,
    });
  },
);

server.registerTool(
  "xmind_read_mindmap",
  {
    title: "Read mind map file",
    description: "Read an existing .xmind file and return its topic tree as JSON.",
    inputSchema: {
      filePath: z.string().describe("Path to the .xmind file"),
    },
  },
  async ({ filePath }) => {
    const data = readMindMapFile(filePath);
    return textResult(data);
  },
);

server.registerTool(
  "xmind_open_file",
  {
    title: "Open mind map in XMind",
    description: "Open an existing .xmind file in the local XMind application.",
    inputSchema: {
      filePath: z.string().describe("Path to the .xmind file"),
    },
  },
  async ({ filePath }) => {
    const openResult = await openInXMind(filePath);
    return textResult({
      ok: true,
      ...openResult,
      xmindHint: getXMindInstallHint(),
    });
  },
);

server.registerTool(
  "xmind_list_files",
  {
    title: "List mind map files",
    description: "List .xmind files in a directory.",
    inputSchema: {
      directory: z
        .string()
        .optional()
        .describe("Directory to scan. Defaults to XMIND_OUTPUT_DIR or Documents/XMind."),
      recursive: z
        .boolean()
        .optional()
        .describe("Whether to scan subdirectories recursively"),
    },
  },
  async ({ directory, recursive }) => {
    const dir = directory ?? getDefaultOutputDir();
    const files = listMindMapFiles(dir, recursive ?? false);
    return textResult({
      directory: dir,
      count: files.length,
      files,
    });
  },
);

server.registerTool(
  "xmind_status",
  {
    title: "XMind environment status",
    description:
      "Check default output directory, platform, and whether a local XMind executable was detected.",
    inputSchema: {},
  },
  async () => {
    return textResult({
      platform: process.platform,
      defaultOutputDir: getDefaultOutputDir(),
      xmindExecutable: process.env.XMIND_EXECUTABLE ?? null,
      xmindHint: getXMindInstallHint(),
    });
  },
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((error) => {
  console.error("xmind-mcp server failed:", error);
  process.exit(1);
});
