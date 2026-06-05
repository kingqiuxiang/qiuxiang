// 现代 XMind（Zen / 2020+ 及 2021/2022/2023/2024）文件生成器。
// .xmind 实质是一个 ZIP 压缩包，核心包含：
//   - content.json   思维导图内容（画布 / 主题树）
//   - metadata.json  元数据
//   - manifest.json  清单
// 该格式可被现代 XMind 直接打开与编辑。

import JSZip from "jszip";
import { randomUUID } from "node:crypto";

/**
 * 常用 marker（图标）别名映射，方便用自然语言指定图标。
 * 也可直接传入 XMind 原生 markerId（如 "priority-1"、"smiley-smile"）。
 */
const MARKER_ALIASES = {
  // 优先级
  "1": "priority-1",
  "2": "priority-2",
  "3": "priority-3",
  "4": "priority-4",
  "5": "priority-5",
  "6": "priority-6",
  优先级1: "priority-1",
  优先级2: "priority-2",
  优先级3: "priority-3",
  // 任务进度
  start: "task-start",
  开始: "task-start",
  "1/4": "task-quarter",
  "1/2": "task-half",
  "3/4": "task-3quar",
  done: "task-done",
  完成: "task-done",
  // 表情
  smile: "smiley-smile",
  laugh: "smiley-laugh",
  cry: "smiley-cry",
  angry: "smiley-angry",
  // 旗帜 / 星标
  flag: "flag-red",
  红旗: "flag-red",
  绿旗: "flag-green",
  star: "star-yellow",
  星: "star-yellow",
  // 符号
  对: "symbol-wrong", // 注意：保留兼容，见下
  yes: "c_simple_check",
  check: "c_simple_check",
  no: "c_simple_clear",
  question: "symbol-question",
  问号: "symbol-question",
  exclam: "symbol-exclam",
  感叹: "symbol-exclam",
  info: "symbol-info",
  plus: "symbol-plus",
  minus: "symbol-minus",
};

function resolveMarker(name) {
  if (!name) return null;
  const key = String(name).trim();
  return MARKER_ALIASES[key] || MARKER_ALIASES[key.toLowerCase()] || key;
}

/**
 * 把一个输入节点（{title, note, labels, markers, href, children}）转换为 XMind topic 对象。
 */
function buildTopic(node) {
  if (typeof node === "string") node = { title: node };
  const topic = {
    id: randomUUID().replace(/-/g, ""),
    class: "topic",
    title: node.title ?? "（未命名）",
  };

  if (node.note) {
    topic.notes = { plain: { content: String(node.note) } };
  }
  if (Array.isArray(node.labels) && node.labels.length) {
    topic.labels = node.labels.map(String);
  }
  if (Array.isArray(node.markers) && node.markers.length) {
    topic.markers = node.markers
      .map(resolveMarker)
      .filter(Boolean)
      .map((markerId) => ({ markerId }));
  }
  if (node.href) {
    topic.href = String(node.href);
  }

  const children = node.children || node.topics;
  if (Array.isArray(children) && children.length) {
    topic.children = { attached: children.map(buildTopic) };
  }
  return topic;
}

/**
 * 构造一张 sheet（画布）。
 * @param {object} opts
 * @param {string} opts.title 中心主题
 * @param {Array} opts.children 子主题树
 * @param {string} [opts.sheetTitle] 画布名
 * @param {string} [opts.structure] 结构类型（见 STRUCTURES）
 */
export function buildSheet({ title, children = [], sheetTitle, structure } = {}) {
  const rootTopic = buildTopic({ title: title ?? "中心主题", children });
  rootTopic.structureClass = resolveStructure(structure);
  return {
    id: randomUUID().replace(/-/g, ""),
    class: "sheet",
    title: sheetTitle || title || "Sheet 1",
    rootTopic,
    topicPositioning: "fixed",
  };
}

