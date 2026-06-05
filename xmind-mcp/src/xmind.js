import JSZip from "jszip";

/**
 * 生成符合 XMind（2020/Zen 及之后版本，content.json 格式）规范的 .xmind 文件。
 *
 * .xmind 实质是一个 zip 包，关键内容：
 *   - content.json   思维导图数据（画布 / 主题树）
 *   - metadata.json  元数据（创建者等）
 *   - manifest.json  文件清单
 *
 * XMind 桌面端可直接打开本函数生成的文件。
 */

/** 生成 XMind 风格的随机 id（小写字母/数字，长度 26）。 */
export function genId() {
  const chars = "abcdefghijklmnopqrstuvwxyz0123456789";
  let s = "";
  for (let i = 0; i < 26; i++) s += chars[Math.floor(Math.random() * chars.length)];
  return s;
}

/**
 * 规范化一个主题节点。
 * 输入节点支持字段：title / note(notes) / marker(markers) / children
 * @param {object} node
 * @returns {object} XMind topic 对象
 */
function buildTopic(node) {
  const topic = {
    id: genId(),
    class: "topic",
    title: String(node?.title ?? "").trim() || "未命名主题",
  };

  // 备注
  const note = node?.note ?? node?.notes;
  if (note && String(note).trim()) {
    topic.notes = { plain: { content: String(note) } };
  }

  // 图标/标记：支持字符串或字符串数组（XMind markerId，如 priority-1、task-done、flag-red 等）
  const markersInput = node?.marker ?? node?.markers;
  if (markersInput) {
    const arr = Array.isArray(markersInput) ? markersInput : [markersInput];
    const markers = arr
      .map((m) => String(m).trim())
      .filter(Boolean)
      .map((markerId) => ({ markerId }));
    if (markers.length) topic.markers = markers;
  }

  // 超链接
  if (node?.href && String(node.href).trim()) {
    topic.href = String(node.href).trim();
  }

  // 子节点
  const children = Array.isArray(node?.children) ? node.children : [];
  if (children.length) {
    topic.children = { attached: children.map((c) => buildTopic(c)) };
  }

  return topic;
}

/**
 * 由一个根节点（含 children）构建单个 sheet。
 * @param {object} root  根主题对象（title + children）
 * @param {object} [opts]
 * @param {string} [opts.sheetTitle]
 * @param {string} [opts.structure] XMind 结构类，默认思维导图（向四周展开）
 */
export function buildSheet(root, opts = {}) {
  const rootTopic = buildTopic(root);
  // 默认使用平衡思维导图结构
  rootTopic.structureClass = opts.structure || "org.xmind.ui.map.unbalanced";
  return {
    id: genId(),
    class: "sheet",
    title: opts.sheetTitle || root?.title || "Sheet 1",
    rootTopic,
    topicPositioning: "fixed",
  };
}

/**
 * 生成 .xmind 文件的二进制内容（Buffer）。
 * @param {object[]|object} sheetsOrRoot  sheet 数组，或单个根节点（会自动包成一个 sheet）
 * @param {object} [opts]
 * @returns {Promise<Buffer>}
 */
export async function generateXmindBuffer(sheetsOrRoot, opts = {}) {
  let sheets;
  if (Array.isArray(sheetsOrRoot) && sheetsOrRoot.length && sheetsOrRoot[0]?.class === "sheet") {
    sheets = sheetsOrRoot;
  } else if (Array.isArray(sheetsOrRoot)) {
    // 一组根节点 → 多个 sheet
    sheets = sheetsOrRoot.map((root, i) => buildSheet(root, { ...opts, sheetTitle: root?.title || `Sheet ${i + 1}` }));
  } else {
    sheets = [buildSheet(sheetsOrRoot, opts)];
  }

  const content = sheets;

  const metadata = {
    creator: { name: "xmind-mcp", version: "1.0.0" },
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
  });
}
