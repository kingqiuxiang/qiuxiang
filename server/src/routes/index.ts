import { Router } from "express";
import { config } from "../config/index.js";
import { yapiClient } from "../services/yapiClient.js";
import { codeIndexer } from "../services/codeIndexer.js";
import { fillParams } from "../services/paramFiller.js";
import { aiClient } from "../services/aiClient.js";
import { runInterfaceTest } from "../services/testRunner.js";
import { testFrontendPage } from "../services/frontendTester.js";
import { projectRunner } from "../services/projectRunner.js";
import { autopilot, autopilotAll } from "../services/autopilot.js";
import { randomUUID } from "node:crypto";
import type { PlatformStatus } from "../types/index.js";

export const api = Router();

const wrap =
  (fn: (req: any, res: any) => Promise<unknown>) =>
  (req: any, res: any) => {
    fn(req, res).catch((err: Error) => {
      res.status(500).json({ ok: false, error: err.message });
    });
  };

/* ---------- 平台状态 ---------- */
api.get(
  "/status",
  wrap(async (_req, res) => {
    const status: PlatformStatus = {
      yapi: { configured: config.yapi.configured, baseUrl: config.yapi.baseUrl || undefined },
      ai: { configured: aiClient.configured, model: config.ai.model },
      project: {
        root: config.project.root || undefined,
        devApi: config.project.devApiBaseUrl,
        devWeb: config.project.devWebBaseUrl,
        startCommand: config.project.startCommand || undefined,
      },
    };
    res.json({ ok: true, data: status });
  }),
);

/* ---------- YAPI ---------- */
api.get(
  "/yapi/project",
  wrap(async (_req, res) => {
    res.json({ ok: true, data: await yapiClient.getProject(), mock: yapiClient.isMock });
  }),
);

api.get(
  "/yapi/interfaces",
  wrap(async (_req, res) => {
    res.json({ ok: true, data: await yapiClient.listInterfaces(), mock: yapiClient.isMock });
  }),
);

api.get(
  "/yapi/interfaces/:id",
  wrap(async (req, res) => {
    const iface = await yapiClient.getInterface(req.params.id);
    if (!iface) return res.status(404).json({ ok: false, error: "接口不存在" });
    res.json({ ok: true, data: iface });
  }),
);

/* ---------- 项目代码 ---------- */
api.get(
  "/code/overview",
  wrap(async (_req, res) => {
    res.json({ ok: true, enabled: codeIndexer.enabled, data: codeIndexer.overview() });
  }),
);

api.get(
  "/code/evidence/:id",
  wrap(async (req, res) => {
    const iface = await yapiClient.getInterface(req.params.id);
    if (!iface) return res.status(404).json({ ok: false, error: "接口不存在" });
    res.json({ ok: true, data: codeIndexer.searchForInterface(iface) });
  }),
);

/* ---------- AI 一键填充 ---------- */
api.post(
  "/ai/fill/:id",
  wrap(async (req, res) => {
    const iface = await yapiClient.getInterface(req.params.id);
    if (!iface) return res.status(404).json({ ok: false, error: "接口不存在" });
    res.json({ ok: true, data: await fillParams(iface) });
  }),
);

/* ---------- 接口测试 ---------- */
api.post(
  "/test/:id",
  wrap(async (req, res) => {
    const iface = await yapiClient.getInterface(req.params.id);
    if (!iface) return res.status(404).json({ ok: false, error: "接口不存在" });
    const runId = randomUUID();
    const filled = req.body?.filled ?? (await fillParams(iface));
    const result = await runInterfaceTest(iface, filled, runId, req.body?.options ?? {});
    res.json({ ok: true, data: result });
  }),
);

/* ---------- 前端页面测试 ---------- */
api.post(
  "/test-page",
  wrap(async (req, res) => {
    const runId = randomUUID();
    const path = req.body?.path ?? "/";
    res.json({ ok: true, data: await testFrontendPage(path, runId) });
  }),
);

/* ---------- AI 自动测试（写完接口之后 AI 去测试）---------- */
api.post(
  "/autopilot/:id",
  wrap(async (req, res) => {
    res.json({ ok: true, data: await autopilot(req.params.id, req.body ?? {}) });
  }),
);

api.post(
  "/autopilot",
  wrap(async (req, res) => {
    res.json({ ok: true, data: await autopilotAll(req.body ?? {}) });
  }),
);

/* ---------- 项目快捷启动 ---------- */
api.get(
  "/project/state",
  wrap(async (_req, res) => {
    res.json({ ok: true, data: projectRunner.state });
  }),
);

api.post(
  "/project/start",
  wrap(async (_req, res) => {
    const runId = randomUUID();
    res.json({ ok: true, runId, data: await projectRunner.start(runId) });
  }),
);

api.post(
  "/project/stop",
  wrap(async (_req, res) => {
    const runId = randomUUID();
    res.json({ ok: true, data: projectRunner.stop(runId) });
  }),
);
