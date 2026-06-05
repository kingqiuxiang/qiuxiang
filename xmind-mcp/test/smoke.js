import { spawn } from "node:child_process";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import assert from "node:assert";
import JSZip from "jszip";

import { parseOutline } from "../src/outline.js";
import { generateXmindBuffer } from "../src/xmind.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SERVER = path.join(__dirname, "..", "src", "index.js");

function log(ok, msg) {
  console.log(`${ok ? "✓" : "✗"} ${msg}`);
  if (!ok) process.exitCode = 1;
}

async function testGeneratorAndParser() {
  const outline = `# 产品需求\n## 用户系统\n- 注册\n  - 邮箱验证\n- 登录\n## 支付系统\n- 下单\n- 退款`;
  const root = parseOutline(outline);
  assert.equal(root.title, "产品需求", "根标题应为 产品需求");
  assert.equal(root.children.length, 2, "应有 2 个一级分支");
  const userSys = root.children[0];
  assert.equal(userSys.title, "用户系统");
  assert.equal(userSys.children.length, 2, "用户系统应有 2 个子节点");
  assert.equal(userSys.children[0].children[0].title, "邮箱验证", "嵌套子点解析正确");
  log(true, "parseOutline 解析层级正确");

  const buf = await generateXmindBuffer(root, {});
  const zip = await JSZip.loadAsync(buf);
  assert.ok(zip.file("content.json"), "应包含 content.json");
  assert.ok(zip.file("metadata.json"), "应包含 metadata.json");
  assert.ok(zip.file("manifest.json"), "应包含 manifest.json");
  const content = JSON.parse(await zip.file("content.json").async("string"));
  assert.ok(Array.isArray(content) && content.length === 1, "content 应为单 sheet 数组");
  assert.equal(content[0].class, "sheet");
  assert.equal(content[0].rootTopic.title, "产品需求");
  assert.equal(content[0].rootTopic.children.attached.length, 2);
  log(true, "generateXmindBuffer 产出合法 .xmind zip 结构");
}

function rpc(child, obj) {
  child.stdin.write(JSON.stringify(obj) + "\n");
}

async function testMcpHandshake() {
  const tmp = await mkdtemp(path.join(os.tmpdir(), "xmind-mcp-"));
  const child = spawn(process.execPath, [SERVER], { stdio: ["pipe", "pipe", "pipe"] });

  let buffer = "";
  const pending = new Map();
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
      if (msg.id != null && pending.has(msg.id)) {
        pending.get(msg.id)(msg);
        pending.delete(msg.id);
      }
    }
  });

  const call = (id, method, params) =>
    new Promise((resolve, reject) => {
      const t = setTimeout(() => reject(new Error(`RPC ${method} 超时`)), 10000);
      pending.set(id, (m) => {
        clearTimeout(t);
        resolve(m);
      });
      rpc(child, { jsonrpc: "2.0", id, method, params });
    });

  try {
    const init = await call(1, "initialize", {
      protocolVersion: "2024-11-05",
      capabilities: {},
      clientInfo: { name: "smoke", version: "1.0.0" },
    });
    assert.ok(init.result?.serverInfo?.name === "xmind-mcp", "initialize 返回 serverInfo");
    rpc(child, { jsonrpc: "2.0", method: "notifications/initialized" });
    log(true, "MCP initialize 握手成功");

    const tools = await call(2, "tools/list", {});
    const names = (tools.result?.tools || []).map((t) => t.name);
    assert.ok(names.includes("create_mindmap"), "应注册 create_mindmap");
    assert.ok(names.includes("open_xmind"), "应注册 open_xmind");
    log(true, `tools/list 返回工具：${names.join(", ")}`);

    const res = await call(3, "tools/call", {
      name: "create_mindmap",
      arguments: {
        outline: "# 烟囱测试\n## A\n- a1\n## B",
        directory: tmp,
        filename: "smoke-test",
        open: false,
      },
    });
    const text = res.result?.content?.[0]?.text || "";
    assert.ok(text.includes("已生成思维导图"), "create_mindmap 应返回成功文本");
    const outFile = path.join(tmp, "smoke-test.xmind");
    const fileBuf = await readFile(outFile);
    const zip = await JSZip.loadAsync(fileBuf);
    const content = JSON.parse(await zip.file("content.json").async("string"));
    assert.equal(content[0].rootTopic.title, "烟囱测试");
    log(true, "tools/call create_mindmap 成功写出可解析的 .xmind 文件");
  } finally {
    child.kill();
    await rm(tmp, { recursive: true, force: true });
  }
}

(async () => {
  await testGeneratorAndParser();
  await testMcpHandshake();
  console.log(process.exitCode ? "\n部分用例失败" : "\n全部用例通过 ✅");
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
