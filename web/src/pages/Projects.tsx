import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, Save, Trash2, FolderCog, Cpu, Network, Code2, Globe, Terminal, X } from 'lucide-react';
import { useApp } from '../lib/store';
import { api } from '../lib/api';
import type { Project } from '../lib/types';
import { Card, SectionTitle, Empty, Spinner } from '../components/ui';

const blank = (defaults: any): Partial<Project> => ({
  name: '',
  description: '',
  yapi: { baseUrl: defaults?.yapi?.baseUrl || '', token: defaults?.yapi?.token || '' },
  ai: {
    baseUrl: defaults?.ai?.baseUrl || 'https://api.openai.com/v1',
    apiKey: defaults?.ai?.apiKey || '',
    model: defaults?.ai?.model || 'gpt-4o-mini',
  },
  codePath: '',
  devBaseUrl: 'http://localhost:8080',
  devWebUrl: 'http://localhost:3000',
  startCommand: 'npm run dev',
});

export default function Projects() {
  const { projects, loadProjects, toast, setActive, defaults } = useApp();
  const [editing, setEditing] = useState<Partial<Project> | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    loadProjects();
  }, []);

  const startNew = () => setEditing(blank(defaults));

  const save = async () => {
    if (!editing?.name?.trim()) return toast('error', '请填写项目名称');
    setSaving(true);
    try {
      if (editing.id) {
        await api.updateProject(editing.id, editing);
        toast('success', '项目已更新');
      } else {
        const created = await api.createProject(editing);
        setActive(created.id);
        toast('success', '项目已创建');
      }
      await loadProjects();
      setEditing(null);
    } catch (e: any) {
      toast('error', e.message);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: string) => {
    if (!confirm('确认删除该项目及其测试记录？')) return;
    try {
      await api.deleteProject(id);
      toast('success', '已删除');
      await loadProjects();
    } catch (e: any) {
      toast('error', e.message);
    }
  };

  const set = (patch: Partial<Project>) => setEditing((p) => ({ ...p, ...patch }));
  const setYapi = (patch: any) => setEditing((p) => ({ ...p, yapi: { ...(p?.yapi as any), ...patch } }));
  const setAi = (patch: any) => setEditing((p) => ({ ...p, ai: { ...(p?.ai as any), ...patch } }));

  return (
    <div className="max-w-6xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">项目管理</h1>
          <p className="text-sm text-slate-500 mt-1">配置 YAPI、AI 模型、项目源码与开发环境地址</p>
        </div>
        <button className="btn-primary" onClick={startNew}>
          <Plus size={16} /> 新建项目
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {projects.length === 0 && !editing && (
          <div className="lg:col-span-2">
            <Card>
              <Empty
                icon={<FolderCog size={26} />}
                title="还没有任何项目"
                hint="创建一个项目，连接你的 YAPI 与 AI 模型，即可开始智能接口测试。未配置 YAPI / AI 时将自动进入演示模式。"
                action={
                  <button className="btn-primary" onClick={startNew}>
                    <Plus size={16} /> 立即创建
                  </button>
                }
              />
            </Card>
          </div>
        )}

        {projects.map((p, i) => (
          <Card key={p.id} delay={i * 0.05}>
            <div className="flex items-start justify-between">
              <div className="min-w-0">
                <div className="font-semibold text-slate-100 truncate">{p.name}</div>
                <div className="text-xs text-slate-500 mt-1 line-clamp-2">{p.description || '暂无描述'}</div>
              </div>
              <div className="flex gap-2 shrink-0 ml-3">
                <button className="btn-ghost px-2.5 py-1.5" onClick={() => setEditing(p)}>
                  编辑
                </button>
                <button className="btn-danger px-2.5 py-1.5" onClick={() => remove(p.id)}>
                  <Trash2 size={14} />
                </button>
              </div>
            </div>
            <div className="mt-4 grid grid-cols-2 gap-2 text-xs">
              <Meta icon={<Network size={13} />} label="YAPI" value={p.yapi.baseUrl ? '已配置' : '演示模式'} ok={!!p.yapi.token} />
              <Meta icon={<Cpu size={13} />} label="AI" value={p.ai.apiKey ? p.ai.model : '演示模式'} ok={!!p.ai.apiKey} />
              <Meta icon={<Code2 size={13} />} label="代码" value={p.codePath || '未配置'} ok={!!p.codePath} />
              <Meta icon={<Globe size={13} />} label="开发环境" value={p.devBaseUrl || '未配置'} ok={!!p.devBaseUrl} />
            </div>
          </Card>
        ))}
      </div>

      <AnimatePresence>
        {editing && (
          <motion.div
            className="fixed inset-0 z-40 grid place-items-center bg-black/60 backdrop-blur-sm p-4"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setEditing(null)}
          >
            <motion.div
              className="card w-full max-w-2xl max-h-[88vh] overflow-auto p-6"
              initial={{ scale: 0.95, y: 20 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.95, y: 20 }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between mb-5">
                <h2 className="text-lg font-bold text-slate-100">{editing.id ? '编辑项目' : '新建项目'}</h2>
                <button className="text-slate-500 hover:text-slate-200" onClick={() => setEditing(null)}>
                  <X size={20} />
                </button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="md:col-span-2">
                  <label className="label">项目名称 *</label>
                  <input className="input" value={editing.name || ''} onChange={(e) => set({ name: e.target.value })} placeholder="例如：电商交易中台" />
                </div>
                <div className="md:col-span-2">
                  <label className="label">描述</label>
                  <input className="input" value={editing.description || ''} onChange={(e) => set({ description: e.target.value })} placeholder="一句话描述项目" />
                </div>

                <FormGroup title="YAPI 配置" icon={<Network size={14} />}>
                  <Field label="YAPI 地址">
                    <input className="input" value={editing.yapi?.baseUrl || ''} onChange={(e) => setYapi({ baseUrl: e.target.value })} placeholder="http://yapi.company.com" />
                  </Field>
                  <Field label="项目 Token">
                    <input className="input" value={editing.yapi?.token || ''} onChange={(e) => setYapi({ token: e.target.value })} placeholder="YAPI 项目 token" />
                  </Field>
                  <Field label="Project ID（可选）">
                    <input className="input" value={editing.yapi?.projectId ?? ''} onChange={(e) => setYapi({ projectId: e.target.value ? Number(e.target.value) : undefined })} placeholder="数字 ID" />
                  </Field>
                </FormGroup>

                <FormGroup title="AI 模型（OpenAI 兼容）" icon={<Cpu size={14} />}>
                  <Field label="Base URL">
                    <input className="input" value={editing.ai?.baseUrl || ''} onChange={(e) => setAi({ baseUrl: e.target.value })} placeholder="https://api.openai.com/v1" />
                  </Field>
                  <Field label="API Key">
                    <input className="input" type="password" value={editing.ai?.apiKey || ''} onChange={(e) => setAi({ apiKey: e.target.value })} placeholder="sk-..." />
                  </Field>
                  <Field label="模型">
                    <input className="input" value={editing.ai?.model || ''} onChange={(e) => setAi({ model: e.target.value })} placeholder="gpt-4o-mini / deepseek-chat" />
                  </Field>
                </FormGroup>

                <FormGroup title="项目源码 & 开发环境" icon={<Terminal size={14} />}>
                  <Field label="项目源码绝对路径">
                    <input className="input" value={editing.codePath || ''} onChange={(e) => set({ codePath: e.target.value })} placeholder="/path/to/your/project" />
                  </Field>
                  <Field label="后端基础地址 devBaseUrl">
                    <input className="input" value={editing.devBaseUrl || ''} onChange={(e) => set({ devBaseUrl: e.target.value })} placeholder="http://localhost:8080" />
                  </Field>
                  <Field label="前端地址 devWebUrl">
                    <input className="input" value={editing.devWebUrl || ''} onChange={(e) => set({ devWebUrl: e.target.value })} placeholder="http://localhost:3000" />
                  </Field>
                  <Field label="一键启动命令">
                    <input className="input" value={editing.startCommand || ''} onChange={(e) => set({ startCommand: e.target.value })} placeholder="npm run dev / mvn spring-boot:run" />
                  </Field>
                </FormGroup>
              </div>

              <div className="flex justify-end gap-3 mt-6">
                <button className="btn-ghost" onClick={() => setEditing(null)}>
                  取消
                </button>
                <button className="btn-primary" onClick={save} disabled={saving}>
                  {saving ? <Spinner /> : <Save size={16} />} 保存
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function Meta({ icon, label, value, ok }: { icon: any; label: string; value: string; ok: boolean }) {
  return (
    <div className="flex items-center gap-2 rounded-lg bg-white/[0.03] px-2.5 py-2 border border-white/5">
      <span className={ok ? 'text-accent-400' : 'text-slate-600'}>{icon}</span>
      <span className="text-slate-500">{label}</span>
      <span className="text-slate-300 truncate ml-auto max-w-[60%]" title={value}>
        {value}
      </span>
    </div>
  );
}

function FormGroup({ title, icon, children }: { title: string; icon: any; children: any }) {
  return (
    <div className="md:col-span-2 rounded-xl border border-white/5 bg-white/[0.02] p-4">
      <SectionTitle title={title} icon={icon} />
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">{children}</div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: any }) {
  return (
    <div>
      <label className="label">{label}</label>
      {children}
    </div>
  );
}
