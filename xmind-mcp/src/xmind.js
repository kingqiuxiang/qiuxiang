import AdmZip from "adm-zip";
import { randomBytes } from "node:crypto";

/**
 * 生成一个 XMind 兼容的随机 id。
 * XMind 使用类似 26 位的字符串 id，这里用 base36 随机串即可保证唯一。
 */
function genId() {
  return randomBytes(16).toString("hex").slice(0, 26);
}

/**
 * 把内部统一的节点树转换为 XMind topic 结构。
 * 内部节点结构：{ title: string, note?: string, children?: Node[] }
 *
 * @param {{title?: string, note?: string, children?: any[]}} node
 * @returns {object} XMind topic 对象
 */
function toTopic(node) {
  const topic = {
    id: genId(),
    class: "topic",
    title: (node.title ?? "").toString(),
  };

  if (node.note && String(node.note).trim()) {
    topic.notes = { plain: { content: String(node.note) } };
  }

  const children = Array.isArray(node.children) ? node.children : [];
  if (children.length > 0) {
    topic.children = {
      attached: children.map((child) => toTopic(child)),
    };
  }

  return topic;
}

/**
 * 把一个或多个根节点（每个对应一张 sheet）构建为 XMind content.json 数组。
 *
 * @param {Array<{title?: string, note?: string, children?: any[], sheetTitle?: string}>} roots
 * @returns {Array<object>}
 */
function buildContent(roots) {
  return roots.map((root, idx) => {
    const rootTopic = toTopic(root);
    return {
      id: genId(),
      class: "sheet",
      title: root.sheetTitle || rootTopic.title || `Sheet ${idx + 1}`,
      rootTopic,
      // 平衡布局，画出来更美观；XMind 不识别也会回退到默认
      topicOverlapping: "overlap",
    };
  });
}

/**
 * 生成 .xmind 文件的二进制内容（ZIP 包）。
 *
 * 现代 XMind（Zen / 2020+ / 2021+ / 2022+）的 .xmind 实质是一个 ZIP，
 * 至少包含 content.json / metadata.json / manifest.json。
 *
 * @param {Array<object>} roots 根节点数组（每项一张 sheet）
 * @returns {Buffer} .xmind 文件二进制
 */
export function buildXmindBuffer(roots) {
  if (!Array.isArray(roots) || roots.length === 0) {
    throw new Error("至少需要一个根主题（root topic）来生成思维导图");
  }

  const content = buildContent(roots);

  const metadata = {
    creator: { name: "xmind-mcp", version: "1.0.0" },
    dataStructureVersion: "2",
  };

  const manifest = {
    "file-entries": {
      "content.json": {},
      "metadata.json": {},
    },
  };

  const zip = new AdmZip();
  zip.addFile("content.json", Buffer.from(JSON.stringify(content), "utf-8"));
  zip.addFile("metadata.json", Buffer.from(JSON.stringify(metadata), "utf-8"));
  zip.addFile(
    "manifest.json",
    Buffer.from(JSON.stringify(manifest), "utf-8")
  );

  return zip.toBuffer();
}
