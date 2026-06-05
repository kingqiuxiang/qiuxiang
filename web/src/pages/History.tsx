import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { History as HistoryIcon, Trash2, Zap, CheckCircle2, XCircle, Clock, ChevronDown } from 'lucide-react';
import RequireProject from '../components/RequireProject';
import { Card, MethodBadge, Spinner, Empty, JsonView, Stat } from '../components/ui';
import { api } from '../lib/api';
import { useApp } from '../lib/store';
import type { Project, TestRecord } from '../lib/types';

function Inner({ project }: { project: Project }) {
  const { toast } = useApp();
  const [tests, setTests] = useState<TestRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [open, setOpen] = useState<string | null>(null);
  const [filter, setFilter] = useState<'all' | 'pass' | 'fail'>('all');

  const load = async () => {
    setLoading(true);
    try {
      setTests(await api.tests(project.id));
    } catch (e: any) {
      toast('error', e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [project.id]);

  const runAuto = async () => {
    setRunning(true);
    toast('info', 'AI 正在批量测试，请稍候…');
    try {
      const r = await api.autotest(project.id);
      toast('success', `完成 ${r.count} 个接口测试`);
      await load();
    } catch (e: any) {
      toast('error', e.message);
    } finally {
      setRunning(false);
    }
  };

  const clear = async () => {
    if (!confirm('确认清空全部测试记录？')) return;
    await api.clearTests(project.id);
    toast('success', '已清空');
    load();
  };

  const filtered = tests.filter((t) =>
    filter === 'all' ? true : filter === 'pass' ? t.analysis?.passed : !t.analysis?.passed
  );
  const passed = tests.filter((t) => t.analysis?.passed).length;

  return (
    <div className="max-w-6xl mx-auto space-y-5">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">测试历史</h1>
          <p className="text-sm text-slate-500 mt-1">查看每次接口测试的请求、响应与 AI 评审</p>
        </div>
        <div className="flex gap-2">
          <button className="btn-primary" onClick={runAuto} disabled={running}>
            {running ? <Spinner /> : <Zap size={15} />} AI 全量测试
          </button>
          <button className="btn-danger" onClick={clear} disabled={!tests.length}>
            <Trash2 size={15} /> 清空
          </button>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <Card delay={0.05}><Stat label="总计" value={tests.length} accent="text-brand-400" /></Card>
        <Card delay={0.1}><Stat label="通过" value={passed} accent="text-emerald-400" /></Card>
        <Card delay={0.15}><Stat label="失败" value={tests.length - passed} accent="text-rose-400" /></Card>
      </div>

      <div className="flex gap-1.5">
        {(['all', 'pass', 'fail'] as const).map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`px-3.5 py-1.5 rounded-lg text-sm transition-all ${
              filter === f ? 'bg-white/10 text-slate-100' : 'text-slate-500 hover:text-slate-300'
            }`}
          >
            {f === 'all' ? '全部' : f === 'pass' ? '通过' : '失败'}
          </button>
        ))}
      </div>

      {loading ? (
        <Card><div className="flex items-center justify-center py-12 gap-3 text-slate-400"><Spinner className="w-5 h-5" /> 加载中…</div></Card>
      ) : filtered.length === 0 ? (
        <Card><Empty icon={<HistoryIcon size={24} />} title="暂无测试记录" hint="点击「AI 全量测试」开始" /></Card>
      ) : (
        <div className="space-y-2.5">
          {filtered.map((t, i) => (
            <motion.div
              key={t.id}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: Math.min(i * 0.03, 0.3) }}
              className="card overflow-hidden"
            >
              <button
                className="w-full flex items-center gap-3 p-4 text-left"
                onClick={() => setOpen(open === t.id ? null : t.id)}
              >
                <MethodBadge method={t.method} />
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium text-slate-100 truncate">{t.title}</div>
                  <div className="text-xs text-slate-500 font-mono truncate">{t.url}</div>
                </div>
                {t.source === 'auto' && <span className="chip bg-brand-500/15 text-brand-400">AUTO</span>}
                <span className={`chip ${t.response.ok ? 'bg-emerald-500/10 text-emerald-300' : 'bg-rose-500/10 text-rose-300'}`}>
                  {t.response.status || 'ERR'}
                </span>
                <span className="flex items-center gap-1 text-xs text-slate-500"><Clock size={12} />{t.response.durationMs}ms</span>
                {t.analysis?.passed ? <CheckCircle2 size={18} className="text-emerald-400" /> : <XCircle size={18} className="text-rose-400" />}
                <ChevronDown size={16} className={`text-slate-500 transition-transform ${open === t.id ? 'rotate-180' : ''}`} />
              </button>

              <AnimatePresence>
                {open === t.id && (
                  <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: 'auto', opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    className="border-t border-white/5"
                  >
                    <div className="p-4 space-y-4">
                      {t.analysis && (
                        <div className={`rounded-xl px-4 py-3 ${t.analysis.passed ? 'bg-emerald-500/10' : 'bg-rose-500/10'}`}>
                          <div className="flex items-center gap-2 text-sm font-semibold text-slate-100">
                            AI 评审 · {t.analysis.score} 分 · {t.analysis.passed ? '通过' : '未通过'}
                          </div>
                          <p className="text-xs text-slate-400 mt-1">{t.analysis.summary}</p>
                          {t.analysis.issues.length > 0 && (
                            <ul className="mt-2 space-y-1">
                              {t.analysis.issues.map((iss, k) => (
                                <li key={k} className="text-xs text-amber-200/90">· {iss}</li>
                              ))}
                            </ul>
                          )}
                        </div>
                      )}
                      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                        <div>
                          <div className="text-xs text-slate-400 mb-1.5">请求参数</div>
                          <JsonView data={t.request} className="max-h-64" />
                        </div>
                        <div>
                          <div className="text-xs text-slate-400 mb-1.5">响应内容</div>
                          <JsonView data={t.response.body} className="max-h-64" />
                        </div>
                      </div>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function History() {
  return <RequireProject>{(project) => <Inner project={project} />}</RequireProject>;
}
