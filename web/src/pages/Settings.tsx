import { useEffect, useState } from "react";
import { Database, BrainCircuit, FolderGit2, CheckCircle2, XCircle, Copy, Check } from "lucide-react";
import { api, type PlatformStatus } from "../lib/api";
import { Card } from "../components/ui";

const ENV_TEMPLATE = `# YAPI
YAPI_BASE_URL=http://yapi.company.com
YAPI_TOKEN=your_project_token

# AI（OpenAI 兼容，留空则使用内置启发式）
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=sk-...
AI_MODEL=gpt-4o-mini

# 被测项目
PROJECT_ROOT=/path/to/your/project
DEV_API_BASE_URL=http://localhost:3000
DEV_WEB_BASE_URL=http://localhost:5173
PROJECT_START_COMMAND=npm run dev`;

export function Settings({ status }: { status: PlatformStatus | null }) {
  const [overview, setOverview] = useState<{ enabled: boolean; data: { name: string; type: string }[] }>({ enabled: false, data: [] });
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    api.codeOverview().then(setOverview).catch(() => {});
  }, []);

  const copy = () => {
    navigator.clipboard?.writeText(ENV_TEMPLATE);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="grid max-w-4xl gap-5">
      <ConfigCard
        icon={<Database className="h-5 w-5" />}
        title="YAPI 接口源"
        ok={!!status?.yapi.configured}
        rows={[
          ["状态", status?.yapi.configured ? "已连接真实 YAPI" : "使用内置演示数据"],
          ["地址", status?.yapi.baseUrl || "—"],
        ]}
        hint="在仓库根目录 .env 中配置 YAPI_BASE_URL 与 YAPI_TOKEN（YAPI 项目设置 → token 配置）。"
      />

      <ConfigCard
        icon={<BrainCircuit className="h-5 w-5" />}
        title="AI 模型"
        ok={!!status?.ai.configured}
        rows={[
          ["状态", status?.ai.configured ? "已接入大模型" : "使用内置启发式生成"],
          ["模型", status?.ai.model || "—"],
        ]}
        hint="配置 AI_API_KEY 即可启用大模型参数生成；支持任意 OpenAI 兼容服务（含国产模型网关）。"
      />

      <ConfigCard
        icon={<FolderGit2 className="h-5 w-5" />}
        title="被测项目"
        ok={!!status?.project.root}
        rows={[
          ["源码目录", status?.project.root || "未配置"],
          ["后端地址", status?.project.devApi || "—"],
          ["前端地址", status?.project.devWeb || "—"],
          ["启动命令", status?.project.startCommand || "未配置"],
        ]}
        hint="配置 PROJECT_ROOT 后，AI 会读取项目代码作为参数填充基准。"
      />

      {overview.enabled && (
        <Card>
          <h3 className="mb-3 text-sm font-semibold text-slate-200">项目结构预览</h3>
          <div className="flex flex-wrap gap-2">
            {overview.data.map((e) => (
              <span key={e.name} className="chip bg-white/5 text-slate-300">
                {e.type === "dir" ? "📁" : "📄"} {e.name}
              </span>
            ))}
          </div>
        </Card>
      )}

      <Card>
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-slate-200">.env 配置模板</h3>
          <button onClick={copy} className="btn-ghost">
            {copied ? <Check className="h-4 w-4 text-accent-400" /> : <Copy className="h-4 w-4" />}
            {copied ? "已复制" : "复制"}
          </button>
        </div>
        <pre className="code-area overflow-auto rounded-xl bg-ink-950/80 border border-white/5 p-3 text-slate-300">{ENV_TEMPLATE}</pre>
        <p className="mt-2 text-xs text-slate-500">修改 .env 后重启服务端生效。</p>
      </Card>
    </div>
  );
}

function ConfigCard({
  icon,
  title,
  ok,
  rows,
  hint,
}: {
  icon: React.ReactNode;
  title: string;
  ok: boolean;
  rows: [string, string][];
  hint: string;
}) {
  return (
    <Card>
      <div className="flex items-center gap-3">
        <div className="grid h-10 w-10 place-items-center rounded-xl bg-brand-500/15 text-brand-400">{icon}</div>
        <h3 className="flex-1 font-semibold text-slate-100">{title}</h3>
        {ok ? (
          <span className="chip bg-accent-500/15 text-accent-400"><CheckCircle2 className="h-4 w-4" /> 已配置</span>
        ) : (
          <span className="chip bg-white/5 text-slate-400"><XCircle className="h-4 w-4" /> 未配置</span>
        )}
      </div>
      <dl className="mt-4 space-y-2 text-sm">
        {rows.map(([k, v]) => (
          <div key={k} className="flex items-center justify-between gap-3">
            <dt className="shrink-0 text-slate-400">{k}</dt>
            <dd className="truncate font-mono text-xs text-slate-300" title={v}>{v}</dd>
          </div>
        ))}
      </dl>
      <p className="mt-3 rounded-xl bg-white/5 px-3 py-2 text-xs text-slate-400">{hint}</p>
    </Card>
  );
}
