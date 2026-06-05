/** 共享类型定义 —— 同时供前端通过 lib/api 复用其形态。 */

export type HttpMethod =
  | "GET"
  | "POST"
  | "PUT"
  | "DELETE"
  | "PATCH"
  | "HEAD"
  | "OPTIONS";

/** YAPI 接口参数（请求体 / query / header / path 的统一抽象） */
export interface ApiParam {
  name: string;
  /** 参数所在位置 */
  in: "query" | "header" | "path" | "body" | "form";
  type: string;
  required: boolean;
  desc?: string;
  /** 示例值（来自 YAPI 或 AI 生成） */
  example?: unknown;
  /** 嵌套结构（对象 / 数组）的子参数 */
  children?: ApiParam[];
}

/** 标准化后的接口定义（由 YAPI 原始数据转换而来） */
export interface ApiInterface {
  id: string;
  title: string;
  path: string;
  method: HttpMethod;
  catName?: string;
  desc?: string;
  /** 统一参数列表 */
  params: ApiParam[];
  /** 请求体的原始 JSON Schema（若有） */
  reqBodySchema?: unknown;
  /** 期望响应的原始 JSON Schema（若有） */
  resBodySchema?: unknown;
  /** YAPI 原始数据（调试用） */
  raw?: unknown;
}

export interface ApiProject {
  id: string;
  name: string;
  desc?: string;
  basepath?: string;
}

/** AI 一键填充返回的填充值集合 */
export interface FilledParams {
  query: Record<string, unknown>;
  headers: Record<string, string>;
  path: Record<string, unknown>;
  body: unknown;
  /** AI 对每个字段取值理由的简述 */
  rationale?: string;
  /** 命中的项目代码片段（作为填充基准的证据） */
  evidence?: CodeEvidence[];
  /** 数据来源：ai = 大模型，heuristic = 内置规则 */
  source: "ai" | "heuristic";
}

export interface CodeEvidence {
  file: string;
  line: number;
  snippet: string;
}

export interface AssertionResult {
  description: string;
  passed: boolean;
  detail?: string;
}

export interface TestResult {
  interfaceId: string;
  request: {
    method: HttpMethod;
    url: string;
    headers: Record<string, string>;
    body?: unknown;
  };
  response?: {
    status: number;
    durationMs: number;
    headers: Record<string, string>;
    body: unknown;
  };
  assertions: AssertionResult[];
  passed: boolean;
  error?: string;
  startedAt: string;
  finishedAt: string;
}

/** 实时日志事件（WebSocket 推送） */
export interface RunEvent {
  type: "log" | "result" | "done" | "error" | "status";
  runId: string;
  message?: string;
  level?: "info" | "warn" | "error" | "success";
  payload?: unknown;
  ts: string;
}

export interface PlatformStatus {
  yapi: { configured: boolean; baseUrl?: string };
  ai: { configured: boolean; model: string };
  project: { root?: string; devApi?: string; devWeb?: string; startCommand?: string };
}
