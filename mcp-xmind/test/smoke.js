// 冒烟测试：
// 1) 校验 .xmind 生成的 ZIP 内容结构正确（content.json / metadata.json / manifest.json）
// 2) 校验大纲解析层级正确
// 3) 启动 MCP server，走 stdio JSON-RPC 调用 tools/list 与 create_mindmap（不自动打开）

import assert from "node:assert";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";
import os from "node:os";
import { readFile, rm } from "node:fs/promises";
import JSZip from "jszip";

import { generateXmindBuffer, parseOutline } from "../src/xmind.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SERVER = path.resolve(__dirname, "../src/index.js");

let failures = 0;
function ok(name) {
  console.log(`  \u2713 ${name}`);
}
function fail(name, err) {
  failures++;
  console.error(`  \u2717 ${name}\n    ${err?.stack || err}`);
}

async function testGenerate() {
  const buf = await generateXmindBuffer({
    title: "需求分析",
    structure: "map",
    children: [
      {
        title: "功能模块",
        markers: ["优先级1"],
        children: [{ title: "登录" }, { title: "下单", note: "包含库存校验" }],
      },
      { title: "非功能", labels: ["性能", "安全"] },
    ],
  });
  assert.ok(Buffer.isBuffer(buf) && buf.length > 0, "buffer 非空");

  const zip = await JSZip.loadAsync(buf);
  for (const f of ["content.json", "metadata.json", "manifest.json"]) {
    assert.ok(zip.file(f), `包含 ${f}`);
  }
  const content = JSON.parse(await zip.file("content.json").async("string"));
  assert.ok(Array.isArray(content) && content.length === 1, "content 是单 sheet 数组");
  const root = content[0].rootTopic;
  assert.strictEqual(root.title, "需求分析", "中心主题正确");
  assert.strictEqual(root.structureClass, "org.xmind.ui.map.unbalanced", "结构类型正确");
  assert.strictEqual(root.children.attached.length, 2, "两个一级子主题");
  const mod = root.children.attached[0];
  assert.strictEqual(mod.markers[0].markerId, "priority-1", "marker 别名映射正确");
  assert.strictEqual(mod.children.attached[1].notes.plain.content, "包含库存校验", "笔记正确");
  assert.deepStrictEqual(root.children.attached[1].labels, ["性能", "安全"], "标签正确");
  ok("generateXmindBuffer 结构正确");
}

function testOutline() {
  const tree = parseOutline(`# 项目计划
## 需求阶段
- 调研
- 评审
## 开发阶段
- 后端
  - 接口
- 前端`);
  assert.strictEqual(tree.title, "项目计划", "中心主题=首行");
  assert.strictEqual(tree.children.length, 2, "两个阶段");
  assert.strictEqual(tree.children[0].children.length, 2, "需求阶段两子项");
  const dev = tree.children[1];
  assert.strictEqual(dev.children[0].title, "后端", "开发-后端");
  assert.strictEqual(dev.children[0].children[0].title, "接口", "后端-接口（缩进嵌套）");
  ok("parseOutline 层级解析正确");
}

function rpc(child, obj) {
  child.stdin.write(JSON.stringify(obj) + "\n");
}

function testServer() {
  return new Promise((resolve) => {
    const outFile = path.join(os.tmpdir(), `xmind-smoke-${Date.now()}.xmind`);
    const child = spawn(process.execPath, [SERVER], { stdio: ["pipe", "pipe", "pipe"] });
    let buffer = "";
    const seen = {};
    const timeout = setTimeout(async () => {
      try {
        const data = await readFile(outFile);
        const zip = await JSZip.loadAsync(data);
        assert.ok(zip.file("content.json"), "server 产出文件含 content.json");
        ok("MCP server 生成文件成功");
      } catch (e) {
        fail("MCP server 生成文件", e);
      }
      child.kill();
      await rm(outFile, { force: true });
      resolve();
    }, 6000);

    child.stdout.on("data", (d) => {
      buffer += d.toString();
      let idx;
      while ((idx = buffer.indexOf("\n")) >= 0) {
        const line = buffer.slice(0, idx).trim();
        buffer = buffer.slice(idx + 1);
        if (!line) continue;
        let msg;
        try {
          msg = JSON.parse(line);
        } catch {
          continue;
        }
        if (msg.id === 1 && msg.result) {
          seen.init = true;
          rpc(child, { jsonrpc: "2.0", method: "notifications/initialized" });
          rpc(child, { jsonrpc: "2.0", id: 2, method: "tools/list", params: {} });
        }
        if (msg.id === 2 && msg.result) {
          const names = (msg.result.tools || []).map((t) => t.name);
          try {
            assert.ok(names.includes("create_mindmap"), "工具列表含 create_mindmap");
            assert.ok(names.includes("create_mindmap_from_outline"), "含 outline 工具");
            assert.ok(names.includes("open_xmind"), "含 open_xmind");
            ok(`tools/list 返回 ${names.length} 个工具: ${names.join(", ")}`);
          } catch (e) {
            fail("tools/list", e);
          }
          rpc(child, {
            jsonrpc: "2.0",
            id: 3,
            method: "tools/call",
            params: {
              name: "create_mindmap",
              arguments: {
                title: "冒烟测试",
                open: false,
                outputPath: outFile,
                children: [{ title: "A" }, { title: "B", children: [{ title: "B1" }] }],
              },
            },
          });
        }
        if (msg.id === 3 && msg.result) {
          ok("tools/call create_mindmap 返回成功");
        }
      }
    });

    child.on("error", (e) => {
      clearTimeout(timeout);
      fail("spawn server", e);
      resolve();
    });

    // 发送 initialize
    rpc(child, {
      jsonrpc: "2.0",
      id: 1,
      method: "initialize",
      params: {
        protocolVersion: "2024-11-05",
        capabilities: {},
        clientInfo: { name: "smoke", version: "0" },
      },
    });
  });
}

(async () => {
  console.log("XMind MCP 冒烟测试");
  try {
    await testGenerate();
  } catch (e) {
    fail("testGenerate", e);
  }
  try {
    testOutline();
  } catch (e) {
    fail("testOutline", e);
  }
  await testServer();

  if (failures > 0) {
    console.error(`\n${failures} 项失败`);
    process.exit(1);
  }
  console.log("\n全部通过 \u2713");
})();
