import React from 'react';
import { motion } from 'framer-motion';
import { Zap, Settings, Sparkles, Database, Globe } from 'lucide-react';
import { Pill, Dot } from './ui.jsx';

export default function Header({ capabilities, source, onOpenSettings }) {
  const cap = capabilities || {};
  return (
    <header className="sticky top-0 z-30 border-b border-white/10 bg-ink-900/70 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-[1600px] items-center gap-4 px-5">
        <motion.div
          initial={{ rotate: -12, scale: 0.8, opacity: 0 }}
          animate={{ rotate: 0, scale: 1, opacity: 1 }}
          transition={{ type: 'spring', stiffness: 200, damping: 14 }}
          className="grid h-10 w-10 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-accent-500 text-white shadow-glow"
        >
          <Zap size={20} fill="currentColor" />
        </motion.div>
        <div className="leading-tight">
          <h1 className="text-lg font-extrabold tracking-tight text-white">
            ApiPilot
          </h1>
          <p className="text-[11px] text-slate-400">AI 接口测试驾驶舱</p>
        </div>

        <div className="ml-6 hidden items-center gap-2 md:flex">
          <Pill tone={cap.ai ? 'good' : 'default'}>
            <Sparkles size={12} /> AI {cap.ai ? '已接入' : '启发式'}
          </Pill>
          <Pill tone={cap.yapi ? 'good' : 'brand'}>
            <Database size={12} /> {source === 'yapi' ? 'YAPI 已连接' : '样例数据'}
          </Pill>
          <Pill tone={cap.playwright ? 'good' : 'default'}>
            <Globe size={12} /> 浏览器 {cap.playwright ? 'Playwright' : 'HTTP'}
          </Pill>
        </div>

        <div className="ml-auto flex items-center gap-3">
          <div className="hidden items-center gap-2 text-xs text-slate-400 sm:flex">
            <Dot ok /> 服务在线
          </div>
          <button className="btn-ghost" onClick={onOpenSettings}>
            <Settings size={16} /> 设置
          </button>
        </div>
      </div>
    </header>
  );
}
