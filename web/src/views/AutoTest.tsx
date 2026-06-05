import { AnimatePresence, motion } from "framer-motion";
import { useEffect, useState } from "react";
import {
  FlaskConical,
  Play,
  Sparkles,
  CheckCircle2,
  XCircle,
  ChevronDown,
  Wand2,
  ShieldAlert,
  ListChecks,
} from "lucide-react";
import { api } from "../api";
import { Badge, Button, Card, Empty, JsonView, MethodBadge, Spinner, cx } from "../components/ui";
import type { ApiInterface, TestCase, TestCaseResult } from "../types";

const kindMeta: Record<string, { label: string; tone: any; icon: any }> = {
  happy: { label: "正常", tone: "ok", icon: CheckCircle2 },
  validation: { label: "参数校验", tone: "warn", icon: ListChecks },
  auth: { label: "鉴权", tone: "info", icon: ShieldAlert },
  boundary: { label: "边界", tone: "default", icon: Wand2 },
};

export function AutoTest({ initialId }: { initialId: string | null }) {
  const [items, setItems] = useState<ApiInterface[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(initialId);
  const [plan, setPlan] = useState<TestCase[] | null>(null);
  const [planning, setPlanning] = useState(false);
  const [running, setRunning] = useState(false);
  const [results, setResults] = useState<TestCaseResult[] | null>(null);
  const [aiConfigured, setAiConfigured] = useState(true);

  useEffect(() => {
    api.interfaces().then((res) => {
      setItems(res.items);
      if (!selectedId && res.items[0]) setSelectedId(res.items[0].id);
    });
  }, []);

  useEffect(() => {
    if (initialId) setSelectedId(initialId);
  }, [initialId]);

  const selected = items.find((i) => i.id === selectedId);

  const genPlan = async () => {
    if (!selectedId) return;
    setPlanning(true);
    setPlan(null);
    setResults(null);
    try {
      const res = await api.plan(selectedId);
      setPlan(res.plan);
      setAiConfigured(res.aiConfigured);
    } finally {
      setPlanning(false);
    }
  };

  const runAll = async () => {
    if (!selectedId) return;
    setRunning(true);
    setResults(null);
    try {
      const res = await api.autorun(selectedId, plan || undefined);
      setResults(res.results);
    } finally {
      setRunning(false);
    }
  };

  const passed = results?.filter((r) => r.result.ok).length ?? 0;

  return (
    <div className="space-y-5">
      <Card className="p-5">
        <div className="flex items-center gap-4 flex-wrap">
          <div className="size-11 rounded-xl bg-gradient-to-br from-indigo-500/30 to-cyan-400/20 grid place-items-center">
            <FlaskConical className="text-cyan-300" size={20} />
          </div>
          <div className="flex-1 min-w-[200px]">
            <div className="font-semibold">写完接口,让 AI 来测</div>
            <div className="text-sm text-slate-400">
              选择接口 → AI 设计用例(正常/校验/鉴权/边界)→ 一键执行并断言。
            </div>
          </div>

          <div className="relative">
            <select
              value={selectedId || ""}
              onChange={(e) => {
                setSelectedId(e.target.value);
                setPlan(null);
                setResults(null);
              }}
              className="appearance-none bg-black/30 border border-white/10 rounded-xl pl-3 pr-9 py-2.5 text-sm outline-none focus:border-indigo-400/50 min-w-[220px]"
            >
              {items.map((i) => (
                <option key={i.id} value={i.id} className="bg-slate-900">
                  {i.method} · {i.title}
                </option>
              ))}
            </select>
            <ChevronDown size={15} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
          </div>

          <Button onClick={genPlan} loading={planning} variant="soft">
            <Sparkles size={15} /> 生成测试方案
          </Button>
          <Button onClick={runAll} loading={running} disabled={!selectedId}>
            <Play size={15} /> 一键执行
          </Button>
        </div>
        {selected && (
          <div className="mt-3 flex items-center gap-2 text-sm">
            <MethodBadge method={selected.method} />
            <code className="text-cyan-300/90 font-mono text-xs">{selected.path}</code>
            {!aiConfigured && plan && <Badge tone="warn">AI 未配置 · 使用启发式方案</Badge>}
          </div>
        )}
      </Card>

      {(planning || running) && (
        <Card className="p-5">
          <Spinner label={planning ? "AI 正在设计测试用例…" : "正在执行测试用例…"} />
        </Card>
      )}

      <AnimatePresence>
        {results && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
            <Card className="p-5 mb-5">
              <div className="flex items-center justify-between">
                <div className="font-semibold">测试结果</div>
                <div className="flex items-center gap-3">
                  <Badge tone={passed === results.length ? "ok" : passed === 0 ? "danger" : "warn"}>
                    {passed}/{results.length} 通过
                  </Badge>
                  <div className="w-40 h-2 rounded-full bg-white/10 overflow-hidden">
                    <motion.div
                      className="h-full bg-gradient-to-r from-emerald-400 to-cyan-400"
                      initial={{ width: 0 }}
                      animate={{ width: `${(passed / results.length) * 100}%` }}
                    />
                  </div>
                </div>
              </div>
            </Card>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="space-y-3">
        {results
          ? results.map((r, i) => <ResultCard key={i} r={r} index={i} />)
          : plan?.map((c, i) => <PlanCard key={i} c={c} index={i} />)}
      </div>

      {!plan && !results && !planning && (
        <Empty
          icon={<FlaskConical size={36} />}
          title="尚无测试方案"
          hint="选择一个接口并点击「生成测试方案」,或直接「一键执行」由 AI 自动设计并运行。"
        />
      )}
    </div>
  );
}

function PlanCard({ c, index }: { c: TestCase; index: number }) {
  const meta = kindMeta[c.kind] || kindMeta.happy;
  const Icon = meta.icon;
  return (
    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.05 }}>
      <Card className="p-4">
        <div className="flex items-center gap-3">
          <Icon size={16} className="text-cyan-300" />
          <span className="font-medium text-sm">{c.name}</span>
          <Badge tone={meta.tone}>{meta.label}</Badge>
          <span className="ml-auto text-xs text-slate-500">{c.assertions.length} 条断言</span>
        </div>
      </Card>
    </motion.div>
  );
}

