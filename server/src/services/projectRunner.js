import { spawn } from 'node:child_process';
import { EventEmitter } from 'node:events';
import axios from 'axios';
import { getConfig } from '../config.js';

/**
 * Manages the lifecycle of the project under test: launch its start command,
 * stream logs, and probe readiness ("AI 快捷启动并调用该项目").
 */
class ProjectRunner extends EventEmitter {
  constructor() {
    super();
    this.proc = null;
    this.logs = [];
    this.maxLogs = 500;
    this.status = 'stopped'; // stopped | starting | running | ready | exited | error
    this.startedAt = null;
    this.cmd = '';
  }

  _log(stream, line) {
    const entry = { ts: Date.now(), stream, line };
    this.logs.push(entry);
    if (this.logs.length > this.maxLogs) this.logs.shift();
    this.emit('log', entry);
  }

  getState() {
    return {
      status: this.status,
      pid: this.proc?.pid || null,
      cmd: this.cmd,
      startedAt: this.startedAt,
      logCount: this.logs.length,
    };
  }

  getLogs() {
    return this.logs;
  }

  start({ cmd, cwd } = {}) {
    const { project } = getConfig();
    const command = cmd || project.startCmd;
    const dir = cwd || project.path;
    if (!command) throw Object.assign(new Error('未配置启动命令 startCmd。'), { code: 'NO_CMD' });
    if (!dir) throw Object.assign(new Error('未配置项目路径 path。'), { code: 'NO_PATH' });
    if (this.proc) throw Object.assign(new Error('项目已在运行中，请先停止。'), { code: 'ALREADY_RUNNING' });

    this.cmd = command;
    this.status = 'starting';
    this.startedAt = Date.now();
    this.logs = [];
    this._log('system', `$ ${command}  (cwd: ${dir})`);

    this.proc = spawn(command, { cwd: dir, shell: true, env: { ...process.env } });
    this.status = 'running';

    this.proc.stdout.on('data', (d) => String(d).split(/\r?\n/).filter(Boolean).forEach((l) => this._log('stdout', l)));
    this.proc.stderr.on('data', (d) => String(d).split(/\r?\n/).filter(Boolean).forEach((l) => this._log('stderr', l)));
    this.proc.on('error', (err) => { this.status = 'error'; this._log('system', `启动失败: ${err.message}`); this.emit('state', this.getState()); });
    this.proc.on('exit', (code, signal) => {
      this.status = 'exited';
      this._log('system', `进程退出 code=${code} signal=${signal || ''}`);
      this.proc = null;
      this.emit('state', this.getState());
    });

    this.emit('state', this.getState());
    return this.getState();
  }

  stop() {
    if (!this.proc) return { status: this.status };
    this._log('system', '正在停止进程…');
    try { process.kill(-this.proc.pid, 'SIGTERM'); } catch { this.proc.kill('SIGTERM'); }
    const p = this.proc;
    setTimeout(() => { try { p.kill('SIGKILL'); } catch { /* noop */ } }, 4000);
    return { status: 'stopping' };
  }

  /** Poll a readiness URL until it responds or times out. */
  async waitReady({ url, timeoutMs = 30000, intervalMs = 1000 } = {}) {
    const { project } = getConfig();
    const target = url || (project.devApiBaseUrl || project.devWebUrl || '').replace(/\/$/, '') + (project.readyPath || '/');
    if (!target || target === '/') throw Object.assign(new Error('无可探活的地址。'), { code: 'NO_READY_URL' });
    const deadline = Date.now() + timeoutMs;
    let lastErr = '';
    while (Date.now() < deadline) {
      try {
        const res = await axios.get(target, { timeout: 4000, validateStatus: () => true });
        if (res.status > 0) {
          this.status = 'ready';
          this._log('system', `探活成功: ${target} -> ${res.status}`);
          this.emit('state', this.getState());
          return { ready: true, url: target, status: res.status };
        }
      } catch (err) { lastErr = err.message; }
      await new Promise((r) => setTimeout(r, intervalMs));
    }
    return { ready: false, url: target, error: lastErr || 'timeout' };
  }
}

export const projectRunner = new ProjectRunner();
