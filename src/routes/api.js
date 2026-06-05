import express from "express";
import { appState } from "../state/store.js";
import { loadYapiFromInput, findInterface } from "../services/yapiService.js";
import { collectProjectContext } from "../services/codeContextService.js";
import { generateFilledParams } from "../services/aiService.js";
import {
  getRunner,
  invokeInterface,
  startProjectCommand,
  stopRunner
} from "../services/projectRunnerService.js";
import { executeAiTestFlow } from "../services/testOrchestratorService.js";

function getTemplateKeys(template, keys = []) {
  if (!template || typeof template !== "object") {
    return keys;
  }
  Object.entries(template).forEach(([key, value]) => {
    keys.push(key);
    if (value && typeof value === "object" && !Array.isArray(value)) {
      getTemplateKeys(value, keys);
    }
  });
  return keys;
}

export const apiRouter = express.Router();

apiRouter.get("/health", (_req, res) => {
  res.json({
    ok: true,
    loadedInterfaces: appState.yapi.interfaces.length
  });
});

apiRouter.post("/yapi/load", async (req, res, next) => {
  try {
    const { sourceType = "json", value } = req.body || {};
    if (!value) {
      return res.status(400).json({ error: "value is required" });
    }
    const loaded = await loadYapiFromInput({ sourceType, value });
    appState.yapi.raw = loaded.raw;
    appState.yapi.interfaces = loaded.interfaces;

    return res.json({
      ok: true,
      total: loaded.interfaces.length,
      interfaces: loaded.interfaces.slice(0, 200).map((it) => ({
        id: it.id,
        title: it.title,
        method: it.method,
        path: it.path
      }))
    });
  } catch (error) {
    return next(error);
  }
});

apiRouter.post("/params/fill", async (req, res, next) => {
  try {
    const {
      interfaceId,
      method,
      path,
      projectPath = ".",
      aiModel
    } = req.body || {};

    const interfaceInfo = findInterface(appState.yapi.interfaces, {
      interfaceId,
      method,
      path
    });

    if (!interfaceInfo) {
      return res
        .status(404)
        .json({ error: "interface not found, call /api/yapi/load first" });
    }

    const template = interfaceInfo.reqBodyTemplate || {};
    const paramNames = getTemplateKeys(template);
    const codeContext = await collectProjectContext(projectPath, paramNames);
    const generated = await generateFilledParams({
      interfaceInfo,
      template,
      codeContext,
      aiModel
    });

    return res.json({
      ok: true,
      interfaceInfo: {
        id: interfaceInfo.id,
        title: interfaceInfo.title,
        method: interfaceInfo.method,
        path: interfaceInfo.path
      },
      payload: generated.payload,
      mode: generated.mode,
      reason: generated.reason || null,
      context: {
        filesScanned: codeContext.filesScanned,
        paramHints: codeContext.paramHints
      }
    });
  } catch (error) {
    return next(error);
  }
});

apiRouter.post("/run/start", (req, res, next) => {
  try {
    const { command, cwd = "." } = req.body || {};
    if (!command) {
      return res.status(400).json({ error: "command is required" });
    }
    const runner = startProjectCommand({ command, cwd });
    return res.json({ ok: true, runner });
  } catch (error) {
    return next(error);
  }
});

apiRouter.get("/run/:id/log", (req, res) => {
  const runner = getRunner(req.params.id);
  if (!runner) {
    return res.status(404).json({ error: "runner not found" });
  }
  return res.json({ ok: true, runner });
});

apiRouter.post("/run/:id/stop", (req, res) => {
  const runner = stopRunner(req.params.id);
  if (!runner) {
    return res.status(404).json({ error: "runner not found" });
  }
  return res.json({ ok: true, runner });
});

apiRouter.post("/invoke", async (req, res, next) => {
  try {
    const response = await invokeInterface(req.body || {});
    return res.json({ ok: true, response });
  } catch (error) {
    return next(error);
  }
});

apiRouter.post("/test/execute", async (req, res, next) => {
  try {
    const result = await executeAiTestFlow(req.body || {});
    return res.json({ ok: true, result });
  } catch (error) {
    return next(error);
  }
});
