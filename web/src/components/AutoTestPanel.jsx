import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Bot, CheckCircle2, XCircle, AlertTriangle, Loader2, Circle, MinusCircle, Gauge, Lightbulb } from 'lucide-react';
import clsx from 'clsx';
import { JsonView, Pill, EmptyState } from './ui.jsx';

const STATUS_ICON = {
  running: { icon: Loader2, cls: 'text-brand-400 animate-spin' },
  done: { icon: CheckCircle2, cls: 'text-emerald-400' },
  warn: { icon: AlertTriangle, cls: 'text-amber-400' },
  error: { icon: XCircle, cls: 'text-rose-400' },
  skip: { icon: MinusCircle, cls: 'text-slate-500' },
  pending: { icon: Circle, cls: 'text-slate-600' },
};

const STEP_ORDER = [
  { key: 'resolve', title: '解析接口定义' },
  { key: 'code', title: '读取项目代码作为基准' },
  { key: 'fill', title: 'AI 一键参数填充' },
  { key: 'request', title: '向开发环境发送请求' },
  { key: 'analyze', title: 'AI 分析响应并断言' },
];

export default function AutoTestPanel({ iface, running, steps, report, onRun }) {
  if (!iface) {
    return <EmptyState icon={Bot} title="选择接口后即可一键 AI 自动测试" hint="流程：拉取接口 → 读代码 → AI 填参 → 发请求 → AI 断言 → 报告。" />;
  }
  const stepMap = {};
  steps.forEach((s) => { stepMap[s.key] = s; });

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between border-b border-white/10 p-5">
        <div>
          <h2 className="flex items-center gap-2 text-lg font-bold text-white">
            <Bot size={20} className="text-brand-400" /> AI 自动测试
          </h2>
          <p className="mt-1 font-mono text-sm text-slate-400">{iface.method} {iface.path}</p>
        </div>
        <button className="btn-primary" onClick={onRun} disabled={running}>
          {running ? <Loader2 size={16} className="animate-spin" /> : <Bot size={16} />}
          {running ? '测试进行中…' : '开始自动测试'}
        </button>
      </div>

      <div className="grid flex-1 grid-cols-1 gap-px overflow-hidden bg-white/5 lg:grid-cols-5">
        {/* Stepper */}
        <div className="overflow-y-auto bg-ink-900/40 p-5 lg:col-span-2">
          <p className="label mb-4">执行流程</p>
          <div className="relative space-y-1">
            {STEP_ORDER.map((step, i) => {
              const live = stepMap[step.key];
              const status = live?.status || 'pending';
              const { icon: Icon, cls } = STATUS_ICON[status] || STATUS_ICON.pending;
              return (
                <div key={step.key} className="flex gap-3">
                  <div className="flex flex-col items-center">
                    <motion.div
                      key={status}
                      initial={{ scale: 0.6, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}
                      className="grid h-8 w-8 place-items-center rounded-full bg-ink-800 ring-1 ring-white/10"
                    >
                      <Icon size={16} className={cls} />
                    </motion.div>
                    {i < STEP_ORDER.length - 1 && <div className={clsx('w-px flex-1 my-1', status === 'done' ? 'bg-emerald-500/40' : 'bg-white/10')} style={{ minHeight: 18 }} />}
                  </div>
                  <div className="pb-4">
                    <p className={clsx('text-sm font-medium', status === 'pending' ? 'text-slate-500' : 'text-slate-100')}>{step.title}</p>
                    {live?.detail && <p className="mt-0.5 text-[11px] text-slate-400">{live.detail}</p>}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Report */}
        <div className="overflow-y-auto bg-ink-900/40 p-5 lg:col-span-3">
          <AnimatePresence mode="wait">
            {report ? (
              <motion.div key="report" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="space-y-4">
                <Verdict report={report} />
                {report.analysis?.assertions?.length > 0 && (
                  <div className="card p-4">
                    <p className="label mb-3">断言结果</p>
                    <div className="space-y-2">
                      {report.analysis.assertions.map((a, i) => (
                        <div key={i} className="flex items-start gap-2 text-sm">
                          {a.passed ? <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-400" /> : <XCircle size={16} className="mt-0.5 shrink-0 text-rose-400" />}
                          <div>
                            <span className="text-slate-200">{a.name}</span>
                            {a.detail && <span className="ml-2 text-[11px] text-slate-500">{a.detail}</span>}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {report.analysis?.suggestions?.length > 0 && (
                  <div className="card p-4">
                    <p className="label mb-2 flex items-center gap-1"><Lightbulb size={13} /> AI 建议</p>
                    <ul className="list-inside list-disc space-y-1 text-sm text-slate-300">
                      {report.analysis.suggestions.map((s, i) => <li key={i}>{s}</li>)}
                    </ul>
                  </div>
                )}

                {report.filled && (
                  <details className="card p-4">
                    <summary className="cursor-pointer text-sm font-semibold text-white">AI 生成的请求参数</summary>
                    <JsonView className="mt-3" data={{ pathParams: report.filled.pathParams, query: report.filled.query, headers: report.filled.headers, body: report.filled.body }} />
                  </details>
                )}
                {report.result?.response && (
                  <details className="card p-4">
                    <summary className="cursor-pointer text-sm font-semibold text-white">实际响应</summary>
                    <JsonView className="mt-3" data={report.result.response.data} />
                  </details>
                )}
                {report.codeContext?.snippets?.length > 0 && (
                  <details className="card p-4">
                    <summary className="cursor-pointer text-sm font-semibold text-white">命中的项目代码（基准）</summary>
                    <div className="mt-3 space-y-2">
                      {report.codeContext.snippets.slice(0, 4).map((s, i) => (
                        <div key={i}>
                          <p className="mb-1 font-mono text-[11px] text-brand-400">{s.file}:{s.startLine}-{s.endLine}</p>
                          <JsonView data={s.code} max={200} />
                        </div>
                      ))}
                    </div>
                  </details>
                )}
              </motion.div>
            ) : (
              <motion.div key="empty" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <EmptyState icon={Gauge} title={running ? 'AI 正在测试…' : '点击「开始自动测试」'} hint="AI 将读取代码、生成参数、发起请求并自动断言，最终生成测试报告。" />
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}

function Verdict({ report }) {
  const v = report.analysis?.verdict || (report.ok ? 'pass' : 'fail');
  const map = {
    pass: { tone: 'good', icon: CheckCircle2, label: '测试通过', ring: 'ring-emerald-500/40', from: 'from-emerald-500/20' },
    warn: { tone: 'warn', icon: AlertTriangle, label: '存在警告', ring: 'ring-amber-500/40', from: 'from-amber-500/20' },
    fail: { tone: 'bad', icon: XCircle, label: '测试未通过', ring: 'ring-rose-500/40', from: 'from-rose-500/20' },
  };
  const m = map[v] || map.warn;
  const Icon = m.icon;
  const score = report.analysis?.score;
  return (
    <div className={clsx('card relative overflow-hidden p-5 ring-1', m.ring)}>
      <div className={clsx('pointer-events-none absolute inset-0 bg-gradient-to-br to-transparent', m.from)} />
      <div className="relative flex items-center gap-4">
        <div className="grid h-14 w-14 shrink-0 place-items-center rounded-2xl bg-ink-900/60">
          <Icon size={28} className={m.tone === 'good' ? 'text-emerald-400' : m.tone === 'warn' ? 'text-amber-400' : 'text-rose-400'} />
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h3 className="text-xl font-extrabold text-white">{m.label}</h3>
            {typeof score === 'number' && <Pill tone={m.tone}>{score} 分</Pill>}
            {report.analysis?.engine && <Pill>{report.analysis.engine}</Pill>}
          </div>
          <p className="mt-1 text-sm text-slate-300">{report.analysis?.summary || (report.ok ? '接口调用成功' : '接口调用失败')}</p>
        </div>
      </div>
    </div>
  );
}
