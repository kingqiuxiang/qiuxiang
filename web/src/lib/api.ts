export type HttpMethod = "GET" | "POST" | "PUT" | "DELETE" | "PATCH" | "HEAD" | "OPTIONS";

export interface ApiParam {
  name: string;
  in: "query" | "header" | "path" | "body" | "form";
  type: string;
  required: boolean;
  desc?: string;
  example?: unknown;
}

export interface ApiInterface {
  id: string;
  title: string;
  path: string;
  method: HttpMethod;
  catName?: string;
  desc?: string;
  params: ApiParam[];
  reqBodySchema?: unknown;
  resBodySchema?: unknown;
}

export interface CodeEvidence {
  file: string;
  line: number;
  snippet: string;
}

export interface FilledParams {
  query: Record<string, unknown>;
  headers: Record<string, string>;
  path: Record<string, unknown>;
  body: unknown;
  rationale?: string;
  evidence?: CodeEvidence[];
  source: "ai" | "heuristic";
}

export interface AssertionResult {
  description: string;
  passed: boolean;
  detail?: string;
}

export interface TestResult {
  interfaceId: string;
  request: { method: HttpMethod; url: string; headers: Record<string, string>; body?: unknown };
  response?: { status: number; durationMs: number; headers: Record<string, string>; body: unknown };
  assertions: AssertionResult[];
  passed: boolean;
  error?: string;
  startedAt: string;
  finishedAt: string;
}

export interface PageCheck {
  url: string;
  ok: boolean;
  status?: number;
  durationMs: number;
  title?: string;
  detectedErrors: string[];
  engine: "playwright" | "http";
  screenshot?: string;
  detail?: string;
}

export interface AutopilotReport {
  runId: string;
  interface: { id: string; title: string; method: string; path: string };
  fill: { source: string; rationale?: string; evidenceCount: number };
  test: TestResult;
  page?: PageCheck;
  passed: boolean;
}

export interface PlatformStatus {
  yapi: { configured: boolean; baseUrl?: string };
  ai: { configured: boolean; model: string };
  project: { root?: string; devApi?: string; devWeb?: string; startCommand?: string };
}

export interface ProjectRunState {
  running: boolean;
  pid?: number;
  command?: string;
  startedAt?: string;
  ready?: boolean;
}

async function req<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  const json = await res.json();
  if (!json.ok) throw new Error(json.error ?? "请求失败");
  return json as T;
}

export const api = {
  status: () => req<{ data: PlatformStatus }>("/api/status").then((r) => r.data),
  project: () => req<{ data: { id: string; name: string; desc?: string }; mock: boolean }>("/api/yapi/project"),
  interfaces: () => req<{ data: ApiInterface[]; mock: boolean }>("/api/yapi/interfaces"),
  interface: (id: string) => req<{ data: ApiInterface }>(`/api/yapi/interfaces/${id}`).then((r) => r.data),
  codeOverview: () => req<{ enabled: boolean; data: { name: string; type: string }[] }>("/api/code/overview"),
  evidence: (id: string) => req<{ data: CodeEvidence[] }>(`/api/code/evidence/${id}`).then((r) => r.data),
  fill: (id: string) => req<{ data: FilledParams }>(`/api/ai/fill/${id}`, { method: "POST" }).then((r) => r.data),
  test: (id: string, body: { filled?: FilledParams; options?: unknown }) =>
    req<{ data: TestResult }>(`/api/test/${id}`, { method: "POST", body: JSON.stringify(body) }).then((r) => r.data),
  testPage: (path: string) =>
    req<{ data: PageCheck }>("/api/test-page", { method: "POST", body: JSON.stringify({ path }) }).then((r) => r.data),
  autopilot: (id: string, body: unknown) =>
    req<{ data: AutopilotReport }>(`/api/autopilot/${id}`, { method: "POST", body: JSON.stringify(body) }).then((r) => r.data),
  autopilotAll: (body: unknown) =>
    req<{ data: AutopilotReport[] }>("/api/autopilot", { method: "POST", body: JSON.stringify(body) }).then((r) => r.data),
  projectState: () => req<{ data: ProjectRunState }>("/api/project/state").then((r) => r.data),
  projectStart: () => req<{ data: ProjectRunState; runId: string }>("/api/project/start", { method: "POST" }),
  projectStop: () => req<{ data: ProjectRunState }>("/api/project/stop", { method: "POST" }).then((r) => r.data),
};

export interface RunEvent {
  type: "log" | "result" | "done" | "error" | "status";
  runId: string;
  message?: string;
  level?: "info" | "warn" | "error" | "success";
  payload?: unknown;
  ts: string;
}
