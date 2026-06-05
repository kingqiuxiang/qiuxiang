import fs from 'node:fs';
import JSZip from 'jszip';

export interface MindMapTopic {
  id: string;
  title: string;
  note?: string;
  labels?: string[];
  children: MindMapTopic[];
}

export interface MindMapSheet {
  id: string;
  title: string;
  rootTopic: MindMapTopic;
}

export interface MindMapSummary {
  filePath: string;
  sheets: MindMapSheet[];
}

interface RawTopic {
  id?: string;
  title?: string;
  labels?: string[];
  notes?: { plain?: { content?: string } };
  children?: { attached?: RawTopic[] };
}

interface RawSheet {
  id?: string;
  title?: string;
  rootTopic?: RawTopic;
}

function parseTopic(raw: RawTopic): MindMapTopic {
  const children = (raw.children?.attached ?? []).map(parseTopic);
  return {
    id: raw.id ?? '',
    title: raw.title ?? '(无标题)',
    note: raw.notes?.plain?.content || undefined,
    labels: raw.labels?.length ? raw.labels : undefined,
    children,
  };
}

function countTopics(topic: MindMapTopic): number {
  return 1 + topic.children.reduce((sum, child) => sum + countTopics(child), 0);
}

export async function readMindMap(filePath: string): Promise<MindMapSummary> {
  const buffer = fs.readFileSync(filePath);
  const zip = await JSZip.loadAsync(buffer);

  const contentFile = zip.file('content.json');
  if (!contentFile) {
    throw new Error('不是有效的现代 XMind 文件（缺少 content.json），请使用 XMind 8 Update 3 及以上版本保存的文件。');
  }

  const contentText = await contentFile.async('string');
  const sheets = JSON.parse(contentText) as RawSheet[];

  if (!Array.isArray(sheets) || sheets.length === 0) {
    throw new Error('XMind 文件内容为空。');
  }

  return {
    filePath,
    sheets: sheets.map((sheet) => ({
      id: sheet.id ?? '',
      title: sheet.title ?? 'Sheet',
      rootTopic: parseTopic(sheet.rootTopic ?? {}),
    })),
  };
}

export function mindMapToOutline(summary: MindMapSummary, sheetIndex = 0): string {
  const sheet = summary.sheets[sheetIndex];
  if (!sheet) {
    throw new Error(`工作表索引 ${sheetIndex} 不存在。`);
  }

  const lines: string[] = [`# ${sheet.title}`, '', sheet.rootTopic.title];

  function walk(topic: MindMapTopic, depth: number) {
    for (const child of topic.children) {
      lines.push(`${'  '.repeat(depth)}- ${child.title}`);
      if (child.note) {
        lines.push(`${'  '.repeat(depth + 1)}> ${child.note.replace(/\n/g, ' ')}`);
      }
      walk(child, depth + 1);
    }
  }

  walk(sheet.rootTopic, 1);
  return lines.join('\n');
}

export function mindMapStats(summary: MindMapSummary): { sheetCount: number; topicCount: number } {
  const topicCount = summary.sheets.reduce(
    (sum, sheet) => sum + countTopics(sheet.rootTopic),
    0,
  );
  return { sheetCount: summary.sheets.length, topicCount };
}
