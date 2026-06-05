import { AnimatePresence, motion } from "framer-motion";
import { useCallback, useEffect, useState } from "react";
import { Bot, Sparkles } from "lucide-react";
import { Sidebar, type ViewKey } from "./components/Sidebar";
import { Assistant } from "./components/Assistant";
import { Dashboard } from "./views/Dashboard";
import { Interfaces } from "./views/Interfaces";
import { AutoTest } from "./views/AutoTest";
import { Project } from "./views/Project";
import { PageCheck } from "./views/PageCheck";
import { Settings } from "./views/Settings";
import { api } from "./api";
import type { SystemStatus } from "./types";

const titles: Record<ViewKey, { title: string; sub: string }> = {
  dashboard: { title: "驾驶舱", sub: "一览系统状态与核心工作流" },
  interfaces: { title: "接口 & 一键填参", sub: "读取 YAPI 接口,以项目代码为基准由 AI 填充参数" },
  autotest: { title: "AI 自动测试", sub: "接口写完后,AI 设计并执行测试用例" },
  project: { title: "项目启动 & 代码", sub: "一键拉起开发环境,浏览代码基准" },
  page: { title: "前端页面巡检", sub: "访问开发环境前端页面进行验证" },
  settings: { title: "设置", sub: "连接 YAPI、AI、项目与开发环境" },
};

export default function App() {
  const [view, setView] = useState<ViewKey>("dashboard");
  const [status, setStatus] = useState<SystemStatus | null>(null);
  const [assistantOpen, setAssistantOpen] = useState(false);
  const [autoTestId, setAutoTestId] = useState<string | null>(null);

  const refreshStatus = useCallback(() => {
    api.status().then(setStatus).catch(() => setStatus(null));
  }, []);

  useEffect(() => {
    refreshStatus();
    const t = setInterval(refreshStatus, 4000);
    return () => clearInterval(t);
  }, [refreshStatus]);

  const goAutoTest = (id: string) => {
    setAutoTestId(id);
    setView("autotest");
  };

  return (
    <div className="h-full flex">
      <Sidebar view={view} setView={setView} status={status} />

      <div className="flex-1 flex flex-col min-w-0">
        <header className="flex items-center justify-between px-7 py-4 border-b border-white/5">
          <div>
            <motion.h1
              key={view}
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              className="text-xl font-bold"
            >
              {titles[view].title}
            </motion.h1>
            <p className="text-sm text-slate-400">{titles[view].sub}</p>
          </div>
          <motion.button
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.97 }}
            onClick={() => setAssistantOpen(true)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-indigo-500/20 to-cyan-400/15 border border-indigo-400/30 text-sm font-medium hover:border-cyan-400/50 transition-colors"
          >
            <Bot size={16} className="text-cyan-300" />
            智能助手
            <Sparkles size={13} className="text-indigo-300" />
          </motion.button>
        </header>

        <main className="flex-1 overflow-y-auto p-7">
          <AnimatePresence mode="wait">
            <motion.div
              key={view}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.22 }}
              className="h-full"
            >
              {view === "dashboard" && <Dashboard status={status} setView={setView} />}
              {view === "interfaces" && <Interfaces onAutoTest={goAutoTest} />}
              {view === "autotest" && <AutoTest initialId={autoTestId} />}
              {view === "project" && <Project />}
              {view === "page" && <PageCheck />}
              {view === "settings" && <Settings onSaved={refreshStatus} />}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>

      <Assistant open={assistantOpen} onClose={() => setAssistantOpen(false)} />
    </div>
  );
}