function ResultCard({ r, index }: { r: TestCaseResult; index: number }) {
  const [open, setOpen] = useState(false);
  const meta = kindMeta[r.kind] || kindMeta.happy;
  const ok = r.result.ok;
  return (
    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.05 }}>
      <Card className={cx("overflow-hidden border", ok ? "border-emerald-400/20" : "border-rose-400/20")}>
        <button onClick={() => setOpen((o) => !o)} className="w-full flex items-center gap-3 p-4 text-left">
          {ok ? <CheckCircle2 size={18} className="text-emerald-400" /> : <XCircle size={18} className="text-rose-400" />}
          <span className="font-medium text-sm">{r.name}</span>
          <Badge tone={meta.tone}>{meta.label}</Badge>
          <div className="ml-auto flex items-center gap-2 text-xs text-slate-400">
            {r.result.status != null && <Badge tone={ok ? "ok" : "danger"}>{r.result.status}</Badge>}
            {r.result.error && <span className="text-rose-300">{r.result.error}</span>}
            <span>{r.result.durationMs}ms</span>
            <ChevronDown size={15} className={cx("transition-transform", open && "rotate-180")} />
          </div>
        </button>
        <AnimatePresence>
          {open && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: "auto", opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="border-t border-white/5"
            >
              <div className="p-4 grid md:grid-cols-2 gap-4">
                <div>
                  <div className="text-[11px] uppercase tracking-wider text-slate-500 mb-2">断言</div>
                  <div className="space-y-1.5">
                    {r.assertions.length === 0 && <div className="text-xs text-slate-500">无断言</div>}
                    {r.result.assertions.map((a, i) => (
                      <div key={i} className="flex items-start gap-2 text-xs">
                        {a.passed ? (
                          <CheckCircle2 size={13} className="text-emerald-400 mt-0.5 shrink-0" />
                        ) : (
                          <XCircle size={13} className="text-rose-400 mt-0.5 shrink-0" />
                        )}
                        <span className={a.passed ? "text-slate-300" : "text-rose-200"}>{a.message}</span>
                      </div>
                    ))}
                  </div>
                  <div className="text-[11px] uppercase tracking-wider text-slate-500 mt-4 mb-1">请求</div>
                  <div className="text-[11px] font-mono text-slate-400 break-all">
                    {r.result.requestMethod} {r.result.requestUrl}
                  </div>
                </div>
                <div>
                  <div className="text-[11px] uppercase tracking-wider text-slate-500 mb-2">响应</div>
                  <JsonView data={r.result.responseBody ?? r.result.error ?? "(无)"} className="max-h-64" />
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </Card>
    </motion.div>
  );
}
