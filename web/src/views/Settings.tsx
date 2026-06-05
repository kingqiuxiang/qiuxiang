import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Save, Plug, Bot, FolderGit2, Globe, CheckCircle2 } from "lucide-react";
import { api } from "../api";
import { Button, Card, cx } from "../components/ui";
import type { AppConfig } from "../types";

export function Settings({ onSaved }: { onSaved: () => void }) {
  const [cfg, setCfg] = useState<AppConfig | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    api.getConfig().then(setCfg);
  }, []);

  const update = (path: string, value: string) => {
    setCfg((c) => {
      if (!c) return c;
      const [a, b] = path.split(".") as [keyof AppConfig, string];
      return { ...c, [a]: { ...(c[a] as any), [b]: value } };
    });
  };

  const save = async () => {
    if (!cfg) return;
    setSaving(true);
    try {
      const updated = await api.saveConfig(cfg);
      setCfg(updated);
      setSaved(true);
      onSaved();
      setTimeout(() => setSaved(false), 2000);
    } finally {
      setSaving(false);
    }
  };

  if (!cfg) return null;

  return (
    <div className="max-w-3xl space-y-5">
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
        <Group icon={<Plug size={17} />} title="YAPI 接入" desc="读取接口定义与参数 schema">
          <Input label="YAPI 地址" value={cfg.yapi.baseUrl} onChange={(v) => update("yapi.baseUrl", v)} placeholder="https://yapi.your-company.com" />
          <Input label="Token" value={cfg.yapi.token} onChange={(v) => update("yapi.token", v)} placeholder="项目 token" secret />
          <Input label="Project ID (可选)" value={cfg.yapi.projectId} onChange={(v) => update("yapi.projectId", v)} placeholder="123" />
        </Group>
      </motion.div>

      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 }}>
        <Group icon={<Bot size={17} />} title="AI 引擎" desc="OpenAI 兼容:OpenAI / DeepSeek / 通义 / Moonshot / 本地 Ollama">
          <Input label="Base URL" value={cfg.ai.baseUrl} onChange={(v) => update("ai.baseUrl", v)} placeholder="https://api.openai.com/v1" />
          <Input label="API Key" value={cfg.ai.apiKey} onChange={(v) => update("ai.apiKey", v)} placeholder="sk-..." secret />
          <Input label="模型" value={cfg.ai.model} onChange={(v) => update("ai.model", v)} placeholder="gpt-4o-mini" />
        </Group>
      </motion.div>

      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
        <Group icon={<FolderGit2 size={17} />} title="目标项目" desc="作为 AI 取值基准,并支持一键启动">
          <Input label="项目根路径" value={cfg.project.rootPath} onChange={(v) => update("project.rootPath", v)} placeholder="/path/to/your/project" />
          <Input label="启动命令" value={cfg.project.startCommand} onChange={(v) => update("project.startCommand", v)} placeholder="npm run dev" />
          <Input label="健康检查地址" value={cfg.project.healthUrl} onChange={(v) => update("project.healthUrl", v)} placeholder="http://localhost:3000" />
        </Group>
      </motion.div>

      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}>
        <Group icon={<Globe size={17} />} title="开发环境" desc="接口测试与页面巡检的目标地址">
          <Input label="API Base URL" value={cfg.devEnv.apiBaseUrl} onChange={(v) => update("devEnv.apiBaseUrl", v)} placeholder="http://localhost:3000" />
          <Input label="前端 Base URL" value={cfg.devEnv.webBaseUrl} onChange={(v) => update("devEnv.webBaseUrl", v)} placeholder="http://localhost:5173" />
        </Group>
      </motion.div>

      <div className="flex items-center gap-3 sticky bottom-0 py-3">
        <Button onClick={save} loading={saving}>
          <Save size={15} /> 保存配置
        </Button>
        {saved && (
          <motion.span
            initial={{ opacity: 0, x: -6 }}
            animate={{ opacity: 1, x: 0 }}
            className="flex items-center gap-1.5 text-emerald-300 text-sm"
          >
            <CheckCircle2 size={15} /> 已保存
          </motion.span>
        )}
      </div>
    </div>
  );
}

function Group({
  icon,
  title,
  desc,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  desc: string;
  children: React.ReactNode;
}) {
  return (
    <Card className="p-5">
      <div className="flex items-center gap-3 mb-4">
        <div className="size-9 rounded-lg bg-gradient-to-br from-indigo-500/30 to-cyan-400/20 grid place-items-center text-cyan-300">
          {icon}
        </div>
        <div>
          <div className="font-semibold text-sm">{title}</div>
          <div className="text-xs text-slate-400">{desc}</div>
        </div>
      </div>
      <div className="grid sm:grid-cols-2 gap-3">{children}</div>
    </Card>
  );
}

function Input({
  label,
  value,
  onChange,
  placeholder,
  secret,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  secret?: boolean;
}) {
  return (
    <label className="block">
      <span className="text-[11px] uppercase tracking-wider text-slate-500">{label}</span>
      <input
        type={secret ? "password" : "text"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={cx(
          "mt-1 w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm outline-none focus:border-indigo-400/50 text-slate-200 placeholder:text-slate-600 font-mono"
        )}
      />
    </label>
  );
}
