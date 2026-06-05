#!/usr/bin/env node

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import * as fs from 'fs';
import * as path from 'path';
import { z } from 'zod';
import { CreateMindMapSchema, createMindMapFile } from './builder.js';
import {
  getDefaultOutputDir,
  launchXmindApp,
  openXmindFile,
  resolveXmindExe,
  shouldAutoOpen,
} from './launcher.js';
import { readXmindToTree, treeToMarkdown } from './read.js';

const server = new McpServer({
  name: 'cursor-xmind',
  version: '1.0.0',
});

function textResult(text: string, isError = false) {
  return {
    content: [{ type: 'text' as const, text }],
    ...(isError ? { isError: true } : {}),
  };
}

function ensureDir(dirPath: string) {
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true });
  }
}

server.tool(
  'create_mind_map',
  '根据主题结构生成 .xmind 思维导图文件，并自动用本地 Xmind 打开（Windows 优先）',
  CreateMindMapSchema.shape,
  async (params) => {
    try {
      const outputDir = params.outputPath
        ? path.dirname(params.outputPath.endsWith('.xmind') ? params.outputPath : params.outputPath)
        : getDefaultOutputDir();

      ensureDir(outputDir);

      const filePath = await createMindMapFile(params, getDefaultOutputDir());
      const autoOpen = params.autoOpen ?? shouldAutoOpen();

      let openInfo = '';
      if (autoOpen) {
        const result = await openXmindFile(filePath);
        openInfo = `\n已在 Xmind 中打开（${result.method}: ${result.detail}）`;
      }

      return textResult(
        `思维导图已生成：${filePath}${openInfo}\n\n` +
          `根主题：${params.title}\n` +
          `一级分支数：${params.topics.length}`,
      );
    } catch (error) {
      return textResult(
        `生成思维导图失败：${error instanceof Error ? error.message : String(error)}`,
        true,
      );
    }
  },
);

server.tool(
  'open_xmind',
  '用本地 Xmind 打开已有的 .xmind 文件',
  {
    filePath: z.string().describe('要打开的 .xmind 文件绝对路径'),
  },
  async ({ filePath }) => {
    try {
      const result = await openXmindFile(filePath);
      return textResult(`已打开：${path.resolve(filePath)}\n方式：${result.method} (${result.detail})`);
    } catch (error) {
      return textResult(
        `打开失败：${error instanceof Error ? error.message : String(error)}`,
        true,
      );
    }
  },
);

server.tool(
  'launch_xmind',
  '启动本地 Xmind 应用程序（不打开具体文件）',
  {},
  async () => {
    try {
      const result = await launchXmindApp();
      return textResult(`Xmind 已启动\n方式：${result.method} (${result.detail})`);
    } catch (error) {
      return textResult(
        `启动 Xmind 失败：${error instanceof Error ? error.message : String(error)}`,
        true,
      );
    }
  },
);

server.tool(
  'read_mind_map',
  '读取 .xmind 文件并导出为 Markdown 大纲，便于 AI 理解现有导图',
  {
    filePath: z.string().describe('.xmind 文件路径'),
  },
  async ({ filePath }) => {
    try {
      const tree = await readXmindToTree(path.resolve(filePath));
      return textResult(treeToMarkdown(tree));
    } catch (error) {
      return textResult(
        `读取失败：${error instanceof Error ? error.message : String(error)}`,
        true,
      );
    }
  },
);

server.tool(
  'list_xmind_files',
  '列出目录下所有 .xmind 文件',
  {
    directory: z.string().optional().describe('要扫描的目录，默认使用 XMIND_OUTPUT_PATH 或 ~/Documents/XMind'),
    recursive: z.boolean().optional().describe('是否递归子目录，默认 false'),
  },
  async ({ directory, recursive }) => {
    try {
      const root = path.resolve(directory ?? getDefaultOutputDir());
      if (!fs.existsSync(root)) {
        return textResult(`目录不存在：${root}`);
      }

      const files: string[] = [];
      const walk = (dir: string) => {
        for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
          const full = path.join(dir, entry.name);
          if (entry.isDirectory() && recursive) walk(full);
          else if (entry.isFile() && entry.name.toLowerCase().endsWith('.xmind')) files.push(full);
        }
      };
      walk(root);

      if (!files.length) return textResult(`目录 ${root} 下未找到 .xmind 文件`);
      return textResult(files.map((f) => `- ${f}`).join('\n'));
    } catch (error) {
      return textResult(
        `列出文件失败：${error instanceof Error ? error.message : String(error)}`,
        true,
      );
    }
  },
);

server.tool(
  'xmind_status',
  '检查 Xmind 安装路径与 MCP 配置状态（排障用）',
  {},
  async () => {
    const outputDir = getDefaultOutputDir();
    const xmindExe = resolveXmindExe();
    const lines = [
      `平台：${process.platform}`,
      `Node：${process.version}`,
      `默认输出目录：${outputDir}`,
      `目录是否存在：${fs.existsSync(outputDir) ? '是' : '否（首次生成时会自动创建）'}`,
      `自动打开 Xmind：${shouldAutoOpen() ? '是' : '否'}`,
      `XMIND_EXE_PATH：${process.env.XMIND_EXE_PATH ?? '（未设置，将自动探测）'}`,
      `XMIND_OUTPUT_PATH：${process.env.XMIND_OUTPUT_PATH ?? '（未设置）'}`,
      `检测到的 Xmind：${xmindExe ?? '未找到（将使用系统默认关联打开 .xmind）'}`,
    ];
    return textResult(lines.join('\n'));
  },
);

const transport = new StdioServerTransport();
server.connect(transport).catch((error: Error) => {
  console.error('MCP server failed to start:', error);
  process.exit(1);
});
