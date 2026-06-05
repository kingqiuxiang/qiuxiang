import { ReactNode } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import clsx from 'clsx';
import {
  LayoutDashboard,
  Boxes,
  FlaskConical,
  Rocket,
  MonitorPlay,
  History,
  FolderCog,
  ChevronDown,
  Sparkles,
} from 'lucide-react';
import { useApp } from '../lib/store';

const nav = [
  { to: '/', label: '总览', icon: LayoutDashboard, end: true },
  { to: '/interfaces', label: '接口库', icon: Boxes },
  { to: '/workbench', label: 'AI 测试台', icon: FlaskConical },
  { to: '/runner', label: '快捷启动', icon: Rocket },
  { to: '/pagetest', label: '前端页面测试', icon: MonitorPlay },
  { to: '/history', label: '测试历史', icon: History },
  { to: '/projects', label: '项目管理', icon: FolderCog },
];

function ProjectSwitcher() {
  const { projects, activeId, setActive } = useApp();
  const navigate = useNavigate();
  return (
    <div className="relative">
      <select
        value={activeId ?? ''}
        onChange={(e) => {
          if (e.target.value === '__new') {
            navigate('/projects');
            return;
          }
          setActive(e.target.value);
        }}
        className="appearance-none input pr-9 cursor-pointer font-medium"
      >
        {projects.length === 0 && <option value="">暂无项目</option>}
        {projects.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name}
          </option>
        ))}
        <option value="__new">＋ 新建项目…</option>
      </select>
      <ChevronDown size={15} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-500" />
    </div>
  );
}

export default function Layout({ children }: { children: ReactNode }) {
  const location = useLocation();
  return (
    <div className="flex h-full">
      <aside className="w-64 shrink-0 flex flex-col gap-2 p-4 border-r border-white/5 bg-ink-900/40 backdrop-blur-xl">
        <div className="flex items-center gap-3 px-2 py-3">
          <div className="relative">
            <img src="/logo.svg" alt="logo" className="w-10 h-10" />
            <span className="absolute -right-1 -top-1 text-accent-400">
              <Sparkles size={12} />
            </span>
          </div>
          <div>
            <div className="font-extrabold text-slate-100 leading-tight tracking-tight">灵测 LingCe</div>
            <div className="text-[11px] text-slate-500">AI 接口智测平台</div>
          </div>
        </div>

        <nav className="flex flex-col gap-1 mt-2">
          {nav.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.end}
              className={({ isActive }) => clsx('nav-link', isActive && 'nav-link-active')}
            >
              <n.icon size={18} />
              {n.label}
            </NavLink>
          ))}
        </nav>

        <div className="mt-auto px-2">
          <div className="card p-3 bg-gradient-to-br from-brand-600/15 to-accent-500/10">
            <p className="text-xs text-slate-300 font-medium flex items-center gap-1.5">
              <Sparkles size={13} className="text-accent-400" /> 工作流
            </p>
            <p className="text-[11px] text-slate-500 mt-1.5 leading-relaxed">
              读取 YAPI → 结合代码 AI 填参 → 一键启动 → 自动发请求 → AI 评审结果
            </p>
          </div>
        </div>
      </aside>

      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-16 shrink-0 flex items-center justify-between px-6 border-b border-white/5 bg-ink-900/30 backdrop-blur-xl">
          <div className="flex items-center gap-3 text-sm text-slate-400">
            <span className="text-slate-500">当前项目</span>
          </div>
          <div className="flex items-center gap-3">
            <div className="w-64">
              <ProjectSwitcher />
            </div>
          </div>
        </header>

        <motion.main
          key={location.pathname}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
          className="flex-1 overflow-auto p-6"
        >
          {children}
        </motion.main>
      </div>
    </div>
  );
}
