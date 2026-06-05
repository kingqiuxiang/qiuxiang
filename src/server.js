import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { fillWithAiOrRules } from "./lib/aiClient.js";
import { scanCodebase } from "./lib/codeScanner.js";
import { loadConfig, safeConfig, workspaceRoot } from "./lib/config.js";
import { checkHealth, projectStatus, startProject, stopProject } from "./lib/projectLauncher.js";
import { runApiTest } from "./lib/testRunner.js";
import { getInterface, listInterfaces } from "./lib/yapiClient.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const publicRoot = path.join(__dirname, "../public");
const port = Number(process.env.PORT || 3025);

const contentTypes = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml"
};

function sendJson(response, statusCode, payload) {
  response.writeHead(statusCode, { "Content-Type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(payload, null, 2));
}

async function readBody(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  const raw = Buffer.concat(chunks).toString("utf8");
  return raw ? JSON.parse(raw) : {};
}

function configOverrides(body = {}) {
  return {
    yapi: body.yapi,
    ai: body.ai,
    project: body.project,
    runner: {
      targetBaseUrl: body.targetBaseUrl,
      timeoutMs: body.timeoutMs
    }
  };
}

async function handleApi(request, response, pathname) {
  const body = request.method === "GET" ? {} : await readBody(request);
  const config = await loadConfig(configOverrides(body));

  if (request.method === "GET" && pathname === "/api/status") {
    sendJson(response, 200, {
      ok: true,
      config: safeConfig(config),
      project: projectStatus()
    });
    return;
  }

  if (request.method === "POST" && pathname === "/api/code/scan") {
    const scan = await scanCodebase(config.project.root);
    sendJson(response, 200, { ok: true, scan });
    return;
  }

  if (request.method === "POST" && pathname === "/api/yapi/interfaces") {
    const result = await listInterfaces(config.yapi, { timeoutMs: config.runner.timeoutMs });
    sendJson(response, 200, { ok: true, ...result });
    return;
  }

  if (request.method === "POST" && pathname === "/api/yapi/interface") {
    const result = await getInterface(config.yapi, body.interfaceId, {
      timeoutMs: config.runner.timeoutMs
    });
    sendJson(response, 200, { ok: true, ...result });
    return;
  }

  if (request.method === "POST" && pathname === "/api/ai/fill") {
    const codeScan = body.codeScan || (await scanCodebase(config.project.root));
    const filled = await fillWithAiOrRules({
      aiConfig: config.ai,
      api: body.interface,
      codeScan,
      overrides: body.overrides || {}
    });
    sendJson(response, 200, { ok: true, filled });
    return;
  }

  if (request.method === "POST" && pathname === "/api/project/start") {
    const started = await startProject(config.project);
    const health = await checkHealth(config.project.healthUrl, 5000);
    sendJson(response, 200, { ok: true, ...started, health });
    return;
  }

  if (request.method === "POST" && pathname === "/api/project/stop") {
    const stopped = await stopProject();
    sendJson(response, 200, { ok: true, ...stopped });
    return;
  }

  if (request.method === "GET" && pathname === "/api/project/logs") {
    sendJson(response, 200, { ok: true, status: projectStatus() });
    return;
  }

  if (request.method === "POST" && pathname === "/api/test/run") {
    const result = await runApiTest({
      baseUrl: body.targetBaseUrl || config.runner.targetBaseUrl,
      request: body.request,
      timeoutMs: config.runner.timeoutMs
    });
    sendJson(response, 200, { ok: true, result });
    return;
  }

  if (request.method === "POST" && pathname === "/api/workflow/run") {
    const steps = [];
    const codeScan = await scanCodebase(config.project.root);
    steps.push({ name: "读取项目代码", ok: true, detail: codeScan.summary });

    let api;
    if (body.interfaceId) {
      const detail = await getInterface(config.yapi, body.interfaceId, {
        timeoutMs: config.runner.timeoutMs
      });
      api = detail.interface;
      steps.push({ name: "读取 YAPI 接口详情", ok: true, detail: detail.source });
    } else if (body.interface) {
      api = body.interface;
      steps.push({ name: "使用当前选中接口", ok: true, detail: api.title || api.path });
    } else {
      const list = await listInterfaces(config.yapi, { timeoutMs: config.runner.timeoutMs });
      api = list.list[0];
      steps.push({ name: "读取 YAPI 接口列表", ok: true, detail: list.source });
    }

    const filled = await fillWithAiOrRules({
      aiConfig: config.ai,
      api,
      codeScan,
      overrides: body.overrides || {}
    });
    steps.push({ name: "AI 一键参数填充", ok: true, detail: filled.strategy });

    if (body.startProject) {
      const started = await startProject(config.project);
      const health = await checkHealth(config.project.healthUrl, 5000);
      steps.push({
        name: "快捷启动项目",
        ok: Boolean(started.started || started.status.running),
        detail: health.checked ? health.message : started.message
      });
    }

    let test = null;
    if (body.runTest) {
      test = await runApiTest({
        baseUrl: body.targetBaseUrl || config.runner.targetBaseUrl,
        request: filled.request,
        timeoutMs: config.runner.timeoutMs
      });
      steps.push({
        name: "调用开发环境接口",
        ok: test.ok,
        detail: `${test.status} ${test.statusText} (${test.durationMs}ms)`
      });
    }

    sendJson(response, 200, { ok: true, steps, codeScan, filled, test });
    return;
  }

  sendJson(response, 404, { ok: false, error: "API not found" });
}

async function serveStatic(request, response, pathname) {
  const normalized = pathname === "/" ? "/index.html" : pathname;
  const filePath = path.resolve(publicRoot, `.${normalized}`);
  if (!filePath.startsWith(publicRoot)) {
    sendJson(response, 403, { ok: false, error: "Forbidden" });
    return;
  }

  try {
    const content = await readFile(filePath);
    response.writeHead(200, {
      "Content-Type": contentTypes[path.extname(filePath)] || "application/octet-stream"
    });
    response.end(content);
  } catch (error) {
    if (error.code === "ENOENT") {
      response.writeHead(302, { Location: "/" });
      response.end();
      return;
    }
    throw error;
  }
}

const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, `http://${request.headers.host}`);
    if (url.pathname.startsWith("/api/")) {
      await handleApi(request, response, url.pathname);
      return;
    }
    await serveStatic(request, response, url.pathname);
  } catch (error) {
    sendJson(response, 500, {
      ok: false,
      error: error.message,
      stack: process.env.NODE_ENV === "production" ? undefined : error.stack
    });
  }
});

server.listen(port, () => {
  console.log(`AI YAPI Runner is ready: http://localhost:${port}`);
  console.log(`Workspace root: ${workspaceRoot}`);
});
