import type { ApiInterface, FilledParams, ParamDef } from "../types.js";
import { aiConfigured, chat, extractJson } from "./ai.js";
import { projectAvailable, relevantSnippets } from "./code.js";

function heuristicValue(p: ParamDef): unknown {
  const name = p.name.toLowerCase();
  const type = (p.type || "string").toLowerCase();
  if (p.example) {
    if (type.includes("int") || type.includes("number")) {
      const n = Number(p.example);
      if (!Number.isNaN(n)) return n;
    }
    return p.example;
  }
  if (type.includes("bool")) return true;
  if (type.includes("int") || type.includes("number")) {
    if (name.includes("page") && !name.includes("size")) return 1;
    if (name.includes("size") || name.includes("limit")) return 20;
    if (name.includes("price") || name.includes("amount")) return 99;
    if (name.includes("quantity") || name.includes("count") || name.includes("num")) return 1;
    if (name.includes("id")) return 1001;
    return 0;
  }
  if (type.includes("array")) return [];
  // string heuristics by common field names
  if (name.includes("email")) return "qa.tester@example.com";
  if (name.includes("phone") || name.includes("mobile")) return "13800138000";
  if (name === "password" || name.includes("pwd")) return "Test@1234";
  if (name.includes("username") || name === "user" || name.includes("account")) return "qa_tester";
  if (name.includes("name")) return "测试名称";
  if (name.includes("token") || name.includes("authorization")) return "Bearer test-token";
  if (name.includes("captcha") || name.includes("code")) return "8888";
  if (name.includes("url") || name.includes("link")) return "https://example.com";
  if (name.includes("date") || name.includes("time")) return new Date().toISOString();
  if (name.includes("id")) return "test-" + name + "-001";
  if (name.includes("keyword") || name.includes("search") || name.includes("query")) return "test";
  if (name.includes("remark") || name.includes("desc") || name.includes("note")) return "自动化测试备注";
  return "test_" + p.name;
}

function fillSchemaExample(schema: unknown): unknown {
  if (Array.isArray(schema)) {
    return schema.map((s) => fillSchemaExample(s));
  }
  if (schema && typeof schema === "object") {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(schema as Record<string, unknown>)) {
      out[k] = fillSchemaExample(v);
    }
    return out;
  }
  // leaf node: schema string like "string" / "integer" describes the type
  if (typeof schema === "string") {
    const t = schema.toLowerCase();
    if (t === "string") return "test_value";
    if (t === "integer" || t === "number") return 1;
    if (t === "boolean") return true;
    return schema; // already a concrete value
  }
  return schema;
}

export function heuristicFill(iface: ApiInterface): FilledParams {
  const pathParams: Record<string, unknown> = {};
  for (const p of iface.reqParams) pathParams[p.name] = heuristicValue(p);
  const query: Record<string, unknown> = {};
  for (const p of iface.reqQuery) {
    if (p.required || ["page", "pageSize", "limit"].includes(p.name)) query[p.name] = heuristicValue(p);
  }
  const headers: Record<string, unknown> = {};
  for (const p of iface.reqHeaders) headers[p.name] = heuristicValue(p);

  let body: unknown;
  if (iface.reqBodyType === "json") {
    body = fillSchemaExample(iface.reqBody);
  } else if (iface.reqBodyType === "form" && Array.isArray(iface.reqBody)) {
    const form: Record<string, unknown> = {};
    for (const p of iface.reqBody as ParamDef[]) form[p.name] = heuristicValue(p);
    body = form;
  }
  return {
    pathParams,
    query,
    headers,
    body,
    source: "heuristic",
    notes: "基于参数名称/类型的智能推断值(未配置 AI 或 AI 调用失败时使用)。",
  };
}

export async function aiFill(iface: ApiInterface): Promise<FilledParams> {
  if (!aiConfigured()) return heuristicFill(iface);

  let codeContext = "";
  if (projectAvailable()) {
    try {
      const snippets = relevantSnippets(iface.path, iface.title);
      if (snippets.length) {
        codeContext =
          "以下是与该接口相关的项目代码片段(作为参数取值的事实基准):\n" +
          snippets.map((s) => `// ${s.rel}:${s.line}\n${s.text}`).join("\n");
      }
    } catch {
      /* ignore code scan errors */
    }
  }

  const spec = {
    title: iface.title,
    method: iface.method,
    path: iface.path,
    pathParams: iface.reqParams,
    query: iface.reqQuery,
    headers: iface.reqHeaders,
    bodyType: iface.reqBodyType,
    bodyShape: iface.reqBody,
  };

  const system =
    "你是一名资深 QA 工程师。请根据接口定义(来自 YAPI)以及项目代码片段,为该接口生成一组真实、可直接发起请求的测试参数。" +
    "要求:1) 取值要符合字段语义与项目代码中的约定;2) 必填项必须给值;3) 严格输出 JSON。";
  const user = `接口定义:\n${JSON.stringify(spec, null, 2)}\n\n${codeContext}\n\n请仅输出如下结构的 JSON:\n{\n  "pathParams": {},\n  "query": {},\n  "headers": {},\n  "body": <符合 bodyShape 的对象或 null>,\n  "notes": "简要说明取值依据"\n}`;

  try {
    const raw = await chat(
      [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
      { json: true, temperature: 0.3 }
    );
    const parsed = extractJson(raw) as any;
    if (!parsed || typeof parsed !== "object") return heuristicFill(iface);
    return {
      pathParams: parsed.pathParams || {},
      query: parsed.query || {},
      headers: parsed.headers || {},
      body: parsed.body ?? null,
      notes: parsed.notes || (codeContext ? "AI 结合项目代码生成。" : "AI 基于接口定义生成。"),
      source: "ai",
    };
  } catch {
    return heuristicFill(iface);
  }
}
