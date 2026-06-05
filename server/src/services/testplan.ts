import type { ApiInterface, FilledParams, TestAssertion, TestResult } from "../types.js";
import { aiConfigured, chat, extractJson } from "./ai.js";
import { aiFill, heuristicFill } from "./fill.js";
import { runRequest, toRequest } from "./tester.js";

export interface TestCase {
  name: string;
  kind: "happy" | "validation" | "auth" | "boundary";
  filled: FilledParams;
  assertions: TestAssertion[];
}

export interface TestCaseResult extends TestCase {
  result: TestResult;
}

function defaultAssertions(iface: ApiInterface): TestAssertion[] {
  const a: TestAssertion[] = [{ type: "status", expected: 200 }];
  a.push({ type: "responseTime", expected: 3000 });
  // most chinese backends wrap with code:0
  a.push({ type: "jsonPath", expression: "code", comparator: "exists" });
  return a;
}

function heuristicPlan(iface: ApiInterface): TestCase[] {
  const happy = heuristicFill(iface);
  const cases: TestCase[] = [
    {
      name: "正常调用 (Happy Path)",
      kind: "happy",
      filled: happy,
      assertions: defaultAssertions(iface),
    },
  ];
  const hasRequired =
    iface.reqParams.some((p) => p.required) ||
    iface.reqQuery.some((p) => p.required) ||
    iface.reqBodyType === "json";
  if (hasRequired) {
    cases.push({
      name: "缺少必填参数",
      kind: "validation",
      filled: { ...happy, body: iface.reqBodyType === "json" ? {} : happy.body, query: {} },
      assertions: [{ type: "responseTime", expected: 3000 }],
    });
  }
  if (iface.reqHeaders.some((h) => h.name.toLowerCase() === "authorization")) {
    const noAuth = { ...happy, headers: { ...happy.headers } };
    delete (noAuth.headers as any).Authorization;
    cases.push({
      name: "未携带鉴权",
      kind: "auth",
      filled: noAuth,
      assertions: [{ type: "responseTime", expected: 3000 }],
    });
  }
  return cases;
}

export async function generatePlan(iface: ApiInterface): Promise<TestCase[]> {
  // base values, preferably AI-grounded
  const happy = await aiFill(iface);
  if (!aiConfigured()) {
    const plan = heuristicPlan(iface);
    plan[0].filled = happy;
    return plan;
  }
  const system =
    "你是测试架构师。请根据接口定义,设计 3-5 条测试用例(覆盖正常、参数校验、鉴权、边界场景)。严格输出 JSON 数组。";
  const user = `接口: ${iface.method} ${iface.path} - ${iface.title}\n参数: ${JSON.stringify({
    pathParams: iface.reqParams,
    query: iface.reqQuery,
    headers: iface.reqHeaders,
    bodyShape: iface.reqBody,
  })}\n\n基准正常取值: ${JSON.stringify({
    pathParams: happy.pathParams,
    query: happy.query,
    headers: happy.headers,
    body: happy.body,
  })}\n\n请输出 JSON 数组,每个元素:\n{\n "name":"用例名","kind":"happy|validation|auth|boundary",\n "filled":{"pathParams":{},"query":{},"headers":{},"body":null},\n "assertions":[{"type":"status","expected":200},{"type":"jsonPath","expression":"code","comparator":"eq","expected":0}]\n}`;
  try {
    const raw = await chat(
      [
        { role: "system", content: system },
        { role: "user", content: user },
      ],
      { json: true, temperature: 0.4 }
    );
    let parsed = extractJson(raw) as any;
    if (parsed && !Array.isArray(parsed) && Array.isArray(parsed.cases)) parsed = parsed.cases;
    if (!Array.isArray(parsed) || parsed.length === 0) {
      const plan = heuristicPlan(iface);
      plan[0].filled = happy;
      return plan;
    }
    return parsed.map((c: any, idx: number) => ({
      name: c.name || `用例 ${idx + 1}`,
      kind: c.kind || "happy",
      filled: {
        pathParams: c.filled?.pathParams || {},
        query: c.filled?.query || {},
        headers: c.filled?.headers || {},
        body: c.filled?.body ?? null,
        source: "ai" as const,
      },
      assertions: Array.isArray(c.assertions) ? c.assertions : defaultAssertions(iface),
    }));
  } catch {
    const plan = heuristicPlan(iface);
    plan[0].filled = happy;
    return plan;
  }
}

export async function runPlan(iface: ApiInterface, plan: TestCase[]): Promise<TestCaseResult[]> {
  const results: TestCaseResult[] = [];
  for (const c of plan) {
    const req = toRequest(iface, c.filled, c.assertions);
    const result = await runRequest(req);
    results.push({ ...c, result });
  }
  return results;
}
