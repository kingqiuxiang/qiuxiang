import { createRequire } from 'node:module';
import fs from 'node:fs';
import fsPromises from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const require = createRequire(import.meta.url);

export interface TopicInput {
  title: string;
  ref?: string;
  note?: string;
  labels?: string[];
  markers?: string[];
  children?: TopicInput[];
}

export interface RelationshipInput {
  title: string;
  from: string;
  to: string;
}

export interface GenerateMindMapInput {
  title: string;
  topics: TopicInput[];
  filename: string;
  outputPath?: string;
  relationships?: RelationshipInput[];
}

export function defaultOutputDir(): string {
  const configured = process.env.XMIND_OUTPUT_PATH ?? process.env.outputPath;
  if (configured) {
    return configured.replace(/^~(?=$|[\\/])/, os.homedir());
  }

  if (process.platform === 'win32') {
    return path.join(os.homedir(), 'Documents', 'XmindFiles');
  }

  return path.join(os.homedir(), 'Documents', 'XmindFiles');
}

export function sanitizeFilename(filename: string): string {
  return filename.replace(/[\\/:*?"<>|]/g, '-').trim() || 'mindmap';
}

export function resolveOutputPath(filename: string, outputPath?: string): string {
  const base = outputPath ?? defaultOutputDir();
  const expanded = base.replace(/^~(?=$|[\\/])/, os.homedir());

  if (expanded.toLowerCase().endsWith('.xmind')) {
    return path.resolve(expanded);
  }

  const safeName = sanitizeFilename(filename);
  return path.resolve(expanded, `${safeName}.xmind`);
}

function getXmindGenerator() {
  return require('xmind-generator') as {
    Topic: (title: string) => XmindTopicBuilder;
    RootTopic: (title: string) => XmindTopicBuilder;
    Workbook: (root: XmindTopicBuilder) => { archive: () => Promise<ArrayBuffer> };
    Relationship: (title: string, refs: { from: string; to: string }) => unknown;
    Marker: Record<string, Record<string, unknown>>;
  };
}

interface XmindTopicBuilder {
  ref: (id: string) => XmindTopicBuilder;
  note: (text: string) => XmindTopicBuilder;
  labels: (labels: string[]) => XmindTopicBuilder;
  markers: (markers: unknown[]) => XmindTopicBuilder;
  children: (children: XmindTopicBuilder[]) => XmindTopicBuilder;
  relationships: (rels: unknown[]) => XmindTopicBuilder;
}

function buildTopic(xmind: ReturnType<typeof getXmindGenerator>, data: TopicInput): XmindTopicBuilder {
  const topic = xmind.Topic(data.title);

  if (data.ref) topic.ref(data.ref);
  if (data.note) topic.note(data.note);
  if (data.labels?.length) topic.labels(data.labels);
  if (data.markers?.length) {
    const markers = data.markers.map((markerStr) => {
      const [category, name] = markerStr.split('.');
      return xmind.Marker[category]?.[name] ?? markerStr;
    });
    topic.markers(markers);
  }
  if (data.children?.length) {
    topic.children(data.children.map((child) => buildTopic(xmind, child)));
  }

  return topic;
}

export async function generateMindMap(input: GenerateMindMapInput): Promise<string> {
  const xmind = getXmindGenerator();
  const outputPath = resolveOutputPath(input.filename, input.outputPath);
  const outputDir = path.dirname(outputPath);

  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  const rootTopic = xmind.RootTopic(input.title);

  if (input.relationships?.length) {
    rootTopic.relationships(
      input.relationships.map((rel) => xmind.Relationship(rel.title, { from: rel.from, to: rel.to })),
    );
  }

  if (input.topics.length > 0) {
    rootTopic.children(input.topics.map((topic) => buildTopic(xmind, topic)));
  }

  const workbook = xmind.Workbook(rootTopic);
  const buffer = await workbook.archive();
  await fsPromises.writeFile(outputPath, Buffer.from(buffer));

  return outputPath;
}

export function parseOutlineToTopics(outline: string): TopicInput[] {
  const lines = outline.split(/\r?\n/).filter((line) => line.trim().length > 0);
  const roots: TopicInput[] = [];
  const stack: { depth: number; topic: TopicInput }[] = [];

  for (const line of lines) {
    const markdownMatch = line.match(/^(\s*)[-*+]\s+(.+)$/);
    const plainMatch = line.match(/^(\s+)(.+)$/);
    const match = markdownMatch ?? plainMatch;
    if (!match) continue;

    const title = match[2].trim();
    if (!title || title.startsWith('#')) continue;

    const depth = Math.floor(match[1].replace(/\t/g, '  ').length / 2);
    const node: TopicInput = { title, children: [] };

    while (stack.length > 0 && stack[stack.length - 1].depth >= depth) {
      stack.pop();
    }

    if (stack.length === 0) {
      roots.push(node);
    } else {
      const parent = stack[stack.length - 1].topic;
      parent.children = parent.children ?? [];
      parent.children.push(node);
    }

    stack.push({ depth, topic: node });
  }

  return roots;
}
