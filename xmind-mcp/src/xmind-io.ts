import AdmZip from "adm-zip";
import { randomUUID } from "node:crypto";
import { existsSync, mkdirSync, readdirSync, statSync, writeFileSync } from "node:fs";
import path from "node:path";
import type { MindMapInput, MindMapTree, TopicNode } from "./types.js";

interface RawTopic {
  id: string;
  class: "topic";
  title: string;
  notes?: { plain: { content: string } };
  labels?: string[];
  children?: { attached: RawTopic[] };
}

interface RawSheet {
  id: string;
  class: "sheet";
  title: string;
  rootTopic: RawTopic;
}

function buildRawTopic(node: TopicNode): RawTopic {
  const topic: RawTopic = {
    id: randomUUID(),
    class: "topic",
    title: node.title,
  };

  if (node.note) {
    topic.notes = { plain: { content: `${node.note}\n` } };
  }
  if (node.labels?.length) {
    topic.labels = node.labels;
  }
  if (node.children?.length) {
    topic.children = { attached: node.children.map(buildRawTopic) };
  }

  return topic;
}

function buildWorkbookContent(input: MindMapInput): RawSheet[] {
  return [
    {
      id: randomUUID(),
      class: "sheet",
      title: input.sheetTitle ?? "",
      rootTopic: buildRawTopic({
        title: input.title,
        children: input.children,
      }),
    },
  ];
}

function writeMindMapBuffer(input: MindMapInput): Buffer {
  const zip = new AdmZip();

  zip.addFile(
    "content.json",
    Buffer.from(JSON.stringify(buildWorkbookContent(input)), "utf8"),
  );
  zip.addFile(
    "metadata.json",
    Buffer.from(
      JSON.stringify({
        creator: { name: "xmind-mcp", version: "1.0.0" },
        dataStructureVersion: "2",
      }),
      "utf8",
    ),
  );
  zip.addFile(
    "manifest.json",
    Buffer.from(
      JSON.stringify({
        "file-entries": {
          "content.json": {},
          "metadata.json": {},
        },
      }),
      "utf8",
    ),
  );

  return zip.toBuffer();
}

export function resolveOutputPath(filePath: string, outputDir?: string): string {
  const resolved = path.isAbsolute(filePath)
    ? filePath
    : path.resolve(outputDir ?? process.cwd(), filePath);

  if (!resolved.toLowerCase().endsWith(".xmind")) {
    throw new Error("Output path must end with .xmind");
  }

  const dir = path.dirname(resolved);
  if (!existsSync(dir)) {
    mkdirSync(dir, { recursive: true });
  }

  return resolved;
}

export async function createMindMapFile(
  input: MindMapInput,
  filePath: string,
  outputDir?: string,
): Promise<string> {
  const resolved = resolveOutputPath(filePath, outputDir);
  const buffer = writeMindMapBuffer(input);
  writeFileSync(resolved, buffer);
  return resolved;
}

interface ParsedTopic {
  title?: string;
  notes?: { plain?: { content?: string } };
  labels?: string[];
  children?: { attached?: ParsedTopic[] };
}

interface ParsedSheet {
  title?: string;
  rootTopic?: ParsedTopic;
}

function parseTopic(raw: ParsedTopic): TopicNode {
  const node: TopicNode = {
    title: raw.title ?? "(untitled)",
  };

  const note = raw.notes?.plain?.content?.trim();
  if (note) node.note = note;
  if (raw.labels?.length) node.labels = raw.labels;

  const children = raw.children?.attached ?? [];
  if (children.length) {
    node.children = children.map(parseTopic);
  }

  return node;
}

function readContentJson(zip: AdmZip): unknown {
  const entry =
    zip.getEntry("content.json") ?? zip.getEntry("content/content.json");

  if (!entry) {
    throw new Error("Unsupported XMind format: content.json not found");
  }

  return JSON.parse(zip.readAsText(entry, "utf8"));
}

export function readMindMapFile(filePath: string): MindMapTree {
  const resolved = path.resolve(filePath);
  if (!existsSync(resolved)) {
    throw new Error(`File not found: ${resolved}`);
  }

  const zip = new AdmZip(resolved);
  const content = readContentJson(zip) as ParsedSheet[] | { sheets?: ParsedSheet[] };
  const sheets = Array.isArray(content) ? content : (content.sheets ?? []);

  return {
    filePath: resolved,
    sheets: sheets.map((sheet) => ({
      title: sheet.title ?? "Sheet",
      root: parseTopic(sheet.rootTopic ?? {}),
    })),
  };
}

export function listMindMapFiles(directory: string, recursive = false): string[] {
  const resolved = path.resolve(directory);
  if (!existsSync(resolved)) {
    throw new Error(`Directory not found: ${resolved}`);
  }

  const results: string[] = [];

  const walk = (dir: string) => {
    for (const entry of readdirSync(dir)) {
      const fullPath = path.join(dir, entry);
      const stats = statSync(fullPath);

      if (stats.isDirectory()) {
        if (recursive) walk(fullPath);
        continue;
      }

      if (entry.toLowerCase().endsWith(".xmind")) {
        results.push(fullPath);
      }
    }
  };

  walk(resolved);
  return results.sort();
}

export function getDefaultOutputDir(): string {
  if (process.env.XMIND_OUTPUT_DIR) {
    return path.resolve(process.env.XMIND_OUTPUT_DIR);
  }

  const home = process.env.USERPROFILE ?? process.env.HOME ?? process.cwd();

  if (process.platform === "win32") {
    return process.env.USERPROFILE
      ? path.join(process.env.USERPROFILE, "Documents", "XMind")
      : path.join(home, "Documents", "XMind");
  }

  return path.join(home, "Documents", "XMind");
}
