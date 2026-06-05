import express from 'express';
import cors from 'cors';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { nanoid } from 'nanoid';
import type { Project } from './types.js';
import { store } from './store.js';
import { fetchInterfaces, fetchInterfaceDetail } from './services/yapi.js';
import { fillParameters, analyzeResult } from './services/ai.js';
import { findCodeContext, describeProject } from './services/code.js';
import { executeTest } from './services/tester.js';
import { runner } from './services/runner.js';
import { testFrontendPage } from './services/browser.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
app.use(cors());
app.use(express.json({ limit: '5mb' }));

const PORT = Number(process.env.PORT) || 8787;

/** 读取环境变量中的默认 AI / YAPI 配置 */
function envDefaults() {
  return {
    ai: {
      baseUrl: process.env.AI_BASE_URL || 'https://api.openai.com/v1',
      apiKey: process.env.AI_API_KEY || '',
      model: process.env.AI_MODEL || 'gpt-4o-mini',
    },
    yapi: {
      baseUrl: process.env.YAPI_BASE_URL || '',
      token: process.env.YAPI_TOKEN || '',
    },
  };
}

function requireProject(req: express.Request, res: express.Response): Project | null {
  const project = store.getProject(req.params.id);
  if (!project) {
    res.status(404).json({ error: '项目不存在' });
    return null;
  }
  return project;
}

/* ----------------------------- 基础 ----------------------------- */
app.get('/api/health', (_req, res) => {
  res.json({ ok: true, name: '灵测 LingCe', version: '1.0.0', defaults: envDefaults() });
});

/* ----------------------------- 项目 ----------------------------- */
app.get('/api/projects', (_req, res) => {
  res.json(store.getProjects());
});

app.post('/api/projects', (req, res) => {
  const d = envDefaults();
  const body = req.body || {};
  const now = Date.now();
  const project: Project = {
    id: nanoid(10),
    name: body.name || '未命名项目',
    description: body.description || '',
    yapi: { baseUrl: body.yapi?.baseUrl ?? d.yapi.baseUrl, token: body.yapi?.token ?? d.yapi.token, projectId: body.yapi?.projectId },
    ai: { baseUrl: body.ai?.baseUrl ?? d.ai.baseUrl, apiKey: body.ai?.apiKey ?? d.ai.apiKey, model: body.ai?.model ?? d.ai.model },
    codePath: body.codePath || '',
    devBaseUrl: body.devBaseUrl || '',
    devWebUrl: body.devWebUrl || '',
    startCommand: body.startCommand || '',
    createdAt: now,
    updatedAt: now,
  };
  store.saveProject(project);
  res.json(project);
});

app.get('/api/projects/:id', (req, res) => {
  const project = requireProject(req, res);
  if (project) res.json(project);
});

app.put('/api/projects/:id', (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  const b = req.body || {};
  const updated: Project = {
    ...project,
    name: b.name ?? project.name,
    description: b.description ?? project.description,
    yapi: { ...project.yapi, ...(b.yapi || {}) },
    ai: { ...project.ai, ...(b.ai || {}) },
    codePath: b.codePath ?? project.codePath,
    devBaseUrl: b.devBaseUrl ?? project.devBaseUrl,
    devWebUrl: b.devWebUrl ?? project.devWebUrl,
    startCommand: b.startCommand ?? project.startCommand,
    updatedAt: Date.now(),
  };
  store.saveProject(updated);
  res.json(updated);
});

app.delete('/api/projects/:id', (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  store.deleteProject(project.id);
  res.json({ ok: true });
});

app.get('/api/projects/:id/overview', (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  res.json({
    code: describeProject(project),
    runner: runner.getState(project.id),
    testCount: store.getTests(project.id).length,
  });
});

/* ----------------------------- 接口 ----------------------------- */
app.get('/api/projects/:id/interfaces', async (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  const result = await fetchInterfaces(project);
  res.json(result);
});

app.get('/api/projects/:id/interfaces/:iid', async (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  const result = await fetchInterfaceDetail(project, req.params.iid);
  const code = findCodeContext(project, result.interface);
  res.json({ ...result, code });
});

/** AI 一键参数填充 */
app.post('/api/projects/:id/interfaces/:iid/fill', async (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  const { interface: api } = await fetchInterfaceDetail(project, req.params.iid);
  const code = findCodeContext(project, api);
  const result = await fillParameters(project.ai, api, code);
  res.json({ ...result, code, interface: api });
});

