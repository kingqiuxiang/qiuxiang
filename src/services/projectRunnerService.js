import { spawn } from "node:child_process";
import path from "node:path";
import { randomUUID } from "node:crypto";
import axios from "axios";
import { appState } from "../state/store.js";

function toAbsoluteCwd(cwd) {
  if (!cwd) {
    return process.cwd();
  }
  return path.isAbsolute(cwd) ? cwd : path.resolve(process.cwd(), cwd);
}

export function startProjectCommand({ command, cwd }) {
  const runId = randomUUID();
  const absoluteCwd = toAbsoluteCwd(cwd);

  const child = spawn(command, {
    cwd: absoluteCwd,
    shell: true,
    env: process.env
  });

  const record = {
    id: runId,
    command,
    cwd: absoluteCwd,
    status: "running",
    logs: [],
    startedAt: new Date().toISOString(),
    pid: child.pid
  };

  const appendLog = (type, chunk) => {
    const text = chunk.toString();
    const line = `[${new Date().toISOString()}][${type}] ${text}`;
    record.logs.push(line);
    if (record.logs.length > 300) {
      record.logs = record.logs.slice(-300);
    }
  };

  child.stdout.on("data", (chunk) => appendLog("stdout", chunk));
  child.stderr.on("data", (chunk) => appendLog("stderr", chunk));
  child.on("exit", (code, signal) => {
    record.status = "stopped";
    record.exitCode = code;
    record.signal = signal;
    record.stoppedAt = new Date().toISOString();
  });

  appState.runners.set(runId, { record, child });
  return record;
}

export function getRunner(runId) {
  const container = appState.runners.get(runId);
  return container?.record || null;
}

export function stopRunner(runId) {
  const container = appState.runners.get(runId);
  if (!container) {
    return null;
  }
  container.child.kill("SIGTERM");
  container.record.status = "stopping";
  return container.record;
}

export async function invokeInterface({
  url,
  method = "GET",
  headers = {},
  params,
  body,
  timeoutMs = 20000
}) {
  const response = await axios({
    url,
    method,
    headers,
    params,
    data: body,
    timeout: timeoutMs,
    validateStatus: () => true
  });

  return {
    status: response.status,
    headers: response.headers,
    data: response.data
  };
}
