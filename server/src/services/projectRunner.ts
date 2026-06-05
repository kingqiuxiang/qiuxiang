import { spawn, type ChildProcess } from "node:child_process";
import axios from "axios";
import { config } from "../config/index.js";
import { bus } from "../utils/events.js";
import { logger } from "../utils/logger.js";

export interface ProjectRunState {
  running: boolean;
  pid?: number;
  command?: string;
  startedAt?: string;
  ready?: boolean;
}

/**
 * AI 快捷启动并调用被测项目：在 PROJECT_ROOT 下执行启动命令，
 * 实时回传日志，并轮询开发环境地址直到就绪。
 */
class ProjectRunner {
  private child?: ChildProcess;
  private startedAt?: string;
  private ready = false;

  get state(): ProjectRunState {
    return {
      running: Boolean(this.child && !this.child.killed),
      pid: this.child?.pid,
      command: config.project.startCommand,
      startedAt: this.startedAt,
      ready: this.ready,
    };
  }

  async start(runId: string): Promise<ProjectRunState> {
    if (this.child && !this.child.killed) {
      bus.log(runId, "项目已在运行中。", "warn");
      return this.state;
    }
    const cmd = config.project.startCommand;
    if (!cmd) throw new Error("未配置 PROJECT_START_COMMAND");
    if (!config.project.root) throw new Error("未配置 PROJECT_ROOT");

    bus.log(runId, `启动项目：${cmd}（cwd=${config.project.root}）`, "info");
    this.ready = false;
    this.startedAt = new Date().toISOString();
    this.child = spawn(cmd, {
      cwd: config.project.root,
      shell: true,
      env: { ...process.env },
    });

    this.child.stdout?.on("data", (d) => bus.log(runId, String(d).trimEnd(), "info"));
    this.child.stderr?.on("data", (d) => bus.log(runId, String(d).trimEnd(), "warn"));
    this.child.on("exit", (code) => {
      bus.log(runId, `项目进程退出，code=${code}`, code === 0 ? "info" : "error");
      this.ready = false;
    });

    // 异步等待就绪（不阻塞接口返回）
    this.waitReady(runId).catch((e) => logger.error(e));
    return this.state;
  }

  private async waitReady(runId: string, attempts = 30, intervalMs = 2000): Promise<void> {
    const target = config.project.devApiBaseUrl;
    for (let i = 0; i < attempts; i++) {
      await new Promise((r) => setTimeout(r, intervalMs));
      if (!this.child || this.child.killed) return;
      try {
        await axios.get(target, { timeout: 3000, validateStatus: () => true });
        this.ready = true;
        bus.log(runId, `开发环境已就绪：${target}`, "success");
        bus.emit({ type: "status", runId, payload: this.state, ts: new Date().toISOString() });
        return;
      } catch {
        bus.log(runId, `等待开发环境就绪… (${i + 1}/${attempts})`, "info");
      }
    }
    bus.log(runId, "开发环境就绪检测超时（项目可能仍在启动）。", "warn");
  }

  stop(runId: string): ProjectRunState {
    if (this.child && !this.child.killed) {
      bus.log(runId, "停止项目进程。", "info");
      this.child.kill("SIGTERM");
    }
    this.ready = false;
    return this.state;
  }
}

export const projectRunner = new ProjectRunner();
