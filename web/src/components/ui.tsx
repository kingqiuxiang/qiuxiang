import { motion } from "framer-motion";
import type { ReactNode } from "react";

const METHOD_COLORS: Record<string, string> = {
  GET: "bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-400/30",
  POST: "bg-blue-500/15 text-blue-300 ring-1 ring-blue-400/30",
  PUT: "bg-amber-500/15 text-amber-300 ring-1 ring-amber-400/30",
  DELETE: "bg-rose-500/15 text-rose-300 ring-1 ring-rose-400/30",
  PATCH: "bg-violet-500/15 text-violet-300 ring-1 ring-violet-400/30",
};

export function MethodBadge({ method }: { method: string }) {
  const cls = METHOD_COLORS[method] ?? "bg-slate-500/15 text-slate-300 ring-1 ring-slate-400/30";
  return <span className={`chip font-mono font-semibold ${cls}`}>{method}</span>;
}

export function StatusDot({ ok, pulse }: { ok: boolean; pulse?: boolean }) {
  return (
    <span className="relative inline-flex h-2.5 w-2.5">
      {pulse && ok && (
        <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent-400 opacity-60" />
      )}
      <span className={`relative inline-flex h-2.5 w-2.5 rounded-full ${ok ? "bg-accent-400" : "bg-slate-500"}`} />
    </span>
  );
}

export function Card({ children, className = "", delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay, ease: [0.22, 1, 0.36, 1] }}
      className={`card p-5 ${className}`}
    >
      {children}
    </motion.div>
  );
}

export function JsonBlock({ value, className = "" }: { value: unknown; className?: string }) {
  const text = typeof value === "string" ? value : JSON.stringify(value, null, 2);
  return (
    <pre className={`code-area overflow-auto rounded-xl bg-ink-950/80 border border-white/5 p-3 text-slate-300 ${className}`}>
      {text}
    </pre>
  );
}

export function PassFail({ passed, label }: { passed: boolean; label?: string }) {
  return (
    <span
      className={`chip ${
        passed ? "bg-accent-500/15 text-accent-400 ring-1 ring-accent-400/30" : "bg-rose-500/15 text-rose-300 ring-1 ring-rose-400/30"
      }`}
    >
      {passed ? "✓ " : "✗ "}
      {label ?? (passed ? "通过" : "失败")}
    </span>
  );
}

export function Spinner({ className = "" }: { className?: string }) {
  return (
    <span
      className={`inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white ${className}`}
    />
  );
}

export function SectionTitle({ icon, title, sub }: { icon?: ReactNode; title: string; sub?: string }) {
  return (
    <div className="flex items-center gap-3">
      {icon && <div className="grid h-9 w-9 place-items-center rounded-xl bg-brand-500/15 text-brand-400">{icon}</div>}
      <div>
        <h2 className="text-base font-semibold text-slate-100">{title}</h2>
        {sub && <p className="text-xs text-slate-400">{sub}</p>}
      </div>
    </div>
  );
}
