import { useEffect, useRef, useState } from 'react';
import { Play, Square, Rocket, Terminal, Trash2, Cpu } from 'lucide-react';
import clsx from 'clsx';
import RequireProject from '../components/RequireProject';
import { Card, SectionTitle, StatusDot, Spinner, Empty } from '../components/ui';
import { api } from '../lib/api';
import { useApp } from '../lib/store';
import type { LogLine, Project, RunnerState } from '../lib/types';

function Inner({ project }: { project: Project }) {
  const { toast } = useApp();
  const [state, setState] = useState<RunnerState | null>(null);
  const [logs, setLogs] = useState<LogLine[]>([]);
  const [busy, setBusy] = useState(false);
  const logRef = useRef<HTMLDivElement>(null);
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    const es = new EventSource(`/api/projects/${project.id}/runner/stream`);
    esRef.current = es;
    es.addEventListener('snapshot', (e) => {
      const s = JSON.parse((e as MessageEvent).data) as RunnerState;
      setState(s);
      setLogs(s.logs || []);
    });
    es.addEventListener('log', (e) => {
      const line = JSON.parse((e as MessageEvent).data) as LogLine;
      setLogs((prev) => [...prev.slice(-800), line]);
    });
    es.addEventListener('status', (e) => {
      setState(JSON.parse((e as MessageEvent).data) as RunnerState);
    });
    return () => es.close();
  }, [project.id]);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [logs]);

  const start = async () => {
    setBusy(true);
    try {
      await api.runnerStart(project.id);
      toast('success', '已启动项目进程');
    } catch (e: any) {
      toast('error', e.message);
    } finally {
      setBusy(false);
    }
  };
  const stop = async () => {
    setBusy(true);
    try {
      await api.runnerStop(project.id);
      toast('info', '已发送停止信号');
    } catch (e: any) {
      toast('error', e.message);
    } finally {
      setBusy(false);
    }
  };

  const running = !!state?.running;

  return (
    <div className="max-w-6xl mx-auto space-y-5">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">快捷启动</h1>
          <p className="text-sm text-slate-500 mt-1">一键运行项目并实时查看启动日志</p>
        </div>
        <div className="flex gap-2">
          {running ? (
            <button className="btn-danger" onClick={stop} disabled={busy}>
              {busy ? <Spinner /> : <Square size={15} />} 停止
            </button>
          ) : (
            <button className="btn-primary" onClick={start} disabled={busy}>
              {busy ? <Spinner /> : <Play size={15} />} 启动项目
            </button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card>
          <SectionTitle title="运行状态" icon={<Cpu size={16} />} />
          <div className="space-y-2.5 text-sm">
            <Row label="状态">
              <span className="flex items-center gap-2">
                <StatusDot ok={running} />
                <span className={running ? 'text-emerald-300' : 'text-slate-400'}>{running ? '运行中' : '已停止'}</span>
              </span>
            </Row>
            <Row label="PID">{state?.pid ?? '—'}</Row>
            <Row label="退出码">{state?.exitCode ?? '—'}</Row>
          </div>
        </Card>
        <Card className="lg:col-span-2">
          <SectionTitle title="启动配置" icon={<Rocket size={16} />} />
          <div className="space-y-2.5 text-sm">
            <Row label="启动命令">
              <code className="font-mono text-accent-400">{project.startCommand || '未配置'}</code>
            </Row>
            <Row label="工作目录">
              <code className="font-mono text-slate-400 text-xs">{project.codePath || '未配置'}</code>
            </Row>
            <Row label="后端地址">
              <code className="font-mono text-slate-400 text-xs">{project.devBaseUrl || '—'}</code>
            </Row>
          </div>
        </Card>
      </div>

      <Card className="!p-0 overflow-hidden">
        <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/5 bg-white/[0.02]">
          <div className="flex items-center gap-2 text-sm font-medium text-slate-300">
            <Terminal size={15} className="text-brand-400" /> 实时日志
          </div>
          <button className="btn-ghost px-2.5 py-1.5 text-xs" onClick={() => setLogs([])}>
            <Trash2 size={13} /> 清屏
          </button>
        </div>
        <div ref={logRef} className="h-[44vh] overflow-auto p-4 code-scroll bg-ink-950/80">
          {logs.length === 0 ? (
            <Empty icon={<Terminal size={22} />} title="暂无日志" hint="点击「启动项目」开始运行" />
          ) : (
            logs.map((l, i) => (
              <div
                key={i}
                className={clsx(
                  'whitespace-pre-wrap break-words',
                  l.stream === 'stderr' && 'text-rose-300/90',
                  l.stream === 'system' && 'text-brand-400',
                  l.stream === 'stdout' && 'text-slate-300'
                )}
              >
                {l.text.replace(/\n$/, '')}
              </div>
            ))
          )}
        </div>
      </Card>
    </div>
  );
}

function Row({ label, children }: { label: string; children: any }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg bg-white/[0.02] px-3 py-2 border border-white/5">
      <span className="text-slate-500 text-xs">{label}</span>
      <span className="text-slate-200 truncate text-right">{children}</span>
    </div>
  );
}

export default function Runner() {
  return <RequireProject>{(project) => <Inner project={project} />}</RequireProject>;
}
