import { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  Play,
  Square,
  Activity,
  Terminal,
  FolderTree,
  Search,
  HeartPulse,
  FileCode,
} from "lucide-react";
import { api } from "../api";
import { Badge, Button, Card, Empty, Spinner, cx } from "../components/ui";
import type { RunnerStatus } from "../types";

export function Project() {
  return (
    <div className="grid lg:grid-cols-2 gap-5 h-full">
      <Runner />
      <CodeBrowser />
    </div>
  );
}

function Runner() {
  const [status, setStatus] = useState<RunnerStatus | null>(null);
  const [health, setHealth] = useState<{ ok: boolean; status?: number; message: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const logRef = useRef<HTMLDivElement>(null);

  const poll = async () => {
    try {
      setStatus(await api.runnerStatus());
    } catch {
      /* ignore */
    }
  };

  useEffect(() => {
    poll();
    const t = setInterval(poll, 1500);
    return () => clearInterval(t);
  }, []);

  useEffect(() => {
    logRef.current?.scrollTo(0, logRef.current.scrollHeight);
  }, [status?.logs.length]);

  const start = async () => {
    setBusy(true);
    try {
      await api.runnerStart();
      await poll();
    } finally {
      setBusy(false);
    }
  };
  const stop = async () => {
    setBusy(true);
    try {
      await api.runnerStop();
      await poll();
    } finally {
      setBusy(false);
    }
  };
  const checkHealth = async () => setHealth(await api.runnerHealth());

  const running = status?.status === "running";
  const tone =
    status?.status === "running" ? "ok" : status?.status === "error" ? "danger" : status?.status === "starting" ? "warn" : "default";

  return (
    <Card className="p-5 flex flex-col">
      <div className="flex items-center gap-3 mb-4">
        <div className="size-10 rounded-xl bg-gradient-to-br from-indigo-500/30 to-cyan-400/20 grid place-items-center">
          <Activity className="text-cyan-300" size={19} />
        </div>
        <div>
          <div className="font-semibold">AI 快捷启动</div>
          <div className="text-xs text-slate-400">一键拉起开发环境并健康检查</div>
        </div>
        <Badge tone={tone as any}>{status?.status ?? "—"}</Badge>
      </div>

      <div className="flex gap-2 mb-3 flex-wrap">
        <Button onClick={start} loading={busy && !running} disabled={running}>
          <Play size={15} /> 启动项目
        </Button>
        <Button variant="danger" onClick={stop} disabled={!running}>
          <Square size={14} /> 停止
        </Button>
        <Button variant="ghost" onClick={checkHealth}>
          <HeartPulse size={15} /> 健康检查
        </Button>
      </div>

      {status?.command && (
        <div className="text-[11px] font-mono text-slate-500 mb-2">$ {status.command}</div>
      )}
      {health && (
        <div className={cx("text-xs mb-2 flex items-center gap-1.5", health.ok ? "text-emerald-300" : "text-rose-300")}>
          <HeartPulse size={12} /> {health.message} {health.status ? `(${health.status})` : ""}
        </div>
      )}

      <div className="flex items-center gap-2 text-[11px] uppercase tracking-wider text-slate-500 mb-1.5">
        <Terminal size={13} /> 实时日志
      </div>
      <div
        ref={logRef}
        className="flex-1 min-h-[260px] max-h-[420px] overflow-y-auto bg-black/40 rounded-xl border border-white/5 p-3 font-mono text-[11px] leading-relaxed"
      >
        {!status?.logs.length && (
          <div className="text-slate-600">尚无日志。点击「启动项目」运行配置的启动命令。</div>
        )}
        {status?.logs.map((l, i) => (
          <div
            key={i}
            className={cx(
              "whitespace-pre-wrap break-all",
              l.stream === "stderr" ? "text-amber-300/90" : l.stream === "system" ? "text-cyan-300/80" : "text-slate-300"
            )}
          >
            {l.line}
          </div>
        ))}
      </div>
    </Card>
  );
}

function CodeBrowser() {
  const [scan, setScan] = useState<Awaited<ReturnType<typeof api.codeScan>> | null>(null);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState("");
  const [matches, setMatches] = useState<{ rel: string; line: number; text: string }[]>([]);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    api.codeScan().then(setScan).finally(() => setLoading(false));
  }, []);

  const doSearch = async () => {
    if (!q.trim()) return;
    setSearching(true);
    try {
      const res = await api.codeSearch(q);
      setMatches(res.matches);
    } finally {
      setSearching(false);
    }
  };

  return (
    <Card className="p-5 flex flex-col">
      <div className="flex items-center gap-3 mb-4">
        <div className="size-10 rounded-xl bg-gradient-to-br from-indigo-500/30 to-cyan-400/20 grid place-items-center">
          <FolderTree className="text-cyan-300" size={19} />
        </div>
        <div>
          <div className="font-semibold">项目代码基准</div>
          <div className="text-xs text-slate-400">AI 填参与测试的事实依据</div>
        </div>
      </div>

      {loading && <Spinner />}
      {!loading && !scan?.available && (
        <Empty
          icon={<FileCode size={32} />}
          title="未挂载项目代码"
          hint="在「设置」中填写项目根路径 (PROJECT_ROOT),即可让 AI 以真实代码为基准生成参数。"
        />
      )}

      {scan?.available && (
        <>
          <div className="grid grid-cols-3 gap-2 mb-4">
            <Stat label="文件数" value={String(scan.total ?? 0)} />
            <Stat label="类型" value={String(Object.keys(scan.byExt || {}).length)} />
            <Stat label="状态" value={scan.truncated ? "已截断" : "完整"} />
          </div>
          <div className="flex flex-wrap gap-1.5 mb-4">
            {Object.entries(scan.byExt || {})
              .sort((a, b) => b[1] - a[1])
              .slice(0, 8)
              .map(([ext, n]) => (
                <Badge key={ext}>
                  {ext} · {n}
                </Badge>
              ))}
          </div>

          <div className="flex items-center gap-2 bg-black/30 rounded-lg px-3 py-2 border border-white/10 mb-3">
            <Search size={15} className="text-slate-500" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && doSearch()}
              placeholder="在代码中搜索 (函数名 / 路由 / 关键字)"
              className="bg-transparent outline-none text-sm flex-1 text-slate-200 placeholder:text-slate-600"
            />
            <Button size="sm" onClick={doSearch} loading={searching}>
              搜索
            </Button>
          </div>

          <div className="flex-1 max-h-[320px] overflow-y-auto space-y-1.5">
            {matches.map((m, i) => (
              <motion.div
                key={i}
                initial={{ opacity: 0, x: -6 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.02 }}
                className="rounded-lg bg-white/[0.03] border border-white/5 px-3 py-2"
              >
                <div className="text-[11px] text-cyan-300/80 font-mono">
                  {m.rel}:{m.line}
                </div>
                <div className="text-[11px] text-slate-400 font-mono truncate">{m.text}</div>
              </motion.div>
            ))}
            {q && !matches.length && !searching && (
              <div className="text-sm text-slate-500 text-center py-6">无匹配结果</div>
            )}
          </div>
        </>
      )}
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-white/[0.03] border border-white/5 px-3 py-2.5">
      <div className="text-[10px] text-slate-500">{label}</div>
      <div className="text-lg font-bold text-slate-100">{value}</div>
    </div>
  );
}
