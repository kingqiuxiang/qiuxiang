import { useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Search, Sparkles, Play, FileCode2, Wand2 } from "lucide-react";
import { api, type ApiInterface, type FilledParams, type TestResult } from "../lib/api";
import { Card, JsonBlock, MethodBadge, PassFail, Spinner } from "../components/ui";
import { Console } from "../components/Console";
import type { useEventStream } from "../hooks/useEventStream";

export function Interfaces({ stream }: { stream: ReturnType<typeof useEventStream> }) {
  const [list, setList] = useState<ApiInterface[]>([]);
  const [q, setQ] = useState("");
  const [activeId, setActiveId] = useState<string | null>(null);

  useEffect(() => {
    api.interfaces().then((r) => {
      setList(r.data);
      if (r.data[0]) setActiveId(r.data[0].id);
    }).catch(() => {});
  }, []);

  const filtered = useMemo(
    () =>
      list.filter((i) =>
        (i.title + i.path + (i.catName ?? "")).toLowerCase().includes(q.toLowerCase()),
      ),
    [list, q],
  );

  const grouped = useMemo(() => {
    const m = new Map<string, ApiInterface[]>();
    for (const i of filtered) {
      const key = i.catName ?? "默认分类";
      if (!m.has(key)) m.set(key, []);
      m.get(key)!.push(i);
    }
    return [...m.entries()];
  }, [filtered]);

  return (
    <div className="grid h-full grid-cols-12 gap-5">
      {/* 接口列表 */}
      <div className="col-span-3 flex flex-col gap-3">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="搜索接口…"
            className="input pl-9"
          />
        </div>
        <div className="card flex-1 space-y-3 overflow-auto p-3">
          {grouped.map(([cat, items]) => (
            <div key={cat}>
              <p className="px-2 py-1 text-[11px] font-semibold uppercase tracking-wide text-slate-500">{cat}</p>
              {items.map((i) => (
                <button
                  key={i.id}
                  onClick={() => setActiveId(i.id)}
                  className={`mb-1 flex w-full items-center gap-2 rounded-xl px-2.5 py-2 text-left transition ${
                    activeId === i.id ? "bg-brand-500/15 ring-1 ring-brand-400/30" : "hover:bg-white/5"
                  }`}
                >
                  <MethodBadge method={i.method} />
                  <div className="min-w-0">
                    <p className="truncate text-sm text-slate-200">{i.title}</p>
                    <p className="truncate text-[11px] text-slate-500">{i.path}</p>
                  </div>
                </button>
              ))}
            </div>
          ))}
          {grouped.length === 0 && <p className="p-4 text-sm text-slate-500">无匹配接口</p>}
        </div>
      </div>

      {/* 详情工作区 */}
      <div className="col-span-6 overflow-auto pr-1">
        {activeId ? <Detail id={activeId} /> : <EmptyDetail />}
      </div>

      {/* 控制台 */}
      <div className="col-span-3 h-full">
        <Console events={stream.events} connected={stream.connected} onClear={stream.clear} />
      </div>
    </div>
  );
}

function EmptyDetail() {
  return (
    <Card className="grid h-full place-items-center text-slate-500">
      <div className="text-center">
        <FileCode2 className="mx-auto mb-3 h-10 w-10 opacity-40" />
        选择左侧接口开始
      </div>
    </Card>
  );
}

