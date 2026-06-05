import assert from "node:assert";
import { writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import AdmZip from "adm-zip";

import { buildXmindBuffer } from "../src/xmind.js";
import { parseOutline, normalizeTree } from "../src/outline.js";

let passed = 0;
function check(name, fn) {
  fn();
  passed++;
  console.log(`  ✓ ${name}`);
}

console.log("运行 xmind-mcp 冒烟测试...");

// 1) 大纲解析：标题 + 缩进列表
check("parseOutline 构建正确层级", () => {
  const roots = parseOutline(
    [
      "# 产品需求",
      "## 用户系统",
      "- 注册",
      "  - 手机号",
      "  - 邮箱",
      "- 登录",
      "## 订单系统",
      "- 下单",
    ].join("\n")
  );
  assert.equal(roots.length, 1, "应只有一个中心主题");
  const root = roots[0];
  assert.equal(root.title, "产品需求");
  assert.equal(root.children.length, 2, "应有两个一级分支");
  const user = root.children[0];
  assert.equal(user.title, "用户系统");
  assert.equal(user.children.length, 2, "用户系统应有 注册/登录 两个子项");
  assert.equal(user.children[0].title, "注册");
  assert.equal(
    user.children[0].children.length,
    2,
    "注册应有 手机号/邮箱 两个孙节点"
  );
});

// 2) 纯缩进（无标题）
check("parseOutline 支持纯缩进", () => {
  const roots = parseOutline("中心\n  A\n    A1\n  B");
  assert.equal(roots.length, 1);
  assert.equal(roots[0].title, "中心");
  assert.equal(roots[0].children.length, 2);
  assert.equal(roots[0].children[0].children[0].title, "A1");
});

// 3) normalizeTree 兼容多种字段
check("normalizeTree 归一化字段", () => {
  const t = normalizeTree({
    topic: "根",
    topics: [{ text: "叶", note: "备注" }],
  });
  assert.equal(t.title, "根");
  assert.equal(t.children[0].title, "叶");
  assert.equal(t.children[0].note, "备注");
});

// 4) 生成 .xmind 并校验 zip 内容
check("buildXmindBuffer 产出有效 zip", () => {
  const roots = parseOutline("# 中心\n## 分支1\n## 分支2");
  const buf = buildXmindBuffer(roots);
  const zip = new AdmZip(buf);
  const names = zip.getEntries().map((e) => e.entryName);
  for (const need of ["content.json", "metadata.json", "manifest.json"]) {
    assert.ok(names.includes(need), `缺少 ${need}`);
  }
  const content = JSON.parse(zip.getEntry("content.json").getData().toString());
  assert.ok(Array.isArray(content));
  assert.equal(content[0].class, "sheet");
  assert.equal(content[0].rootTopic.title, "中心");
  assert.equal(content[0].rootTopic.children.attached.length, 2);

  // 落盘一个样例，便于人工用 XMind 打开验证
  const out = path.join(os.tmpdir(), "xmind-mcp-smoke.xmind");
  writeFileSync(out, buf);
  console.log(`    （样例文件：${out}）`);
});

console.log(`\n全部通过：${passed} 项 ✅`);
