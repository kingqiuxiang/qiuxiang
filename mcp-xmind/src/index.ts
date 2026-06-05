#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod/v4";
import { openXMindFile } from "./open.js";
import { createXMindFile, type MindNode } from "./xmind.js";

const mindNodeSchema: z.ZodType<MindNode> = z.lazy(() =>
  z.object({
    title: z.string().min(1).describe("Topic title."),
    children: z.array(mindNodeSchema).optional().describe("Nested subtopics.")
  })
);

const server = new McpServer({
  name: "xmind-local-mcp",
  version: "1.0.0"
});

server.registerTool(
  "create_xmind_map",
  {
    title: "Create XMind mind map",
    description:
      "Create a local .xmind mind-map file from a Markdown outline or structured nodes, then optionally open it in the local XMind app. Use this after converting the user's requirement into a clear mind-map outline.",
    inputSchema: {
      title: z.string().min(1).describe("Root topic and sheet title."),
      outline: z
        .string()
        .optional()
        .describe("Markdown headings or bullet list. Ignored when nodes is provided."),
      nodes: z.array(mindNodeSchema).optional().describe("Structured first-level topics and children."),
      outputPath: z
        .string()
        .optional()
        .describe("Optional .xmind output path. Defaults to ~/Documents/Cursor-XMind/<title>.xmind."),
      openAfterCreate: z
        .boolean()
        .optional()
        .describe("Open the generated file in XMind after creation. Defaults to true."),
      xmindExecutable: z
        .string()
        .optional()
        .describe("Optional XMind executable path, e.g. C:\\Program Files\\Xmind\\Xmind.exe.")
    },
    outputSchema: {
      filePath: z.string(),
      opened: z.boolean()
    },
    annotations: {
      readOnlyHint: false,
      destructiveHint: false,
      idempotentHint: false,
      openWorldHint: false
    }
  },
  async ({ title, outline, nodes, outputPath, openAfterCreate, xmindExecutable }) => {
    const filePath = await createXMindFile({
      title,
      outline,
      nodes,
      outputPath
    });

    const shouldOpen = openAfterCreate ?? true;
    if (shouldOpen) {
      await openXMindFile({ filePath, xmindExecutable });
    }

    const structuredContent = {
      filePath,
      opened: shouldOpen
    };

    return {
      content: [
        {
          type: "text",
          text: shouldOpen
            ? `Created and opened XMind file: ${filePath}`
            : `Created XMind file: ${filePath}`
        }
      ],
      structuredContent
    };
  }
);

server.registerTool(
  "open_xmind_map",
  {
    title: "Open XMind file",
    description: "Open an existing local .xmind file with XMind or the OS file association.",
    inputSchema: {
      filePath: z.string().min(1).describe("Existing .xmind file path."),
      xmindExecutable: z
        .string()
        .optional()
        .describe("Optional XMind executable path, e.g. C:\\Program Files\\Xmind\\Xmind.exe.")
    },
    outputSchema: {
      opened: z.boolean(),
      filePath: z.string()
    },
    annotations: {
      readOnlyHint: false,
      destructiveHint: false,
      idempotentHint: false,
      openWorldHint: false
    }
  },
  async ({ filePath, xmindExecutable }) => {
    await openXMindFile({ filePath, xmindExecutable });

    const structuredContent = {
      opened: true,
      filePath
    };

    return {
      content: [{ type: "text", text: `Opened XMind file: ${filePath}` }],
      structuredContent
    };
  }
);

server.registerTool(
  "get_xmind_cursor_setup",
  {
    title: "Get Cursor setup guide",
    description: "Return Windows Cursor MCP configuration guidance for this XMind MCP server.",
    inputSchema: {},
    outputSchema: {
      guide: z.string()
    },
    annotations: {
      readOnlyHint: true,
      destructiveHint: false,
      idempotentHint: true,
      openWorldHint: false
    }
  },
  async () => {
    const guide = [
      "1. Install Node.js 18+ and XMind on Windows.",
      "2. Build this package with: npm install && npm run build",
      "3. In Cursor Settings > MCP, add a stdio server:",
      '{ "mcpServers": { "xmind": { "command": "node", "args": ["C:\\\\path\\\\to\\\\mcp-xmind\\\\dist\\\\index.js"], "env": { "XMIND_EXE": "C:\\\\Program Files\\\\Xmind\\\\Xmind.exe" } } } }',
      "4. Restart Cursor MCP tools. Ask Cursor to create a mind map; it should call create_xmind_map."
    ].join("\n");

    return {
      content: [{ type: "text", text: guide }],
      structuredContent: { guide }
    };
  }
);

async function main(): Promise<void> {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("xmind-local-mcp is running on stdio.");
}

main().catch((error: unknown) => {
  console.error("xmind-local-mcp failed:", error);
  process.exit(1);
});
