export interface ParamDef {
  name: string;
  type?: string;
  required?: boolean;
  desc?: string;
  example?: string;
}

export interface ApiInterface {
  id: string;
  title: string;
  method: string;
  path: string;
  catName?: string;
  reqParams: ParamDef[];
  reqQuery: ParamDef[];
  reqHeaders: ParamDef[];
  reqBodyType?: string;
  reqBody?: unknown;
  resBody?: unknown;
  updatedAt?: number;
}

export interface FilledParams {
  pathParams: Record<string, unknown>;
  query: Record<string, unknown>;
  headers: Record<string, unknown>;
  body: unknown;
  notes?: string;
  source: "ai" | "heuristic";
}

export interface TestAssertion {
  type: "status" | "jsonPath" | "contains" | "responseTime";
  expression?: string;
  expected?: unknown;
  comparator?: "eq" | "neq" | "lt" | "gt" | "exists";
}

export interface TestResult {
  ok: boolean;
  status?: number;
  durationMs: number;
  requestUrl: string;
  requestMethod: string;
  responseHeaders?: Record<string, string>;
  responseBody?: unknown;
  assertions: { assertion: TestAssertion; passed: boolean; message: string }[];
  error?: string;
}

export interface TestCase {
  name: string;
  kind: "happy" | "validation" | "auth" | "boundary";
  filled: FilledParams;
  assertions: TestAssertion[];
}

export interface TestCaseResult extends TestCase {
  result: TestResult;
}

export interface AppConfig {
  yapi: { baseUrl: string; token: string; projectId: string };
  ai: { baseUrl: string; apiKey: string; model: string };
  project: { rootPath: string; startCommand: string; healthUrl: string };
  devEnv: { apiBaseUrl: string; webBaseUrl: string };
}

export interface SystemStatus {
  yapi: boolean;
  ai: boolean;
  project: boolean;
  runner: string;
}

export interface RunnerStatus {
  status: "stopped" | "starting" | "running" | "error";
  command: string;
  startedAt: number | null;
  exitCode: number | null;
  logs: { ts: number; stream: "stdout" | "stderr" | "system"; line: string }[];
}

export interface PageCheck {
  ok: boolean;
  url: string;
  status?: number;
  durationMs: number;
  title?: string;
  contentType?: string;
  sizeBytes?: number;
  links?: number;
  scripts?: number;
  hasRootEl?: boolean;
  errorHints?: string[];
  error?: string;
}
