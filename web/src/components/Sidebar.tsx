import { motion } from "framer-motion";
import { LayoutDashboard, Plug, FlaskConical, Rocket, Settings, Globe } from "lucide-react";
import { cx } from "./ui";
import type { SystemStatus } from "../types";

export type ViewKey = "dashboard" | "interfaces" | "autotest" | "project" | "page" | "settings";

const items: { key: ViewKey; label: string; sub: string; icon: any }[] = [
  { key: "dashboard", label: "驾驶舱", sub: "Dashboard", icon: LayoutDashboard },
  { key: "interfaces", label: "接口 & 一键填参", sub: "YAPI · AI Fill", icon: Plug },
  { key: "autotest", label: "AI 自动测试", sub: "Auto Test", icon: FlaskConical },
  { key: "project", label: "项目启动 & 代码", sub: "Runner · Code", icon: Rocket },
  { key: "page", label: "前端页面巡检", sub: "Page Check", icon: Globe },
  { key: "settings", label: "设置", sub: "Settings", icon: Settings },
];

export function Sidebar({
  view,
  setView,
  status,
}: {
  view: ViewKey;
  setView: (v: ViewKey) => void;
  status: SystemStatus | null;
}) {
  return (
    <aside className="w-[248px] shrink-0 h-full flex flex-col px-4 py-5 border-r border-white/5">
      <div className="flex items-center gap-3 px-2 mb-7">
        <div className="relative">
          <div className="size-9 rounded-xl bg-gradient-to-br from-indigo-500 to-cyan-400 grid place-items-center text-slate-950 font-black">
            ▲
          </div>
          <span className="absolute -bottom-1 -right-1 size-3 rounded-full bg-emerald-400 ring-2 ring-[#070b18] animate-pulse" />
        </div>
        <div>
          <div className="font-bold text-[15px] leading-tight">
            API<span className="text-gradient">Pilot</span>
          </div>
          <div className="text-[10px] text-slate-500 tracking-wider">AI 接口测试驾驶舱</div>
        </div>
      </div>

      <nav className="flex flex-col gap-1">
        {items.map((it) => {
          const active = view === it.key;
          const Icon = it.icon;
          return (
            <button
              key={it.key}
              onClick={() => setView(it.key)}
              className={cx(
                "relative group flex items-center gap-3 px-3 py-2.5 rounded-xl text-left transition-colors",
                active ? "text-white" : "text-slate-400 hover:text-slate-200 hover:bg-white/5"
              )}
            >
              {active && (
                <motion.div
                  layoutId="nav-active"
                  className="absolute inset-0 rounded-xl bg-gradient-to-r from-indigo-500/20 to-cyan-400/10 border border-indigo-400/30"
                  transition={{ type: "spring", stiffness: 400, damping: 32 }}
                />
              )}
              <Icon size={18} className={cx("relative z-10", active && "text-cyan-300")} />
              <span className="relative z-10">
                <span className="block text-sm font-medium">{it.label}</span>
                <span className="block text-[10px] text-slate-500">{it.sub}</span>
              </span>
            </button>
          );
        })}
      </nav>

      <div className="mt-auto space-y-2 text-[11px]">
        <StatusRow label="YAPI" ok={status?.yapi} okText="已连接" offText="演示模式" />
        <StatusRow label="AI 引擎" ok={status?.ai} okText="已就绪" offText="启发式回退" />
        <StatusRow label="项目代码" ok={status?.project} okText="已挂载" offText="未配置" />
      </div>
    </aside>
  );
}

function StatusRow({
  label,
  ok,
  okText,
  offText,
}: {
  label: string;
  ok?: boolean;
  okText: string;
  offText: string;
}) {
  return (
    <div className="flex items-center justify-between px-3 py-1.5 rounded-lg bg-white/[0.03] border border-white/5">
      <span className="text-slate-400">{label}</span>
      <span className={cx("flex items-center gap-1.5", ok ? "text-emerald-300" : "text-amber-300")}>
        <span className={cx("size-1.5 rounded-full", ok ? "bg-emerald-400" : "bg-amber-400")} />
        {ok ? okText : offText}
      </span>
    </div>
  );
}
