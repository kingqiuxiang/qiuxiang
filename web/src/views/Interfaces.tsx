import { AnimatePresence, motion } from "framer-motion";
import { useEffect, useMemo, useState } from "react";
import {
  Search,
  Wand2,
  Send,
  Sparkles,
  Code2,
  Info,
  Clock,
  CheckCircle2,
  XCircle,
} from "lucide-react";
import { api } from "../api";
import { Badge, Button, Card, Empty, JsonView, MethodBadge, Spinner, cx } from "../components/ui";
import type { ApiInterface, FilledParams, TestResult } from "../types";

export function Interfaces({ onAutoTest }: { onAutoTest: (id: string) => void }) {
  const [items, setItems] = useState<ApiInterface[]>([]);
  const [statusMsg, setStatusMsg] = useState<{ mode: string; message: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    api
      .interfaces()
      .then((res) => {
        setItems(res.items);
        setStatusMsg(res.status);
        if (res.items[0]) setSelectedId(res.items[0].id);
      })
      .finally(() => setLoading(false));
  }, []);

  const grouped = useMemo(() => {
    const filtered = items.filter(
      (i) =>
        i.title.toLowerCase().includes(q.toLowerCase()) ||
        i.path.toLowerCase().includes(q.toLowerCase())
    );
    const map: Record<string, ApiInterface[]> = {};
    for (const it of filtered) {
      const cat = it.catName || "未分组";
      (map[cat] ||= []).push(it);
    }
    return map;
  }, [items, q]);

  const selected = items.find((i) => i.id === selectedId) || null;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[320px_1fr] gap-5 h-full">
      <Card className="flex flex-col overflow-hidden">
        <div className="p-3 border-b border-white/5">
          <div className="flex items-center gap-2 bg-black/30 rounded-lg px-3 py-2 border border-white/10">
            <Search size={15} className="text-slate-500" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="搜索接口 / 路径"
              className="bg-transparent outline-none text-sm flex-1 text-slate-200 placeholder:text-slate-600"
            />
          </div>
          {statusMsg && (
            <div className="mt-2 flex items-start gap-1.5 text-[11px] text-slate-400">
              <Info size={12} className="mt-0.5 shrink-0 text-indigo-300" />
              <span>{statusMsg.message}</span>
            </div>
          )}
        </div>
        <div className="flex-1 overflow-y-auto p-2">
          {loading && <div className="p-4"><Spinner /></div>}
          {Object.entries(grouped).map(([cat, list]) => (
            <div key={cat} className="mb-3">
              <div className="px-2 py-1 text-[10px] uppercase tracking-wider text-slate-500">{cat}</div>
              {list.map((it) => (
                <button
                  key={it.id}
                  onClick={() => setSelectedId(it.id)}
                  className={cx(
                    "w-full text-left px-2.5 py-2 rounded-lg mb-0.5 transition-colors flex items-center gap-2",
                    selectedId === it.id ? "bg-indigo-500/15 border border-indigo-400/30" : "hover:bg-white/5 border border-transparent"
                  )}
                >
                  <MethodBadge method={it.method} />
                  <span className="flex-1 min-w-0">
                    <span className="block text-[13px] text-slate-200 truncate">{it.title}</span>
                    <span className="block text-[11px] text-slate-500 truncate font-mono">{it.path}</span>
                  </span>
                </button>
              ))}
            </div>
          ))}
        </div>
      </Card>

      <div className="overflow-y-auto pr-1">
        <AnimatePresence mode="wait">
          {selected ? (
            <motion.div
              key={selected.id}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
            >
              <Detail iface={selected} onAutoTest={onAutoTest} />
            </motion.div>
          ) : (
            !loading && (
              <Empty icon={<Code2 size={36} />} title="选择一个接口" hint="从左侧列表选择接口以查看详情并进行 AI 填参与测试。" />
            )
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}

function Detail({ iface, onAutoTest }: { iface: ApiInterface; onAutoTest: (id: string) => void }) {
  const [filled, setFilled] = useState<FilledParams | null>(null);
  const [filling, setFilling] = useState(false);
  const [fillMeta, setFillMeta] = useState<{ aiConfigured: boolean; codeGrounded: boolean } | null>(null);

  const [pathParams, setPathParams] = useState<Record<string, string>>({});
  const [queryText, setQueryText] = useState("{}");
  const [headersText, setHeadersText] = useState("{}");
  const [bodyText, setBodyText] = useState("");

  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<TestResult | null>(null);

  useEffect(() => {
    setFilled(null);
    setResult(null);
    setFillMeta(null);
    const pp: Record<string, string> = {};
    iface.reqParams.forEach((p) => (pp[p.name] = ""));
    setPathParams(pp);
    setQueryText("{}");
    setHeadersText("{}");
    setBodyText(iface.reqBodyType === "json" && iface.reqBody ? JSON.stringify(iface.reqBody, null, 2) : "");
  }, [iface.id]);

  const applyFilled = (f: FilledParams) => {
    setFilled(f);
    const pp: Record<string, string> = {};
    for (const p of iface.reqParams) pp[p.name] = String(f.pathParams?.[p.name] ?? "");
    setPathParams(pp);
    setQueryText(JSON.stringify(f.query || {}, null, 2));
    setHeadersText(JSON.stringify(f.headers || {}, null, 2));
    setBodyText(f.body != null ? JSON.stringify(f.body, null, 2) : "");
  };

  const doFill = async (mode: "ai" | "heuristic") => {
    setFilling(true);
    try {
      const res = await api.fill(iface.id, mode);
      applyFilled(res.filled);
      setFillMeta({ aiConfigured: res.aiConfigured, codeGrounded: res.codeGrounded });
    } finally {
      setFilling(false);
    }
  };

  const send = async () => {
    setSending(true);
    setResult(null);
    try {
      const parse = (t: string) => {
        try {
          return t.trim() ? JSON.parse(t) : undefined;
        } catch {
          return undefined;
        }
      };
      const res = await api.runTest({
        method: iface.method,
        path: iface.path,
        pathParams,
        query: parse(queryText),
        headers: parse(headersText),
        body: parse(bodyText),
        bodyType: iface.reqBodyType,
      });
      setResult(res);
    } catch (e: any) {
      setResult({
        ok: false,
        durationMs: 0,
        requestUrl: iface.path,
        requestMethod: iface.method,
        assertions: [],
        error: e.message,
      });
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="space-y-5">
      <Card className="p-5">
        <div className="flex items-start justify-between gap-3 flex-wrap">
          <div>
            <div className="flex items-center gap-2.5">
              <MethodBadge method={iface.method} />
              <h2 className="text-lg font-bold">{iface.title}</h2>
            </div>
            <code className="mt-1.5 block text-sm text-cyan-300/90 font-mono">{iface.path}</code>
          </div>
          <div className="flex gap-2">
            <Button variant="soft" loading={filling} onClick={() => doFill("ai")}>
              <Wand2 size={15} /> AI 一键填参
            </Button>
            <Button variant="ghost" size="md" onClick={() => doFill("heuristic")} disabled={filling}>
              启发式填充
            </Button>
          </div>
        </div>

        {filled && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            className="mt-3 flex items-center gap-2 flex-wrap text-xs"
          >
            <Badge tone={filled.source === "ai" ? "info" : "warn"}>
              <Sparkles size={11} className="inline mr-1" />
              {filled.source === "ai" ? "AI 生成" : "启发式生成"}
            </Badge>
            {fillMeta?.codeGrounded && <Badge tone="ok">已结合项目代码基准</Badge>}
            {fillMeta && !fillMeta.aiConfigured && <Badge tone="warn">AI 未配置 · 已回退</Badge>}
            {filled.notes && <span className="text-slate-400">{filled.notes}</span>}
          </motion.div>
        )}
      </Card>

      <div className="grid lg:grid-cols-2 gap-5">
        <Card className="p-5 space-y-4">
          <div className="text-sm font-semibold text-slate-200">请求参数</div>

          {iface.reqParams.length > 0 && (
            <Field label="Path 参数">
              <div className="space-y-2">
                {iface.reqParams.map((p) => (
                  <div key={p.name} className="flex items-center gap-2">
                    <span className="text-xs font-mono text-slate-400 w-28 shrink-0 truncate" title={p.name}>
                      {p.name}
                      {p.required && <span className="text-rose-400">*</span>}
                    </span>
                    <input
                      value={pathParams[p.name] ?? ""}
                      onChange={(e) => setPathParams((s) => ({ ...s, [p.name]: e.target.value }))}
                      placeholder={p.desc || p.type}
                      className="flex-1 bg-black/30 border border-white/10 rounded-lg px-2.5 py-1.5 text-xs outline-none focus:border-indigo-400/50"
                    />
                  </div>
                ))}
              </div>
            </Field>
          )}

          <Field label="Query">
            <Editor value={queryText} onChange={setQueryText} placeholder="{}" minH={64} />
          </Field>
          <Field label="Headers">
            <Editor value={headersText} onChange={setHeadersText} placeholder="{}" minH={64} />
          </Field>
          {iface.reqBodyType === "json" && (
            <Field label="Body (JSON)">
              <Editor value={bodyText} onChange={setBodyText} placeholder="{}" minH={140} />
            </Field>
          )}

          <div className="flex gap-2 pt-1">
            <Button onClick={send} loading={sending}>
              <Send size={15} /> 发送请求
            </Button>
            <Button variant="ghost" onClick={() => onAutoTest(iface.id)}>
              <Sparkles size={15} /> AI 自动测试
            </Button>
          </div>
        </Card>

        <Card className="p-5 space-y-3 min-h-[200px]">
          <div className="flex items-center justify-between">
            <div className="text-sm font-semibold text-slate-200">响应</div>
            {result && !result.error && (
              <div className="flex items-center gap-2 text-xs">
                <Badge tone={result.ok ? "ok" : "danger"}>
                  {result.ok ? <CheckCircle2 size={11} className="inline mr-1" /> : <XCircle size={11} className="inline mr-1" />}
                  {result.status}
                </Badge>
                <span className="flex items-center gap-1 text-slate-400">
                  <Clock size={11} /> {result.durationMs}ms
                </span>
              </div>
            )}
          </div>
          {sending && <Spinner label="请求开发环境中…" />}
          {!sending && !result && (
            <div className="text-sm text-slate-500 py-8 text-center">
              点击「发送请求」向开发环境
              <br />
              <code className="text-cyan-300/80">apiBaseUrl + path</code> 发起调用
            </div>
          )}
          {result?.error && (
            <div className="rounded-xl bg-rose-500/10 border border-rose-400/30 p-3 text-sm text-rose-200">
              {result.error}
              <div className="text-xs text-rose-300/70 mt-1 font-mono break-all">{result.requestUrl}</div>
            </div>
          )}
          {result && !result.error && (
            <>
              <div className="text-[11px] text-slate-500 font-mono break-all">
                {result.requestMethod} {result.requestUrl}
              </div>
              <JsonView data={result.responseBody} className="max-h-[420px]" />
            </>
          )}
        </Card>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-[11px] uppercase tracking-wider text-slate-500 mb-1.5">{label}</div>
      {children}
    </div>
  );
}

function Editor({
  value,
  onChange,
  placeholder,
  minH = 80,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  minH?: number;
}) {
  const valid = useMemo(() => {
    if (!value.trim()) return true;
    try {
      JSON.parse(value);
      return true;
    } catch {
      return false;
    }
  }, [value]);
  return (
    <div className="relative">
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        style={{ minHeight: minH }}
        spellCheck={false}
        className={cx(
          "w-full bg-black/30 border rounded-lg px-3 py-2 text-xs font-mono outline-none resize-y text-slate-200 placeholder:text-slate-600",
          valid ? "border-white/10 focus:border-indigo-400/50" : "border-rose-400/50"
        )}
      />
      {!valid && <span className="absolute right-2 top-2 text-[10px] text-rose-400">JSON 格式错误</span>}
    </div>
  );
}
