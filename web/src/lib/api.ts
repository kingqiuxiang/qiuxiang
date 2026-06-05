import type {
  ApiInterface,
  CodeContext,
  FilledRequest,
  PageTestResult,
  Project,
  RunnerState,
  TestRecord,
} from './types';

const BASE = '/api';

async function req<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(BASE + url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let msg = `请求失败 (${res.status})`;
    try {
      const j = await res.json();
      msg = j.error || msg;
    } catch {
      /* noop */
    }
    throw new Error(msg);
  }
  return res.json() as Promise<T>;
}

export const api = {
  health: () =>
    req<{ ok: boolean; name: string; version: string; defaults: { ai: any; yapi: any } }>('/health'),

  listProjects: () => req<Project[]>('/projects'),
  getProject: (id: string) => req<Project>(`/projects/${id}`),
  createProject: (body: Partial<Project>) =>
    req<Project>('/projects', { method: 'POST', body: JSON.stringify(body) }),
  updateProject: (id: string, body: Partial<Project>) =>
    req<Project>(`/projects/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteProject: (id: string) => req<{ ok: boolean }>(`/projects/${id}`, { method: 'DELETE' }),
  overview: (id: string) =>
    req<{ code: { available: boolean; root: string; techHints: string[] }; runner: RunnerState; testCount: number }>(
      `/projects/${id}/overview`
    ),

  interfaces: (id: string) =>
    req<{ demo: boolean; source: string; interfaces: ApiInterface[] }>(`/projects/${id}/interfaces`),
  interfaceDetail: (id: string, iid: string) =>
    req<{ demo: boolean; interface: ApiInterface; code: CodeContext }>(`/projects/${id}/interfaces/${iid}`),
  fill: (id: string, iid: string) =>
    req<{ ai: boolean; filled: FilledRequest; code: CodeContext; interface: ApiInterface }>(
      `/projects/${id}/interfaces/${iid}/fill`,
      { method: 'POST' }
    ),
  test: (id: string, iid: string, filled: FilledRequest, source: 'manual' | 'auto' = 'manual') =>
    req<TestRecord>(`/projects/${id}/interfaces/${iid}/test`, {
      method: 'POST',
      body: JSON.stringify({ filled, source }),
    }),
  autotest: (id: string, interfaceIds?: string[]) =>
    req<{ count: number; records: TestRecord[] }>(`/projects/${id}/autotest`, {
      method: 'POST',
      body: JSON.stringify({ interfaceIds }),
    }),

  tests: (id: string) => req<TestRecord[]>(`/projects/${id}/tests`),
  clearTests: (id: string) => req<{ ok: boolean }>(`/projects/${id}/tests`, { method: 'DELETE' }),

  runnerState: (id: string) => req<RunnerState>(`/projects/${id}/runner`),
  runnerStart: (id: string) => req<RunnerState>(`/projects/${id}/runner/start`, { method: 'POST' }),
  runnerStop: (id: string) => req<RunnerState>(`/projects/${id}/runner/stop`, { method: 'POST' }),

  pageTest: (id: string, path: string) =>
    req<PageTestResult>(`/projects/${id}/page-test`, { method: 'POST', body: JSON.stringify({ path }) }),
};
