import React, { useEffect, useState, useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import { FlaskConical, Bot } from 'lucide-react';
import clsx from 'clsx';
import { api, streamAutoTest } from './api/client.js';
import Header from './components/Header.jsx';
import Sidebar from './components/Sidebar.jsx';
import SettingsDrawer from './components/SettingsDrawer.jsx';
import InterfacePanel from './components/InterfacePanel.jsx';
import AutoTestPanel from './components/AutoTestPanel.jsx';
import ProjectConsole from './components/ProjectConsole.jsx';
import Toaster from './components/Toast.jsx';

const EMPTY_FILLED = { pathParams: {}, query: {}, headers: {}, body: null };

export default function App() {
  const [config, setConfig] = useState(null);
  const [capabilities, setCapabilities] = useState({});
  const [menu, setMenu] = useState([]);
  const [source, setSource] = useState('sample');
  const [menuLoading, setMenuLoading] = useState(true);
  const [settingsOpen, setSettingsOpen] = useState(false);

  const [selected, setSelected] = useState(null); // {id, catName}
  const [iface, setIface] = useState(null);
  const [tab, setTab] = useState('manual');

  const [filled, setFilled] = useState(EMPTY_FILLED);
  const [fillMeta, setFillMeta] = useState(null);
  const [aiFilling, setAiFilling] = useState(false);
  const [sending, setSending] = useState(false);
  const [response, setResponse] = useState(null);

  const [autoRunning, setAutoRunning] = useState(false);
  const [autoSteps, setAutoSteps] = useState([]);
  const [autoReport, setAutoReport] = useState(null);

  const [toasts, setToasts] = useState([]);
  const toastId = useRef(0);

  const toast = useCallback((message, tone = 'default') => {
    const id = ++toastId.current;
    setToasts((t) => [...t, { id, message, tone }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 3800);
  }, []);

  const loadConfig = useCallback(async () => {
    try {
      const { config: c, capabilities: cap } = await api.getConfig();
      setConfig(c); setCapabilities(cap);
    } catch (e) { toast(e.message, 'bad'); }
  }, [toast]);

  const loadMenu = useCallback(async () => {
    setMenuLoading(true);
    try {
      const { menu: m, source: s } = await api.getMenu();
      setMenu(m); setSource(s);
    } catch (e) {
      toast(`加载接口失败：${e.message}`, 'bad');
    } finally { setMenuLoading(false); }
  }, [toast]);

  useEffect(() => { loadConfig(); loadMenu(); }, [loadConfig, loadMenu]);

  const handleSelect = useCallback(async (item) => {
    setSelected({ id: item.id, catName: item.catName });
    setIface(null); setFilled(EMPTY_FILLED); setFillMeta(null); setResponse(null);
    setAutoSteps([]); setAutoReport(null);
    try {
      const { interface: detail } = await api.getInterface(item.id, item.catName);
      setIface(detail);
    } catch (e) { toast(e.message, 'bad'); }
  }, [toast]);

  const handleAiFill = useCallback(async () => {
    if (!selected) return;
    setAiFilling(true);
    try {
      const { filled: f, aiEnabled, codeMatched } = await api.aiFill(selected.id, selected.catName);
      setFilled({ pathParams: f.pathParams || {}, query: f.query || {}, headers: f.headers || {}, body: f.body ?? null });
      setFillMeta({ engine: f.engine, notes: f.notes, codeMatched });
      toast(aiEnabled ? 'AI 已生成参数' : '已用启发式引擎生成参数', 'good');
    } catch (e) { toast(e.message, 'bad'); }
    finally { setAiFilling(false); }
  }, [selected, toast]);

  const handleSend = useCallback(async () => {
    if (!selected) return;
    setSending(true); setResponse(null);
    try {
      const res = await api.runRequest({ interfaceId: selected.id, catName: selected.catName, filled });
      setResponse(res);
      if (res.ok) toast(`HTTP ${res.response.status} · ${res.response.durationMs}ms`, res.response.status < 400 ? 'good' : 'bad');
      else toast(`请求失败：${res.error?.message}`, 'bad');
    } catch (e) { toast(e.message, 'bad'); }
    finally { setSending(false); }
  }, [selected, filled, toast]);

  const handleAutoTest = useCallback(() => {
    if (!selected) return;
    setAutoRunning(true); setAutoSteps([]); setAutoReport(null);
    streamAutoTest(selected.id, { catName: selected.catName }, {
      onStep: (step) => setAutoSteps((prev) => {
        const idx = prev.findIndex((s) => s.key === step.key);
        if (idx >= 0) { const next = [...prev]; next[idx] = step; return next; }
        return [...prev, step];
      }),
      onDone: (report) => {
        setAutoReport(report); setAutoRunning(false);
        toast(report.ok ? '自动测试通过' : '自动测试发现问题', report.ok ? 'good' : 'bad');
      },
      onError: (err) => { setAutoRunning(false); toast(`自动测试失败：${err.message || '连接中断'}`, 'bad'); },
    });
  }, [selected, toast]);

  const handleSaveConfig = useCallback(async (patch) => {
    try {
      const { config: c } = await api.saveConfig(patch);
      setConfig(c);
      await loadConfig();
      await loadMenu();
      toast('设置已保存', 'good');
    } catch (e) { toast(e.message, 'bad'); throw e; }
  }, [loadConfig, loadMenu, toast]);

  return (
    <div className="flex h-screen flex-col overflow-hidden">
      <Header capabilities={capabilities} source={source} onOpenSettings={() => setSettingsOpen(true)} />

      <div className="flex min-h-0 flex-1">
        <Sidebar menu={menu} loading={menuLoading} selectedId={selected?.id} onSelect={handleSelect} onRefresh={loadMenu} />

        <main className="flex min-w-0 flex-1 flex-col">
          {/* Tabs */}
          <div className="flex items-center gap-1 border-b border-white/10 bg-ink-800/30 px-4">
            <Tab active={tab === 'manual'} onClick={() => setTab('manual')} icon={FlaskConical} label="手动测试" />
            <Tab active={tab === 'auto'} onClick={() => setTab('auto')} icon={Bot} label="AI 自动测试" />
          </div>
          <div className="min-h-0 flex-1 overflow-hidden">
            {tab === 'manual' ? (
              <InterfacePanel
                iface={iface}
                filled={filled}
                onFilledChange={setFilled}
                onAiFill={handleAiFill}
                aiFilling={aiFilling}
                fillMeta={fillMeta}
                onSend={handleSend}
                sending={sending}
                response={response}
              />
            ) : (
              <AutoTestPanel iface={iface} running={autoRunning} steps={autoSteps} report={autoReport} onRun={handleAutoTest} />
            )}
          </div>
        </main>

        <div className="hidden w-96 shrink-0 border-l border-white/10 bg-ink-800/40 xl:block">
          <ProjectConsole config={config} onToast={toast} />
        </div>
      </div>

      <SettingsDrawer open={settingsOpen} onClose={() => setSettingsOpen(false)} config={config} onSave={handleSaveConfig} />
      <Toaster toasts={toasts} />
    </div>
  );
}

function Tab({ active, onClick, icon: Icon, label }) {
  return (
    <button
      onClick={onClick}
      className={clsx('relative flex items-center gap-2 px-4 py-3 text-sm font-medium transition', active ? 'text-white' : 'text-slate-400 hover:text-slate-200')}
    >
      <Icon size={16} className={active ? 'text-brand-400' : ''} /> {label}
      {active && <motion.span layoutId="tab-underline" className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-gradient-to-r from-brand-500 to-accent-500" />}
    </button>
  );
}
