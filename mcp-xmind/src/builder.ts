import { createRequire } from 'module';
import { writeFile } from 'fs/promises';
import * as fs from 'fs';
import * as path from 'path';
import { z } from 'zod';

const require = createRequire(import.meta.url);
const xmind = require('xmind-generator') as {
  Topic: (title: string) => TopicBuilder;
  RootTopic: (title: string) => RootTopicBuilder;
  Relationship: (title: string, attrs: { from: string; to: string }) => unknown;
  Workbook: (root: RootTopicBuilder) => { archive: () => Promise<ArrayBuffer> };
  Marker: Record<string, Record<string, unknown>>;
};

type TopicBuilder = {
  ref: (ref: string) => TopicBuilder;
  note: (note: string) => TopicBuilder;
  labels: (labels: string[]) => TopicBuilder;
  markers: (markers: unknown[]) => TopicBuilder;
  children: (children: TopicBuilder[]) => TopicBuilder;
};

type RootTopicBuilder = Omit<TopicBuilder, 'children'> & {
  children: (children: TopicBuilder[]) => RootTopicBuilder;
  relationships: (relationships: unknown[]) => RootTopicBuilder;
};

export const RelationshipSchema = z.object({
  title: z.string().optional().describe('Relationship label'),
  from: z.string().describe('Source topic ref or title'),
  to: z.string().describe('Target topic ref or title'),
});

export const TopicSchema: z.ZodType<TopicInput> = z.lazy(() =>
  z.object({
    title: z.string().describe('Topic title'),
    ref: z.string().optional().describe('Reference id for relationships'),
    note: z.string().optional().describe('Plain text note'),
    labels: z.array(z.string()).optional().describe('Labels'),
    markers: z.array(z.string()).optional().describe('Marker ids, e.g. Arrow.refresh'),
    children: z.array(TopicSchema).optional().describe('Child topics'),
  }),
);

export const CreateMindMapSchema = z.object({
  title: z.string().describe('Root topic / mind map title'),
  topics: z.array(TopicSchema).describe('Top-level child topics'),
  filename: z.string().describe('Output filename without extension'),
  outputPath: z.string().optional().describe('Output directory or full .xmind file path'),
  relationships: z.array(RelationshipSchema).optional().describe('Relationships between topics'),
  autoOpen: z.boolean().optional().describe('Open in Xmind after creation (default: true)'),
});

export type TopicInput = {
  title: string;
  ref?: string;
  note?: string;
  labels?: string[];
  markers?: string[];
  children?: TopicInput[];
};

export type CreateMindMapInput = z.infer<typeof CreateMindMapSchema>;

function resolveMarker(markerStr: string): unknown {
  const [category, name] = markerStr.split('.');
  return xmind.Marker[category]?.[name] ?? markerStr;
}

function buildTopic(topicData: TopicInput): TopicBuilder {
  let topic = xmind.Topic(topicData.title);

  if (topicData.ref) topic = topic.ref(topicData.ref);
  if (topicData.note) topic = topic.note(topicData.note);
  if (topicData.labels?.length) topic = topic.labels(topicData.labels);
  if (topicData.markers?.length) {
    topic = topic.markers(topicData.markers.map((m) => resolveMarker(m)));
  }
  if (topicData.children?.length) {
    topic = topic.children(topicData.children.map(buildTopic));
  }

  return topic;
}

export async function createMindMapFile(input: CreateMindMapInput, defaultOutputDir: string): Promise<string> {
  let root = xmind.RootTopic(input.title);

  if (input.relationships?.length) {
    root = root.relationships(
      input.relationships.map((rel) =>
        xmind.Relationship(rel.title ?? '', { from: rel.from, to: rel.to }),
      ),
    );
  }

  if (input.topics.length) {
    root = root.children(input.topics.map(buildTopic));
  }

  const workbook = xmind.Workbook(root);
  const sanitizedFilename = input.filename.replace(/[\\/:*?"<>|]/g, '-');

  let outputPath: string;
  const baseOutputPath = input.outputPath ?? defaultOutputDir;

  if (baseOutputPath.toLowerCase().endsWith('.xmind')) {
    outputPath = baseOutputPath;
  } else {
    outputPath = path.join(baseOutputPath, `${sanitizedFilename}.xmind`);
  }

  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  const buffer = await workbook.archive();
  await writeFile(outputPath, Buffer.from(buffer));
  return outputPath;
}
