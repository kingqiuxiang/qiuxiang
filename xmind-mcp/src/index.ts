#!/usr/bin/env node
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';
import {
  addNode,
  addNodesBatch,
  createFromOutline,
  createMap,
  defaultOutputPath,
  deleteNode,
  exportMap,
  importMarkdown,
  readMap,
  updateNode,
  type OutlineNode,
} from './xmind-service.js';
import { getPlatformHints, openInXmind, resolveXmindExecutable } from './xmind-open.js';

const outlineNodeSchema: z.ZodType<OutlineNode> = z.lazy(() =>
  z.object({
    title: z.string().min(1),
    note: z.string().optional(),
    children: z.array(outlineNodeSchema).optional(),
  }),
);

const server = new McpServer({
  name: 'xmind-mcp',
  version: '1.0.0',
});

function textResult(text: string, isError = false) {
  return {
    content: [{ type: 'text' as const, text }],
    isError,
  };
}

function jsonResult(data: unknown) {
  return textResult(JSON.stringify(data, null, 2));
}

server.registerTool(
  'xmind_draw',
  {
    description:
      '根据 Markdown 大纲或 JSON 树结构创建 XMind 思维导图，保存为 .xmind 文件，并可选在本地 XMind 客户端中打开。这是首选的一站式工具。',
    inputSchema: {
      title: z.string().describe('思维导图中心主题标题'),
      outline_markdown: z
        .string()
        .optional()
        .describe(
          'Markdown 大纲，支持 # 标题层级和 - 列表。示例：# 项目\\n## 模块A\\n- 功能1\\n- 功能2',
        ),
      outline_json: z
        .array(outlineNodeSchema)
        .optional()
        .describe('JSON 树形结构，每项含 title、可选 note 和 children'),
      file_path: z
        .string()
        .optional()
        .describe('输出 .xmind 文件的绝对路径。不填则写入 XMIND_OUTPUT_DIR 或 ./xmind-output/'),
      open_in_xmind: z
        .boolean()
        .optional()
        .default(true)
        .describe('创建后是否用本地 XMind 打开（Windows 默认 true）'),
    },
  },
  async ({ title, outline_markdown, outline_json, file_path, open_in_xmind }) => {
    const target = file_path ?? defaultOutputPath(title, process.env.XMIND_OUTPUT_DIR);

    let result;
    if (outline_markdown?.trim()) {
      result = await importMarkdown(target, outline_markdown.trim());
    } else if (outline_json?.length) {
      result = await createFromOutline(target, title, outline_json);
    } else {
      result = await createMap(target, title);
    }

    let openMessage = 'Skipped opening XMind.';
    if (open_in_xmind) {
      openMessage = await openInXmind(target);
    }

    return jsonResult({
      status: 'success',
      file_path: target,
      open: openMessage,
      detail: result,
      hint: getPlatformHints(),
    });
  },
);

server.registerTool(
  'xmind_create',
  {
    description: '创建新的空白 XMind 文件，仅包含中心主题。',
    inputSchema: {
      root_topic: z.string().describe('中心主题文字'),
      file_path: z.string().describe('.xmind 文件绝对路径'),
    },
  },
  async ({ root_topic, file_path }) => {
    const result = await createMap(file_path, root_topic);
    return jsonResult({ file_path, ...result });
  },
);

server.registerTool(
  'xmind_add_nodes',
  {
    description: '向已有 .xmind 文件批量添加子节点。',
    inputSchema: {
      file_path: z.string().describe('.xmind 文件绝对路径'),
      parent_id: z.string().describe('父节点 ID，可通过 xmind_read 获取'),
      topics: z.array(z.string()).min(1).describe('要添加的子主题标题列表'),
    },
  },
  async ({ file_path, parent_id, topics }) => {
    const result = await addNodesBatch(file_path, parent_id, topics);
    return jsonResult(result);
  },
);

server.registerTool(
  'xmind_add_node',
  {
    description: '向已有 .xmind 文件添加单个子节点，可附带备注。',
    inputSchema: {
      file_path: z.string(),
      parent_id: z.string(),
      topic: z.string(),
      note: z.string().optional(),
    },
  },
  async ({ file_path, parent_id, topic, note }) => {
    const added = await addNode(file_path, parent_id, topic);
    const nodeId = String(added.new_node_id ?? added.id ?? '');
    if (note && nodeId) {
      await updateNode(file_path, nodeId, { note });
    }
    return jsonResult(added);
  },
);

server.registerTool(
  'xmind_read',
  {
    description: '读取 .xmind 文件结构，返回 JSON 树。',
    inputSchema: {
      file_path: z.string(),
      depth: z.number().int().positive().optional().describe('读取深度，默认全部'),
    },
  },
  async ({ file_path, depth }) => {
    const result = await readMap(file_path, depth);
    return jsonResult(result);
  },
);

server.registerTool(
  'xmind_update_node',
  {
    description: '更新节点标题、标签或备注。',
    inputSchema: {
      file_path: z.string(),
      target_id: z.string(),
      topic: z.string().optional(),
      label: z.string().optional(),
      note: z.string().optional(),
    },
  },
  async ({ file_path, target_id, topic, label, note }) => {
    const result = await updateNode(file_path, target_id, { topic, label, note });
    return jsonResult(result);
  },
);

server.registerTool(
  'xmind_delete_node',
  {
    description: '删除指定节点及其子树。',
    inputSchema: {
      file_path: z.string(),
      target_id: z.string(),
    },
  },
  async ({ file_path, target_id }) => {
    const result = await deleteNode(file_path, target_id);
    return jsonResult(result);
  },
);

server.registerTool(
  'xmind_export',
  {
    description: '将 .xmind 导出为 Markdown 或纯文本。',
    inputSchema: {
      file_path: z.string(),
      format: z.enum(['md', 'txt']).default('md'),
    },
  },
  async ({ file_path, format }) => {
    const content = await exportMap(file_path, format);
    return textResult(content);
  },
);

server.registerTool(
  'xmind_open',
  {
    description: '在本地 XMind 客户端（Windows）或系统默认应用中打开 .xmind 文件。',
    inputSchema: {
      file_path: z.string().describe('.xmind 文件绝对路径'),
      xmind_exe_path: z.string().optional().describe('XMind.exe 自定义路径，不填则自动探测'),
    },
  },
  async ({ file_path, xmind_exe_path }) => {
    const message = await openInXmind(file_path, { exePath: xmind_exe_path });
    return textResult(message);
  },
);

server.registerTool(
  'xmind_status',
  {
    description: '检查 XMind MCP 运行环境：平台、输出目录、XMind 可执行文件路径等。',
    inputSchema: {},
  },
  async () => {
    const exe = await resolveXmindExecutable().catch(() => undefined);
    return jsonResult({
      platform: process.platform,
      node: process.version,
      output_dir: process.env.XMIND_OUTPUT_DIR ?? './xmind-output',
      xmind_exe: exe ?? null,
      xmind_exe_env: process.env.XMIND_EXE_PATH ?? null,
      hints: getPlatformHints(),
    });
  },
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((error) => {
  console.error('[xmind-mcp] fatal:', error);
  process.exit(1);
});
