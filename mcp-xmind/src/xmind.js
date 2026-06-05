import { randomUUID } from "node:crypto";
import { promises as fs } from "node:fs";
import path from "node:path";
import JSZip from "jszip";

/**
 * .xmind 文件本质是一个 zip 容器，现代 XMind（XMind 2020 / XMind 8 之后的
 * Zen 格式，以及当前版本的 XMind）读取根目录下的 content.json。
 * 这里生成 content.json + metadata.json + manifest.json，兼容当前 XMind 桌面端。
 */

const STRUCTURE_MAP = "org.xmind.ui.map.unbalanced"; // 思维导图（左右分布）
const STRUCTURE_LOGIC_RIGHT = "org.xmind.ui.logic.right"; // 逻辑图（向右）
const STRUCTURE_ORG_DOWN = "org.xmind.ui.org-chart.down"; // 组织结构图（向下）
const STRUCTURE_TREE_RIGHT = "org.xmind.ui.tree.right"; // 树形图（向右）

const STRUCTURES = {
  map: STRUCTURE_MAP,
  mindmap: STRUCTURE_MAP,
  logic: STRUCTURE_LOGIC_RIGHT,
  logic_right: STRUCTURE_LOGIC_RIGHT,
  org: STRUCTURE_ORG_DOWN,
  org_chart: STRUCTURE_ORG_DOWN,
  tree: STRUCTURE_TREE_RIGHT,
};

/** 把任意输入树节点规范化为 { title, note?, children:[] }。 */
function normalizeNode(node) {
  if (node == null) return null;
  if (typeof node === "string") {
    return { title: node, children: [] };
  }
  const title =
    node.title ?? node.text ?? node.name ?? node.label ?? "(未命名)";
  const note = node.note ?? node.notes ?? node.comment ?? undefined;

  let rawChildren = node.children ?? node.subtopics ?? node.nodes ?? [];
  // 兼容 XMind 原生结构 children.attached
  if (rawChildren && !Array.isArray(rawChildren) && rawChildren.attached) {
    rawChildren = rawChildren.attached;
  }
  const children = Array.isArray(rawChildren)
    ? rawChildren.map(normalizeNode).filter(Boolean)
    : [];

  return { title: String(title), note, children };
}

/** 把规范化节点转成 XMind topic 结构。 */
function toTopic(node) {
  const topic = {
    id: randomUUID().replace(/-/g, ""),
    class: "topic",
    title: node.title,
  };
  if (node.note) {
    topic.notes = { plain: { content: String(node.note) } };
  }
  if (node.children && node.children.length > 0) {
    topic.children = { attached: node.children.map(toTopic) };
  }
  return topic;
}

/**
 * 由节点树构建 XMind content.json 的 sheet 对象。
 * @param {object} tree 规范化或原始的根节点树
 * @param {object} [opts]
 * @param {string} [opts.sheetTitle]
 * @param {string} [opts.structure] map | logic | org | tree
 */
export function buildSheet(tree, opts = {}) {
  const root = normalizeNode(tree) ?? { title: "中心主题", children: [] };
  const rootTopic = toTopic(root);
  rootTopic.structureClass = STRUCTURES[opts.structure] ?? STRUCTURE_MAP;

  return {
    id: randomUUID().replace(/-/g, ""),
    class: "sheet",
    title: opts.sheetTitle ?? root.title ?? "Sheet 1",
    rootTopic,
    topicPositioning: "fixed",
  };
}

/**
 * 生成 .xmind 文件的二进制内容（Buffer）。
 * @param {object[]|object} sheets 单个 sheet 或 sheet 数组
 * @returns {Promise<Buffer>}
 */
export async function buildXmindBuffer(sheets) {
  const sheetArr = Array.isArray(sheets) ? sheets : [sheets];
  const zip = new JSZip();

  zip.file("content.json", JSON.stringify(sheetArr));
  zip.file(
    "metadata.json",
    JSON.stringify({
      creator: { name: "mcp-xmind", version: "1.0.0" },
    })
  );
  zip.file(
    "manifest.json",
    JSON.stringify({
      "file-entries": {
        "content.json": {},
        "metadata.json": {},
      },
    })
  );

  return zip.generateAsync({
    type: "nodebuffer",
    compression: "DEFLATE",
    compressionOptions: { level: 6 },
  });
}

/**
 * 构建并写出 .xmind 文件。
 * @param {object} params
 * @param {object|object[]} params.tree 节点树（单图）或多 sheet 数组
 * @param {string} params.filePath 输出绝对路径（.xmind）
 * @param {string} [params.sheetTitle]
 * @param {string} [params.structure]
 * @returns {Promise<{filePath:string, bytes:number}>}
 */
export async function writeXmindFile({ tree, filePath, sheetTitle, structure }) {
  let sheets;
  if (Array.isArray(tree)) {
    // 多 sheet：数组每项是一个独立的图
    sheets = tree.map((t, i) =>
      buildSheet(t, { sheetTitle: t.sheetTitle ?? `Sheet ${i + 1}`, structure })
    );
  } else {
    sheets = [buildSheet(tree, { sheetTitle, structure })];
  }

  const buffer = await buildXmindBuffer(sheets);
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await fs.writeFile(filePath, buffer);
  return { filePath, bytes: buffer.length };
}

export const SUPPORTED_STRUCTURES = Object.keys(STRUCTURES);
