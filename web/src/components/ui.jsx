import React from 'react';
import clsx from 'clsx';

export const METHOD_COLORS = {
  GET: 'text-emerald-300 bg-emerald-500/15 border-emerald-500/30',
  POST: 'text-amber-300 bg-amber-500/15 border-amber-500/30',
  PUT: 'text-sky-300 bg-sky-500/15 border-sky-500/30',
  DELETE: 'text-rose-300 bg-rose-500/15 border-rose-500/30',
  PATCH: 'text-violet-300 bg-violet-500/15 border-violet-500/30',
};

export function MethodBadge({ method, className }) {
  const m = (method || 'GET').toUpperCase();
  return (
    <span className={clsx('chip border font-mono', METHOD_COLORS[m] || 'text-slate-300 bg-white/10 border-white/15', className)}>
      {m}
    </span>
  );
}

export function Dot({ ok }) {
  return <span className={clsx('inline-block h-2 w-2 rounded-full', ok ? 'bg-emerald-400 shadow-[0_0_8px] shadow-emerald-400/60' : 'bg-slate-500')} />;
}

export function Pill({ children, tone = 'default' }) {
  const tones = {
    default: 'bg-white/8 text-slate-300 border-white/10',
    good: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
    warn: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
    bad: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
    brand: 'bg-brand-500/15 text-brand-400 border-brand-500/30',
  };
  return <span className={clsx('chip border', tones[tone])}>{children}</span>;
}

export function JsonView({ data, className, max = 480 }) {
  const text = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
  return (
    <pre
      className={clsx('code-scroll overflow-auto rounded-xl bg-ink-900/80 border border-white/10 p-3 text-xs leading-relaxed font-mono text-slate-300', className)}
      style={{ maxHeight: max }}
    >
      {text}
    </pre>
  );
}

export function Spinner({ className }) {
  return (
    <svg className={clsx('animate-spin', className)} width="16" height="16" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="3" opacity="0.25" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

export function EmptyState({ icon: Icon, title, hint }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-20 text-center">
      {Icon && (
        <div className="grid h-16 w-16 place-items-center rounded-2xl bg-white/5 border border-white/10 text-brand-400 animate-floaty">
          <Icon size={28} />
        </div>
      )}
      <p className="text-slate-300 font-medium">{title}</p>
      {hint && <p className="max-w-sm text-sm text-slate-500">{hint}</p>}
    </div>
  );
}
