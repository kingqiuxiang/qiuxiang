import { normalizeTopics } from '../src/topic.js';
import { createXmindFile } from '../src/xmindFile.js';

const result = await createXmindFile({
  title: 'Cursor XMind MCP Smoke Test',
  outputDir: 'tmp',
  topics: normalizeTopics([
    {
      title: '目标',
      children: [{ title: '从 Cursor 生成 XMind 文件' }, { title: '按需自动打开本地 XMind' }],
    },
    {
      title: '验证项',
      children: [{ title: 'content.json 写入成功' }, { title: '.xmind 压缩包生成成功' }],
    },
  ]),
});

console.log(JSON.stringify(result, null, 2));
