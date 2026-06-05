import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { LayoutDashboard, Boxes, Rocket, Settings as SettingsIcon, Zap, Github } from "lucide-react";
import { api, type PlatformStatus } from "./lib/api";
import { useEventStream } from "./hooks/useEventStream";
import { StatusDot } from "./components/ui";
import { Dashboard } from "./pages/Dashboard";
import { Interfaces } from "./pages/Interfaces";
import { Autopilot } from "./pages/Autopilot";
import { Settings } from "./pages/Settings";

type Tab = "dashboard" | "interfaces" | "autopilot" | "settings";

const NAV: { id: Tab; label: string; icon: typeof Boxes }[] = [
  { id: "dashboard", label: "概览", icon: LayoutDashboard },
  { id: "interfaces", label: "接口工作台", icon: Boxes },
  { id: "autopilot", label: "AI 自动测试", icon: Rocket },
  { id: "settings", label: "配置", icon: SettingsIcon },
];

export default function App() {
  const [tab, setTab] = useState<Tab>("dashboard");
  const [status, setStatus] = useState<PlatformStatus | null>(null);
  const stream = useEventStream();

  useEffect(() => {
    api.status().then(setStatus).catch(() => {});
  }, []);

  return (
    <div className="flex h-screen overflow-hidden">
      {/* 侧边栏 */}
      <aside className="flex w-64 shrink-0 flex-col border-r border-white/5 bg-ink-900/50 backdrop-blur-xl">
        <div className="flex items-center gap-3 px-5 py-5">
          <motion.div
            initial={{ rotate: -20, scale: 0.8 }}
            animate={{ rotate: 0, scale: 1 }}
            className="grid h-10 w-10 place-items-center rounded-2xl bg-gradient-to-br from-brand-500 to-accent-500 text-ink-950 shadow-glow"
          >
            <Zap className="h-5 w-5" />
          </motion.div>
          <div>
            <h1 className="text-sm font-bold leading-tight text-white">AI YAPI</h1>
            <p className="text-[11px] text-slate-400">智能接口测试平台</p>
          </div>
        </div>

        <nav className="flex-1 space-y-1 px-3 py-2">
          {NAV.map((n) => {
            const active = tab === n.id;
            return (
              <button
                key={n.id}
                onClick={() => setTab(n.id)}
                className={`relative flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                  active ? "text-white" : "text-slate-400 hover:text-slate-200 hover:bg-white/5"
                }`}
              >
                {active && (
                  <motion.span
                    layoutId="nav-active"
                    className="absolute inset-0 rounded-xl bg-gradient-to-r from-brand-500/25 to-brand-500/5 ring-1 ring-brand-400/30"
                    transition={{ type: "spring", stiffness: 400, damping: 32 }}
                  />
                )}
                <n.icon className="relative h-4 w-4" />
                <span className="relative">{n.label}</span>
              </button>
            );
          })}
        </nav>

        <div className="space-y-2 border-t border-white/5 px-4 py-4 text-xs">
          <StatusRow label="YAPI" ok={!!status?.yapi.configured} note={status?.yapi.configured ? "已连接" : "演示数据"} />
          <StatusRow label="AI 模型" ok={!!status?.ai.configured} note={status?.ai.configured ? status?.ai.model : "启发式"} />
          <StatusRow label="实时通道" ok={stream.connected} note={stream.connected ? "在线" : "重连中"} />
        </div>
      </aside>

      {/* 主区域 */}
      <main className="flex flex-1 flex-col overflow-hidden">
        <header className="flex items-center justify-between border-b border-white/5 px-7 py-4">
          <div>
            <h2 className="text-lg font-semibold text-white">{NAV.find((n) => n.id === tab)?.label}</h2>
            <p className="text-xs text-slate-400">
              读取 YAPI 参数 · 以项目代码为基准 AI 一键填充 · 自动化接口与页面测试
            </p>
          </div>
          <a
            href="https://hellosean1025.github.io/yapi/"
            target="_blank"
            rel="noreferrer"
            className="btn-ghost"
          >
            <Github className="h-4 w-4" /> 文档
          </a>
        </header>

        <div className="flex-1 overflow-auto p-7">
          {tab === "dashboard" && <Dashboard status={status} onNavigate={setTab} />}
          {tab === "interfaces" && <Interfaces stream={stream} />}
          {tab === "autopilot" && <Autopilot stream={stream} status={status} />}
          {tab === "settings" && <Settings status={status} />}
        </div>
      </main>
    </div>
  );
}

function StatusRow({ label, ok, note }: { label: string; ok: boolean; note?: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="flex items-center gap-2 text-slate-400">
        <StatusDot ok={ok} pulse />
        {label}
      </span>
      <span className={ok ? "text-accent-400" : "text-slate-500"}>{note}</span>
    </div>
  );
}
