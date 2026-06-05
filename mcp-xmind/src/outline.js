/**
 * 把缩进式 Markdown 大纲解析成思维导图节点树。
 *
 * 支持的写法（缩进可用 2 个空格 / 4 个空格 / Tab，自动识别）：
 *
 *   # 中心主题
 *   - 分支 A
 *     - 子节点 A1
 *     - 子节点 A2
 *   - 分支 B
 *
 * 也兼容纯缩进（不带 - * + 标记）和 # / ## / ### 标题层级混排。
 */

const TAB_WIDTH = 4;

/** 计算一行的缩进宽度（Tab 按 4 个空格折算）。 */
function indentWidth(line) {
  let width = 0;
  for (const ch of line) {
    if (ch === " ") width += 1;
    else if (ch === "\t") width += TAB_WIDTH;
    else break;
  }
  return width;
}

/** 去掉行首的列表标记 / 标题井号，返回纯文本与（如果是标题）标题级别。 */
function stripMarker(text) {
  const trimmed = text.trim();
  // Markdown 标题：# / ## / ###
  const heading = /^(#{1,6})\s+(.*)$/.exec(trimmed);
  if (heading) {
    return { title: heading[2].trim(), headingLevel: heading[1].length };
  }
  // 列表标记：- / * / + / 1. / 1)
  const list = /^(?:[-*+]|\d+[.)])\s+(.*)$/.exec(trimmed);
  if (list) {
    return { title: list[1].trim(), headingLevel: null };
  }
  return { title: trimmed, headingLevel: null };
}

/**
 * 解析大纲文本为节点数组（顶层可能有多个节点）。
 * 节点结构：{ title: string, children: Node[] }
 *
 * @param {string} markdown
 * @returns {{title:string, children:any[]}[]}
 */
export function parseOutline(markdown) {
  const rawLines = String(markdown ?? "")
    .split(/\r?\n/)
    .filter((l) => l.trim().length > 0);

  if (rawLines.length === 0) return [];

  const entries = rawLines.map((line) => {
    const indent = indentWidth(line);
    const { title, headingLevel } = stripMarker(line);
    return { indent, title, headingLevel };
  });

  // 把所有“非标题行”的缩进宽度去重排序后，映射成 0,1,2... 的层级 rank。
  // 这样不依赖具体缩进单位（2/4 空格或 Tab 都可），只看相对深浅。
  const listIndents = Array.from(
    new Set(entries.filter((e) => e.headingLevel === null).map((e) => e.indent))
  ).sort((a, b) => a - b);
  const rankOf = new Map(listIndents.map((w, i) => [w, i]));

  const roots = [];
  const stack = []; // { depth, node }

  // 统一深度模型：
  //  - 标题行：depth = headingLevel - 1（# 为 0，## 为 1 …），作为骨架层级；
  //  - 列表/纯文本行：depth = 最近标题的深度 + 1 + 该行缩进 rank，
  //    从而自然挂在最近的标题之下，并按自身缩进互相嵌套。
  let currentHeadingDepth = -1;

  for (const e of entries) {
    if (!e.title) continue;

    let depth;
    if (e.headingLevel !== null) {
      depth = e.headingLevel - 1;
      currentHeadingDepth = depth;
    } else {
      depth = currentHeadingDepth + 1 + (rankOf.get(e.indent) ?? 0);
    }

    const node = { title: e.title, children: [] };

    while (stack.length > 0 && stack[stack.length - 1].depth >= depth) {
      stack.pop();
    }

    if (stack.length === 0) {
      roots.push(node);
    } else {
      stack[stack.length - 1].node.children.push(node);
    }
    stack.push({ depth, node });
  }

  return roots;
}

/**
 * 把大纲规整成单一“中心主题 + 分支”的树。
 * - 若顶层只有 1 个节点，则它作为中心主题；
 * - 若顶层有多个节点，则用 fallbackTitle 作为中心主题，把这些节点挂为分支。
 *
 * @param {string} markdown
 * @param {string} [fallbackTitle]
 * @returns {{title:string, children:any[]}}
 */
export function outlineToTree(markdown, fallbackTitle = "中心主题") {
  const roots = parseOutline(markdown);
  if (roots.length === 0) {
    return { title: fallbackTitle, children: [] };
  }
  if (roots.length === 1) {
    return roots[0];
  }
  return { title: fallbackTitle, children: roots };
}
