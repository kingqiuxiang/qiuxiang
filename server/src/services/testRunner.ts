import axios, { type AxiosResponse } from "axios";
import { config } from "../config/index.js";
import { bus } from "../utils/events.js";
import type {
  ApiInterface,
  AssertionResult,
  FilledParams,
  TestResult,
} from "../types/index.js";

export interface RunOptions {
  baseUrl?: string;
  /** 期望的 HTTP 状态码，默认接受 2xx */
  expectStatus?: number;
  /** 期望响应体中包含的字段路径（点语法），如 "data.token" */
  expectFields?: string[];
}

/** 用填充参数构造并执行一次接口请求，返回带断言的结果。 */
export async function runInterfaceTest(
  iface: ApiInterface,
  filled: FilledParams,
  runId: string,
  opts: RunOptions = {},
): Promise<TestResult> {
  const startedAt = new Date().toISOString();
  const baseUrl = (opts.baseUrl ?? config.project.devApiBaseUrl).replace(/\/$/, "");

  // 路径参数替换
  let pathStr = iface.path;
  for (const [k, v] of Object.entries(filled.path ?? {})) {
    pathStr = pathStr.replace(new RegExp(`[{:]${k}}?`, "g"), encodeURIComponent(String(v)));
  }
  const url = baseUrl + pathStr;

  bus.log(runId, `→ ${iface.method} ${url}`, "info");

  const request = {
    method: iface.method,
    url,
    headers: filled.headers ?? {},
    body: filled.body,
  };

  let response: TestResult["response"];
  let error: string | undefined;
  const t0 = Date.now();

  try {
    const res: AxiosResponse = await axios.request({
      method: iface.method,
      url,
      headers: filled.headers,
      params: filled.query,
      data: ["GET", "HEAD"].includes(iface.method) ? undefined : filled.body,
      timeout: 20000,
      validateStatus: () => true,
    });
    response = {
      status: res.status,
      durationMs: Date.now() - t0,
      headers: flattenHeaders(res.headers),
      body: res.data,
    };
    bus.log(runId, `← ${res.status} (${response.durationMs}ms)`, res.status < 400 ? "success" : "warn");
  } catch (err) {
    error = (err as Error).message;
    bus.log(runId, `✗ 请求失败：${error}`, "error");
  }

  const assertions = buildAssertions(iface, response, opts);
  const passed = !error && assertions.every((a) => a.passed);

  return {
    interfaceId: iface.id,
    request,
    response,
    assertions,
    passed,
    error,
    startedAt,
    finishedAt: new Date().toISOString(),
  };
}

function buildAssertions(
  iface: ApiInterface,
  response: TestResult["response"],
  opts: RunOptions,
): AssertionResult[] {
  const results: AssertionResult[] = [];
  if (!response) {
    return [{ description: "收到响应", passed: false, detail: "无响应" }];
  }

  // 1. 状态码
  if (opts.expectStatus) {
    results.push({
      description: `状态码 == ${opts.expectStatus}`,
      passed: response.status === opts.expectStatus,
      detail: `实际 ${response.status}`,
    });
  } else {
    results.push({
      description: "状态码为 2xx/3xx",
      passed: response.status < 400,
      detail: `实际 ${response.status}`,
    });
  }

  // 2. 响应可解析为 JSON
  const isObj = response.body !== null && typeof response.body === "object";
  results.push({
    description: "响应体为 JSON 对象",
    passed: isObj,
    detail: isObj ? undefined : "响应非 JSON",
  });

  // 3. 期望字段存在
  for (const field of opts.expectFields ?? []) {
    results.push({
      description: `包含字段 ${field}`,
      passed: hasPath(response.body, field),
      detail: hasPath(response.body, field) ? undefined : "字段缺失",
    });
  }

  // 4. 与 YAPI 期望响应 schema 的顶层键比对（轻量校验）
  if (iface.resBodySchema && isObj) {
    const expectedKeys = topLevelKeys(iface.resBodySchema);
    if (expectedKeys.length) {
      const actualKeys = Object.keys(response.body as object);
      const missing = expectedKeys.filter((k) => !actualKeys.includes(k));
      results.push({
        description: "响应顶层字段符合 YAPI 定义",
        passed: missing.length === 0,
        detail: missing.length ? `缺失：${missing.join(", ")}` : undefined,
      });
    }
  }

  return results;
}

function flattenHeaders(h: unknown): Record<string, string> {
  const out: Record<string, string> = {};
  if (h && typeof h === "object") {
    for (const [k, v] of Object.entries(h as Record<string, unknown>)) out[k] = String(v);
  }
  return out;
}

export function hasPath(obj: unknown, pathStr: string): boolean {
  let cur: any = obj;
  for (const key of pathStr.split(".")) {
    if (cur == null || typeof cur !== "object" || !(key in cur)) return false;
    cur = cur[key];
  }
  return true;
}

function topLevelKeys(schema: unknown): string[] {
  const s = schema as Record<string, any> | null;
  if (s && typeof s === "object" && s.properties && typeof s.properties === "object") {
    return Object.keys(s.properties);
  }
  return [];
}
