import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Search, RefreshCw, FlaskConical, Boxes, Info, ChevronRight } from 'lucide-react';
import RequireProject from '../components/RequireProject';
import { Card, MethodBadge, Spinner, Empty } from '../components/ui';
import { api } from '../lib/api';
import { useApp } from '../lib/store';
import type { ApiInterface, Project } from '../lib/types';

function Inner({ project }: { project: Project }) {
  const navigate = useNavigate();
  const { toast } = useApp();
  const [loading, setLoading] = useState(true);
  const [demo, setDemo] = useState(false);
  const [source, setSource] = useState('');
  const [list, setList] = useState<ApiInterface[]>([]);
  const [q, setQ] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const r = await api.interfaces(project.id);
      setList(r.interfaces);
      setDemo(r.demo);
      setSource(r.source);
    } catch (e: any) {
      toast('error', e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [project.id]);

  const grouped = useMemo(() => {
    const filtered = list.filter(
      (i) =>
        i.title.toLowerCase().includes(q.toLowerCase()) ||
        i.path.toLowerCase().includes(q.toLowerCase())
    );
    const groups: Record<string, ApiInterface[]> = {};
    for (const it of filtered) {
      const cat = it.catName || '未分类';
      (groups[cat] ||= []).push(it);
    }
    return groups;
  }, [list, q]);

  return (
    <div className="max-w-6xl mx-auto space-y-5">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">接口库</h1>
          <p className="text-sm text-slate-500 mt-1">
            共 {list.length} 个接口
            {source && <span className="text-slate-600"> · 来源：{source}</span>}
          </p>
        </div>
        <button className="btn-ghost" onClick={load} disabled={loading}>
          {loading ? <Spinner /> : <RefreshCw size={15} />} 刷新
        </button>
      </div>

      {demo && (
        <div className="flex items-center gap-2 rounded-xl bg-amber-500/10 border border-amber-500/20 px-4 py-3 text-sm text-amber-200">
          <Info size={16} /> 当前为演示数据。在「项目管理」中配置 YAPI 地址与 Token 后即可拉取真实接口。
        </div>
      )}

      <div className="relative">
        <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
        <input
          className="input pl-10"
          placeholder="搜索接口名称或路径…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>

      {loading ? (
        <Card>
          <div className="flex items-center justify-center py-16 gap-3 text-slate-400">
            <Spinner className="w-5 h-5" /> 正在加载接口…
          </div>
        </Card>
      ) : Object.keys(grouped).length === 0 ? (
        <Card>
          <Empty icon={<Boxes size={24} />} title="没有匹配的接口" hint="尝试更换搜索关键字或刷新接口列表" />
        </Card>
      ) : (
        <div className="space-y-5">
          {Object.entries(grouped).map(([cat, items], gi) => (
            <motion.div
              key={cat}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: gi * 0.04 }}
            >
              <div className="flex items-center gap-2 mb-2.5 px-1">
                <Boxes size={15} className="text-brand-400" />
                <h3 className="text-sm font-semibold text-slate-300">{cat}</h3>
                <span className="text-xs text-slate-600">{items.length}</span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
                {items.map((it) => (
                  <motion.div
                    key={it.id}
                    whileHover={{ y: -2 }}
                    className="card p-4 flex items-center gap-3 cursor-pointer group"
                    onClick={() => navigate(`/workbench?iid=${it.id}`)}
                  >
                    <MethodBadge method={it.method} />
                    <div className="min-w-0 flex-1">
                      <div className="text-sm font-medium text-slate-100 truncate">{it.title}</div>
                      <div className="text-xs text-slate-500 font-mono truncate">{it.path}</div>
                    </div>
                    <button
                      className="btn-soft px-2.5 py-1.5 text-xs shrink-0"
                      onClick={(e) => {
                        e.stopPropagation();
                        navigate(`/workbench?iid=${it.id}`);
                      }}
                    >
                      <FlaskConical size={13} /> AI 测试
                    </button>
                    <ChevronRight size={16} className="text-slate-600 group-hover:text-slate-400 transition-colors" />
                  </motion.div>
                ))}
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function Interfaces() {
  return <RequireProject>{(project) => <Inner project={project} />}</RequireProject>;
}
