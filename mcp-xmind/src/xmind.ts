import { randomUUID } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import JSZip from "jszip";

export type MindNode = {
  title: string;
  children?: MindNode[];
};

export type CreateXMindInput = {
  title: string;
  outline?: string;
  nodes?: MindNode[];
  outputPath?: string;
};

type XMindTopic = {
  id: string;
  class: "topic";
  title: string;
  structureClass?: string;
  children?: {
    attached: XMindTopic[];
  };
};

type XMindSheet = {
  id: string;
  class: "sheet";
  title: string;
  rootTopic: XMindTopic;
};

const DEFAULT_OUTPUT_FOLDER = "Cursor-XMind";

export async function createXMindFile(input: CreateXMindInput): Promise<string> {
  const title = cleanTitle(input.title) || "Cursor Mind Map";
  const nodes = normalizeNodes(input.nodes?.length ? input.nodes : parseOutline(input.outline ?? "", title));
  const outputPath = resolveOutputPath(input.outputPath, title);

  await mkdir(path.dirname(outputPath), { recursive: true });

  const content = createXMindContent(title, nodes);
  const metadata = createMetadata(content[0]?.id);
  const manifest = createManifest();

  const zip = new JSZip();
  zip.file("content.json", JSON.stringify(content, null, 2));
  zip.file("metadata.json", JSON.stringify(metadata, null, 2));
  zip.file("manifest.json", JSON.stringify(manifest, null, 2));

  const buffer = await zip.generateAsync({
    type: "nodebuffer",
    compression: "DEFLATE",
    compressionOptions: { level: 6 }
  });

  await writeFile(outputPath, buffer);
  return outputPath;
}

export function parseOutline(outline: string, rootTitle: string): MindNode[] {
  const roots: MindNode[] = [];
  const stack: Array<{ level: number; node: MindNode; heading: boolean }> = [];
  let inFence = false;

  for (const rawLine of outline.split(/\r?\n/)) {
    const line = rawLine.replace(/\t/g, "    ");
    const trimmed = line.trim();

    if (!trimmed) {
      continue;
    }

    if (/^```/.test(trimmed)) {
      inFence = !inFence;
      continue;
    }

    if (inFence) {
      continue;
    }

    const heading = /^(#{1,6})\s+(.+)$/.exec(trimmed);
    if (heading) {
      const level = heading[1].length;
      const title = cleanTitle(heading[2]);

      if (!title) {
        continue;
      }

      if (level === 1 && roots.length === 0 && sameTitle(title, rootTitle)) {
        stack.length = 0;
        continue;
      }

      addNodeAtLevel(roots, stack, level, { title }, true);
      continue;
    }

    const bullet = /^(\s*)(?:[-*+]|\d+[.)])\s+(.+)$/.exec(line);
    if (bullet) {
      const indent = bullet[1].length;
      const title = cleanTitle(bullet[2]);

      if (!title) {
        continue;
      }

      const parentHeadingLevel = findNearestHeadingLevel(stack);
      const level = parentHeadingLevel + 1 + Math.floor(indent / 2);
      addNodeAtLevel(roots, stack, level, { title }, false);
      continue;
    }

    const title = cleanTitle(trimmed);
    if (title) {
      addNodeAtLevel(roots, stack, 1, { title }, false);
    }
  }

  return roots;
}

export function resolveOutputPath(outputPath: string | undefined, title: string): string {
  const finalPath = outputPath?.trim()
    ? expandHome(outputPath.trim())
    : path.join(os.homedir(), "Documents", DEFAULT_OUTPUT_FOLDER, `${slugify(title)}.xmind`);

  return path.extname(finalPath).toLowerCase() === ".xmind" ? finalPath : `${finalPath}.xmind`;
}

function createXMindContent(title: string, nodes: MindNode[]): XMindSheet[] {
  const rootTopic: XMindTopic = {
    id: randomId(),
    class: "topic",
    title,
    structureClass: "org.xmind.ui.map.unbalanced"
  };

  const children = nodes.map(toXMindTopic);
  if (children.length > 0) {
    rootTopic.children = { attached: children };
  }

  return [
    {
      id: randomId(),
      class: "sheet",
      title,
      rootTopic
    }
  ];
}

function toXMindTopic(node: MindNode): XMindTopic {
  const topic: XMindTopic = {
    id: randomId(),
    class: "topic",
    title: cleanTitle(node.title) || "Untitled"
  };

  const children = normalizeNodes(node.children ?? []).map(toXMindTopic);
  if (children.length > 0) {
    topic.children = { attached: children };
  }

  return topic;
}

function createMetadata(activeSheetId: string | undefined) {
  return {
    creator: {
      name: "Cursor XMind MCP",
      version: "1.0.0"
    },
    created: new Date().toISOString(),
    modified: new Date().toISOString(),
    activeSheetId
  };
}

function createManifest() {
  return {
    "file-entries": {
      "content.json": {
        "media-type": "application/json"
      },
      "metadata.json": {
        "media-type": "application/json"
      },
      "manifest.json": {
        "media-type": "application/json"
      }
    }
  };
}

function addNodeAtLevel(
  roots: MindNode[],
  stack: Array<{ level: number; node: MindNode; heading: boolean }>,
  level: number,
  node: MindNode,
  heading: boolean
): void {
  while (stack.length > 0 && stack[stack.length - 1].level >= level) {
    stack.pop();
  }

  const parent = stack[stack.length - 1]?.node;
  if (parent) {
    parent.children ??= [];
    parent.children.push(node);
  } else {
    roots.push(node);
  }

  stack.push({ level, node, heading });
}

function findNearestHeadingLevel(stack: Array<{ level: number; heading: boolean }>): number {
  for (let index = stack.length - 1; index >= 0; index -= 1) {
    if (stack[index].heading) {
      return stack[index].level;
    }
  }

  return 0;
}

function normalizeNodes(nodes: MindNode[]): MindNode[] {
  return nodes
    .map((node) => ({
      title: cleanTitle(node.title),
      children: normalizeNodes(node.children ?? [])
    }))
    .filter((node) => node.title.length > 0);
}

function cleanTitle(value: string): string {
  return value
    .replace(/^\s*\[[ xX]\]\s+/, "")
    .replace(/^\s*\d+[.)]\s+/, "")
    .replace(/\s+/g, " ")
    .trim();
}

function sameTitle(a: string, b: string): boolean {
  return cleanTitle(a).toLocaleLowerCase() === cleanTitle(b).toLocaleLowerCase();
}

function slugify(value: string): string {
  const slug = value
    .toLocaleLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);

  return slug || "cursor-mind-map";
}

function expandHome(value: string): string {
  if (value === "~") {
    return os.homedir();
  }

  if (value.startsWith("~/") || value.startsWith("~\\")) {
    return path.join(os.homedir(), value.slice(2));
  }

  return value;
}

function randomId(): string {
  return randomUUID().replace(/-/g, "");
}
