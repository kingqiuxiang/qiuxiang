import axios from 'axios';
import type { ApiInterface, FilledRequest, Project, TestRecord } from '../types.js';

function buildUrl(project: Project, api: ApiInterface, filled: FilledRequest): string {
  let p = api.path;
  for (const [k, v] of Object.entries(filled.pathParams || {})) {
    p = p.replace(new RegExp(`\\{${k}\\}|:${k}`, 'g'), encodeURIComponent(String(v)));
  }
  const base = (project.devBaseUrl || '').replace(/\/$/, '');
  return base + (p.startsWith('/') ? p : `/${p}`);
}

export async function executeTest(
  project: Project,
  api: ApiInterface,
  filled: FilledRequest
): Promise<TestRecord['response'] & { url: string }> {
  const url = buildUrl(project, api, filled);
  const method = (api.method || 'GET').toLowerCase();
  const hasBody = !['get', 'head'].includes(method) && filled.body !== undefined && filled.body !== null;
  const start = Date.now();
  try {
    const resp = await axios.request({
      url,
      method: method as any,
      params: filled.query,
      headers: { 'Content-Type': 'application/json', ...(filled.headers || {}) },
      data: hasBody ? filled.body : undefined,
      timeout: 20000,
      validateStatus: () => true,
    });
    const durationMs = Date.now() - start;
    return {
      url,
      ok: resp.status >= 200 && resp.status < 300,
      status: resp.status,
      statusText: resp.statusText || '',
      durationMs,
      headers: Object.fromEntries(
        Object.entries(resp.headers || {}).map(([k, v]) => [k, String(v)])
      ),
      body: resp.data,
    };
  } catch (err: any) {
    const durationMs = Date.now() - start;
    return {
      url,
      ok: false,
      status: 0,
      statusText: 'NETWORK_ERROR',
      durationMs,
      headers: {},
      body: null,
      error: err?.message || '请求失败（开发环境是否已启动？）',
    };
  }
}
