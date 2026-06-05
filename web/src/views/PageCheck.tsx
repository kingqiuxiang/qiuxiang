import { useState } from "react";
import { motion } from "framer-motion";
import { Globe, ScanSearch, CheckCircle2, XCircle, Link2, FileCode2, Clock } from "lucide-react";
import { api } from "../api";
import { Badge, Button, Card, Empty, cx } from "../components/ui";
import type { PageCheck as PageCheckResult } from "../types";

export function PageCheck() {
  const [url, setUrl] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<PageCheckResult | null>(null);

  const run = async () => {
    setLoading(true);
    setResult(null);
    try {
      setResult(await api.pageCheck(url || undefined));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-5">
      <Card className="p-5">
        <div className="flex items-center gap-3 mb-4">
          <div className="size-10 rounded-xl bg-gradient-to-br from-indigo-500/30 to-cyan-400/20 grid place-items-center">
            <Globe className="text-cyan-300" size={19} />
          </div>
          <div>
            <div className="font-semibold">前端页面巡检</div>
            <div className="text-xs text-slate-400">访问开发环境前端页面,验证可达性与渲染要点</div>
          </div>
        </div>
        <div className="flex gap-2">
          <input
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && run()}
            placeholder="留空使用配置的 devEnv.webBaseUrl,或输入页面地址"
            className="flex-1 bg-black/30 border border-white/10 rounded-xl px-3.5 py-2.5 text-sm outline-none focus:border-indigo-400/50 font-mono"
          />
          <Button onClick={run} loading={loading}>
            <ScanSearch size={15} /> 巡检
          </Button>
        </div>
      </Card>

      {result && (
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <Card className={cx("p-5 border", result.ok ? "border-emerald-400/20" : "border-rose-400/20")}>
            <div className="flex items-center gap-3 mb-4">
              {result.ok ? (
                <CheckCircle2 className="text-emerald-400" size={22} />
              ) : (
                <XCircle className="text-rose-400" size={22} />
              )}
              <div className="flex-1 min-w-0">
                <div className="font-medium truncate">{result.title || "(无标题)"}</div>
                <div className="text-xs text-cyan-300/80 font-mono truncate">{result.url}</div>
              </div>
              <Badge tone={result.ok ? "ok" : "danger"}>{result.error ? "不可达" : result.status}</Badge>
            </div>

            {result.error ? (
              <div className="rounded-xl bg-rose-500/10 border border-rose-400/30 p-3 text-sm text-rose-200">
                {result.error}
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                  <Metric icon={<Clock size={14} />} label="耗时" value={`${result.durationMs}ms`} />
                  <Metric icon={<FileCode2 size={14} />} label="大小" value={`${((result.sizeBytes || 0) / 1024).toFixed(1)}KB`} />
                  <Metric icon={<Link2 size={14} />} label="链接" value={String(result.links ?? 0)} />
                  <Metric icon={<FileCode2 size={14} />} label="脚本" value={String(result.scripts ?? 0)} />
                </div>
                <div className="mt-4 flex flex-wrap gap-2">
                  <Badge tone={result.hasRootEl ? "ok" : "warn"}>
                    {result.hasRootEl ? "检测到 SPA 挂载点" : "未检测到挂载点"}
                  </Badge>
                  <Badge>{result.contentType?.split(";")[0] || "unknown"}</Badge>
                  {result.errorHints?.map((h) => (
                    <Badge key={h} tone="danger">
                      页面错误信号: {h}
                    </Badge>
                  ))}
                  {!result.errorHints?.length && <Badge tone="ok">无明显错误信号</Badge>}
                </div>
              </>
            )}
          </Card>
        </motion.div>
      )}

      {!result && !loading && (
        <Empty
          icon={<Globe size={36} />}
          title="尚未巡检"
          hint="输入开发环境前端地址并点击「巡检」,系统将访问该页面并分析标题、挂载点、资源与错误信号。"
        />
      )}
    </div>
  );
}

function Metric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="rounded-xl bg-white/[0.03] border border-white/5 px-3 py-2.5">
      <div className="flex items-center gap-1.5 text-[10px] text-slate-500">
        {icon} {label}
      </div>
      <div className="text-lg font-bold text-slate-100 mt-0.5">{value}</div>
    </div>
  );
}
