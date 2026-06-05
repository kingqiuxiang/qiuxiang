import { spawn, type ChildProcess } from "node:child_process";
import { getConfig } from "../config.js";

interface RunnerState {
  child: ChildProcess | null;
  status: "stopped" | "starting" | "running" | "error";
  command: string;
  startedAt: number | null;
  logs: { ts: number; stream: "stdout" | "stderr" | "system"; line: string }[];
  exitCode: number | null;
}

const MAX_LOGS = 500;

const state: RunnerState = {
  child: null,
  status: "stopped",
  command: "",
  startedAt: null,
  logs: [],
  exitCode: null,
};

function pushLog(stream: RunnerState["logs"][number]["stream"], line: string) {
  for (const l of line.split(/\r?\n/)) {
    if (!l.trim() && stream !== "system") continue;
    state.logs.push({ ts: Date.now(), stream, line: l });
  }
  if (state.logs.length > MAX_LOGS) state.logs.splice(0, state.logs.length - MAX_LOGS);
}

export function startProject(): { ok: boolean; message: string } {
  if (state.child && state.status === "running") {
    return { ok: false, message: "项目已在运行中。" };
  }
  const cfg = getConfig();
  const command = cfg.project.startCommand;
  const cwd = cfg.project.rootPath;
  if (!command) return { ok: false, message: "未配置启动命令(PROJECT_START_COMMAND)。" };
  if (!cwd) return { ok: false, message: "未配置项目路径(PROJECT_ROOT)。" };

  state.command = command;
  state.status = "starting";
  state.startedAt = Date.now();
  state.exitCode = null;
  state.logs = [];
  pushLog("system", `$ ${command}  (cwd: ${cwd})`);

  try {
    const child = spawn(command, { cwd, shell: true, env: process.env });
    state.child = child;
    state.status = "running";
    child.stdout?.on("data", (d) => pushLog("stdout", d.toString()));
    child.stderr?.on("data", (d) => pushLog("stderr", d.toString()));
    child.on("exit", (code) => {
      state.exitCode = code;
      state.status = code === 0 ? "stopped" : "error";
      pushLog("system", `进程退出,exit code = ${code}`);
      state.child = null;
    });
    child.on("error", (err) => {
      state.status = "error";
      pushLog("system", `启动失败: ${err.message}`);
      state.child = null;
    });
    return { ok: true, message: "已发起启动命令。" };
  } catch (err: any) {
    state.status = "error";
    pushLog("system", `启动异常: ${err.message}`);
    return { ok: false, message: err.message };
  }
}

export function stopProject(): { ok: boolean; message: string } {
  if (!state.child) {
    state.status = "stopped";
    return { ok: false, message: "没有正在运行的进程。" };
  }
  state.child.kill("SIGTERM");
  pushLog("system", "已发送停止信号 (SIGTERM)。");
  return { ok: true, message: "已停止。" };
}

export function runnerStatus() {
  return {
    status: state.status,
    command: state.command,
    startedAt: state.startedAt,
    exitCode: state.exitCode,
    logs: state.logs.slice(-200),
  };
}

export async function healthCheck(): Promise<{ ok: boolean; status?: number; message: string }> {
  const url = getConfig().project.healthUrl;
  if (!url) return { ok: false, message: "未配置健康检查地址。" };
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(5000) });
    return { ok: res.ok, status: res.status, message: res.ok ? "服务可访问" : `返回 ${res.status}` };
  } catch (err: any) {
    return { ok: false, message: `不可访问: ${err.message}` };
  }
}
