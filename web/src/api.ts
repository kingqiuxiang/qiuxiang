import type {
  ApiInterface,
  AppConfig,
  FilledParams,
  PageCheck,
  RunnerStatus,
  SystemStatus,
  TestCase,
  TestCaseResult,
  TestResult,
} from "./types";

async function http<T>(url: string, opts?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...opts,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `请求失败 (${res.status})`);
  }
  return res.json();
}

export const api = {
  status: () => http<SystemStatus>("/api/status"),
  getConfig: () => http<AppConfig>("/api/config"),
  saveConfig: (patch: Partial<AppConfig>) =>
    http<AppConfig>("/api/config", { method: "PUT", body: JSON.stringify(patch) }),

  interfaces: () =>
    http<{ items: ApiInterface[]; status: { mode: string; message: string; connected: boolean } }>(
      "/api/interfaces"
    ),

  fill: (id: string, mode: "ai" | "heuristic" = "ai") =>
    http<{ filled: FilledParams; aiConfigured: boolean; codeGrounded: boolean }>(
      `/api/fill/${id}?mode=${mode}`,
      { method: "POST" }
    ),

  runTest: (payload: unknown) =>
    http<TestResult>("/api/test/run", { method: "POST", body: JSON.stringify(payload) }),

  plan: (id: string) =>
    http<{ plan: TestCase[]; aiConfigured: boolean }>(`/api/test/plan/${id}`, { method: "POST" }),

  autorun: (id: string, plan?: TestCase[]) =>
    http<{ results: TestCaseResult[] }>(`/api/test/autorun/${id}`, {
      method: "POST",
      body: JSON.stringify({ plan }),
    }),

  codeScan: () =>
    http<{
      available: boolean;
      root: string;
      total?: number;
      truncated?: boolean;
      byExt?: Record<string, number>;
      files?: { rel: string; size: number; ext: string }[];
    }>("/api/code/scan"),

  codeSearch: (q: string) =>
    http<{ available: boolean; matches: { rel: string; line: number; text: string }[] }>(
      `/api/code/search?q=${encodeURIComponent(q)}`
    ),

  runnerStart: () => http<{ ok: boolean; message: string }>("/api/runner/start", { method: "POST" }),
  runnerStop: () => http<{ ok: boolean; message: string }>("/api/runner/stop", { method: "POST" }),
  runnerStatus: () => http<RunnerStatus>("/api/runner/status"),
  runnerHealth: () => http<{ ok: boolean; status?: number; message: string }>("/api/runner/health"),

  pageCheck: (url?: string) =>
    http<PageCheck>("/api/page/check", { method: "POST", body: JSON.stringify({ url }) }),

  chat: (messages: { role: string; content: string }[]) =>
    http<{ configured: boolean; reply: string }>("/api/ai/chat", {
      method: "POST",
      body: JSON.stringify({ messages }),
    }),
};
