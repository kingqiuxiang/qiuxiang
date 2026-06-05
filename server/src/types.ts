export interface ParamDef {
  name: string;
  type?: string;
  required?: boolean;
  desc?: string;
  example?: string;
  default?: string;
}

export interface ApiInterface {
  id: string;
  title: string;
  method: string;
  path: string;
  catName?: string;
  reqParams: ParamDef[]; // path params
  reqQuery: ParamDef[];
  reqHeaders: ParamDef[];
  reqBodyType?: string; // json | form | raw | none
  reqBody?: unknown; // example object or json-schema
  resBody?: unknown;
  updatedAt?: number;
}

export interface AppConfig {
  yapi: {
    baseUrl: string;
    token: string;
    projectId: string;
  };
  ai: {
    baseUrl: string;
    apiKey: string;
    model: string;
  };
  project: {
    rootPath: string;
    startCommand: string;
    healthUrl: string;
  };
  devEnv: {
    apiBaseUrl: string;
    webBaseUrl: string;
  };
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
