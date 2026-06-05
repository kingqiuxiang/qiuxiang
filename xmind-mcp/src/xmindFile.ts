import JSZip from 'jszip';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { countTopics, normalizeTitle, sanitizeFileName, type NormalizedTopic } from './topic.js';

export type CreateXmindFileOptions = {
  title: string;
  topics: NormalizedTopic[];
  requirement?: string;
  outputDir?: string;
  fileName?: string;
};

export type CreateXmindFileResult = {
  filePath: string;
  topicCount: number;
  sheetTitle: string;
};

type XmindTopic = {
  id: string;
  class: 'topic';
  title: string;
  children?: {
    attached: XmindTopic[];
  };
  notes?: {
    plain: {
      content: string;
    };
  };
  labels?: string[];
};

export async function createXmindFile(options: CreateXmindFileOptions): Promise<CreateXmindFileResult> {
  const title = normalizeTitle(options.title, '需求脑图');
  const outputDir = resolveOutputDir(options.outputDir);
  const filePath = resolveFilePath(outputDir, options.fileName ?? title);
  const now = new Date().toISOString();
  const rootTopic: XmindTopic = {
    id: createId(),
    class: 'topic',
    title,
    children: {
      attached: options.topics.map(toXmindTopic),
    },
  };

  if (options.requirement?.trim()) {
    rootTopic.notes = {
      plain: {
        content: options.requirement.trim(),
      },
    };
  }

  const content = [
    {
      id: createId(),
      class: 'sheet',
      title,
      rootTopic,
      topicPositioning: 'fixed',
      theme: {
        id: 'default',
      },
    },
  ];

  const metadata = {
    creator: {
      name: 'Cursor XMind MCP',
      version: '1.0.0',
    },
    created: now,
    modified: now,
  };

  const manifest = {
    'file-entries': {
      'content.json': {},
      'metadata.json': {},
    },
  };

  await mkdir(outputDir, { recursive: true });

  const zip = new JSZip();
  zip.file('content.json', JSON.stringify(content, null, 2));
  zip.file('metadata.json', JSON.stringify(metadata, null, 2));
  zip.file('manifest.json', JSON.stringify(manifest, null, 2));

  const data = await zip.generateAsync({
    type: 'nodebuffer',
    compression: 'DEFLATE',
    compressionOptions: {
      level: 6,
    },
  });

  await writeFile(filePath, data);

  return {
    filePath,
    topicCount: 1 + countTopics(options.topics),
    sheetTitle: title,
  };
}

function toXmindTopic(topic: NormalizedTopic): XmindTopic {
  const xmindTopic: XmindTopic = {
    id: createId(),
    class: 'topic',
    title: topic.title,
  };

  if (topic.notes) {
    xmindTopic.notes = {
      plain: {
        content: topic.notes,
      },
    };
  }

  if (topic.labels && topic.labels.length > 0) {
    xmindTopic.labels = topic.labels;
  }

  if (topic.children.length > 0) {
    xmindTopic.children = {
      attached: topic.children.map(toXmindTopic),
    };
  }

  return xmindTopic;
}

function resolveOutputDir(outputDir: string | undefined): string {
  if (outputDir?.trim()) {
    return path.resolve(outputDir.trim());
  }

  if (process.env.XMIND_OUTPUT_DIR?.trim()) {
    return path.resolve(process.env.XMIND_OUTPUT_DIR.trim());
  }

  if (process.platform === 'win32') {
    return path.join(os.homedir(), 'Documents', 'Cursor-XMind');
  }

  return path.join(process.cwd(), 'xmind-output');
}

function resolveFilePath(outputDir: string, requestedName: string): string {
  const parsed = path.parse(requestedName);
  const baseName = sanitizeFileName(parsed.name || requestedName);
  return path.join(outputDir, `${baseName}.xmind`);
}

function createId(): string {
  return randomUUID().replace(/-/g, '');
}
