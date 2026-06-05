import express from "express";
import cors from "cors";
import path from "node:path";
import fs from "node:fs";
import { fileURLToPath } from "node:url";
import { publicConfig, saveConfig } from "./config.js";
import { aiConfigured, chat, extractJson } from "./services/ai.js";
import { projectAvailable, readFileSnippet, scan, search } from "./services/code.js";
import { getInterface, listInterfaces, yapiConfigured } from "./services/yapi.js";
import { aiFill, heuristicFill } from "./services/fill.js";
import { runRequest } from "./services/tester.js";
import { healthCheck, runnerStatus, startProject, stopProject } from "./services/runner.js";
import { checkPage } from "./services/page.js";
import { generatePlan, runPlan } from "./services/testplan.js";

const app = express();
app.use(cors());
app.use(express.json({ limit: "4mb" }));

const wrap = (fn: express.RequestHandler): express.RequestHandler => async (req, res, next) => {
  try {
    await fn(req, res, next);
  } catch (err: any) {
    res.status(500).json({ error: err.message || "internal error" });
  }
};

// ---- meta / status ----
app.get("/api/status", wrap(async (_req, res) => {
  res.json({
    yapi: yapiConfigured(),
    ai: aiConfigured(),
    project: projectAvailable(),
    runner: runnerStatus().status,
  });
}));

app.get("/api/config", wrap(async (_req, res) => res.json(publicConfig())));
app.put("/api/config", wrap(async (req, res) => {
  // ignore masked secret values (containing the bullet char) so we don't overwrite real ones
  const patch = req.body || {};
  if (patch.yapi?.token?.includes("•")) delete patch.yapi.token;
  if (patch.ai?.apiKey?.includes("•")) delete patch.ai.apiKey;
  saveConfig(patch);
  res.json(publicConfig());
}));

// ---- YAPI interfaces ----
app.get("/api/interfaces", wrap(async (_req, res) => {
  const { items, status } = await listInterfaces();
  res.json({ items, status });
}));
app.get("/api/interfaces/:id", wrap(async (req, res) => {
  const iface = await getInterface(req.params.id);
  if (!iface) return res.status(404).json({ error: "接口不存在" });
  res.json(iface);
}));

// ---- AI parameter filling ----
app.post("/api/fill/:id", wrap(async (req, res) => {
  const iface = await getInterface(req.params.id);
  if (!iface) return res.status(404).json({ error: "接口不存在" });
  const mode = req.query.mode === "heuristic" ? "heuristic" : "ai";
  const filled = mode === "heuristic" ? heuristicFill(iface) : await aiFill(iface);
  res.json({ filled, aiConfigured: aiConfigured(), codeGrounded: projectAvailable() });
}));

// ---- code (baseline) ----
app.get("/api/code/scan", wrap(async (_req, res) => {
  if (!projectAvailable()) return res.json({ available: false, files: [], root: "" });
  const { root, files, truncated } = scan();
  const byExt: Record<string, number> = {};
  for (const f of files) byExt[f.ext] = (byExt[f.ext] || 0) + 1;
  res.json({ available: true, root, total: files.length, truncated, byExt, files: files.slice(0, 500) });
}));
app.get("/api/code/search", wrap(async (req, res) => {
  if (!projectAvailable()) return res.json({ available: false, matches: [] });
  res.json({ available: true, matches: search(String(req.query.q || "")) });
}));
app.get("/api/code/file", wrap(async (req, res) => {
  if (!projectAvailable()) return res.status(400).json({ error: "未配置项目路径" });
  res.json(readFileSnippet(String(req.query.rel || "")));
}));

// ---- test execution ----
app.post("/api/test/run", wrap(async (req, res) => {
  const result = await runRequest(req.body);
  res.json(result);
}));

app.post("/api/test/plan/:id", wrap(async (req, res) => {
  const iface = await getInterface(req.params.id);
  if (!iface) return res.status(404).json({ error: "接口不存在" });
  const plan = await generatePlan(iface);
  res.json({ plan, aiConfigured: aiConfigured() });
}));

app.post("/api/test/autorun/:id", wrap(async (req, res) => {
  const iface = await getInterface(req.params.id);
  if (!iface) return res.status(404).json({ error: "接口不存在" });
  const plan = Array.isArray(req.body?.plan) && req.body.plan.length ? req.body.plan : await generatePlan(iface);
  const results = await runPlan(iface, plan);
  res.json({ results });
}));

// ---- project runner (AI quick start) ----
app.post("/api/runner/start", wrap(async (_req, res) => res.json(startProject())));
app.post("/api/runner/stop", wrap(async (_req, res) => res.json(stopProject())));
app.get("/api/runner/status", wrap(async (_req, res) => res.json(runnerStatus())));
app.get("/api/runner/health", wrap(async (_req, res) => res.json(await healthCheck())));

// ---- frontend page checker ----
app.post("/api/page/check", wrap(async (req, res) => {
  res.json(await checkPage(req.body?.url));
}));

// ---- generic AI chat (assistant) ----
app.post("/api/ai/chat", wrap(async (req, res) => {
  if (!aiConfigured()) {
    return res.json({
      configured: false,
      reply:
        "AI 尚未配置。请在「设置」中填入 AI_BASE_URL / AI_API_KEY / 模型(支持 OpenAI、DeepSeek、通义、Moonshot、本地 Ollama 等 OpenAI 兼容服务)。配置后我即可基于 YAPI 与项目代码进行参数填充、生成并执行测试。",
    });
  }
  const reply = await chat(req.body.messages || [], { temperature: 0.5 });
  res.json({ configured: true, reply });
}));

// ---- serve built frontend (production) ----
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webDist = path.resolve(__dirname, "../../web/dist");
if (fs.existsSync(webDist)) {
  app.use(express.static(webDist));
  app.get(/^(?!\/api).*/, (_req, res) => res.sendFile(path.join(webDist, "index.html")));
}

const PORT = Number(process.env.PORT || 8787);
app.listen(PORT, () => {
  console.log(`\n  APIPilot server  ▸  http://localhost:${PORT}`);
  console.log(`  YAPI: ${yapiConfigured() ? "configured" : "demo mode"}  |  AI: ${aiConfigured() ? "configured" : "fallback heuristics"}\n`);
});
