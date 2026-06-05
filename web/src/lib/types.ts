export interface AIConfig {
  baseUrl: string;
  apiKey: string;
  model: string;
}
export interface YApiConfig {
  baseUrl: string;
  token: string;
  projectId?: number;
}
export interface Project {
  id: string;
  name: string;
  description?: string;
  yapi: YApiConfig;
  ai: AIConfig;
  codePath: string;
  devBaseUrl: string;
  devWebUrl: string;
  startCommand: string;
  createdAt: number;
  updatedAt: number;
}
export interface ApiParam {
  name: string;
  required: boolean;
  type: string;
  desc?: string;
  example?: string;
}
export interface ApiInterface {
  id: string;
  title: string;
  path: string;
  method: string;
  catName?: string;
  status?: string;
  reqParams: ApiParam[];
  reqQuery: ApiParam[];
  reqHeaders: ApiParam[];
  reqBody?: any;
  reqBodyType?: 'json' | 'form' | 'none';
  resBody?: any;
  updatedAt?: number;
}
export interface FilledRequest {
  pathParams: Record<string, any>;
  query: Record<string, any>;
  headers: Record<string, any>;
  body: any;
  reasoning?: string;
}
export interface CodeSnippet {
  file: string;
  line: number;
  preview: string;
}
export interface CodeContext {
  available: boolean;
  root: string;
  fileCount: number;
  snippets: CodeSnippet[];
  note?: string;
}
export interface TestRecord {
  id: string;
  projectId: string;
  interfaceId: string;
  title: string;
  method: string;
  url: string;
  request: FilledRequest;
  response: {
    ok: boolean;
    status: number;
    statusText: string;
    durationMs: number;
    headers: Record<string, string>;
    body: any;
    error?: string;
  };
  analysis?: {
    passed: boolean;
    score: number;
    summary: string;
    issues: string[];
  };
  source: 'manual' | 'auto';
  createdAt: number;
}
export interface LogLine {
  ts: number;
  stream: 'stdout' | 'stderr' | 'system';
  text: string;
}
export interface RunnerState {
  projectId: string;
  running: boolean;
  pid?: number;
  command: string;
  startedAt?: number;
  exitCode?: number | null;
  logs: LogLine[];
}
export interface PageTestResult {
  url: string;
  reachable: boolean;
  status: number;
  durationMs: number;
  title?: string;
  contentLength?: number;
  scriptCount?: number;
  rootMounted?: boolean;
  engine: 'http' | 'playwright';
  notes: string[];
  error?: string;
}
