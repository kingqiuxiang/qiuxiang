import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Sparkles, Database, FolderGit2, Save } from 'lucide-react';
import { Spinner } from './ui.jsx';

function Field({ label, hint, children }) {
  return (
    <label className="block space-y-1.5">
      <span className="label">{label}</span>
      {children}
      {hint && <span className="block text-[11px] text-slate-500">{hint}</span>}
    </label>
  );
}

function Section({ icon: Icon, title, children }) {
  return (
    <div className="card p-4">
      <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-white">
        <Icon size={16} className="text-brand-400" /> {title}
      </div>
      <div className="space-y-3">{children}</div>
    </div>
  );
}

export default function SettingsDrawer({ open, onClose, config, onSave }) {
  const [form, setForm] = useState({ ai: {}, yapi: {}, project: {} });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (config) {
      setForm({
        ai: { baseUrl: config.ai.baseUrl, model: config.ai.model, apiKey: '' },
        yapi: { baseUrl: config.yapi.baseUrl, token: '' },
        project: { ...config.project },
      });
    }
  }, [config, open]);

  const set = (section, key, value) =>
    setForm((f) => ({ ...f, [section]: { ...f[section], [key]: value } }));

  const handleSave = async () => {
    setSaving(true);
    try {
      const patch = JSON.parse(JSON.stringify(form));
      if (!patch.ai.apiKey) delete patch.ai.apiKey;
      if (!patch.yapi.token) delete patch.yapi.token;
      await onSave(patch);
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            onClick={onClose}
          />
          <motion.aside
            className="fixed right-0 top-0 z-50 flex h-full w-full max-w-md flex-col border-l border-white/10 bg-ink-800/95 backdrop-blur-2xl"
            initial={{ x: '100%' }} animate={{ x: 0 }} exit={{ x: '100%' }}
            transition={{ type: 'spring', stiffness: 260, damping: 30 }}
          >
            <div className="flex items-center justify-between border-b border-white/10 px-5 py-4">
              <h2 className="text-base font-bold text-white">连接与环境设置</h2>
              <button className="rounded-lg p-1.5 text-slate-400 hover:bg-white/10 hover:text-white" onClick={onClose}>
                <X size={18} />
              </button>
            </div>

            <div className="flex-1 space-y-4 overflow-y-auto p-5">
              <Section icon={Sparkles} title="AI 模型（兼容 OpenAI 协议）">
                <Field label="Base URL">
                  <input className="input" value={form.ai.baseUrl || ''} onChange={(e) => set('ai', 'baseUrl', e.target.value)} placeholder="https://api.openai.com/v1" />
                </Field>
                <Field label="Model">
                  <input className="input" value={form.ai.model || ''} onChange={(e) => set('ai', 'model', e.target.value)} placeholder="gpt-4o-mini" />
                </Field>
                <Field label="API Key" hint={config?.ai.hasKey ? '已配置（留空则保持不变），不填将使用本地启发式引擎' : '留空则使用本地启发式引擎，离线可用'}>
                  <input className="input" type="password" value={form.ai.apiKey || ''} onChange={(e) => set('ai', 'apiKey', e.target.value)} placeholder={config?.ai.apiKeyMask || 'sk-...'} />
                </Field>
              </Section>

              <Section icon={Database} title="YAPI">
                <Field label="YAPI Base URL" hint="留空则使用内置样例接口数据">
                  <input className="input" value={form.yapi.baseUrl || ''} onChange={(e) => set('yapi', 'baseUrl', e.target.value)} placeholder="https://yapi.your.com" />
                </Field>
                <Field label="项目 Token" hint={config?.yapi.hasToken ? '已配置（留空则保持不变）' : 'YAPI 项目 token'}>
                  <input className="input" type="password" value={form.yapi.token || ''} onChange={(e) => set('yapi', 'token', e.target.value)} placeholder={config?.yapi.tokenMask || 'token'} />
                </Field>
              </Section>

              <Section icon={FolderGit2} title="被测项目 / 开发环境">
                <Field label="项目代码路径" hint="用于代码扫描与一键启动">
                  <input className="input" value={form.project.path || ''} onChange={(e) => set('project', 'path', e.target.value)} placeholder="/path/to/project" />
                </Field>
                <Field label="启动命令">
                  <input className="input" value={form.project.startCmd || ''} onChange={(e) => set('project', 'startCmd', e.target.value)} placeholder="npm run dev" />
                </Field>
                <Field label="后端 API 基址 (devApiBaseUrl)">
                  <input className="input" value={form.project.devApiBaseUrl || ''} onChange={(e) => set('project', 'devApiBaseUrl', e.target.value)} placeholder="http://localhost:8080" />
                </Field>
                <Field label="前端页面地址 (devWebUrl)">
                  <input className="input" value={form.project.devWebUrl || ''} onChange={(e) => set('project', 'devWebUrl', e.target.value)} placeholder="http://localhost:3000" />
                </Field>
              </Section>
            </div>

            <div className="border-t border-white/10 p-4">
              <button className="btn-primary w-full" onClick={handleSave} disabled={saving}>
                {saving ? <Spinner /> : <Save size={16} />} 保存设置
              </button>
            </div>
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}
