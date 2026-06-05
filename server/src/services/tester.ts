import { getConfig } from "../config.js";
import type { ApiInterface, FilledParams, TestAssertion, TestResult } from "../types.js";

export interface RunRequestInput {
  method: string;
  path: string;
  pathParams?: Record<string, unknown>;
  query?: Record<string, unknown>;
  headers?: Record<string, unknown>;
  body?: unknown;
  bodyType?: string;
  baseUrl?: string;
  assertions?: TestAssertion[];
}

function buildUrl(input: RunRequestInput): string {
  const base = (input.baseUrl || getConfig().devEnv.apiBaseUrl || "").replace(/\/$/, "");
  let path = input.path;
  for (const [k, v] of Object.entries(input.pathParams || {})) {
    path = path.replace(new RegExp(`[:{]${k}}?`, "g"), encodeURIComponent(String(v)));
  }
  const url = new URL(base + path);
  for (const [k, v] of Object.entries(input.query || {})) {
    if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, String(v));
  }
  return url.toString();
}

function getJsonPath(obj: unknown, expr: string): unknown {
  // supports a.b.c and a.b[0].c
  const parts = expr.replace(/\[(\d+)\]/g, ".$1").split(".").filter(Boolean);
  let cur: any = obj;
  for (const part of parts) {
    if (cur == null) return undefined;
    cur = cur[part];
  }
  return cur;
}

function evalAssertion(a: TestAssertion, ctx: { status?: number; durationMs: number; body: unknown }): {
  passed: boolean;
  message: string;
} {
  try {
    if (a.type === "status") {
      const passed = ctx.status === Number(a.expected);
      return { passed, message: `状态码 ${ctx.status} ${passed ? "==" : "!="} ${a.expected}` };
    }
    if (a.type === "responseTime") {
      const passed = ctx.durationMs <= Number(a.expected);
      return { passed, message: `响应耗时 ${ctx.durationMs}ms ${passed ? "<=" : ">"} ${a.expected}ms` };
    }
    if (a.type === "contains") {
      const text = typeof ctx.body === "string" ? ctx.body : JSON.stringify(ctx.body);
      const passed = text.includes(String(a.expected));
      return { passed, message: `响应${passed ? "包含" : "不包含"} "${a.expected}"` };
    }
    if (a.type === "jsonPath") {
      const actual = getJsonPath(ctx.body, a.expression || "");
      const cmp = a.comparator || "eq";
      let passed = false;
      if (cmp === "exists") passed = actual !== undefined && actual !== null;
      else if (cmp === "eq") passed = String(actual) === String(a.expected);
      else if (cmp === "neq") passed = String(actual) !== String(a.expected);
      else if (cmp === "lt") passed = Number(actual) < Number(a.expected);
      else if (cmp === "gt") passed = Number(actual) > Number(a.expected);
      return {
        passed,
        message: `${a.expression} = ${JSON.stringify(actual)} (${cmp} ${a.expected ?? ""})`,
      };
    }
  } catch (e: any) {
    return { passed: false, message: `断言执行异常: ${e.message}` };
  }
  return { passed: false, message: "未知断言类型" };
}

export async function runRequest(input: RunRequestInput): Promise<TestResult> {
  const url = buildUrl(input);
  const method = (input.method || "GET").toUpperCase();
  const headers: Record<string, string> = {};
  for (const [k, v] of Object.entries(input.headers || {})) {
    if (v !== undefined && v !== null) headers[k] = String(v);
  }

  let bodyInit: BodyInit | undefined;
  if (!["GET", "HEAD"].includes(method) && input.body != null) {
    if (input.bodyType === "form") {
      const params = new URLSearchParams();
      for (const [k, v] of Object.entries(input.body as Record<string, unknown>)) {
        params.set(k, String(v));
      }
      bodyInit = params;
    } else {
      if (!Object.keys(headers).some((h) => h.toLowerCase() === "content-type")) {
        headers["Content-Type"] = "application/json";
      }
      bodyInit = typeof input.body === "string" ? input.body : JSON.stringify(input.body);
    }
  }

  const started = Date.now();
  try {
    const res = await fetch(url, {
      method,
      headers,
      body: bodyInit,
      signal: AbortSignal.timeout(30000),
    });
    const durationMs = Date.now() - started;
    const text = await res.text();
    let parsed: unknown = text;
    const ctype = res.headers.get("content-type") || "";
    if (ctype.includes("application/json")) {
      try {
        parsed = JSON.parse(text);
      } catch {
        parsed = text;
      }
    }
    const respHeaders: Record<string, string> = {};
    res.headers.forEach((v, k) => (respHeaders[k] = v));

    const assertions = (input.assertions || []).map((a) => ({
      assertion: a,
      ...evalAssertion(a, { status: res.status, durationMs, body: parsed }),
    }));

    return {
      ok: assertions.every((a) => a.passed) && res.ok,
      status: res.status,
      durationMs,
      requestUrl: url,
      requestMethod: method,
      responseHeaders: respHeaders,
      responseBody: parsed,
      assertions,
    };
  } catch (err: any) {
    return {
      ok: false,
      durationMs: Date.now() - started,
      requestUrl: url,
      requestMethod: method,
      assertions: [],
      error: err.name === "TimeoutError" ? "请求超时(30s)" : err.message,
    };
  }
}

/** Convenience: build a RunRequestInput from an interface + filled params. */
export function toRequest(iface: ApiInterface, filled: FilledParams, assertions?: TestAssertion[]): RunRequestInput {
  return {
    method: iface.method,
    path: iface.path,
    pathParams: filled.pathParams,
    query: filled.query,
    headers: filled.headers,
    body: filled.body,
    bodyType: iface.reqBodyType,
    assertions,
  };
}
