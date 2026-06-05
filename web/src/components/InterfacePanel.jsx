import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Wand2, Send, FileCode2, CheckCircle2, XCircle, Clock, HardDrive } from 'lucide-react';
import clsx from 'clsx';
import { MethodBadge, JsonView, Spinner, Pill, EmptyState } from './ui.jsx';
import RequestEditor from './RequestEditor.jsx';

export default function InterfacePanel({
  iface, filled, onFilledChange, onAiFill, aiFilling, fillMeta,
  onSend, sending, response,
}) {
  if (!iface) {
    return <EmptyState icon={FileCode2} title="选择左侧接口开始测试" hint="支持从 YAPI 拉取或使用内置样例。选中后即可一键 AI 填参、发送请求并查看响应。" />;
  }

  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-white/10 p-5">
        <div className="flex flex-wrap items-center gap-3">
          <MethodBadge method={iface.method} className="!px-3 !py-1 !text-xs" />
          <h2 className="text-lg font-bold text-white">{iface.title}</h2>
          {iface.catName && <Pill>{iface.catName}</Pill>}
        </div>
        <p className="mt-1.5 font-mono text-sm text-slate-400">{iface.path}</p>
      </div>

      <div className="grid flex-1 grid-cols-1 gap-px overflow-hidden bg-white/5 lg:grid-cols-2">
        {/* Request */}
        <div className="flex min-h-0 flex-col bg-ink-900/40">
          <div className="flex items-center justify-between border-b border-white/10 px-5 py-3">
            <span className="text-sm font-semibold text-white">请求参数</span>
            <button className="btn-primary !py-1.5 !text-xs" onClick={onAiFill} disabled={aiFilling}>
              {aiFilling ? <Spinner /> : <Wand2 size={14} />} AI 一键填充
            </button>
          </div>
          <AnimatePresence>
            {fillMeta && (
              <motion.div
                initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }}
                className="overflow-hidden border-b border-white/10 bg-brand-500/5 px-5 py-2"
              >
                <div className="flex flex-wrap items-center gap-2 text-[11px]">
                  <Pill tone={fillMeta.engine?.startsWith('ai') ? 'good' : 'brand'}>引擎: {fillMeta.engine}</Pill>
                  {fillMeta.codeMatched && <Pill tone="good">已命中项目代码</Pill>}
                  {fillMeta.notes && <span className="text-slate-400">{fillMeta.notes}</span>}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
          <div className="flex-1 overflow-y-auto p-5">
            <RequestEditor filled={filled} onChange={onFilledChange} />
          </div>
          <div className="border-t border-white/10 p-4">
            <button className="btn-primary w-full" onClick={onSend} disabled={sending}>
              {sending ? <Spinner /> : <Send size={16} />} 发送请求
            </button>
          </div>
        </div>

        {/* Response */}
        <div className="flex min-h-0 flex-col bg-ink-900/40">
          <div className="flex items-center justify-between border-b border-white/10 px-5 py-3">
            <span className="text-sm font-semibold text-white">响应</span>
            {response && <ResponseMeta response={response} />}
          </div>
          <div className="flex-1 overflow-y-auto p-5">
            {!response ? (
              <EmptyState icon={Send} title="尚未发送请求" hint="点击「发送请求」或先用「AI 一键填充」生成参数。" />
            ) : response.ok ? (
              <div className="space-y-3">
                <JsonView data={response.response.data} max={9999} />
              </div>
            ) : (
              <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 p-4 text-sm text-rose-200">
                请求失败：{response.error?.message}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function ResponseMeta({ response }) {
  const r = response.response || {};
  const ok = response.ok && r.status >= 200 && r.status < 400;
  return (
    <div className="flex items-center gap-2 text-[11px]">
      <span className={clsx('chip border font-mono', ok ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30' : 'bg-rose-500/15 text-rose-300 border-rose-500/30')}>
        {ok ? <CheckCircle2 size={12} /> : <XCircle size={12} />} {r.status || 'ERR'}
      </span>
      {r.durationMs != null && <span className="flex items-center gap-1 text-slate-400"><Clock size={11} /> {r.durationMs}ms</span>}
      {r.size != null && <span className="flex items-center gap-1 text-slate-400"><HardDrive size={11} /> {formatBytes(r.size)}</span>}
    </div>
  );
}

function formatBytes(n) {
  if (!n) return '0 B';
  if (n < 1024) return `${n} B`;
  return `${(n / 1024).toFixed(1)} KB`;
}
