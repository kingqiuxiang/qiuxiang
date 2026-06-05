import { getConfig } from '../config.js';
import { schemaToSample } from './yapiService.js';
import { contextToPrompt } from './codeService.js';

const FILL_SYSTEM = `你是一名资深测试工程师。请根据 YAPI 接口定义与「项目真实代码」生成一组**可直接发送**的请求参数。
要求：
1. 参数必须满足接口约束（必填、类型、枚举），尽量贴近代码中出现的真实取值/常量/默认值。
2. 只输出 JSON，结构为：{"pathParams":{},"query":{},"headers":{},"body":<对象或null>,"notes":"简要说明你的取值依据"}。
3. 不要输出除该 JSON 以外的任何文字、解释或 markdown 代码围栏。`;

const ANALYZE_SYSTEM = `你是一名资深测试工程师。请根据接口定义、请求与真实响应，判断本次接口测试是否通过。
只输出 JSON：{"verdict":"pass"|"fail"|"warn","score":0-100,"summary":"一句话结论","assertions":[{"name":"","passed":true,"detail":""}],"suggestions":["可选的改进建议"]}。
不要输出除该 JSON 以外的任何文字或 markdown 围栏。`;

export function aiEnabled() {
  return Boolean(getConfig().ai.apiKey);
}

async function chat(messages, { json = true, maxTokens = 900 } = {}) {
  const { ai } = getConfig();
  if (!ai.apiKey) throw Object.assign(new Error('AI 未配置 API Key'), { code: 'AI_NO_KEY' });
  const url = `${ai.baseUrl.replace(/\/$/, '')}/chat/completions`;
  const body = {
    model: ai.model,
    temperature: ai.temperature ?? 0.2,
    messages,
    max_tokens: maxTokens,
  };
  if (json) body.response_format = { type: 'json_object' };

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 45000);
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${ai.apiKey}` },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    if (!res.ok) {
      const text = await res.text().catch(() => '');
      throw new Error(`AI 请求失败 ${res.status}: ${text.slice(0, 200)}`);
    }
    const data = await res.json();
    return data.choices?.[0]?.message?.content || '';
  } finally {
    clearTimeout(timer);
  }
}

function parseJsonLoose(text) {
  if (!text) return null;
  try { return JSON.parse(text); } catch { /* try to extract */ }
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fenced) { try { return JSON.parse(fenced[1]); } catch { /* continue */ } }
  const first = text.indexOf('{');
  const last = text.lastIndexOf('}');
  if (first >= 0 && last > first) {
    try { return JSON.parse(text.slice(first, last + 1)); } catch { /* give up */ }
  }
  return null;
}

/** Heuristic, schema-driven parameter generator (used as offline fallback). */
export function heuristicFill(iface) {
  const pathParams = {};
  (iface.pathParams || []).forEach((p) => { pathParams[p.name] = p.example || schemaToSample({ type: 'string' }, p.name); });

  const query = {};
  (iface.query || []).forEach((q) => { query[q.name] = q.example || schemaToSample({ type: 'string' }, q.name); });

  const headers = {};
  (iface.headers || []).forEach((h) => {
    if (/content-type/i.test(h.name)) return;
    headers[h.name] = h.value || h.example || schemaToSample({ type: 'string' }, h.name);
  });

  let body = null;
  if (iface.bodyType === 'json') {
    body = iface.bodyTemplate ?? (iface.bodySchema ? schemaToSample(iface.bodySchema) : {});
  } else if (iface.bodyType === 'form') {
    body = {};
    (iface.bodyForm || []).forEach((f) => { body[f.name] = f.example || schemaToSample({ type: f.type === 'file' ? 'string' : 'string' }, f.name); });
  }

  return { pathParams, query, headers, body, notes: '由本地启发式引擎依据 YAPI schema 与字段命名规则生成。', engine: 'heuristic' };
}

function ifaceForPrompt(iface) {
  return JSON.stringify({
    title: iface.title, method: iface.method, path: iface.path,
    pathParams: iface.pathParams, query: iface.query, headers: iface.headers,
    bodyType: iface.bodyType, bodyForm: iface.bodyForm, bodySchema: iface.bodySchema,
    desc: iface.markdown?.slice(0, 800),
  });
}

export async function fillParams(iface, codeContext) {
  if (!aiEnabled()) {
    return heuristicFill(iface);
  }
  const codeBlob = contextToPrompt(codeContext);
  const user = `# 接口定义\n${ifaceForPrompt(iface)}\n\n# 项目相关代码（以此为基准）\n${codeBlob}`;
  try {
    const content = await chat([
      { role: 'system', content: FILL_SYSTEM },
      { role: 'user', content: user },
    ]);
    const parsed = parseJsonLoose(content);
    if (!parsed) throw new Error('AI 返回无法解析为 JSON');
    return {
      pathParams: parsed.pathParams || {},
      query: parsed.query || {},
      headers: parsed.headers || {},
      body: parsed.body ?? null,
      notes: parsed.notes || '',
      engine: 'ai',
    };
  } catch (err) {
    const fallback = heuristicFill(iface);
    fallback.notes = `AI 调用失败已降级为启发式：${err.message}`;
    fallback.engine = 'heuristic-fallback';
    return fallback;
  }
}

