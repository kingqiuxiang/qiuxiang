#!/usr/bin/env node

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';
import { launchXmindApp, openFileInXmind } from './launcher.js';
import { mindMapStats, mindMapToOutline, readMindMap } from './reader.js';
import {
  defaultOutputDir,
  generateMindMap,
  parseOutlineToTopics,
  type RelationshipInput,
  type TopicInput,
} from './writer.js';

const RelationshipSchema = z.object({
  title: z.string().describe('关系线的标题'),
  from: z.string().describe('源主题的 ref ID'),
  to: z.string().describe('目标主题的 ref ID'),
});

const TopicSchema: z.ZodType<TopicInput> = z.lazy(() =>
  z.object({
    title: z.string().describe('主题标题'),
    ref: z.string().optional().describe('可选引用 ID，用于建立关系线'),
    note: z.string().optional().describe('主题备注'),
    labels: z.array(z.string()).optional().describe('标签列表'),
    markers: z
      .array(z.string())
      .optional()
      .describe('标记，格式如 "priority.1" 或 "Arrow.refresh"'),
    children: z.array(TopicSchema).optional().describe('子主题'),
  }),
);

const server = new McpServer({
  name: 'cursor-xmind',
  version: '1.0.0',
});

function shouldAutoOpen(): boolean {
  const value = process.env.XMIND_AUTO_OPEN ?? process.env.autoOpenFile ?? 'true';
  return value.toLowerCase() !== 'false';
}

function textResult(text: string, isError = false) {
  return {
    content: [{ type: 'text' as const, text }],
    ...(isError ? { isError: true } : {}),
  };
}

server.tool(
  'xmind_generate',
  '根据结构化主题树生成 .xmind 思维导图文件，并自动在本地 XMind 中打开（Windows 优先）。适用于 Cursor 根据需求描述绘制脑图。',
  {
    title: z.string().describe('思维导图中心主题（根节点）标题'),
    topics: z.array(TopicSchema).describe('一级子主题及其嵌套子节点'),
    filename: z.string().describe('文件名（不含路径和扩展名）'),
    outputPath: z
      .string()
      .optional()
      .describe('可选输出路径：目录或完整 .xmind 文件路径。默认 ~/Documents/XmindFiles'),
    relationships: z.array(RelationshipSchema).optional().describe('主题之间的关系线'),
    autoOpen: z.boolean().optional().describe('是否自动打开 XMind，默认 true'),
  },
  async ({ title, topics, filename, outputPath, relationships, autoOpen }) => {
    try {
      const filePath = await generateMindMap({
        title,
        topics,
        filename,
        outputPath,
        relationships: relationships as RelationshipInput[] | undefined,
      });

      let openMessage: string | undefined;
      if (autoOpen ?? shouldAutoOpen()) {
        openMessage = await openFileInXmind(filePath);
      }

      return textResult(
        [
          `✅ 思维导图已生成: ${filePath}`,
          openMessage,
          '',
          '提示：如需调整样式，可直接在 XMind 中编辑；也可让 Cursor 读取该文件后继续修改。',
        ]
          .filter(Boolean)
          .join('\n'),
      );
    } catch (error) {
      return errorResult(error);
    }
  },
);

server.tool(
  'xmind_generate_from_outline',
  '从 Markdown/缩进大纲文本快速生成思维导图。适合将自然语言需求先整理成大纲再一键出图。',
  {
    title: z.string().describe('中心主题标题'),
    outline: z
      .string()
      .describe('大纲文本，支持 "- 子主题" 或两空格缩进层级，例如:\n- 需求分析\n  - 用户故事\n  - 验收标准'),
    filename: z.string().describe('文件名（不含路径和扩展名）'),
    outputPath: z.string().optional().describe('可选输出目录或完整 .xmind 路径'),
    autoOpen: z.boolean().optional().describe('是否自动打开 XMind，默认 true'),
  },
  async ({ title, outline, filename, outputPath, autoOpen }) => {
    try {
      const topics = parseOutlineToTopics(outline);
      const filePath = await generateMindMap({ title, topics, filename, outputPath });

      let openMessage: string | undefined;
      if (autoOpen ?? shouldAutoOpen()) {
        openMessage = await openFileInXmind(filePath);
      }

      return textResult(
        [`✅ 已从大纲生成思维导图: ${filePath}`, openMessage].filter(Boolean).join('\n'),
      );
    } catch (error) {
      return errorResult(error);
    }
  },
);

server.tool(
  'xmind_open',
  '在本地 XMind 应用中打开已有的 .xmind 文件。',
  {
    filePath: z.string().describe('.xmind 文件的绝对或相对路径'),
  },
  async ({ filePath }) => {
    try {
      const message = await openFileInXmind(filePath);
      return textResult(message);
    } catch (error) {
      return errorResult(error);
    }
  },
);

server.tool(
  'xmind_launch',
  '启动本地 XMind 桌面应用（不打开具体文件）。Windows 下会自动搜索常见安装路径。',
  {},
  async () => {
    try {
      const message = await launchXmindApp();
      return textResult(message);
    } catch (error) {
      return errorResult(error);
    }
  },
);

server.tool(
  'xmind_read',
  '读取本地 .xmind 文件结构，返回大纲文本，便于 Cursor 理解现有脑图并继续编辑。',
  {
    filePath: z.string().describe('.xmind 文件路径'),
    sheetIndex: z.number().int().min(0).optional().describe('工作表索引，默认 0'),
  },
  async ({ filePath, sheetIndex }) => {
    try {
      const summary = await readMindMap(filePath);
      const stats = mindMapStats(summary);
      const outline = mindMapToOutline(summary, sheetIndex ?? 0);

      return textResult(
        [
          `文件: ${summary.filePath}`,
          `工作表数: ${stats.sheetCount}，主题总数: ${stats.topicCount}`,
          '',
          outline,
        ].join('\n'),
      );
    } catch (error) {
      return errorResult(error);
    }
  },
);

server.tool(
  'xmind_get_config',
  '返回当前 MCP 的输出目录、自动打开设置等信息，便于排查 Windows 环境问题。',
  {},
  async () => {
    const { resolveXmindExecutable } = await import('./launcher.js');
    const xmindPath = resolveXmindExecutable();

    return textResult(
      [
        'Cursor XMind MCP 配置信息',
        `平台: ${process.platform}`,
        `默认输出目录: ${defaultOutputDir()}`,
        `自动打开 XMind: ${shouldAutoOpen()}`,
        `XMIND_PATH: ${process.env.XMIND_PATH ?? '(未设置)'}`,
        `XMIND_OUTPUT_PATH: ${process.env.XMIND_OUTPUT_PATH ?? process.env.outputPath ?? '(未设置，使用默认)'}`,
        `检测到的 XMind: ${xmindPath ?? '未找到，将使用系统默认关联程序'}`,
      ].join('\n'),
    );
  },
);

function errorResult(error: unknown) {
  const message = error instanceof Error ? error.message : String(error);
  return textResult(`❌ 错误: ${message}`, true);
}

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((error) => {
  console.error('XMind MCP 启动失败:', error);
  process.exit(1);
});
