const BASE = '/api';

async function request(path, { method = 'GET', body, signal } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
    signal,
  });
  const text = await res.text();
  let data;
  try { data = text ? JSON.parse(text) : {}; } catch { data = { raw: text }; }
  if (!res.ok) {
    const err = new Error(data?.error || `请求失败 ${res.status}`);
    err.code = data?.code;
    err.status = res.status;
    throw err;
  }
  return data;
}

export const api = {
  getConfig: () => request('/config'),
  saveConfig: (patch) => request('/config', { method: 'PUT', body: patch }),

  getMenu: () => request('/yapi/menu'),
  getInterface: (id, catName) => request(`/yapi/interface/${id}?catName=${encodeURIComponent(catName || '')}`),

  scanCode: (path) => request(`/code/scan${path ? `?path=${encodeURIComponent(path)}` : ''}`),
  getContext: (id, catName) => request(`/code/context/${id}?catName=${encodeURIComponent(catName || '')}`),

  aiFill: (interfaceId, catName) => request('/ai/fill', { method: 'POST', body: { interfaceId, catName } }),

  runRequest: (payload) => request('/test/run', { method: 'POST', body: payload }),
  testFrontend: (payload) => request('/test/frontend', { method: 'POST', body: payload }),
  autoTest: (payload) => request('/test/auto', { method: 'POST', body: payload }),

  projectStatus: () => request('/project/status'),
  projectStart: (body) => request('/project/start', { method: 'POST', body }),
  projectStop: () => request('/project/stop', { method: 'POST' }),
  projectReady: (body) => request('/project/ready', { method: 'POST', body }),
};

/** Subscribe to an SSE endpoint. Returns an EventSource. */
export function streamAutoTest(interfaceId, { catName = '', baseUrl = '' } = {}, handlers = {}) {
  const params = new URLSearchParams({ interfaceId, catName });
  if (baseUrl) params.set('baseUrl', baseUrl);
  const es = new EventSource(`${BASE}/test/auto/stream?${params.toString()}`);
  es.addEventListener('step', (e) => handlers.onStep?.(JSON.parse(e.data)));
  es.addEventListener('done', (e) => { handlers.onDone?.(JSON.parse(e.data)); es.close(); });
  es.addEventListener('error', (e) => {
    let payload = {};
    try { payload = e.data ? JSON.parse(e.data) : {}; } catch { /* connection error */ }
    handlers.onError?.(payload);
    es.close();
  });
  return es;
}

export function streamProjectLogs(handlers = {}) {
  const es = new EventSource(`${BASE}/project/logs/stream`);
  es.addEventListener('log', (e) => handlers.onLog?.(JSON.parse(e.data)));
  es.addEventListener('state', (e) => handlers.onState?.(JSON.parse(e.data)));
  return es;
}