function Detail({ id }: { id: string }) {
  const [iface, setIface] = useState<ApiInterface | null>(null);
  const [filled, setFilled] = useState<FilledParams | null>(null);
  const [filling, setFilling] = useState(false);
  const [testing, setTesting] = useState(false);
  const [result, setResult] = useState<TestResult | null>(null);
  const [draft, setDraft] = useState("");
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    setIface(null);
    setFilled(null);
    setResult(null);
    setDraft("");
    setErr(null);
    api.interface(id).then(setIface).catch((e) => setErr(e.message));
  }, [id]);

  const onFill = async () => {
    setFilling(true);
    setErr(null);
    try {
      const f = await api.fill(id);
      setFilled(f);
      setDraft(JSON.stringify({ query: f.query, headers: f.headers, path: f.path, body: f.body }, null, 2));
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setFilling(false);
    }
  };

  const onTest = async () => {
    setTesting(true);
    setErr(null);
    setResult(null);
    try {
      let useFilled = filled ?? undefined;
      if (draft) {
        try {
          const parsed = JSON.parse(draft);
          useFilled = { ...(filled ?? { source: "heuristic" }), ...parsed } as FilledParams;
        } catch {
          throw new Error("参数 JSON 格式有误，请检查");
        }
      }
      const r = await api.test(id, { filled: useFilled });
      setResult(r);
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setTesting(false);
    }
  };

  if (!iface) {
    return (
      <Card className="grid h-40 place-items-center">
        {err ? <span className="text-rose-300">{err}</span> : <Spinner />}
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <Card>
        <div className="flex items-center gap-3">
          <MethodBadge method={iface.method} />
          <h2 className="text-lg font-semibold text-white">{iface.title}</h2>
        </div>
        <code className="mt-2 block rounded-lg bg-ink-950/60 px-3 py-2 text-sm text-brand-400">{iface.path}</code>
        {iface.desc && <p className="mt-2 text-sm text-slate-400">{iface.desc}</p>}

        <div className="mt-4 flex flex-wrap gap-3">
          <button onClick={onFill} disabled={filling} className="btn-primary">
            {filling ? <Spinner /> : <Wand2 className="h-4 w-4" />} AI 一键填充参数
          </button>
          <button onClick={onTest} disabled={testing} className="btn-accent">
            {testing ? <Spinner className="border-ink-950/40 border-t-ink-950" /> : <Play className="h-4 w-4" />} 运行测试
          </button>
        </div>
        {err && <p className="mt-3 text-sm text-rose-300">{err}</p>}
      </Card>

      {/* 参数表 */}
      <Card>
        <h3 className="mb-3 text-sm font-semibold text-slate-200">参数定义</h3>
        {iface.params.length === 0 && !iface.reqBodySchema && (
          <p className="text-sm text-slate-500">该接口无显式参数。</p>
        )}
        {iface.params.length > 0 && (
          <div className="overflow-hidden rounded-xl border border-white/5">
            <table className="w-full text-sm">
              <thead className="bg-white/5 text-left text-xs text-slate-400">
                <tr>
                  <th className="px-3 py-2">参数</th>
                  <th className="px-3 py-2">位置</th>
                  <th className="px-3 py-2">类型</th>
                  <th className="px-3 py-2">必填</th>
                  <th className="px-3 py-2">说明</th>
                </tr>
              </thead>
              <tbody>
                {iface.params.map((p, idx) => (
                  <tr key={idx} className="border-t border-white/5">
                    <td className="px-3 py-2 font-mono text-brand-400">{p.name}</td>
                    <td className="px-3 py-2 text-slate-400">{p.in}</td>
                    <td className="px-3 py-2 text-slate-400">{p.type}</td>
                    <td className="px-3 py-2">{p.required ? <span className="text-rose-300">是</span> : <span className="text-slate-500">否</span>}</td>
                    <td className="px-3 py-2 text-slate-400">{p.desc}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {iface.reqBodySchema != null && (
          <details className="mt-3">
            <summary className="cursor-pointer text-xs text-slate-400">请求体 Schema</summary>
            <JsonBlock value={iface.reqBodySchema} className="mt-2 max-h-52" />
          </details>
        )}
      </Card>

      {/* AI 填充结果 */}
      <AnimatePresence>
        {filled && (
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
            <Card>
              <div className="mb-3 flex items-center justify-between">
                <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-200">
                  <Sparkles className="h-4 w-4 text-brand-400" /> AI 填充参数（可编辑）
                </h3>
                <span className={`chip ${filled.source === "ai" ? "bg-brand-500/15 text-brand-400" : "bg-white/5 text-slate-400"}`}>
                  {filled.source === "ai" ? "大模型生成" : "启发式生成"}
                </span>
              </div>
              {filled.rationale && (
                <p className="mb-3 rounded-xl bg-brand-500/10 px-3 py-2 text-xs text-brand-300">💡 {filled.rationale}</p>
              )}
              <textarea
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                spellCheck={false}
                className="input code-area h-56 resize-y"
              />
              {filled.evidence && filled.evidence.length > 0 && (
                <details className="mt-3">
                  <summary className="cursor-pointer text-xs text-slate-400">
                    项目代码证据（{filled.evidence.length} 处）—— 以项目代码为基准
                  </summary>
                  <div className="mt-2 space-y-2">
                    {filled.evidence.map((e, i) => (
                      <div key={i}>
                        <p className="font-mono text-[11px] text-accent-400">{e.file}:{e.line}</p>
                        <JsonBlock value={e.snippet} className="mt-1" />
                      </div>
                    ))}
                  </div>
                </details>
              )}
            </Card>
          </motion.div>
        )}
      </AnimatePresence>

      {/* 测试结果 */}
      <AnimatePresence>{result && <ResultCard result={result} />}</AnimatePresence>
    </div>
  );
}

function ResultCard({ result }: { result: TestResult }) {
  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
      <Card>
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-slate-200">测试结果</h3>
          <PassFail passed={result.passed} label={result.passed ? "全部通过" : "存在失败"} />
        </div>

        <div className="mb-3 flex flex-wrap items-center gap-2 text-xs text-slate-400">
          <MethodBadge method={result.request.method} />
          <code className="truncate">{result.request.url}</code>
          {result.response && (
            <>
              <span className={`chip ${result.response.status < 400 ? "text-accent-400" : "text-rose-300"}`}>
                {result.response.status}
              </span>
              <span>{result.response.durationMs}ms</span>
            </>
          )}
        </div>

        {result.error && <p className="mb-3 rounded-xl bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{result.error}</p>}

        <div className="space-y-1.5">
          {result.assertions.map((a, i) => (
            <div key={i} className="flex items-center gap-2 text-sm">
              <span className={a.passed ? "text-accent-400" : "text-rose-300"}>{a.passed ? "✓" : "✗"}</span>
              <span className="text-slate-300">{a.description}</span>
              {a.detail && <span className="text-xs text-slate-500">— {a.detail}</span>}
            </div>
          ))}
        </div>

        {result.response && (
          <details className="mt-3" open>
            <summary className="cursor-pointer text-xs text-slate-400">响应体</summary>
            <JsonBlock value={result.response.body} className="mt-2 max-h-72" />
          </details>
        )}
      </Card>
    </motion.div>
  );
}
