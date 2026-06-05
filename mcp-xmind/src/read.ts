import * as fs from 'fs';
import JSZip from 'jszip';

export type TopicData = {
  title: string;
  children?: TopicData[];
};

function topicFromXmind(node: Record<string, unknown>): TopicData {
  const t: TopicData = {
    title: String(node?.title ?? '').trim(),
  };

  const children = node?.children as { attached?: unknown[] } | undefined;
  const attached = children?.attached;
  if (Array.isArray(attached) && attached.length) {
    t.children = attached.map((child) => topicFromXmind(child as Record<string, unknown>));
  }

  return t;
}

export async function readXmindToTree(inputXmindPath: string): Promise<{ title: string; topics: TopicData[] }> {
  const buf = fs.readFileSync(inputXmindPath);
  const zip = await JSZip.loadAsync(buf);

  const contentFile = zip.file('content.json');
  if (!contentFile) throw new Error('Invalid .xmind: missing content.json');

  const contentJson = await contentFile.async('string');
  const doc = JSON.parse(contentJson) as Array<{ rootTopic?: Record<string, unknown> }>;
  if (!Array.isArray(doc) || !doc.length) throw new Error('Invalid content.json: expected array of sheets');

  const sheet = doc[0];
  const root = sheet?.rootTopic;
  if (!root?.title) throw new Error('Invalid content.json: missing rootTopic.title');

  const attached = (root.children as { attached?: unknown[] } | undefined)?.attached;
  const topics = Array.isArray(attached)
    ? attached.map((child) => topicFromXmind(child as Record<string, unknown>))
    : [];

  return { title: String(root.title), topics };
}

export function treeToMarkdown(tree: { title: string; topics: TopicData[] }): string {
  const lines: string[] = [];
  lines.push(`# ${tree.title}`);

  const walk = (node: TopicData, depth: number) => {
    const indent = '  '.repeat(Math.max(0, depth - 1));
    lines.push(`${indent}- ${node.title}`);
    if (node.children?.length) {
      for (const c of node.children) walk(c, depth + 1);
    }
  };

  for (const t of tree.topics) walk(t, 1);
  return lines.join('\n');
}
