import { useState } from 'react';
import { MonitorPlay, Play, CheckCircle2, XCircle, Globe, Clock, Gauge } from 'lucide-react';
import RequireProject from '../components/RequireProject';
import { Card, SectionTitle, Spinner, Empty, Stat } from '../components/ui';
import { api } from '../lib/api';
import { useApp } from '../lib/store';
import type { PageTestResult, Project } from '../lib/types';

function Inner({ project }: { project: Project }) {
  const { toast } = useApp();
  const [path, setPath] = useState('/');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<PageTestResult | null>(null);

  const run = async () => {
    setLoading(true);
    try {
      const r = await api.pageTest(project.id, path);
      setResult(r);
      toast(r.reachable ? 'success' : 'error', r.reachable ? '页面可访问' : '页面无法访问');
    } catch (e: any) {
      toast('error', e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-5xl mx-auto space-y-5">
      <div>
        <h1 className="text-2xl font-bold text-slate-100">前端页面测试</h1>
        <p className="text-sm text-slate-500 mt-1">访问开发环境前端页面，检测可达性与渲染情况（安装 Playwright 可启用真实浏览器检测）</p>
      </div>

      <Card>
        <SectionTitle title="目标页面" icon={<Globe size={16} />} sub={project.devWebUrl || '未配置前端地址，请在项目管理中填写'} />
        <div className="flex gap-3">
          <div className="flex-1 flex items-center input !py-0 !px-0 overflow-hidden">
            <span className="px-3 text-xs text-slate-500 font-mono shrink-0 border-r border-white/10 py-2.5">
              {project.devWebUrl || 'http://...'}
            </span>
            <input
              className="flex-1 bg-transparent px-3 py-2.5 text-sm outline-none font-mono"
              value={path}
              onChange={(e) => setPath(e.target.value)}
              placeholder="/login"
            />
          </div>
          <button className="btn-primary" onClick={run} disabled={loading}>
            {loading ? <Spinner /> : <Play size={15} />} 检测
          </button>
        </div>
      </Card>

      {!result ? (
        <Card>
          <Empty icon={<MonitorPlay size={24} />} title="尚未检测" hint="输入页面路径并点击「检测」" />
        </Card>
      ) : (
        <Card>
          <div className="flex items-center gap-3 mb-4">
            <div className={`chip text-sm font-bold ${result.reachable ? 'bg-emerald-500/15 text-emerald-300' : 'bg-rose-500/15 text-rose-300'}`}>
              {result.reachable ? <CheckCircle2 size={15} /> : <XCircle size={15} />}
              {result.reachable ? '可访问' : '不可访问'}
            </div>
            <span className="text-xs font-mono text-slate-500 truncate">{result.url}</span>
            <span className="ml-auto chip bg-white/5 text-slate-400">{result.engine === 'playwright' ? 'Playwright 浏览器' : 'HTTP 检测'}</span>
          </div>

          {result.error ? (
            <div className="text-sm text-rose-300 bg-rose-500/10 rounded-xl px-3 py-2.5">{result.error}</div>
          ) : (
            <>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">
                <Stat label="HTTP 状态" value={result.status} accent={result.reachable ? 'text-emerald-400' : 'text-rose-400'} />
                <Stat label="耗时" value={`${result.durationMs}ms`} />
                <Stat label="挂载根节点" value={result.rootMounted ? '是' : '否'} accent={result.rootMounted ? 'text-accent-400' : 'text-slate-300'} />
                <Stat label="脚本数" value={result.scriptCount ?? '—'} />
              </div>
              {result.title && (
                <div className="text-sm text-slate-300 mb-3">
                  页面标题：<span className="text-slate-100 font-medium">{result.title}</span>
                </div>
              )}
              <div className="space-y-1.5">
                {result.notes.map((n, i) => (
                  <div key={i} className="flex items-start gap-2 text-xs text-slate-400">
                    <Gauge size={13} className="mt-0.5 text-brand-400 shrink-0" /> {n}
                  </div>
                ))}
              </div>
            </>
          )}
        </Card>
      )}
    </div>
  );
}

export default function PageTest() {
  return <RequireProject>{(project) => <Inner project={project} />}</RequireProject>;
}
