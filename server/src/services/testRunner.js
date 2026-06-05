import axios from 'axios';
import { getConfig } from '../config.js';

/** Substitute :param / {param} placeholders in a path. */
function buildPath(pathTemplate, pathParams = {}) {
  let p = pathTemplate || '';
  for (const [k, v] of Object.entries(pathParams)) {
    p = p.replace(new RegExp(`:${k}(?=/|$)`, 'g'), encodeURIComponent(v));
    p = p.replace(new RegExp(`\\{${k}\\}`, 'g'), encodeURIComponent(v));
  }
  return p;
}

/**
 * Execute one HTTP request against the dev environment.
 * @param iface normalized interface
 * @param filled { pathParams, query, headers, body }
 */
export async function runRequest(iface, filled, overrides = {}) {
  const { project } = getConfig();
  const base = (overrides.baseUrl || project.devApiBaseUrl || '').replace(/\/$/, '');
  if (!base) {
    throw Object.assign(new Error('未配置开发环境后端基址 devApiBaseUrl。'), { code: 'NO_BASE_URL' });
  }

  const url = base + buildPath(iface.path, filled.pathParams);
  const method = (iface.method || 'GET').toLowerCase();
  const headers = { ...(filled.headers || {}) };
  const hasBody = ['post', 'put', 'patch', 'delete'].includes(method) && filled.body != null;
  if (hasBody && !Object.keys(headers).some((h) => /content-type/i.test(h))) {
    headers['Content-Type'] = 'application/json';
  }

  const startedAt = Date.now();
  const requestMeta = { url, method: method.toUpperCase(), headers, query: filled.query || {}, body: filled.body ?? null };

  try {
    const res = await axios({
      url,
      method,
      headers,
      params: filled.query || {},
      data: hasBody ? filled.body : undefined,
      timeout: overrides.timeout || 20000,
      validateStatus: () => true,
      maxRedirects: 5,
    });
    return {
      ok: true,
      request: requestMeta,
      response: {
        status: res.status,
        statusText: res.statusText,
        headers: res.headers,
        data: res.data,
        durationMs: Date.now() - startedAt,
        size: estimateSize(res.data),
      },
    };
  } catch (err) {
    return {
      ok: false,
      request: requestMeta,
      error: { message: err.message, code: err.code || 'REQUEST_ERROR' },
      response: { status: 0, durationMs: Date.now() - startedAt, data: null },
    };
  }
}

function estimateSize(data) {
  try { return Buffer.byteLength(typeof data === 'string' ? data : JSON.stringify(data)); } catch { return 0; }
}
