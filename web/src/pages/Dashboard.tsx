import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Boxes,
  FlaskConical,
  Rocket,
  Cpu,
  Network,
  Code2,
  Zap,
  CheckCircle2,
  XCircle,
  ArrowRight,
  Sparkles,
} from 'lucide-react';
import RequireProject from '../components/RequireProject';
import { Card, SectionTitle, Stat, StatusDot, MethodBadge, Spinner, Empty } from '../components/ui';
import { api } from '../lib/api';
import { useApp } from '../lib/store';
import type { Project, TestRecord } from '../lib/types';

function Inner({ project }: { project: Project }) {
  const navigate = useNavigate();
  const { toast } = useApp();
  const [overview, setOverview] = useState<any>(null);
  const [tests, setTests] = useState<TestRecord[]>([]);
  const [running, setRunning] = useState(false);

  const load = async () => {
    try {
      const [ov, t] = await Promise.all([api.overview(project.id), api.tests(project.id)]);
      setOverview(ov);
      setTests(t);
    } catch (e: any) {
      toast('error', e.message);
    }
  };

  useEffect(() => {
    load();
  }, [project.id]);

  const runAuto = async () => {
    setRunning(true);
    toast('info', 'AI 正在批量测试接口，请稍候…');
    try {
      const r = await api.autotest(project.id);
      toast('success', `已完成 ${r.count} 个接口的自动测试`);
      await load();
      navigate('/history');
    } catch (e: any) {
      toast('error', e.message);
    } finally {
      setRunning(false);
    }
  };

  const passed = tests.filter((t) => t.analysis?.passed).length;
  const failed = tests.length - passed;
  const passRate = tests.length ? Math.round((passed / tests.length) * 100) : 0;

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        className="relative overflow-hidden rounded-2xl border border-white/5 p-7 bg-gradient-to-br from-brand-600/25 via-ink-850/60 to-accent-500/15"
      >
        <div className="absolute -right-10 -top-10 w-48 h-48 rounded-full bg-brand-500/20 blur-3xl" />
        <div className="relative flex items-center justify-between flex-wrap gap-4">
          <div>
            <div className="flex items-center gap-2 text-accent-400 text-xs font-medium mb-2">
              <Sparkles size={14} /> AI 驱动的接口智能测试
            </div>
            <h1 className="text-2xl font-bold text-slate-50">{project.name}</h1>
            <p className="text-sm text-slate-400 mt-1.5 max-w-xl">
              {project.description || '读取 YAPI 接口定义，结合项目源码由 AI 一键填充参数并自动发起测试。'}
            </p>
          </div>
          <button className="btn-primary text-base px-5 py-3" onClick={runAuto} disabled={running}>
            {running ? <Spinner /> : <Zap size={18} />} AI 一键全量测试
          </button>
        </div>
      </motion.div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card delay={0.05}>
          <Stat label="测试总数" value={tests.length} accent="text-brand-400" />
        </Card>
        <Card delay={0.1}>
          <Stat label="通过率" value={`${passRate}%`} accent="text-accent-400" />
        </Card>
        <Card delay={0.15}>
          <Stat label="通过 / 失败" value={`${passed} / ${failed}`} />
        </Card>
        <Card delay={0.2}>
          <div className="flex items-center justify-between">
            <Stat label="开发环境" value={overview?.runner?.running ? '运行中' : '未启动'} accent={overview?.runner?.running ? 'text-emerald-400' : 'text-slate-300'} />
            <StatusDot ok={!!overview?.runner?.running} />
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-1" delay={0.1}>
          <SectionTitle title="环境就绪情况" icon={<Code2 size={16} />} />
          <div className="space-y-2.5">
            <ReadyRow icon={<Network size={14} />} label="YAPI 连接" ok={!!project.yapi.token} text={project.yapi.token ? '已配置' : '演示模式'} />
            <ReadyRow icon={<Cpu size={14} />} label="AI 模型" ok={!!project.ai.apiKey} text={project.ai.apiKey ? project.ai.model : '演示模式'} />
            <ReadyRow
              icon={<Code2 size={14} />}
              label="项目源码"
              ok={!!overview?.code?.available}
              text={overview?.code?.available ? overview.code.techHints.join(' · ') || '已识别' : '未配置'}
            />
          </div>
        </Card>

        <Card className="lg:col-span-2" delay={0.15}>
          <div className="flex items-center justify-between mb-4">
            <SectionTitle title="最近测试" icon={<FlaskConical size={16} />} />
            <button className="btn-ghost px-3 py-1.5 text-xs" onClick={() => navigate('/history')}>
              查看全部 <ArrowRight size={13} />
            </button>
          </div>
          {tests.length === 0 ? (
            <Empty icon={<FlaskConical size={22} />} title="暂无测试记录" hint="点击右上角「AI 一键全量测试」开始" />
          ) : (
            <div className="space-y-2">
              {tests.slice(0, 6).map((t) => (
                <div key={t.id} className="flex items-center gap-3 rounded-xl bg-white/[0.02] border border-white/5 px-3 py-2.5">
                  <MethodBadge method={t.method} />
                  <span className="text-sm text-slate-200 truncate flex-1">{t.title}</span>
                  <span className="text-xs text-slate-500 font-mono">{t.response.durationMs}ms</span>
                  {t.analysis?.passed ? (
                    <CheckCircle2 size={16} className="text-emerald-400" />
                  ) : (
                    <XCircle size={16} className="text-rose-400" />
                  )}
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <QuickAction icon={<Boxes size={18} />} title="浏览接口库" desc="从 YAPI 拉取接口定义" onClick={() => navigate('/interfaces')} />
        <QuickAction icon={<FlaskConical size={18} />} title="进入测试台" desc="AI 填参 · 单接口调试" onClick={() => navigate('/workbench')} />
        <QuickAction icon={<Rocket size={18} />} title="快捷启动项目" desc="一键运行 + 实时日志" onClick={() => navigate('/runner')} />
      </div>
    </div>
  );
}

function ReadyRow({ icon, label, ok, text }: { icon: any; label: string; ok: boolean; text: string }) {
  return (
    <div className="flex items-center gap-3 rounded-xl bg-white/[0.02] border border-white/5 px-3 py-2.5">
      <span className={ok ? 'text-accent-400' : 'text-slate-600'}>{icon}</span>
      <span className="text-sm text-slate-300">{label}</span>
      <span className="ml-auto text-xs text-slate-500 truncate max-w-[55%]" title={text}>
        {text}
      </span>
      <StatusDot ok={ok} />
    </div>
  );
}

function QuickAction({ icon, title, desc, onClick }: { icon: any; title: string; desc: string; onClick: () => void }) {
  return (
    <motion.button
      whileHover={{ y: -3 }}
      onClick={onClick}
      className="card p-5 text-left hover:shadow-glow transition-shadow group"
    >
      <div className="grid place-items-center w-10 h-10 rounded-xl bg-gradient-to-br from-brand-600/40 to-accent-500/20 text-brand-400 mb-3">
        {icon}
      </div>
      <div className="font-semibold text-slate-100 flex items-center gap-1.5">
        {title}
        <ArrowRight size={15} className="opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all" />
      </div>
      <div className="text-xs text-slate-500 mt-1">{desc}</div>
    </motion.button>
  );
}

export default function Dashboard() {
  return <RequireProject>{(project) => <Inner project={project} />}</RequireProject>;
}
