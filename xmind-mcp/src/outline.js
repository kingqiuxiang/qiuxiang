/**
 * 把一段「大纲文本」解析成统一节点树。
 *
 * 支持两种常见写法，并可混用：
 *   1) Markdown 标题：  #、##、### ...（# 表示中心主题）
 *   2) 缩进列表 / 缩进文本：  使用 -、*、+ 或纯缩进表示层级
 *
 * 例：
 *   # 产品需求
 *   ## 用户系统
 *   - 注册
 *     - 手机号
 *     - 邮箱
 *   - 登录
 *   ## 订单系统
 *
 * 返回：根节点数组（每个顶层节点对应一张 sheet）。
 *
 * @param {string} text
 * @returns {Array<{title: string, note?: string, children: any[]}>}
 */
export function parseOutline(text) {
  const rawLines = String(text || "")
    .replace(/\r\n?/g, "\n")
    .split("\n");

  // 预处理：保留非空行，记录原始缩进（tab 视为 4 空格）
  const lines = [];
  for (const raw of rawLines) {
    if (!raw.trim()) continue;
    const expanded = raw.replace(/\t/g, "    ");
    const leading = expanded.length - expanded.trimStart().length;
    lines.push({ leading, text: expanded.trim() });
  }

  if (lines.length === 0) {
    throw new Error("大纲内容为空，无法生成思维导图");
  }

  // 计算缩进单位：取所有“非标题行”里最小的正缩进，默认 2
  let unit = Infinity;
  for (const ln of lines) {
    if (/^#{1,6}\s/.test(ln.text)) continue;
    if (ln.leading > 0) unit = Math.min(unit, ln.leading);
  }
  if (!isFinite(unit) || unit <= 0) unit = 2;

  // 为每一行计算统一的 level（数值越小越靠近根）
  const nodes = [];
  let lastHeadingLevel = -1;
  for (const ln of lines) {
    const headingMatch = ln.text.match(/^(#{1,6})\s+(.*)$/);
    let level;
    let title;
    if (headingMatch) {
      level = headingMatch[1].length - 1; // # => 0, ## => 1 ...
      title = headingMatch[2].trim();
      lastHeadingLevel = level;
    } else {
      const indentSteps = Math.round(ln.leading / unit);
      const base = lastHeadingLevel + 1; // 标题之下的列表整体下沉一层
      level = base + indentSteps;
      title = ln.text.replace(/^[-*+]\s+/, "").trim();
    }
    nodes.push({ level, node: { title, children: [] } });
  }

  // 用栈把扁平的 (level, node) 构建成树
  const roots = [];
  const stack = []; // 元素：{ level, node }
  for (const item of nodes) {
    while (stack.length && stack[stack.length - 1].level >= item.level) {
      stack.pop();
    }
    if (stack.length === 0) {
      roots.push(item.node);
    } else {
      stack[stack.length - 1].node.children.push(item.node);
    }
    stack.push(item);
  }

  return roots;
}

/**
 * 归一化一棵「用户直接传入的 JSON 树」，确保 title 为字符串、children 为数组。
 * 接受字段：title / topic / text（任一作为标题），note / notes，children / topics。
 *
 * @param {any} node
 * @returns {{title: string, note?: string, children: any[]}}
 */
export function normalizeTree(node) {
  if (node == null) throw new Error("树节点不能为空");
  if (typeof node === "string") return { title: node, children: [] };

  const title = node.title ?? node.topic ?? node.text ?? "";
  const note = node.note ?? node.notes ?? undefined;
  const rawChildren = node.children ?? node.topics ?? [];
  const children = Array.isArray(rawChildren)
    ? rawChildren.map((c) => normalizeTree(c))
    : [];

  const out = { title: String(title), children };
  if (note != null && String(note).trim()) out.note = String(note);
  return out;
}
