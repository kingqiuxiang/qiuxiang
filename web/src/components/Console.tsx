import { useEffect, useRef } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Terminal, Trash2, Wifi, WifiOff } from "lucide-react";
import type { RunEvent } from "../lib/api";

const LEVEL_STYLE: Record<string, string> = {
  info: "text-slate-300",
  warn: "text-amber-300",
  error: "text-rose-300",
  success: "text-accent-400",
};

export function Console({
  events,
  connected,
  onClear,
  filterRunId,
}: {
  events: RunEvent[];
  connected: boolean;
  onClear: () => void;
  filterRunId?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const logs = events.filter(
    (e) => (e.type === "log" || e.type === "done" || e.type === "status") && (!filterRunId || e.runId === filterRunId),
  );

  useEffect(() => {
    ref.current?.scrollTo({ top: ref.current.scrollHeight, behavior: "smooth" });
  }, [logs.length]);

  return (
    <div className="card flex h-full flex-col overflow-hidden">
      <div className="flex items-center justify-between border-b border-white/5 px-4 py-2.5">
        <div className="flex items-center gap-2 text-sm font-medium text-slate-200">
          <Terminal className="h-4 w-4 text-brand-400" />
          实时控制台
        </div>
        <div className="flex items-center gap-3">
          <span className={`chip ${connected ? "text-accent-400" : "text-slate-400"}`}>
            {connected ? <Wifi className="h-3.5 w-3.5" /> : <WifiOff className="h-3.5 w-3.5" />}
            {connected ? "已连接" : "断开"}
          </span>
          <button onClick={onClear} className="text-slate-400 transition hover:text-rose-300" title="清空">
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </div>
      <div ref={ref} className="code-area flex-1 space-y-0.5 overflow-auto p-4">
        {logs.length === 0 && <p className="text-slate-500">// 暂无日志，运行一次测试以查看实时输出…</p>}
        <AnimatePresence initial={false}>
          {logs.map((e, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: 1, x: 0 }}
              className="flex gap-2"
            >
              <span className="shrink-0 text-slate-600">{new Date(e.ts).toLocaleTimeString()}</span>
              <span className={LEVEL_STYLE[e.level ?? "info"] ?? "text-slate-300"}>
                {e.type === "done" ? `■ ${e.message}` : e.message}
              </span>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </div>
  );
}
