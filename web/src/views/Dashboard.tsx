import { motion } from "framer-motion";
import {
  Plug,
  Wand2,
  FlaskConical,
  Rocket,
  Globe,
  ArrowRight,
  CheckCircle2,
  CircleDashed,
} from "lucide-react";
import { Badge, Button, Card, cx } from "../components/ui";
import type { SystemStatus } from "../types";
import type { ViewKey } from "../components/Sidebar";

const stagger = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06 } },
};
const item = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0 },
};

const flow = [
  { icon: Plug, title: "接入 YAPI", desc: "读取接口定义与参数 schema" },
  { icon: Wand2, title: "AI 一键填参", desc: "以项目代码为基准生成真实参数" },
  { icon: Rocket, title: "AI 快捷启动", desc: "一键拉起项目并健康检查" },
  { icon: FlaskConical, title: "自动测试", desc: "写完接口后 AI 生成用例并执行" },
  { icon: Globe, title: "页面巡检", desc: "访问开发环境前端页面验证" },
];

export function Dashboard({
  status,
  setView,
}: {
  status: SystemStatus | null;
  setView: (v: ViewKey) => void;
}) {
  return (
    <motion.div variants={stagger} initial="hidden" animate="show" className="space-y-6">
      <motion.div variants={item}>
        <Card className="p-7 relative overflow-hidden glow">
          <div className="absolute -right-16 -top-16 size-64 rounded-full bg-cyan-400/10 blur-3xl" />
          <div className="absolute -left-10 -bottom-20 size-64 rounded-full bg-indigo-500/10 blur-3xl" />
          <div className="relative">
            <Badge tone="info">AI · 接口测试一体化工作流</Badge>
            <h1 className="mt-3 text-2xl md:text-3xl font-bold leading-tight">
              让 AI 读懂你的 <span className="text-gradient">YAPI 与项目代码</span>
              <br />
              一键填参 · 启动项目 · 自动测试
            </h1>
            <p className="mt-3 text-slate-400 max-w-2xl text-sm leading-relaxed">
              APIPilot 连接 YAPI 获取接口契约,以项目代码为事实基准,由 AI 生成可直接发起的请求参数,
              快捷拉起开发环境,在接口写完后自动设计并执行测试用例,并巡检前端页面。
            </p>
            <div className="mt-5 flex flex-wrap gap-3">
              <Button onClick={() => setView("interfaces")}>
                开始体验 <ArrowRight size={15} />
              </Button>
              <Button variant="ghost" onClick={() => setView("settings")}>
                配置连接
              </Button>
            </div>
          </div>
        </Card>
      </motion.div>

      <motion.div variants={item} className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="YAPI 接入" ready={status?.yapi} ready_text="已连接" off_text="演示数据" onClick={() => setView("settings")} />
        <StatCard label="AI 引擎" ready={status?.ai} ready_text="已就绪" off_text="启发式回退" onClick={() => setView("settings")} />
        <StatCard label="项目代码" ready={status?.project} ready_text="已挂载" off_text="未配置" onClick={() => setView("project")} />
        <StatCard
          label="运行状态"
          ready={status?.runner === "running"}
          ready_text="运行中"
          off_text={status?.runner === "error" ? "异常" : "未启动"}
          onClick={() => setView("project")}
        />
      </motion.div>

      <motion.div variants={item}>
        <Card className="p-6">
          <div className="text-sm font-semibold text-slate-200 mb-5">核心工作流</div>
          <div className="grid md:grid-cols-5 gap-3">
            {flow.map((f, i) => {
              const Icon = f.icon;
              return (
                <div key={f.title} className="relative">
                  <div className="rounded-xl border border-white/10 bg-white/[0.03] p-4 h-full hover:border-cyan-400/30 transition-colors">
                    <div className="size-9 rounded-lg bg-gradient-to-br from-indigo-500/30 to-cyan-400/20 grid place-items-center mb-3">
                      <Icon size={17} className="text-cyan-300" />
                    </div>
                    <div className="text-[11px] text-slate-500 mb-0.5">步骤 {i + 1}</div>
                    <div className="text-sm font-semibold">{f.title}</div>
                    <div className="text-xs text-slate-400 mt-1 leading-relaxed">{f.desc}</div>
                  </div>
                  {i < flow.length - 1 && (
                    <ArrowRight
                      size={16}
                      className="hidden md:block absolute top-1/2 -right-2.5 -translate-y-1/2 text-slate-600 z-10"
                    />
                  )}
                </div>
              );
            })}
          </div>
        </Card>
      </motion.div>
    </motion.div>
  );
}

function StatCard({
  label,
  ready,
  ready_text,
  off_text,
  onClick,
}: {
  label: string;
  ready?: boolean;
  ready_text: string;
  off_text: string;
  onClick: () => void;
}) {
  return (
    <Card hover className="p-4 cursor-pointer" >
      <button onClick={onClick} className="w-full text-left">
        <div className="flex items-center justify-between">
          <span className="text-xs text-slate-400">{label}</span>
          {ready ? (
            <CheckCircle2 size={16} className="text-emerald-400" />
          ) : (
            <CircleDashed size={16} className="text-amber-400" />
          )}
        </div>
        <div className={cx("mt-2 text-lg font-bold", ready ? "text-emerald-300" : "text-amber-300")}>
          {ready ? ready_text : off_text}
        </div>
      </button>
    </Card>
  );
}
