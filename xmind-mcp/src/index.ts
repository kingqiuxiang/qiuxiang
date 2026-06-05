#!/usr/bin/env node
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';
import { parseOutline } from './outline.js';
import { findXmindExecutable, launchXmind } from './openXmind.js';
import { normalizeTopics, type TopicInput } from './topic.js';
import { createXmindFile } from './xmindFile.js';

const TopicSchema: z.ZodType<TopicInput> = z.lazy(() =>
  z.object({
    title: z.string().min(1).describe('主题标题'),
    notes: z.string().optional().describe('该主题的备注，会写入 XMind notes'),
    labels: z.array(z.string()).optional().describe('该主题的标签'),
    children: z.array(TopicSchema).optional().describe('子主题列表'),
  }),
);

const CreateMindmapInputSchema = z.object({
  title: z.string().optional().describe('中心主题/脑图标题，例如“登录模块需求拆解”'),
  requirement: z
    .string()
    .optional()
    .describe('用户原始需求。服务会写入中心主题备注，方便在 XMind 中追溯上下文。'),
  topics: z
    .array(TopicSchema)
    .optional()
    .describe('结构化主题树。推荐 Cursor 先把自然语言需求整理成这个树，再调用工具。'),
  outline: z
    .string()
    .optional()
    .describe('Markdown 大纲，支持 # 标题、-/*/+ 项目符号和 1. 编号列表。topics 为空时使用。'),
  outputDir: z
    .string()
    .optional()
    .describe('输出目录。Windows 默认是 %USERPROFILE%\\Documents\\Cursor-XMind，也可用 XMIND_OUTPUT_DIR 环境变量设置。'),
  fileName: z.string().optional().describe('输出文件名，可不带 .xmind 后缀。'),
  open: z.boolean().default(true).describe('生成后是否自动启动 XMind 打开文件。默认 true。'),
  xmindPath: z
    .string()
    .optional()
    .describe('XMind.exe 绝对路径。也可以通过 XMIND_PATH 环境变量设置。'),
});

const OpenXmindInputSchema = z.object({
  filePath: z.string().optional().describe('要打开的 .xmind 文件路径。为空时只尝试启动 XMind。'),
  xmindPath: z.string().optional().describe('XMind.exe 绝对路径。优先级高于 XMIND_PATH。'),
});

const CheckSetupInputSchema = z.object({
  xmindPath: z.string().optional().describe('可选的 XMind.exe 绝对路径，用于验证是否存在。'),
});

const server = new McpServer({
  name: 'cursor-xmind-mcp',
  version: '1.0.0',
});

server.registerTool(
  'create_mindmap',
  {
    title: 'Create and open an XMind mind map',
    description:
      '根据用户需求创建 .xmind 脑图文件并可自动拉起本地 XMind。调用前请尽量把自然语言需求整理成 topics 树；也可传 Markdown outline。',
    inputSchema: CreateMindmapInputSchema,
  },
  async (input) => {
    const parsed = CreateMindmapInputSchema.parse(input);
    const title = parsed.title?.trim() || inferTitle(parsed);
    const topics = buildTopics(parsed.topics, parsed.outline, parsed.requirement);

    const file = await createXmindFile({
      title,
      topics,
      requirement: parsed.requirement,
      outputDir: parsed.outputDir,
      fileName: parsed.fileName,
    });

    const launch = parsed.open
      ? await launchXmind({ filePath: file.filePath, xmindPath: parsed.xmindPath })
      : {
          launched: false,
          message: '已按要求只生成文件，未启动 XMind。',
        };

    return {
      content: [
        {
          type: 'text',
          text: [
            'XMind 脑图已生成。',
            `标题：${file.sheetTitle}`,
            `主题数：${file.topicCount}`,
            `文件：${file.filePath}`,
            `打开状态：${launch.message}`,
          ].join('\n'),
        },
        {
          type: 'text',
          text: JSON.stringify(
            {
              filePath: file.filePath,
              topicCount: file.topicCount,
              opened: launch.launched,
              launch,
            },
            null,
            2,
          ),
        },
      ],
    };
  },
);

server.registerTool(
  'open_xmind',
  {
    title: 'Open XMind or an XMind file',
    description: '启动本地 XMind，或打开指定 .xmind 文件。Windows 上会优先使用 XMind.exe，找不到时使用文件关联。',
    inputSchema: OpenXmindInputSchema,
  },
  async (input) => {
    const parsed = OpenXmindInputSchema.parse(input);
    const launch = await launchXmind(parsed);

    return {
      content: [
        {
          type: 'text',
          text: launch.message,
        },
        {
          type: 'text',
          text: JSON.stringify(launch, null, 2),
        },
      ],
    };
  },
);

server.registerTool(
  'check_xmind_setup',
  {
    title: 'Check XMind setup',
    description: '检查 MCP 能否找到本地 XMind，并返回 Windows Cursor MCP 配置建议。',
    inputSchema: CheckSetupInputSchema,
  },
  async (input) => {
    const parsed = CheckSetupInputSchema.parse(input);
    const executable = await findXmindExecutable(parsed.xmindPath);

    return {
      content: [
        {
          type: 'text',
          text: executable
            ? `已找到 XMind：${executable}`
            : [
                '未自动找到 XMind.exe。',
                '如果你安装在自定义位置，请在 Cursor MCP 配置里设置 env.XMIND_PATH，或调用工具时传 xmindPath。',
              ].join('\n'),
        },
        {
          type: 'text',
          text: JSON.stringify(
            {
              found: Boolean(executable),
              executable,
              defaultOutputDir:
                process.platform === 'win32' ? '%USERPROFILE%\\Documents\\Cursor-XMind' : './xmind-output',
              env: {
                XMIND_PATH: 'C:\\Program Files\\Xmind\\Xmind.exe',
                XMIND_OUTPUT_DIR: '%USERPROFILE%\\Documents\\Cursor-XMind',
              },
            },
            null,
            2,
          ),
        },
      ],
    };
  },
);

async function main(): Promise<void> {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

function buildTopics(
  structuredTopics: TopicInput[] | undefined,
  outline: string | undefined,
  requirement: string | undefined,
) {
  const topics = normalizeTopics(structuredTopics);
  if (topics.length > 0) {
    return topics;
  }

  const outlineTopics = normalizeTopics(parseOutline(outline));
  if (outlineTopics.length > 0) {
    return outlineTopics;
  }

  return normalizeTopics([
    {
      title: '需求原文',
      notes: requirement?.trim() || '未提供结构化 topics 或 outline，Cursor 可再次调用本工具并传入主题树以生成更完整的脑图。',
    },
    {
      title: '建议下一步',
      children: [
        { title: '让 Cursor 将需求拆成一级/二级主题' },
        { title: '补充关键流程、角色、边界条件和风险点' },
        { title: '重新调用 create_mindmap 生成最终脑图' },
      ],
    },
  ]);
}

function inferTitle(input: z.infer<typeof CreateMindmapInputSchema>): string {
  const firstTopic = input.topics?.[0]?.title || parseOutline(input.outline)[0]?.title;
  if (firstTopic?.trim()) {
    return firstTopic.trim();
  }

  const requirement = input.requirement?.trim();
  if (requirement) {
    return requirement.slice(0, 40);
  }

  return '需求脑图';
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.stack || error.message : String(error);
  console.error(message);
  process.exit(1);
});
