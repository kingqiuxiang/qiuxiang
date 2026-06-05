import { aiClient, type ChatMessage } from "./aiClient.js";
import { codeIndexer } from "./codeIndexer.js";
import { sampleFromSchema, paramsToExamples } from "../utils/schema.js";
import { logger } from "../utils/logger.js";
import type { ApiInterface, FilledParams, CodeEvidence } from "../types/index.js";

/**
 * AI 一键参数填充：
 *  1. 收集接口定义 + 项目代码证据（以项目代码为基准）；
 *  2. 优先调用 AI 生成贴近真实业务的参数；
 *  3. 失败或未配置时回退到内置启发式生成，保证始终可用。
 */
export async function fillParams(iface: ApiInterface): Promise<FilledParams> {
  const evidence = codeIndexer.searchForInterface(iface);

  if (aiClient.configured) {
    try {
      return await fillWithAi(iface, evidence);
    } catch (err) {
      logger.error("AI 填充失败，回退启发式：", (err as Error).message);
    }
  }
  return fillHeuristic(iface, evidence);
}

async function fillWithAi(iface: ApiInterface, evidence: CodeEvidence[]): Promise<FilledParams> {
  const draft = fillHeuristic(iface, evidence);
  const evidenceText = evidence
    .map((e) => `# ${e.file}:${e.line}\n${e.snippet}`)
    .join("\n\n")
    .slice(0, 6000);

  const messages: ChatMessage[] = [
    {
      role: "system",
      content:
        "你是接口测试参数生成专家。请依据接口定义与「项目真实代码片段」，" +
        "生成最贴近真实业务、可直接用于联调的请求参数。" +
        "严格输出 JSON，结构为：{query:{}, headers:{}, path:{}, body:any, rationale:string}。" +
        "rationale 用中文简述取值依据（尤其是从代码中得到的约束，如枚举、ID、token 格式等）。",
    },
    {
      role: "user",
      content: [
        `接口：${iface.method} ${iface.path}（${iface.title}）`,
        iface.desc ? `说明：${iface.desc}` : "",
        `参数定义：${JSON.stringify(iface.params)}`,
        iface.reqBodySchema ? `请求体 Schema：${JSON.stringify(iface.reqBodySchema)}` : "",
        `启发式草稿（供参考，可修正）：${JSON.stringify({ query: draft.query, headers: draft.headers, path: draft.path, body: draft.body })}`,
        evidenceText ? `项目代码证据：\n${evidenceText}` : "（无可用代码证据，请基于接口定义合理推断）",
      ]
        .filter(Boolean)
        .join("\n\n"),
    },
  ];

  const result = await aiClient.chatJson<Partial<FilledParams>>(messages);
  return {
    query: result.query ?? draft.query,
    headers: normalizeHeaders(result.headers) ?? draft.headers,
    path: result.path ?? draft.path,
    body: result.body ?? draft.body,
    rationale: result.rationale ?? "由 AI 依据接口定义与项目代码生成。",
    evidence,
    source: "ai",
  };
}

export function fillHeuristic(iface: ApiInterface, evidence: CodeEvidence[] = []): FilledParams {
  const { query, headers, path } = paramsToExamples(iface.params);

  // 表单字段并入 body
  const formParams = iface.params.filter((p) => p.in === "form");
  let body: unknown = undefined;
  if (iface.reqBodySchema) {
    body = sampleFromSchema(iface.reqBodySchema);
  } else if (formParams.length > 0) {
    const form: Record<string, unknown> = {};
    for (const f of formParams) form[f.name] = f.example ?? "test_" + f.name;
    body = form;
  } else {
    // 没有显式 schema 的 body 参数
    const bodyParams = iface.params.filter((p) => p.in === "body");
    if (bodyParams.length > 0) {
      const obj: Record<string, unknown> = {};
      for (const p of bodyParams) {
        obj[p.name] =
          p.example !== undefined && p.example !== ""
            ? p.example
            : sampleFromSchema({ type: p.type }, p.name);
      }
      body = obj;
    }
  }

  // 常见鉴权头补充示例
  for (const key of Object.keys(headers)) {
    if (/authorization|token/i.test(key) && !headers[key]) {
      headers[key] = "Bearer test-token";
    }
  }

  return {
    query,
    headers,
    path,
    body,
    rationale: "由内置启发式根据参数名语义与 JSON Schema 生成。",
    evidence,
    source: "heuristic",
  };
}

function normalizeHeaders(h?: Record<string, unknown>): Record<string, string> | undefined {
  if (!h) return undefined;
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(h)) out[k] = String(v);
  return out;
}
