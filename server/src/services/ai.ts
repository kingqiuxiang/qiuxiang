import axios from 'axios';
import type { AIConfig, ApiInterface, ApiParam, FilledRequest, TestRecord } from '../types.js';
import type { CodeContext } from './code.js';

export function hasAI(ai?: AIConfig): boolean {
  return Boolean(ai?.apiKey && ai?.baseUrl && ai?.model);
}

async function chat(ai: AIConfig, system: string, user: string): Promise<string> {
  const base = ai.baseUrl.replace(/\/$/, '');
  const url = base.endsWith('/chat/completions') ? base : `${base}/chat/completions`;
  const { data } = await axios.post(
    url,
    {
      model: ai.model,
      messages: [
        { role: 'system', content: system },
        { role: 'user', content: user },
      ],
      temperature: 0.3,
      response_format: { type: 'json_object' },
    },
    {
      headers: { Authorization: `Bearer ${ai.apiKey}`, 'Content-Type': 'application/json' },
      timeout: 60000,
    }
  );
  return data?.choices?.[0]?.message?.content ?? '';
}

function safeParse(text: string): any {
  if (!text) return null;
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/);
  const candidate = fenced ? fenced[1] : text;
  try {
    return JSON.parse(candidate);
  } catch {
    const m = candidate.match(/\{[\s\S]*\}/);
    if (m) {
      try {
        return JSON.parse(m[0]);
      } catch {
        return null;
      }
    }
    return null;
  }
}

/* ----------------------- 启发式 Mock（无 AI 时降级） ----------------------- */

let seed = 1;
function rnd(max: number) {
  seed = (seed * 9301 + 49297) % 233280;
  return Math.floor((seed / 233280) * max);
}

function mockValueByType(type: string, name = ''): any {
  const t = (type || 'string').toLowerCase();
  const n = name.toLowerCase();
  if (t.includes('int') || t.includes('number') || t.includes('long') || t.includes('float')) {
    if (n.includes('page') && !n.includes('size')) return 1;
    if (n.includes('size') || n.includes('limit')) return 20;
    if (n.includes('id')) return 10000 + rnd(89999);
    if (n.includes('price') || n.includes('amount')) return 99.9;
    if (n.includes('quantity') || n.includes('count') || n.includes('num')) return 2;
    return rnd(100);
  }
  if (t.includes('bool')) return true;
  if (t.includes('array')) return [];
  if (t.includes('object')) return {};
  // string heuristics
  if (n.includes('email')) return 'test@example.com';
  if (n.includes('phone') || n.includes('mobile')) return '13800138000';
  if (n.includes('password') || n.includes('pwd')) return 'P@ssw0rd123';
  if (n.includes('user') || n.includes('account')) return 'demo_user';
  if (n.includes('name')) return '测试名称';
  if (n.includes('token') || n.includes('authorization')) return 'Bearer demo-token';
  if (n.includes('captcha') || n.includes('code')) return '8888';
  if (n.includes('keyword') || n.includes('q') || n.includes('search')) return '机械键盘';
  if (n.includes('sort')) return 'sales';
  if (n.includes('status')) return 'paid';
  if (n.includes('time') || n.includes('date')) return new Date().toISOString();
  if (n.includes('url') || n.includes('avatar')) return 'https://example.com/a.png';
  if (n.includes('content-type')) return 'application/json';
  return `示例_${name || 'value'}`;
}

function mockFromSchema(schema: any): any {
  if (!schema || typeof schema !== 'object') return {};
  if (schema.type === 'object' || schema.properties) {
    const out: Record<string, any> = {};
    const props = schema.properties || {};
    for (const key of Object.keys(props)) {
      const p = props[key];
      if (p.type === 'object' || p.properties) out[key] = mockFromSchema(p);
      else if (p.type === 'array') out[key] = [mockValueByType(p.items?.type || 'string', key)];
      else out[key] = mockValueByType(p.type || 'string', key);
    }
    return out;
  }
  return mockValueByType(schema.type || 'string');
}

function paramsToObj(params: ApiParam[]): Record<string, any> {
  const out: Record<string, any> = {};
  for (const p of params) {
    if (p.example !== undefined && p.example !== '') {
      const t = (p.type || '').toLowerCase();
      out[p.name] = t.includes('int') || t.includes('number') ? Number(p.example) || p.example : p.example;
    } else {
      out[p.name] = mockValueByType(p.type, p.name);
    }
  }
  return out;
}

