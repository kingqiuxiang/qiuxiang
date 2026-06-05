import { ReactNode } from 'react';
import { motion } from 'framer-motion';
import clsx from 'clsx';

export function MethodBadge({ method, className }: { method: string; className?: string }) {
  const m = (method || 'GET').toUpperCase();
  return (
    <span className={clsx('chip font-mono font-semibold tracking-wide', `method-${m}`, className)}>
      {m}
    </span>
  );
}

export function Card({
  children,
  className,
  delay = 0,
}: {
  children: ReactNode;
  className?: string;
  delay?: number;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay, ease: [0.22, 1, 0.36, 1] }}
      className={clsx('card p-5', className)}
    >
      {children}
    </motion.div>
  );
}

export function SectionTitle({ title, sub, icon }: { title: string; sub?: string; icon?: ReactNode }) {
  return (
    <div className="flex items-center gap-3 mb-4">
      {icon && (
        <div className="grid place-items-center w-9 h-9 rounded-xl bg-gradient-to-br from-brand-600/40 to-accent-500/20 text-brand-400">
          {icon}
        </div>
      )}
      <div>
        <h3 className="text-sm font-semibold text-slate-100">{title}</h3>
        {sub && <p className="text-xs text-slate-500 mt-0.5">{sub}</p>}
      </div>
    </div>
  );
}

export function StatusDot({ ok }: { ok: boolean }) {
  return (
    <span className="relative flex h-2.5 w-2.5">
      <span
        className={clsx(
          'absolute inline-flex h-full w-full rounded-full opacity-60',
          ok ? 'bg-emerald-400 animate-ping' : 'bg-slate-500'
        )}
      />
      <span
        className={clsx('relative inline-flex rounded-full h-2.5 w-2.5', ok ? 'bg-emerald-400' : 'bg-slate-500')}
      />
    </span>
  );
}

export function Empty({ icon, title, hint, action }: { icon: ReactNode; title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="grid place-items-center w-16 h-16 rounded-2xl bg-white/5 text-slate-500 mb-4 animate-floaty">
        {icon}
      </div>
      <p className="text-slate-300 font-medium">{title}</p>
      {hint && <p className="text-slate-500 text-sm mt-1.5 max-w-md">{hint}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      className={clsx(
        'inline-block animate-spin rounded-full border-2 border-white/20 border-t-brand-400',
        className || 'w-4 h-4'
      )}
    />
  );
}

export function JsonView({ data, className }: { data: any; className?: string }) {
  let text = '';
  try {
    text = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
  } catch {
    text = String(data);
  }
  return (
    <pre className={clsx('code-scroll overflow-auto rounded-xl bg-ink-950/80 border border-white/5 p-4 text-slate-300', className)}>
      {text || '—'}
    </pre>
  );
}

export function Stat({ label, value, accent }: { label: string; value: ReactNode; accent?: string }) {
  return (
    <div className="rounded-xl bg-white/[0.03] border border-white/5 px-4 py-3">
      <div className="text-xs text-slate-500">{label}</div>
      <div className={clsx('text-lg font-bold mt-0.5', accent || 'text-slate-100')}>{value}</div>
    </div>
  );
}