/** Heuristic response evaluator (offline fallback). */
export function heuristicAnalyze(iface, request, response) {
  const assertions = [];
  const okStatus = response.status >= 200 && response.status < 300;
  assertions.push({ name: 'HTTP 状态码 2xx', passed: okStatus, detail: `实际 ${response.status}` });

  let businessOk = true;
  const data = response.data;
  if (data && typeof data === 'object') {
    if ('code' in data) {
      const codeOk = data.code === 0 || data.code === 200 || data.code === '0';
      businessOk = codeOk;
      assertions.push({ name: '业务 code 成功', passed: codeOk, detail: `code=${JSON.stringify(data.code)}` });
    }
    if (iface.resSchema?.properties?.data && 'data' in data) {
      assertions.push({ name: '响应包含 data 字段', passed: data.data != null, detail: '' });
    }
  }

  const passed = okStatus && businessOk;
  const failed = assertions.filter((a) => !a.passed);
  return {
    verdict: passed ? 'pass' : 'fail',
    score: passed ? 100 : Math.max(0, 100 - failed.length * 40),
    summary: passed ? '接口返回正常，断言通过。' : `存在 ${failed.length} 项断言未通过。`,
    assertions,
    suggestions: passed ? [] : ['检查请求参数是否满足后端校验', '确认开发环境服务已启动且登录态正确'],
    engine: 'heuristic',
  };
}

export async function analyzeResult(iface, request, response) {
  if (!aiEnabled()) return heuristicAnalyze(iface, request, response);
  const user = `# 接口定义\n${ifaceForPrompt(iface)}\n\n# 实际请求\n${JSON.stringify(request).slice(0, 2000)}\n\n# 实际响应\nstatus=${response.status}\nbody=${JSON.stringify(response.data).slice(0, 3000)}`;
  try {
    const content = await chat([
      { role: 'system', content: ANALYZE_SYSTEM },
      { role: 'user', content: user },
    ]);
    const parsed = parseJsonLoose(content);
    if (!parsed) throw new Error('AI 返回无法解析');
    return {
      verdict: parsed.verdict || 'warn',
      score: typeof parsed.score === 'number' ? parsed.score : 60,
      summary: parsed.summary || '',
      assertions: parsed.assertions || [],
      suggestions: parsed.suggestions || [],
      engine: 'ai',
    };
  } catch (err) {
    const fb = heuristicAnalyze(iface, request, response);
    fb.summary = `AI 分析失败已降级：${err.message}。` + fb.summary;
    fb.engine = 'heuristic-fallback';
    return fb;
  }
}
