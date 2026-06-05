import axios from 'axios';
import { getConfig } from '../config.js';

/**
 * Convert a (subset of) JSON-Schema into a representative sample value.
 * Honors YAPI extensions: `mock`, `default`, `example`.
 */
export function schemaToSample(schema, name = '') {
  if (!schema || typeof schema !== 'object') return null;

  if (schema.default !== undefined) return schema.default;
  if (schema.example !== undefined) return schema.example;
  if (schema.mock && schema.mock.mock && !/^@/.test(String(schema.mock.mock))) {
    return schema.mock.mock;
  }
  if (Array.isArray(schema.enum) && schema.enum.length) return schema.enum[0];

  const type = Array.isArray(schema.type) ? schema.type[0] : schema.type;
  switch (type) {
    case 'object': {
      const out = {};
      const props = schema.properties || {};
      for (const key of Object.keys(props)) out[key] = schemaToSample(props[key], key);
      return out;
    }
    case 'array': {
      const item = schemaToSample(schema.items || {}, name);
      return item === null ? [] : [item];
    }
    case 'integer':
    case 'number':
      return guessNumber(name);
    case 'boolean':
      return true;
    case 'string':
      return guessString(name, schema);
    default:
      return guessString(name, schema);
  }
}

function guessNumber(name = '') {
  const n = name.toLowerCase();
  if (/(id|uid|userid)$/.test(n)) return 1;
  if (/(qty|quantity|num|amount_?count)/.test(n)) return 1;
  if (/(page|index)/.test(n)) return 1;
  if (/(size|limit|count|total)/.test(n)) return 10;
  if (/(price|amount|money|fee|balance)/.test(n)) return 99.9;
  if (/(time|stamp|date)/.test(n)) return Date.now();
  return 1;
}

function guessString(name = '', schema = {}) {
  const n = name.toLowerCase();
  if (schema.description && /例如|示例|e\.g\./i.test(schema.description)) {
    const m = schema.description.match(/(?:例如|示例|e\.g\.)[:：]?\s*([^\s，。;]+)/i);
    if (m) return m[1];
  }
  if (/email|mail/.test(n)) return 'demo@example.com';
  if (/phone|mobile|tel/.test(n)) return '13800138000';
  if (/(pwd|pass)/.test(n)) return 'P@ssw0rd';
  if (/(user|account|login|name)/.test(n)) return 'demo_user';
  if (/(token|secret|key)/.test(n)) return 'demo-token-xxxx';
  if (/url|link|avatar|img|image/.test(n)) return 'https://example.com/demo.png';
  if (/(time|date)/.test(n)) return new Date().toISOString();
  if (/(code|sms|captcha)/.test(n)) return '123456';
  if (/(status|state)/.test(n)) return 'active';
  if (/(desc|remark|note|content|msg)/.test(n)) return '测试内容';
  if (/(addr|address)/.test(n)) return '示例地址 1 号';
  return 'demo';
}

/** Normalize a raw YAPI interface detail into a clean, schema-rich shape. */
export function normalizeInterface(raw, extra = {}) {
  if (!raw) return null;
  let bodySchema = null;
  let bodyTemplate = null;
  if (raw.req_body_type === 'json' && raw.req_body_other) {
    try {
      bodySchema = JSON.parse(raw.req_body_other);
      bodyTemplate = schemaToSample(bodySchema);
    } catch {
      try { bodyTemplate = JSON.parse(raw.req_body_other); } catch { bodyTemplate = null; }
    }
  }

  let resSchema = null;
  let resExample = null;
  if (raw.res_body) {
    try {
      resSchema = JSON.parse(raw.res_body);
      if (raw.res_body_type === 'json') resExample = schemaToSample(resSchema);
    } catch { /* res_body may be a raw string */ }
  }

  return {
    id: raw._id ?? raw.id ?? null,
    title: raw.title || raw.path || '未命名接口',
    method: (raw.method || 'GET').toUpperCase(),
    path: raw.path || '',
    catName: extra.catName || raw.catname || '',
    status: raw.status || 'undone',
    pathParams: (raw.req_params || []).map((p) => ({
      name: p.name, desc: p.desc || '', example: p.example || '', type: 'string',
    })),
    query: (raw.req_query || []).map((p) => ({
      name: p.name, required: String(p.required) === '1', desc: p.desc || '', example: p.example || '', type: 'string',
    })),
    headers: (raw.req_headers || []).map((p) => ({
      name: p.name, required: String(p.required) === '1', value: p.value || '', desc: p.desc || '', example: p.example || '',
    })),
    bodyType: raw.req_body_type || (raw.req_body_form?.length ? 'form' : 'none'),
    bodyForm: (raw.req_body_form || []).map((p) => ({
      name: p.name, type: p.type || 'text', required: String(p.required) === '1', desc: p.desc || '', example: p.example || '',
    })),
    bodySchema,
    bodyTemplate,
    resSchema,
    resExample,
    markdown: raw.markdown || raw.desc || '',
  };
}

function client() {
  const { yapi } = getConfig();
  if (!yapi.baseUrl || !yapi.token) {
    const err = new Error('YAPI 未配置：请在设置中填写 baseUrl 与 token，或使用样例数据。');
    err.code = 'YAPI_NOT_CONFIGURED';
    throw err;
  }
  return axios.create({ baseURL: yapi.baseUrl.replace(/\/$/, ''), timeout: 15000 });
}

async function call(pathname, params) {
  const { yapi } = getConfig();
  const http = client();
  const { data } = await http.get(pathname, { params: { token: yapi.token, ...params } });
  if (data.errcode && data.errcode !== 0) {
    throw new Error(`YAPI 错误(${data.errcode}): ${data.errmsg}`);
  }
  return data.data;
}

export async function getProject() {
  return call('/api/project/get');
}

/** Returns categories with their interface summaries: [{ name, list: [...] }]. */
export async function listMenu() {
  const menu = await call('/api/interface/list_menu');
  return (menu || []).map((cat) => ({
    catId: cat._id,
    name: cat.name,
    desc: cat.desc || '',
    list: (cat.list || []).map((it) => ({
      id: it._id,
      title: it.title,
      method: (it.method || 'GET').toUpperCase(),
      path: it.path,
      status: it.status,
      catName: cat.name,
    })),
  }));
}

export async function getInterface(id, catName = '') {
  const raw = await call('/api/interface/get', { id });
  return normalizeInterface(raw, { catName });
}

export const isConfigured = () => {
  const { yapi } = getConfig();
  return Boolean(yapi.baseUrl && yapi.token);
};