/** 执行测试（手动）：传入已填充参数 */
app.post('/api/projects/:id/interfaces/:iid/test', async (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  const { interface: api } = await fetchInterfaceDetail(project, req.params.iid);
  const filled = req.body?.filled;
  if (!filled) return res.status(400).json({ error: '缺少 filled 参数' });
  const response = await executeTest(project, api, filled);
  const { analysis } = await analyzeResult(project.ai, api, { request: filled, response });
  const record = store.addTest({
    id: nanoid(12),
    projectId: project.id,
    interfaceId: api.id,
    title: api.title,
    method: api.method,
    url: response.url,
    request: filled,
    response,
    analysis,
    source: req.body?.source === 'auto' ? 'auto' : 'manual',
    createdAt: Date.now(),
  });
  res.json(record);
});

/** AI 全自动测试：拉取接口 → 读代码 → AI 填参 → 发请求 → AI 评审。可批量。 */
app.post('/api/projects/:id/autotest', async (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  const ids: string[] = Array.isArray(req.body?.interfaceIds) ? req.body.interfaceIds : [];
  let targets = ids;
  if (targets.length === 0) {
    const all = await fetchInterfaces(project);
    targets = all.interfaces.map((i) => i.id);
  }
  const records = [];
  for (const iid of targets.slice(0, 30)) {
    const { interface: api } = await fetchInterfaceDetail(project, iid);
    const code = findCodeContext(project, api);
    const { filled } = await fillParameters(project.ai, api, code);
    const response = await executeTest(project, api, filled);
    const { analysis } = await analyzeResult(project.ai, api, { request: filled, response });
    const record = store.addTest({
      id: nanoid(12),
      projectId: project.id,
      interfaceId: api.id,
      title: api.title,
      method: api.method,
      url: response.url,
      request: filled,
      response,
      analysis,
      source: 'auto',
      createdAt: Date.now(),
    });
    records.push(record);
  }
  res.json({ count: records.length, records });
});

/* ----------------------------- 测试历史 ----------------------------- */
app.get('/api/projects/:id/tests', (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  res.json(store.getTests(project.id));
});

app.delete('/api/projects/:id/tests', (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  store.clearTests(project.id);
  res.json({ ok: true });
});

/* ----------------------------- 快捷启动 / Runner ----------------------------- */
app.get('/api/projects/:id/runner', (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  res.json(runner.getState(project.id));
});

app.post('/api/projects/:id/runner/start', (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  try {
    res.json(runner.start(project));
  } catch (e: any) {
    res.status(400).json({ error: e.message });
  }
});

app.post('/api/projects/:id/runner/stop', (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  res.json(runner.stop(project.id));
});

/** SSE 实时日志 */
app.get('/api/projects/:id/runner/stream', (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  res.set({
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  });
  res.flushHeaders?.();

  const state = runner.getState(project.id);
  res.write(`event: snapshot\ndata: ${JSON.stringify(state)}\n\n`);

  const onLog = (payload: any) => {
    if (payload.projectId !== project.id) return;
    res.write(`event: log\ndata: ${JSON.stringify(payload.line)}\n\n`);
  };
  const onStatus = (payload: any) => {
    if (payload.projectId !== project.id) return;
    res.write(`event: status\ndata: ${JSON.stringify(runner.getState(project.id))}\n\n`);
  };
  runner.on('log', onLog);
  runner.on('status', onStatus);

  const ping = setInterval(() => res.write(': ping\n\n'), 20000);
  req.on('close', () => {
    clearInterval(ping);
    runner.off('log', onLog);
    runner.off('status', onStatus);
  });
});

/* ----------------------------- 前端页面测试 ----------------------------- */
app.post('/api/projects/:id/page-test', async (req, res) => {
  const project = requireProject(req, res);
  if (!project) return;
  const result = await testFrontendPage(project, req.body?.path || '/');
  res.json(result);
});

/* ----------------------------- 静态前端（生产构建） ----------------------------- */
const webDist = path.resolve(__dirname, '../../web/dist');
if (fs.existsSync(webDist)) {
  app.use(express.static(webDist));
  app.get('*', (req, res, next) => {
    if (req.path.startsWith('/api')) return next();
    res.sendFile(path.join(webDist, 'index.html'));
  });
}

app.listen(PORT, () => {
  console.log(`\n  灵测 LingCe 服务已启动: http://localhost:${PORT}`);
  console.log(`  前端构建: ${fs.existsSync(webDist) ? '已加载' : '开发模式（请运行 web 端 vite）'}\n`);
});
