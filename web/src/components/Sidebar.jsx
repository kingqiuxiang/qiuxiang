import React, { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Search, ChevronRight, FolderOpen, RefreshCw } from 'lucide-react';
import clsx from 'clsx';
import { MethodBadge, Spinner } from './ui.jsx';

export default function Sidebar({ menu, loading, selectedId, onSelect, onRefresh }) {
  const [query, setQuery] = useState('');
  const [collapsed, setCollapsed] = useState({});

  const filtered = useMemo(() => {
    if (!query.trim()) return menu;
    const q = query.toLowerCase();
    return menu
      .map((cat) => ({
        ...cat,
        list: cat.list.filter((it) => it.title.toLowerCase().includes(q) || it.path.toLowerCase().includes(q)),
      }))
      .filter((cat) => cat.list.length);
  }, [menu, query]);

  const total = menu.reduce((n, c) => n + c.list.length, 0);

  return (
    <aside className="flex h-full w-80 shrink-0 flex-col border-r border-white/10 bg-ink-800/40">
      <div className="space-y-3 border-b border-white/10 p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-sm font-semibold text-white">
            <FolderOpen size={16} className="text-brand-400" /> 接口列表
            <span className="chip bg-white/10 text-slate-400">{total}</span>
          </div>
          <button className="rounded-lg p-1.5 text-slate-400 hover:bg-white/10 hover:text-white" onClick={onRefresh} title="刷新">
            {loading ? <Spinner /> : <RefreshCw size={15} />}
          </button>
        </div>
        <div className="relative">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input className="input pl-9" placeholder="搜索接口 / 路径" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-3">
        {loading && !menu.length ? (
          <div className="space-y-2">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="h-12 animate-pulse rounded-xl bg-white/5" />
            ))}
          </div>
        ) : (
          filtered.map((cat) => {
            const isCollapsed = collapsed[cat.catId];
            return (
              <div key={cat.catId} className="mb-2">
                <button
                  className="flex w-full items-center gap-1.5 rounded-lg px-2 py-1.5 text-xs font-semibold text-slate-400 hover:text-white"
                  onClick={() => setCollapsed((c) => ({ ...c, [cat.catId]: !c[cat.catId] }))}
                >
                  <ChevronRight size={14} className={clsx('transition-transform', !isCollapsed && 'rotate-90')} />
                  {cat.name}
                  <span className="ml-auto text-slate-600">{cat.list.length}</span>
                </button>
                <AnimatePresence initial={false}>
                  {!isCollapsed && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }}
                      transition={{ duration: 0.2 }} className="overflow-hidden"
                    >
                      {cat.list.map((it) => (
                        <button
                          key={it.id}
                          onClick={() => onSelect(it)}
                          className={clsx(
                            'group mb-1 flex w-full items-center gap-2 rounded-xl px-2.5 py-2 text-left transition',
                            selectedId === it.id ? 'bg-brand-500/15 ring-1 ring-brand-500/40' : 'hover:bg-white/5',
                          )}
                        >
                          <MethodBadge method={it.method} />
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-sm text-slate-200">{it.title}</span>
                            <span className="block truncate font-mono text-[11px] text-slate-500">{it.path}</span>
                          </span>
                          {it.status === 'done' && <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" title="已完成" />}
                        </button>
                      ))}
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            );
          })
        )}
        {!loading && !filtered.length && (
          <p className="px-2 py-8 text-center text-sm text-slate-500">无匹配接口</p>
        )}
      </div>
    </aside>
  );
}