const STRUCTURES = {
  map: "org.xmind.ui.map.unbalanced", // 思维导图（默认，左右分布）
  mindmap: "org.xmind.ui.map.unbalanced",
  思维导图: "org.xmind.ui.map.unbalanced",
  balanced: "org.xmind.ui.map.balanced", // 平衡导图
  logic_right: "org.xmind.ui.logic.right", // 逻辑图（向右）
  逻辑图: "org.xmind.ui.logic.right",
  logic_left: "org.xmind.ui.logic.left",
  tree_right: "org.xmind.ui.tree.right", // 树状图
  tree_left: "org.xmind.ui.tree.left",
  org_down: "org.xmind.ui.org.down", // 组织结构图（向下）
  组织结构: "org.xmind.ui.org.down",
  org_up: "org.xmind.ui.org.up",
  fishbone: "org.xmind.ui.fishbone.rightHeaded", // 鱼骨图
  鱼骨图: "org.xmind.ui.fishbone.rightHeaded",
  timeline: "org.xmind.ui.spreadsheet", // 兜底
};

function resolveStructure(structure) {
  if (!structure) return STRUCTURES.map;
  return STRUCTURES[structure] || STRUCTURES[String(structure).toLowerCase()] || structure;
}

/**
 * 生成 .xmind 文件的二进制 Buffer。
 * @param {object} opts
 * @param {string} opts.title 中心主题
 * @param {Array} opts.children 子主题树
 * @param {Array} [opts.sheets] 多张画布（与 title/children 二选一；提供则优先）
 * @param {string} [opts.structure] 结构类型
 * @param {string} [opts.sheetTitle] 画布名
 * @returns {Promise<Buffer>}
 */
export async function generateXmindBuffer(opts = {}) {
  let sheets;
  if (Array.isArray(opts.sheets) && opts.sheets.length) {
    sheets = opts.sheets.map((s) => buildSheet(s));
  } else {
    sheets = [buildSheet(opts)];
  }

  const content = sheets;
  const metadata = {
    creator: { name: "XMind MCP", version: "1.0.0" },
    dataStructureVersion: "2",
    layoutEngineVersion: "3",
  };
  const manifest = {
    "file-entries": {
      "content.json": {},
      "metadata.json": {},
    },
  };

  const zip = new JSZip();
  zip.file("content.json", JSON.stringify(content));
  zip.file("metadata.json", JSON.stringify(metadata));
  zip.file("manifest.json", JSON.stringify(manifest));

  return zip.generateAsync({
    type: "nodebuffer",
    compression: "DEFLATE",
    compressionOptions: { level: 6 },
    // XMind 期望 mimetype 兼容；保持默认即可。
  });
}

/**
 * 把缩进式 / Markdown 大纲解析为节点树。
 * 支持：
 *   - "#"、"##" ... 标题层级
 *   - "-"、"*"、"+" 列表项（按缩进空格数 / Tab 判定层级）
 *   - 纯缩进文本
 * 第一行（最浅层级、或第一个 # 标题）作为中心主题。
 */
export function parseOutline(markdown) {
  const lines = String(markdown)
    .replace(/\r\n?/g, "\n")
    .split("\n")
    .filter((l) => l.trim().length > 0);

  if (!lines.length) return { title: "中心主题", children: [] };

  const parsed = lines.map((line) => {
    const headingMatch = line.match(/^(#{1,6})\s+(.*)$/);
    if (headingMatch) {
      return { level: headingMatch[1].length - 1, title: headingMatch[2].trim() };
    }
    const indentMatch = line.match(/^(\s*)([-*+]\s+)?(.*)$/);
    const rawIndent = indentMatch[1] || "";
    // 把 Tab 视为 2 个空格，每 2 个空格算一级
    const spaces = rawIndent.replace(/\t/g, "  ").length;
    const level = Math.floor(spaces / 2) + 100; // 加偏移，确保列表层级在标题之后
    return { level, title: indentMatch[3].trim() };
  });

  // 归一化层级为连续整数
  const root = { title: parsed[0].title, children: [] };
  const stack = [{ node: root, level: parsed[0].level }];

  for (let i = 1; i < parsed.length; i++) {
    const item = parsed[i];
    const node = { title: item.title, children: [] };
    while (stack.length > 1 && item.level <= stack[stack.length - 1].level) {
      stack.pop();
    }
    const parent = stack[stack.length - 1].node;
    parent.children.push(node);
    stack.push({ node, level: item.level });
  }

  return root;
}

export { STRUCTURES, MARKER_ALIASES, resolveStructure, resolveMarker };
