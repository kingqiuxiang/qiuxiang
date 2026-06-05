import { motion } from "framer-motion";
import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";

export function cx(...parts: (string | false | undefined | null)[]): string {
  return parts.filter(Boolean).join(" ");
}

export function Card({
  children,
  className,
  hover,
}: {
  children: ReactNode;
  className?: string;
  hover?: boolean;
}) {
  return (
    <div
      className={cx(
        "glass rounded-2xl",
        hover && "transition-all duration-300 hover:border-indigo-400/30 hover:-translate-y-0.5",
        className
      )}
    >
      {children}
    </div>
  );
}

const methodColors: Record<string, string> = {
  GET: "text-emerald-300 bg-emerald-400/10 border-emerald-400/30",
  POST: "text-sky-300 bg-sky-400/10 border-sky-400/30",
  PUT: "text-amber-300 bg-amber-400/10 border-amber-400/30",
  DELETE: "text-rose-300 bg-rose-400/10 border-rose-400/30",
  PATCH: "text-fuchsia-300 bg-fuchsia-400/10 border-fuchsia-400/30",
};

export function MethodBadge({ method }: { method: string }) {
  const m = (method || "GET").toUpperCase();
  return (
    <span
      className={cx(
        "px-2 py-0.5 rounded-md text-[11px] font-bold tracking-wide border",
        methodColors[m] || "text-slate-300 bg-slate-400/10 border-slate-400/30"
      )}
    >
      {m}
    </span>
  );
}

export function Badge({
  children,
  tone = "default",
}: {
  children: ReactNode;
  tone?: "default" | "ok" | "warn" | "danger" | "info";
}) {
  const tones: Record<string, string> = {
    default: "bg-white/5 text-slate-300 border-white/10",
    ok: "bg-emerald-400/10 text-emerald-300 border-emerald-400/30",
    warn: "bg-amber-400/10 text-amber-300 border-amber-400/30",
    danger: "bg-rose-400/10 text-rose-300 border-rose-400/30",
    info: "bg-indigo-400/10 text-indigo-300 border-indigo-400/30",
  };
  return (
    <span className={cx("px-2 py-0.5 rounded-full text-[11px] font-medium border", tones[tone])}>
      {children}
    </span>
  );
}

export function Button({
  children,
  onClick,
  variant = "primary",
  size = "md",
  loading,
  disabled,
  className,
  type = "button",
}: {
  children: ReactNode;
  onClick?: () => void;
  variant?: "primary" | "ghost" | "soft" | "danger";
  size?: "sm" | "md";
  loading?: boolean;
  disabled?: boolean;
  className?: string;
  type?: "button" | "submit";
}) {
  const variants: Record<string, string> = {
    primary:
      "bg-gradient-to-r from-indigo-500 to-cyan-400 text-slate-950 font-semibold hover:shadow-[0_10px_30px_-8px_rgba(34,211,238,0.6)]",
    ghost: "bg-white/5 text-slate-200 hover:bg-white/10 border border-white/10",
    soft: "bg-indigo-400/10 text-indigo-200 hover:bg-indigo-400/20 border border-indigo-400/20",
    danger: "bg-rose-500/15 text-rose-200 hover:bg-rose-500/25 border border-rose-400/30",
  };
  return (
    <motion.button
      type={type}
      whileTap={{ scale: 0.96 }}
      onClick={onClick}
      disabled={disabled || loading}
      className={cx(
        "inline-flex items-center justify-center gap-2 rounded-xl transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed",
        size === "sm" ? "px-3 py-1.5 text-xs" : "px-4 py-2 text-sm",
        variants[variant],
        className
      )}
    >
      {loading && <Loader2 size={size === "sm" ? 13 : 15} className="animate-spin" />}
      {children}
    </motion.button>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-slate-400 text-sm">
      <Loader2 size={16} className="animate-spin" /> {label || "加载中…"}
    </div>
  );
}

export function JsonView({ data, className }: { data: unknown; className?: string }) {
  const text = typeof data === "string" ? data : JSON.stringify(data, null, 2);
  return (
    <pre
      className={cx(
        "text-xs leading-relaxed font-mono text-slate-300 bg-black/30 rounded-xl p-3 overflow-auto border border-white/5",
        className
      )}
    >
      {text}
    </pre>
  );
}

export function SectionTitle({ children, icon }: { children: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex items-center gap-2 text-[13px] font-semibold text-slate-200 uppercase tracking-wider">
      {icon}
      {children}
    </div>
  );
}

export function Empty({ icon, title, hint }: { icon?: ReactNode; title: string; hint?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="text-slate-600 mb-3">{icon}</div>
      <div className="text-slate-300 font-medium">{title}</div>
      {hint && <div className="text-slate-500 text-sm mt-1 max-w-md">{hint}</div>}
    </div>
  );
}
