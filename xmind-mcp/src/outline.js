/**
 * 将「Markdown / 缩进大纲」文本解析为思维导图根节点树。
 *
 * 统一深度规则（同时兼容两种写法，可混用）：
 *   1) Markdown 标题：以 #..###### 开头，深度 = (# 个数 - 1)。
 *      例：`#` 为根(深度0)，`##` 深度1，`###` 深度2 ...
 *   2) 列表/普通行：深度 = 最近一个标题的深度 + 1 + floor(前导空格数 / 2)。
 *      列表前缀（- * + 或 1.）会被去除；Tab 记作 2 个空格。
 *
 * 这样既支持：
 *   # 中心主题
 *   ## 分支一
 *   - 要点 a
 *     - 子点 a1
 * 也支持纯缩进：
 *   中心主题
 *     分支一
 *       要点 a
 *
 * @param {string} text
 * @returns {{title:string, children:object[]}} 根节点
 */
export function parseOutline(text) {
  const rawLines = String(text ?? "").replace(/\r\n?/g, "\n").split("\n");

  /** @type {{depth:number, title:string}[]} */
  const items = [];
  let lastHeadingDepth = -1;

  for (const raw of rawLines) {
    if (!raw.trim()) continue;

    // 展开 Tab 为 2 空格以统一缩进计算
    const line = raw.replace(/\t/g, "  ");

    const headingMatch = line.match(/^\s*(#{1,6})\s+(.*)$/);
    if (headingMatch) {
      const depth = headingMatch[1].length - 1;
      const title = headingMatch[2].trim();
      if (title) {
        items.push({ depth, title });
        lastHeadingDepth = depth;
      }
      continue;
    }

    // 计算前导空格
    const leading = line.match(/^(\s*)/)[1].length;
    // 去掉列表前缀
    const stripped = line.trim().replace(/^([-*+]|\d+[.)])\s+/, "");
    if (!stripped) continue;

    const depth = lastHeadingDepth + 1 + Math.floor(leading / 2);
    items.push({ depth, title: stripped });
  }

  return itemsToTree(items);
}

/**
 * 由 {depth,title} 列表构建树（容错栈算法）。
 * @param {{depth:number,title:string}[]} items
 * @returns {{title:string, children:object[]}}
 */
export function itemsToTree(items) {
  if (!items.length) return { title: "未命名导图", children: [] };

  // 归一化深度：以最小深度为基准
  const minDepth = Math.min(...items.map((i) => i.depth));
  const norm = items.map((i) => ({ title: i.title, depth: i.depth - minDepth, children: [] }));

  // 若存在多个深度为 0 的节点，则以第一个为根，其余降为根的子节点
  const root = norm[0];
  root.depth = 0;

  /** @type {{depth:number, node:object}[]} */
  const stack = [{ depth: 0, node: root }];

  for (let i = 1; i < norm.length; i++) {
    const cur = norm[i];
    // 保证至少为深度 1（根之下）
    let d = Math.max(1, cur.depth);

    // 找到父节点：栈顶 depth < d
    while (stack.length && stack[stack.length - 1].depth >= d) stack.pop();
    if (!stack.length) {
      // 兜底：挂到根下
      stack.push({ depth: 0, node: root });
      d = 1;
    }
    const parent = stack[stack.length - 1].node;
    const child = { title: cur.title, children: [] };
    parent.children.push(child);
    stack.push({ depth: d, node: child });
  }

  return stripEmptyChildren(root);
}

function stripEmptyChildren(node) {
  if (node.children && node.children.length === 0) delete node.children;
  if (node.children) node.children.forEach(stripEmptyChildren);
  return node;
}

/**
 * 归一化结构化 topics 输入为根节点。
 * 接受 {title, topics:[...]} 或直接的根节点 {title, children:[...]}。
 */
export function normalizeStructured({ title, topics, children }) {
  const kids = children ?? topics ?? [];
  return { title: title || "中心主题", children: kids };
}
