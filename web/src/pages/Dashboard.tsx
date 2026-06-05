import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Boxes, BrainCircuit, MonitorPlay, Rocket, ArrowRight, FolderGit2 } from "lucide-react";
import { api, type ApiInterface, type PlatformStatus } from "../lib/api";
import { Card, MethodBadge } from "../components/ui";

const CAPS = [
  { icon: BrainCircuit, title: "AI 一键参数填充", desc: "读取 YAPI 参数，以项目代码为基准智能生成请求参数。", color: "from-brand-500 to-brand-600" },
  { icon: MonitorPlay, title: "接口 + 页面测试", desc: "对开发环境后端接口与前端页面执行自动化冒烟测试。", color: "from-accent-500 to-accent-400" },
  { icon: Rocket, title: "项目快捷启动", desc: "一键拉起被测项目，自动检测开发环境就绪状态。", color: "from-violet-500 to-fuchsia-500" },
  { icon: Boxes, title: "写完即测", desc: "接口写完后 AI 自动填参、发起请求并校验响应。", color: "from-amber-500 to-orange-500" },
];

export function Dashboard({
  status,
  onNavigate,
}: {
  status: PlatformStatus | null;
  onNavigate: (t: "interfaces" | "autopilot") => void;
}) {
  const [interfaces, setInterfaces] = useState<ApiInterface[]>([]);
  const [mock, setMock] = useState(false);

  useEffect(() => {
    api.interfaces().then((r) => {
      setInterfaces(r.data);
      setMock(r.mock);
    }).catch(() => {});
  }, []);

  const cats = new Set(interfaces.map((i) => i.catName).filter(Boolean));

  return (
    <div className="space-y-6">
      {/* 英雄区 */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        className="relative overflow-hidden rounded-3xl border border-white/5 bg-gradient-to-br from-ink-800/80 to-ink-900/40 p-8"
      >
        <div className="pointer-events-none absolute -right-10 -top-10 h-48 w-48 animate-floaty rounded-full bg-brand-500/20 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-16 right-32 h-40 w-40 animate-floaty rounded-full bg-accent-500/10 blur-3xl" />
        <div className="relative max-w-2xl">
          <span className="chip bg-white/5 text-brand-400 ring-1 ring-brand-400/20">⚡ AI 驱动的接口测试</span>
          <h1 className="mt-3 text-3xl font-bold leading-tight text-white">
            写完接口，剩下交给 AI 测试
          </h1>
          <p className="mt-2 text-sm text-slate-400">
            自动读取 YAPI 接口定义，结合项目真实代码生成贴近业务的参数，一键发起接口与前端页面联调测试。
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <button onClick={() => onNavigate("interfaces")} className="btn-primary">
              进入接口工作台 <ArrowRight className="h-4 w-4" />
            </button>
            <button onClick={() => onNavigate("autopilot")} className="btn-ghost">
              <Rocket className="h-4 w-4" /> 一键自动测试全部
            </button>
          </div>
        </div>
      </motion.div>

      {/* 指标 */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="接口总数" value={interfaces.length} delay={0} />
        <Stat label="接口分类" value={cats.size || 1} delay={0.05} />
        <Stat label="数据来源" value={mock ? "演示" : "YAPI"} delay={0.1} />
        <Stat label="AI 模式" value={status?.ai.configured ? "大模型" : "启发式"} delay={0.15} />
      </div>

      {/* 能力卡片 */}
      <div className="grid gap-4 md:grid-cols-2">
        {CAPS.map((c, i) => (
          <Card key={c.title} delay={i * 0.06} className="group">
            <div className="flex items-start gap-4">
              <div className={`grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-gradient-to-br ${c.color} text-white shadow-glow`}>
                <c.icon className="h-5 w-5" />
              </div>
              <div>
                <h3 className="font-semibold text-slate-100">{c.title}</h3>
                <p className="mt-1 text-sm text-slate-400">{c.desc}</p>
              </div>
            </div>
          </Card>
        ))}
      </div>

      {/* 最近接口 + 项目信息 */}
      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2" delay={0.1}>
          <h3 className="mb-3 font-semibold text-slate-100">接口预览</h3>
          <div className="space-y-1">
            {interfaces.slice(0, 6).map((i) => (
              <div key={i.id} className="flex items-center gap-3 rounded-xl px-2 py-2 transition hover:bg-white/5">
                <MethodBadge method={i.method} />
                <span className="text-sm text-slate-200">{i.title}</span>
                <code className="ml-auto truncate text-xs text-slate-500">{i.path}</code>
              </div>
            ))}
          </div>
        </Card>
        <Card delay={0.15}>
          <div className="mb-3 flex items-center gap-2 font-semibold text-slate-100">
            <FolderGit2 className="h-4 w-4 text-brand-400" /> 被测项目
          </div>
          <dl className="space-y-2.5 text-sm">
            <Row k="源码目录" v={status?.project.root || "未配置"} />
            <Row k="后端地址" v={status?.project.devApi || "-"} />
            <Row k="前端地址" v={status?.project.devWeb || "-"} />
            <Row k="启动命令" v={status?.project.startCommand || "未配置"} />
          </dl>
        </Card>
      </div>
    </div>
  );
}

function Stat({ label, value, delay }: { label: string; value: string | number; delay: number }) {
  return (
    <Card delay={delay}>
      <p className="text-xs text-slate-400">{label}</p>
      <p className="mt-1 text-2xl font-bold text-white">{value}</p>
    </Card>
  );
}

function Row({ k, v }: { k: string; v: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="shrink-0 text-slate-400">{k}</dt>
      <dd className="truncate font-mono text-xs text-slate-300" title={v}>{v}</dd>
    </div>
  );
}
