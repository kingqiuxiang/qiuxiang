import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Rocket, Play, Square, Globe, Gauge, CircleCheck, CircleX } from "lucide-react";
import { api, type AutopilotReport, type PageCheck, type PlatformStatus, type ProjectRunState } from "../lib/api";
import { Card, MethodBadge, PassFail, Spinner, StatusDot } from "../components/ui";
import { Console } from "../components/Console";
import type { useEventStream } from "../hooks/useEventStream";

export function Autopilot({
  stream,
  status,
}: {
  stream: ReturnType<typeof useEventStream>;
  status: PlatformStatus | null;
}) {
  const [running, setRunning] = useState(false);
  const [reports, setReports] = useState<AutopilotReport[]>([]);
  const [proj, setProj] = useState<ProjectRunState | null>(null);
  const [pagePath, setPagePath] = useState("/");
  const [pageResult, setPageResult] = useState<PageCheck | null>(null);
  const [pageBusy, setPageBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const refreshProj = () => api.projectState().then(setProj).catch(() => {});
  useEffect(() => {
    refreshProj();
  }, []);

  const runAll = async () => {
    setRunning(true);
    setErr(null);
    setReports([]);
    try {
      const r = await api.autopilotAll({ testPage: pagePath || undefined });
      setReports(r);
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setRunning(false);
    }
  };

  const startProject = async () => {
    setErr(null);
    try {
      await api.projectStart();
      setTimeout(refreshProj, 500);
    } catch (e) {
      setErr((e as Error).message);
    }
  };
  const stopProject = async () => {
    await api.projectStop().then(setProj).catch(() => {});
  };

  const testPage = async () => {
    setPageBusy(true);
    setErr(null);
    try {
      setPageResult(await api.testPage(pagePath));
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setPageBusy(false);
    }
  };

  const passed = reports.filter((r) => r.passed).length;

  return (
    <div className="grid h-full grid-cols-12 gap-5">
      <div className="col-span-7 space-y-5 overflow-auto pr-1">
        {/* 控制面板 */}
        <Card>
          <div className="flex items-start gap-4">
            <div className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-violet-500 to-fuchsia-500 text-white shadow-glow">
              <Rocket className="h-6 w-6" />
            </div>
            <div className="flex-1">
              <h3 className="font-semibold text-white">AI 自动测试流水线</h3>
              <p className="mt-1 text-sm text-slate-400">
                写完接口后，AI 自动读取定义 → 依据项目代码填参 → 发起接口请求 → 校验响应 → 可选页面冒烟。
              </p>
              <div className="mt-4 flex flex-wrap gap-3">
                <button onClick={runAll} disabled={running} className="btn-primary">
                  {running ? <Spinner /> : <Play className="h-4 w-4" />} 一键测试全部接口
                </button>
              </div>
            </div>
          </div>
          {err && <p className="mt-3 text-sm text-rose-300">{err}</p>}
        </Card>

        {/* 项目启动 */}
        <Card>
          <div className="mb-3 flex items-center justify-between">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-200">
              <Gauge className="h-4 w-4 text-brand-400" /> 项目快捷启动
            </h3>
            <span className="flex items-center gap-2 text-xs text-slate-400">
              <StatusDot ok={!!proj?.running} pulse={!!proj?.running} />
              {proj?.running ? (proj.ready ? "运行中 · 已就绪" : "启动中…") : "未运行"}
            </span>
          </div>
          <p className="mb-3 text-xs text-slate-500">
            命令：<code className="text-slate-300">{status?.project.startCommand || "（未配置 PROJECT_START_COMMAND）"}</code>
          </p>
          <div className="flex gap-3">
            <button onClick={startProject} disabled={!status?.project.startCommand || proj?.running} className="btn-accent">
              <Play className="h-4 w-4" /> 启动项目
            </button>
            <button onClick={stopProject} disabled={!proj?.running} className="btn-ghost">
              <Square className="h-4 w-4" /> 停止
            </button>
          </div>
        </Card>

        {/* 前端页面测试 */}
        <Card>
          <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-200">
            <Globe className="h-4 w-4 text-brand-400" /> 前端页面冒烟测试
          </h3>
          <div className="flex gap-2">
            <input value={pagePath} onChange={(e) => setPagePath(e.target.value)} placeholder="/ 或 /login" className="input" />
            <button onClick={testPage} disabled={pageBusy} className="btn-primary shrink-0">
              {pageBusy ? <Spinner /> : <Play className="h-4 w-4" />} 测试
            </button>
          </div>
          <AnimatePresence>
            {pageResult && (
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="mt-4">
                <div className="flex flex-wrap items-center gap-3 text-sm">
                  <PassFail passed={pageResult.ok} />
                  <code className="text-xs text-slate-400">{pageResult.url}</code>
                  {pageResult.status && <span className="chip text-slate-300">HTTP {pageResult.status}</span>}
                  <span className="chip text-slate-400">{pageResult.engine}</span>
                  <span className="text-xs text-slate-500">{pageResult.durationMs}ms</span>
                </div>
                {pageResult.title && <p className="mt-2 text-sm text-slate-300">标题：{pageResult.title}</p>}
                {pageResult.detectedErrors.length > 0 && (
                  <ul className="mt-2 space-y-1 text-xs text-rose-300">
                    {pageResult.detectedErrors.map((x, i) => <li key={i}>• {x}</li>)}
                  </ul>
                )}
                {pageResult.detail && <p className="mt-2 text-xs text-slate-500">{pageResult.detail}</p>}
                {pageResult.screenshot && (
                  <img src={pageResult.screenshot} alt="页面截图" className="mt-3 rounded-xl border border-white/5" />
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </Card>

        {/* 报告 */}
        {reports.length > 0 && (
          <Card>
            <div className="mb-3 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-200">测试报告</h3>
              <div className="flex items-center gap-3 text-sm">
                <span className="flex items-center gap-1 text-accent-400"><CircleCheck className="h-4 w-4" />{passed}</span>
                <span className="flex items-center gap-1 text-rose-300"><CircleX className="h-4 w-4" />{reports.length - passed}</span>
              </div>
            </div>
            <div className="space-y-2">
              {reports.map((r) => (
                <div key={r.runId} className="flex items-center gap-3 rounded-xl border border-white/5 px-3 py-2">
                  <MethodBadge method={r.interface.method} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm text-slate-200">{r.interface.title}</p>
                    <p className="truncate text-[11px] text-slate-500">
                      {r.interface.path} · 填参:{r.fill.source} · 证据:{r.fill.evidenceCount}
                    </p>
                  </div>
                  {r.test.response && <span className="text-xs text-slate-500">{r.test.response.status}</span>}
                  <PassFail passed={r.passed} />
                </div>
              ))}
            </div>
          </Card>
        )}
      </div>

      <div className="col-span-5 h-full">
        <Console events={stream.events} connected={stream.connected} onClear={stream.clear} />
      </div>
    </div>
  );
}
