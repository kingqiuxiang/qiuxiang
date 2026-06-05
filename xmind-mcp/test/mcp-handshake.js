import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const entry = path.join(__dirname, "..", "src", "index.js");

const child = spawn("node", [entry], { stdio: ["pipe", "pipe", "inherit"] });

let buf = "";
const pending = [];
child.stdout.on("data", (d) => {
  buf += d.toString();
  let idx;
  while ((idx = buf.indexOf("\n")) >= 0) {
    const line = buf.slice(0, idx).trim();
    buf = buf.slice(idx + 1);
    if (line) {
      try {
        pending.push(JSON.parse(line));
      } catch {
        /* ignore non-json */
      }
    }
  }
});

function send(obj) {
  child.stdin.write(JSON.stringify(obj) + "\n");
}

function waitFor(id, timeout = 3000) {
  return new Promise((resolve, reject) => {
    const t0 = Date.now();
    const iv = setInterval(() => {
      const m = pending.find((p) => p.id === id);
      if (m) {
        clearInterval(iv);
        resolve(m);
      } else if (Date.now() - t0 > timeout) {
        clearInterval(iv);
        reject(new Error(`等待响应 id=${id} 超时`));
      }
    }, 30);
  });
}

async function run() {
  send({
    jsonrpc: "2.0",
    id: 1,
    method: "initialize",
    params: {
      protocolVersion: "2024-11-05",
      capabilities: {},
      clientInfo: { name: "handshake-test", version: "0.0.0" },
    },
  });
  const init = await waitFor(1);
  console.log("initialize ->", init.result?.serverInfo);

  send({ jsonrpc: "2.0", method: "notifications/initialized", params: {} });

  send({ jsonrpc: "2.0", id: 2, method: "tools/list", params: {} });
  const list = await waitFor(2);
  const tools = (list.result?.tools || []).map((t) => t.name);
  console.log("tools/list ->", tools);
  if (!tools.includes("create_mindmap") || !tools.includes("open_xmind")) {
    throw new Error("缺少预期工具");
  }

  // 调用 create_mindmap（open=false，避免在无 GUI 环境尝试打开）
  send({
    jsonrpc: "2.0",
    id: 3,
    method: "tools/call",
    params: {
      name: "create_mindmap",
      arguments: {
        outline: "# 测试中心\n## 分支A\n- a1\n## 分支B",
        outputDir: "/tmp/xmind-mcp-out",
        open: false,
      },
    },
  });
  const call = await waitFor(3);
  console.log("tools/call ->", call.result?.content?.[0]?.text);
  if (call.result?.isError) throw new Error("create_mindmap 返回错误");

  console.log("\nMCP 握手 + 工具调用 全部通过 ✅");
  child.kill();
  process.exit(0);
}

run().catch((e) => {
  console.error("握手测试失败:", e.message);
  child.kill();
  process.exit(1);
});