function mockFill(api: ApiInterface): FilledRequest {
  const body =
    api.reqBodyType === 'json'
      ? mockFromSchema(api.reqBody)
      : api.reqBodyType === 'form'
      ? paramsToObj((api.reqBody as ApiParam[]) || [])
      : undefined;
  return {
    pathParams: paramsToObj(api.reqParams),
    query: paramsToObj(api.reqQuery),
    headers: paramsToObj(api.reqHeaders),
    body,
    reasoning: '（演示模式）依据接口字段类型与命名规则启发式生成测试参数。',
  };
}

/* ----------------------------- 对外能力 ----------------------------- */

export interface FillResult {
  ai: boolean;
  filled: FilledRequest;
}

/** AI 一键参数填充：以接口定义 + 项目代码为上下文生成测试参数 */
export async function fillParameters(
  ai: AIConfig,
  api: ApiInterface,
  code: CodeContext
): Promise<FillResult> {
  if (!hasAI(ai)) {
    return { ai: false, filled: mockFill(api) };
  }
  const system =
    '你是资深测试工程师。请根据给定的接口定义和项目源码上下文，生成一组真实、合理、可直接发起请求的测试参数。' +
    '严格输出 JSON 对象，结构为 {"pathParams":{},"query":{},"headers":{},"body":{},"reasoning":"中文说明"}。' +
    '数值字段输出数字类型；如接口无请求体则 body 为 null。优先复用代码中出现的真实枚举值/默认值。';
  const user = JSON.stringify({
    接口: {
      title: api.title,
      method: api.method,
      path: api.path,
      reqParams: api.reqParams,
      reqQuery: api.reqQuery,
      reqHeaders: api.reqHeaders,
      reqBodyType: api.reqBodyType,
      reqBody: api.reqBody,
    },
    项目代码片段: code.snippets.slice(0, 6).map((s) => ({ 文件: s.file, 代码: s.preview })),
  });
  try {
    const text = await chat(ai, system, user);
    const parsed = safeParse(text);
    if (!parsed) throw new Error('AI 返回无法解析');
    const filled: FilledRequest = {
      pathParams: parsed.pathParams || {},
      query: parsed.query || {},
      headers: parsed.headers || {},
      body: parsed.body ?? undefined,
      reasoning: parsed.reasoning || 'AI 已根据接口定义与代码上下文生成参数。',
    };
    return { ai: true, filled };
  } catch {
    const fallback = mockFill(api);
    fallback.reasoning = 'AI 调用失败，已降级为启发式生成。';
    return { ai: false, filled: fallback };
  }
}

export interface AnalysisResult {
  ai: boolean;
  analysis: NonNullable<TestRecord['analysis']>;
}

/** AI 分析测试结果：对比响应与期望 schema，给出通过判定与问题列表 */
export async function analyzeResult(
  ai: AIConfig,
  api: ApiInterface,
  record: Pick<TestRecord, 'request' | 'response'>
): Promise<AnalysisResult> {
  const httpOk = record.response.ok;
  if (!hasAI(ai)) {
    const issues: string[] = [];
    if (!httpOk) issues.push(`HTTP 状态码 ${record.response.status} 非 2xx`);
    const body = record.response.body;
    if (body && typeof body === 'object' && 'code' in body && body.code !== 0 && body.code !== 200) {
      issues.push(`业务 code=${(body as any).code}，可能为业务失败`);
    }
    const passed = httpOk && issues.length === 0;
    return {
      ai: false,
      analysis: {
        passed,
        score: passed ? 95 : 40,
        summary: passed ? '（演示模式）请求成功，响应结构基本符合预期。' : '（演示模式）检测到潜在问题。',
        issues,
      },
    };
  }
  const system =
    '你是接口测试评审专家。请判断本次接口测试是否通过，并指出问题。' +
    '严格输出 JSON：{"passed":bool,"score":0-100整数,"summary":"中文总结","issues":["问题1"]}。';
  const user = JSON.stringify({
    接口: { title: api.title, method: api.method, path: api.path, 期望响应Schema: api.resBody },
    请求: record.request,
    响应: {
      status: record.response.status,
      ok: record.response.ok,
      durationMs: record.response.durationMs,
      body: record.response.body,
      error: record.response.error,
    },
  });
  try {
    const text = await chat(ai, system, user);
    const parsed = safeParse(text);
    if (!parsed) throw new Error('无法解析');
    return {
      ai: true,
      analysis: {
        passed: Boolean(parsed.passed),
        score: Number(parsed.score) || (parsed.passed ? 90 : 30),
        summary: parsed.summary || '',
        issues: Array.isArray(parsed.issues) ? parsed.issues : [],
      },
    };
  } catch {
    return {
      ai: false,
      analysis: {
        passed: httpOk,
        score: httpOk ? 85 : 35,
        summary: 'AI 分析失败，已退回基础校验。',
        issues: httpOk ? [] : [`HTTP 状态码 ${record.response.status}`],
      },
    };
  }
}
