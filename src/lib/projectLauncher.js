import { spawn } from "node:child_process";

let runningProcess = null;
let logs = [];

function appendLog(source, chunk) {
  const lines = String(chunk)
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => ({
      at: new Date().toISOString(),
      source,
      line
    }));
  logs.push(...lines);
  if (logs.length > 500) {
    logs = logs.slice(-500);
  }
}

export function projectStatus() {
  return {
    running: Boolean(runningProcess && !runningProcess.killed),
    pid: runningProcess?.pid || null,
    logs: logs.slice(-80)
  };
}

export async function startProject(projectConfig) {
  if (runningProcess && !runningProcess.killed) {
    return {
      started: false,
      message: "项目启动命令已在运行。",
      status: projectStatus()
    };
  }

  if (!projectConfig?.startCommand) {
    return {
      started: false,
      message: "未配置 project.startCommand。请在界面或 config/ai-yapi-runner.local.json 中设置启动命令。",
      status: projectStatus()
    };
  }

  logs = [];
  appendLog("system", `$ ${projectConfig.startCommand}`);
  runningProcess = spawn(projectConfig.startCommand, {
    cwd: projectConfig.root,
    shell: true,
    env: process.env,
    stdio: ["ignore", "pipe", "pipe"]
  });

  runningProcess.stdout.on("data", (chunk) => appendLog("stdout", chunk));
  runningProcess.stderr.on("data", (chunk) => appendLog("stderr", chunk));
  runningProcess.on("exit", (code, signal) => {
    appendLog("system", `process exited with code=${code} signal=${signal || ""}`);
    runningProcess = null;
  });

  await new Promise((resolve) => setTimeout(resolve, 600));
  return {
    started: true,
    message: "已发送项目启动命令。",
    status: projectStatus()
  };
}

export async function stopProject() {
  if (!runningProcess || runningProcess.killed) {
    return {
      stopped: false,
      message: "当前没有由 AI YAPI Runner 启动的项目进程。",
      status: projectStatus()
    };
  }
  runningProcess.kill("SIGTERM");
  await new Promise((resolve) => setTimeout(resolve, 300));
  return {
    stopped: true,
    message: "已请求停止项目进程。",
    status: projectStatus()
  };
}

export async function checkHealth(url, timeoutMs = 5000) {
  if (!url) {
    return { checked: false, ok: false, message: "未配置健康检查地址。" };
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { signal: controller.signal });
    return {
      checked: true,
      ok: response.ok,
      status: response.status,
      message: response.ok ? "健康检查通过。" : `健康检查返回 HTTP ${response.status}`
    };
  } catch (error) {
    return {
      checked: true,
      ok: false,
      message: error.message
    };
  } finally {
    clearTimeout(timer);
  }
}
