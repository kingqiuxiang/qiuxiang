import type { ApiParam } from "../types/index.js";

/**
 * 依据 JSON Schema 生成一个合理的示例值。
 * 用于「内置启发式」参数填充以及为 AI 提供初始草稿。
 */
export function sampleFromSchema(schema: unknown, fieldName = ""): unknown {
  if (schema == null || typeof schema !== "object") return null;
  const s = schema as Record<string, any>;

  if (s.example !== undefined) return s.example;
  if (s.default !== undefined) return s.default;
  if (Array.isArray(s.enum) && s.enum.length > 0) return s.enum[0];

  const type = Array.isArray(s.type) ? s.type[0] : s.type;

  switch (type) {
    case "object": {
      const out: Record<string, unknown> = {};
      const props = s.properties ?? {};
      for (const key of Object.keys(props)) {
        out[key] = sampleFromSchema(props[key], key);
      }
      return out;
    }
    case "array":
      return [sampleFromSchema(s.items ?? {}, fieldName)];
    case "integer":
    case "number":
      return guessNumber(fieldName);
    case "boolean":
      return true;
    case "string":
      return guessString(fieldName, s.format);
    default:
      // 没有显式 type 但有 properties，按 object 处理
      if (s.properties) return sampleFromSchema({ ...s, type: "object" }, fieldName);
      return guessString(fieldName);
  }
}

/** 根据字段名语义猜测一个字符串示例（启发式核心） */
export function guessString(name: string, format?: string): string {
  const n = name.toLowerCase();
  if (format === "date-time" || /time|date/.test(n)) return new Date().toISOString();
  if (format === "email" || /e?mail/.test(n)) return "test@example.com";
  if (/phone|mobile|tel/.test(n)) return "13800138000";
  if (/url|link|website|avatar|img|image/.test(n)) return "https://example.com/x.png";
  if (/(^|_)id$|Id$|uuid|guid/.test(name)) return "1";
  if (/token|secret|key|password|pwd/.test(n)) return "test-" + name;
  if (/name|title|nick/.test(n)) return "测试" + name;
  if (/status|state/.test(n)) return "active";
  if (/code/.test(n)) return "0";
  if (/color/.test(n)) return "#3b82f6";
  return "test_" + (name || "value");
}

function guessNumber(name: string): number {
  const n = name.toLowerCase();
  if (/page(num|index|no)?$/.test(n)) return 1;
  if (/(page)?size|limit|count|num/.test(n)) return 10;
  if (/age/.test(n)) return 18;
  if (/price|amount|money|fee/.test(n)) return 99.9;
  if (/id$/.test(n)) return 1;
  return 1;
}

/**
 * 将 YAPI 的请求体 schema 与 query/header/path 参数合并为统一的 ApiParam 列表。
 */
export function paramsToExamples(params: ApiParam[]): {
  query: Record<string, unknown>;
  headers: Record<string, string>;
  path: Record<string, unknown>;
} {
  const query: Record<string, unknown> = {};
  const headers: Record<string, string> = {};
  const pathParams: Record<string, unknown> = {};

  for (const p of params) {
    const value =
      p.example !== undefined && p.example !== ""
        ? p.example
        : guessString(p.name);
    if (p.in === "query") query[p.name] = value;
    else if (p.in === "header") headers[p.name] = String(value);
    else if (p.in === "path") pathParams[p.name] = value;
  }
  return { query, headers, path: pathParams };
}
