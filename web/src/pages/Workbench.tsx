import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Search,
  Wand2,
  Send,
  Code2,
  FileJson,
  Lightbulb,
  CheckCircle2,
  XCircle,
  Clock,
  Gauge,
  ChevronRight,
  Sparkles,
} from 'lucide-react';
import RequireProject from '../components/RequireProject';
import { Card, MethodBadge, Spinner, Empty, JsonView } from '../components/ui';
import { api } from '../lib/api';
import { useApp } from '../lib/store';
import type { ApiInterface, CodeContext, FilledRequest, Project, TestRecord } from '../lib/types';

function editableJson(value: any): string {
  if (value === undefined || value === null) return '';
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function Inner({ project }: { project: Project }) {
  const { toast } = useApp();
  const [params] = useSearchParams();
  const [list, setList] = useState<ApiInterface[]>([]);
  const [q, setQ] = useState('');
  const [selected, setSelected] = useState<ApiInterface | null>(null);
  const [detail, setDetail] = useState<ApiInterface | null>(null);
  const [code, setCode] = useState<CodeContext | null>(null);
  const [filling, setFilling] = useState(false);
  const [testing, setTesting] = useState(false);
  const [aiUsed, setAiUsed] = useState<boolean | null>(null);
  const [reasoning, setReasoning] = useState('');
  const [tab, setTab] = useState<'params' | 'code' | 'result'>('params');
  const [result, setResult] = useState<TestRecord | null>(null);

  const [pathStr, setPathStr] = useState('');
  const [queryStr, setQueryStr] = useState('');
  const [headerStr, setHeaderStr] = useState('');
  const [bodyStr, setBodyStr] = useState('');
  const initIid = useRef(params.get('iid'));

  const loadList = async () => {
    try {
      const r = await api.interfaces(project.id);
      setList(r.interfaces);
      const target = initIid.current
        ? r.interfaces.find((i) => i.id === initIid.current)
        : r.interfaces[0];
      if (target) selectInterface(target);
      initIid.current = null;
    } catch (e: any) {
      toast('error', e.message);
    }
  };

  useEffect(() => {
    loadList();
  }, [project.id]);

  const selectInterface = async (it: ApiInterface) => {
    setSelected(it);
    setDetail(null);
    setResult(null);
    setReasoning('');
    setAiUsed(null);
    setTab('params');
    setPathStr('');
    setQueryStr('');
    setHeaderStr('');
    setBodyStr('');
    try {
      const d = await api.interfaceDetail(project.id, it.id);
      setDetail(d.interface);
      setCode(d.code);
    } catch (e: any) {
      toast('error', e.message);
    }
  };

  const doFill = async () => {
    if (!selected) return;
    setFilling(true);
    try {
      const r = await api.fill(project.id, selected.id);
      setDetail(r.interface);
      setCode(r.code);
      setAiUsed(r.ai);
      setReasoning(r.filled.reasoning || '');
      setPathStr(editableJson(r.filled.pathParams));
      setQueryStr(editableJson(r.filled.query));
      setHeaderStr(editableJson(r.filled.headers));
      setBodyStr(editableJson(r.filled.body));
      toast('success', r.ai ? 'AI 已完成参数填充' : '已用启发式规则生成参数（演示模式）');
    } catch (e: any) {
      toast('error', e.message);
    } finally {
      setFilling(false);
    }
  };

  const parseOr = (s: string, fallback: any) => {
    if (!s.trim()) return fallback;
    try {
      return JSON.parse(s);
    } catch {
      throw new Error('参数 JSON 格式有误，请检查');
    }
  };

  const doTest = async () => {
    if (!selected) return;
    setTesting(true);
    try {
      const filled: FilledRequest = {
        pathParams: parseOr(pathStr, {}),
        query: parseOr(queryStr, {}),
        headers: parseOr(headerStr, {}),
        body: bodyStr.trim() ? parseOr(bodyStr, undefined) : undefined,
      };
      const rec = await api.test(project.id, selected.id, filled);
      setResult(rec);
      setTab('result');
      toast(rec.analysis?.passed ? 'success' : 'error', rec.analysis?.passed ? '测试通过' : '测试发现问题');
    } catch (e: any) {
      toast('error', e.message);
    } finally {
      setTesting(false);
    }
  };

  const filtered = useMemo(
    () =>
      list.filter(
        (i) => i.title.toLowerCase().includes(q.toLowerCase()) || i.path.toLowerCase().includes(q.toLowerCase())
      ),
    [list, q]
  );

  return (
    <div className="h-[calc(100vh-7rem)] flex gap-4">
      {/* 左侧接口列表 */}
      <div className="w-72 shrink-0 flex flex-col card p-3">
        <div className="relative mb-3">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input className="input pl-9 py-2" placeholder="搜索接口…" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div className="flex-1 overflow-auto space-y-1 pr-1">
          {filtered.map((it) => (
            <button
              key={it.id}
              onClick={() => selectInterface(it)}
              className={`w-full text-left rounded-xl px-3 py-2.5 transition-all border ${
                selected?.id === it.id
                  ? 'bg-brand-500/15 border-brand-500/30'
                  : 'border-transparent hover:bg-white/5'
              }`}
            >
              <div className="flex items-center gap-2">
                <MethodBadge method={it.method} className="scale-90 origin-left" />
              </div>
              <div className="text-sm text-slate-200 truncate mt-1">{it.title}</div>
              <div className="text-[11px] text-slate-500 font-mono truncate">{it.path}</div>
            </button>
          ))}
        </div>
      </div>

      {/* 右侧工作台 */}
      <div className="flex-1 min-w-0 flex flex-col">
        {!selected ? (
          <Card className="flex-1">
            <Empty icon={<Wand2 size={24} />} title="选择一个接口开始" hint="从左侧选择接口，使用 AI 一键填充参数并测试" />
          </Card>
        ) : (
          <div className="flex flex-col h-full gap-4">
            {/* 顶部操作条 */}
            <Card className="!p-4">
              <div className="flex items-center gap-3 flex-wrap">
                <MethodBadge method={selected.method} />
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-semibold text-slate-100 truncate">{selected.title}</div>
                  <div className="text-xs text-slate-500 font-mono truncate">
                    {project.devBaseUrl}
                    {selected.path}
                  </div>
                </div>
                <button className="btn-soft" onClick={doFill} disabled={filling}>
                  {filling ? <Spinner /> : <Wand2 size={15} />} AI 一键填充
                </button>
                <button className="btn-primary" onClick={doTest} disabled={testing}>
                  {testing ? <Spinner /> : <Send size={15} />} 发送测试
                </button>
              </div>
              <AnimatePresence>
                {aiUsed !== null && reasoning && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: 'auto' }}
                    exit={{ opacity: 0, height: 0 }}
                    className="mt-3 flex items-start gap-2 rounded-xl bg-gradient-to-r from-brand-600/15 to-accent-500/10 border border-white/5 px-3.5 py-2.5"
                  >
                    <Sparkles size={15} className="text-accent-400 mt-0.5 shrink-0" />
                    <p className="text-xs text-slate-300 leading-relaxed">
                      <span className="text-accent-400 font-medium">{aiUsed ? 'AI 推理：' : '生成说明：'}</span>
                      {reasoning}
                    </p>
                  </motion.div>
                )}
              </AnimatePresence>
            </Card>

            {/* Tabs */}
            <div className="flex gap-1 px-1">
              {[
                { k: 'params', label: '请求参数', icon: <FileJson size={15} /> },
                { k: 'code', label: `代码上下文${code?.snippets.length ? ` (${code.snippets.length})` : ''}`, icon: <Code2 size={15} /> },
                { k: 'result', label: '测试结果', icon: <Gauge size={15} /> },
              ].map((t) => (
                <button
                  key={t.k}
                  onClick={() => setTab(t.k as any)}
                  className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-all ${
                    tab === t.k ? 'bg-white/10 text-slate-100' : 'text-slate-500 hover:text-slate-300'
                  }`}
                >
                  {t.icon}
                  {t.label}
                </button>
              ))}
            </div>

            <div className="flex-1 overflow-auto">
              {tab === 'params' && (
                <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
                  <ParamEditor title="Path 参数" value={pathStr} onChange={setPathStr} hint={detail?.reqParams} />
                  <ParamEditor title="Query 参数" value={queryStr} onChange={setQueryStr} hint={detail?.reqQuery} />
                  <ParamEditor title="Headers" value={headerStr} onChange={setHeaderStr} hint={detail?.reqHeaders} />
                  <ParamEditor title="请求体 Body" value={bodyStr} onChange={setBodyStr} />
                </div>
              )}

              {tab === 'code' && <CodePanel code={code} />}

              {tab === 'result' && <ResultPanel result={result} />}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function ParamEditor({
  title,
  value,
  onChange,
  hint,
}: {
  title: string;
  value: string;
  onChange: (v: string) => void;
  hint?: { name: string; required: boolean; type: string; desc?: string }[];
}) {
  return (
    <Card className="!p-4">
      <div className="flex items-center justify-between mb-2">
        <h4 className="text-sm font-semibold text-slate-200">{title}</h4>
        <span className="text-[11px] text-slate-600">JSON</span>
      </div>
      {hint && hint.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-2.5">
          {hint.map((h) => (
            <span
              key={h.name}
              className={`chip ${h.required ? 'bg-rose-500/15 text-rose-300' : 'bg-white/5 text-slate-400'}`}
              title={h.desc}
            >
              {h.name}
              <span className="text-slate-500">:{h.type}</span>
            </span>
          ))}
        </div>
      )}
      <textarea
        className="input code-scroll min-h-[150px] resize-y"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="{ }"
        spellCheck={false}
      />
    </Card>
  );
}

function CodePanel({ code }: { code: CodeContext | null }) {
  if (!code) return <Card><Empty icon={<Code2 size={22} />} title="加载中…" /></Card>;
  if (!code.available)
    return (
      <Card>
        <Empty icon={<Code2 size={22} />} title="未配置项目源码路径" hint={code.note || '在「项目管理」中填写项目源码绝对路径，AI 即可结合真实代码生成参数。'} />
      </Card>
    );
  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 text-xs text-slate-500">
        <Code2 size={14} /> 扫描 {code.fileCount} 个源码文件，匹配到 {code.snippets.length} 处相关代码
      </div>
      {code.note && <div className="text-xs text-amber-300/80">{code.note}</div>}
      {code.snippets.map((s, i) => (
        <Card key={i} className="!p-0 overflow-hidden">
          <div className="flex items-center gap-2 px-4 py-2 border-b border-white/5 bg-white/[0.02]">
            <Code2 size={13} className="text-brand-400" />
            <span className="text-xs font-mono text-slate-300 truncate">{s.file}</span>
            <span className="text-[11px] text-slate-600">: {s.line}</span>
          </div>
          <JsonView data={s.preview} className="!rounded-none !border-0" />
        </Card>
      ))}
    </div>
  );
}

function ResultPanel({ result }: { result: TestRecord | null }) {
  if (!result)
    return (
      <Card>
        <Empty icon={<Send size={22} />} title="尚未发送测试" hint="点击「发送测试」向开发环境发起请求并由 AI 评审结果" />
      </Card>
    );
  const r = result.response;
  const a = result.analysis;
  return (
    <div className="space-y-4">
      <Card className="!p-4">
        <div className="flex items-center gap-4 flex-wrap">
          <div className={`chip text-sm font-bold ${r.ok ? 'bg-emerald-500/15 text-emerald-300' : 'bg-rose-500/15 text-rose-300'}`}>
            {r.ok ? <CheckCircle2 size={15} /> : <XCircle size={15} />} HTTP {r.status || '—'} {r.statusText}
          </div>
          <div className="flex items-center gap-1.5 text-sm text-slate-400">
            <Clock size={14} /> {r.durationMs}ms
          </div>
          <div className="text-xs text-slate-500 font-mono truncate flex-1">{result.url}</div>
        </div>
        {r.error && <div className="mt-3 text-sm text-rose-300 bg-rose-500/10 rounded-xl px-3 py-2">{r.error}</div>}
      </Card>

      {a && (
        <Card className="!p-4">
          <div className="flex items-center gap-3 mb-3">
            <div className={`grid place-items-center w-11 h-11 rounded-xl font-bold text-lg ${a.passed ? 'bg-emerald-500/15 text-emerald-300' : 'bg-rose-500/15 text-rose-300'}`}>
              {a.score}
            </div>
            <div>
              <div className="flex items-center gap-2 text-sm font-semibold text-slate-100">
                <Lightbulb size={15} className="text-accent-400" /> AI 评审 · {a.passed ? '通过' : '未通过'}
              </div>
              <p className="text-xs text-slate-400 mt-0.5">{a.summary}</p>
            </div>
          </div>
          {a.issues.length > 0 && (
            <ul className="space-y-1.5 mt-2">
              {a.issues.map((iss, i) => (
                <li key={i} className="flex items-start gap-2 text-xs text-amber-200/90">
                  <ChevronRight size={13} className="mt-0.5 shrink-0 text-amber-400" /> {iss}
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      <Card className="!p-4">
        <h4 className="text-sm font-semibold text-slate-200 mb-2">响应内容</h4>
        <JsonView data={r.body} className="max-h-96" />
      </Card>
    </div>
  );
}

export default function Workbench() {
  return <RequireProject>{(project) => <Inner project={project} />}</RequireProject>;
}
