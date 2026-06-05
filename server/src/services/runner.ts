import { spawn, ChildProcess } from 'node:child_process';
import { EventEmitter } from 'node:events';
import type { Project } from '../types.js';

export interface RunnerState {
  projectId: string;
  running: boolean;
  pid?: number;
  command: string;
  startedAt?: number;
  exitCode?: number | null;
  logs: LogLine[];
}

export interface LogLine {
  ts: number;
  stream: 'stdout' | 'stderr' | 'system';
  text: string;
}

class Runner extends EventEmitter {
  private procs = new Map<string, ChildProcess>();
  private states = new Map<string, RunnerState>();

  getState(projectId: string): RunnerState {
    return (
      this.states.get(projectId) || {
        projectId,
        running: false,
        command: '',
        logs: [],
      }
    );
  }

  private pushLog(projectId: string, line: LogLine) {
    const state = this.getState(projectId);
    state.logs.push(line);
    if (state.logs.length > 1000) state.logs = state.logs.slice(-1000);
    this.states.set(projectId, state);
    this.emit('log', { projectId, line });
  }

  start(project: Project): RunnerState {
    if (this.procs.has(project.id)) {
      this.pushLog(project.id, { ts: Date.now(), stream: 'system', text: '进程已在运行中。' });
      return this.getState(project.id);
    }
    const command = project.startCommand?.trim();
    if (!command) throw new Error('未配置启动命令');
    if (!project.codePath?.trim()) throw new Error('未配置项目代码路径');

    const state: RunnerState = {
      projectId: project.id,
      running: true,
      command,
      startedAt: Date.now(),
      exitCode: undefined,
      logs: [],
    };
    this.states.set(project.id, state);
    this.pushLog(project.id, { ts: Date.now(), stream: 'system', text: `▶ 启动: ${command}` });
    this.pushLog(project.id, { ts: Date.now(), stream: 'system', text: `工作目录: ${project.codePath}` });

    const child = spawn(command, {
      cwd: project.codePath,
      shell: true,
      env: { ...process.env, FORCE_COLOR: '0' },
    });
    state.pid = child.pid;
    this.procs.set(project.id, child);

    child.stdout?.on('data', (d) =>
      this.pushLog(project.id, { ts: Date.now(), stream: 'stdout', text: d.toString() })
    );
    child.stderr?.on('data', (d) =>
      this.pushLog(project.id, { ts: Date.now(), stream: 'stderr', text: d.toString() })
    );
    child.on('error', (err) => {
      this.pushLog(project.id, { ts: Date.now(), stream: 'system', text: `启动失败: ${err.message}` });
    });
    child.on('exit', (code) => {
      const s = this.getState(project.id);
      s.running = false;
      s.exitCode = code;
      this.states.set(project.id, s);
      this.procs.delete(project.id);
      this.pushLog(project.id, { ts: Date.now(), stream: 'system', text: `⏹ 进程退出，code=${code}` });
      this.emit('status', { projectId: project.id });
    });

    this.emit('status', { projectId: project.id });
    return this.getState(project.id);
  }

  stop(projectId: string): RunnerState {
    const child = this.procs.get(projectId);
    if (child) {
      try {
        child.kill('SIGTERM');
        setTimeout(() => {
          if (!child.killed) child.kill('SIGKILL');
        }, 3000);
      } catch {
        /* noop */
      }
      this.pushLog(projectId, { ts: Date.now(), stream: 'system', text: '已发送停止信号…' });
    }
    return this.getState(projectId);
  }
}

export const runner = new Runner();
