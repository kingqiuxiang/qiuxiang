import React, { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { Play, Square, Activity, Terminal, Globe, Rocket, ExternalLink } from 'lucide-react';
import clsx from 'clsx';
import { api, streamProjectLogs } from '../api/client.js';
import { Spinner, Pill, Dot } from './ui.jsx';

const STATE_TONE = { ready: 'good', running: 'brand', starting: 'warn', stopped: 'default', exited: 'default', error: 'bad' };

export default function ProjectConsole({ config, onToast }) {
  const [state, setState] = useState({ status: 'stopped' });
  const [logs, setLogs] = useState([]);
  const [busy, setBusy] = useState(false);
  const [fe, setFe] = useState(null);
  const [feBusy, setFeBusy] = useState(false);
  const [fePath, setFePath] = useState('/');
  const logRef = useRef(null);

  useEffect(() => {
    const es = streamProjectLogs({
      onLog: (l) => setLogs((prev) => [...prev.slice(-300), l]),
      onState: (s) => setState(s),
    });
    return () => es.close();
  }, []);

  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
  }, [logs]);

  const launch = async () => {
    setBusy(true);
    try {
      setLogs([]);
      await api.projectStart({});
      onToast?.('项目启动命令已执行', 'good');
      api.projectReady({}).then((r) => {
        if (r.ready) onToast?.(`探活成功 ${r.url} (${r.status})`, 'good');
      }).catch(() => {});
    } catch (e) {
      onToast?.(e.message, 'bad');
    } finally { setBusy(false); }
  };

  const stop = async () => {
    setBusy(true);
    try { await api.projectStop(); onToast?.('已发送停止信号', 'default'); }
    catch (e) { onToast?.(e.message, 'bad'); }
    finally { setBusy(false); }
  };

  const runFrontend = async () => {
    setFeBusy(true); setFe(null);
    try { setFe(await api.testFrontend({ path: fePath })); }
    catch (e) { onToast?.(e.message, 'bad'); }
    finally { setFeBusy(false); }
  };

  const running = ['running', 'starting', 'ready'].includes(state.status);

  return (
    <div className="flex h-full flex-col gap-4 overflow-y-auto p-4">
      {/* Launcher */}
      <div className="card p-4">
        <div className="mb-3 flex items-center justify-between">
          <span className="flex items-center gap-2 text-sm font-semibold text-white"><Rocket size={16} className="text-brand-400" /> AI 快捷启动项目</span>
          <Pill tone={STATE_TONE[state.status] || 'default'}>
            <Dot ok={state.status === 'ready' || state.status === 'running'} /> {state.status}
          </Pill>
        </div>
        <p className="mb-3 truncate font-mono text-[11px] text-slate-500">$ {config?.project?.startCmd || '未配置启动命令'}</p>
        <div className="flex gap-2">
          <button className="btn-primary flex-1 !py-1.5 !text-xs" onClick={launch} disabled={busy || running}>
            {busy ? <Spinner /> : <Play size={14} />} 启动
          </button>
          <button className="btn-ghost flex-1 !py-1.5 !text-xs" onClick={stop} disabled={busy || !running}>
            <Square size={14} /> 停止
          </button>
        </div>
      </div>

      {/* Logs */}
      <div className="card flex min-h-[180px] flex-1 flex-col overflow-hidden">
        <div className="flex items-center gap-2 border-b border-white/10 px-4 py-2.5 text-sm font-semibold text-white">
          <Terminal size={15} className="text-accent-400" /> 运行日志
          <span className="ml-auto"><Activity size={13} className={clsx(running ? 'text-emerald-400' : 'text-slate-600', running && 'animate-pulse')} /></span>
        </div>
        <div ref={logRef} className="code-scroll flex-1 overflow-y-auto bg-ink-900/80 p-3 font-mono text-[11px] leading-relaxed">
          {logs.length === 0 ? (
            <p className="text-slate-600">暂无日志，点击「启动」运行被测项目…</p>
          ) : (
            logs.map((l, i) => (
              <div key={i} className={clsx('whitespace-pre-wrap break-all', l.stream === 'stderr' ? 'text-rose-300' : l.stream === 'system' ? 'text-brand-400' : 'text-slate-300')}>
                {l.line}
              </div>
            ))
          )}
        </div>
      </div>

      {/* Frontend page test */}
      <div className="card p-4">
        <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-white">
          <Globe size={16} className="text-accent-400" /> 前端页面测试
        </div>
        <div className="flex gap-2">
          <input className="input flex-1 font-mono text-xs" value={fePath} onChange={(e) => setFePath(e.target.value)} placeholder="/path" />
          <button className="btn-primary !py-1.5 !text-xs" onClick={runFrontend} disabled={feBusy}>
            {feBusy ? <Spinner /> : <ExternalLink size={14} />} 访问
          </button>
        </div>
        {fe && (
          <motion.div initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} className="mt-3 space-y-2 rounded-xl bg-ink-900/60 p-3 text-xs">
            <div className="flex flex-wrap items-center gap-2">
              <Pill tone={fe.ok ? 'good' : 'bad'}>{fe.ok ? '正常' : '异常'}</Pill>
              <Pill>HTTP {fe.status}</Pill>
              <Pill>{fe.engine}</Pill>
              {fe.durationMs != null && <span className="text-slate-500">{fe.durationMs}ms</span>}
            </div>
            {fe.title && <p className="text-slate-300">标题：{fe.title}</p>}
            {fe.error && <p className="text-rose-300">错误：{fe.error}</p>}
            {fe.consoleErrors?.length > 0 && <p className="text-amber-300">控制台错误 {fe.consoleErrors.length} 条</p>}
            {fe.note && <p className="text-slate-500">{fe.note}</p>}
            {fe.textPreview && <p className="line-clamp-3 text-slate-500">{fe.textPreview}</p>}
          </motion.div>
        )}
      </div>
    </div>
  );
}
