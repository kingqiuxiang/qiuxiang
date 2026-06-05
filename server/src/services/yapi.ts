import axios from 'axios';
import type { ApiInterface, ApiParam, Project } from '../types.js';
import { demoInterfaces } from './demo.js';

function bool(v: any): boolean {
  return v === '1' || v === 1 || v === true || v === 'true';
}

function mapParams(arr: any[] | undefined): ApiParam[] {
  if (!Array.isArray(arr)) return [];
  return arr.map((p) => ({
    name: p.name,
    required: bool(p.required),
    type: p.type || 'string',
    desc: p.desc || '',
    example: p.example ?? p.value ?? '',
  }));
}

function parseSchema(raw: any): any {
  if (!raw) return undefined;
  if (typeof raw === 'object') return raw;
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

export interface YApiFetchResult {
  demo: boolean;
  source: string;
  interfaces: ApiInterface[];
}

/** 判断项目是否具备真实 YAPI 连接配置 */
export function hasYApi(project: Project): boolean {
  return Boolean(project.yapi?.baseUrl && project.yapi?.token);
}

/** 拉取项目下所有接口（含降级到内置演示数据） */
export async function fetchInterfaces(project: Project): Promise<YApiFetchResult> {
  if (!hasYApi(project)) {
    return { demo: true, source: '演示数据（未配置 YAPI）', interfaces: demoInterfaces() };
  }
  const { baseUrl, token, projectId } = project.yapi;
  try {
    const base = baseUrl.replace(/\/$/, '');
    const params: Record<string, any> = { token, page: 1, limit: 1000 };
    if (projectId) params.project_id = projectId;
    const { data } = await axios.get(`${base}/api/interface/list`, { params, timeout: 15000 });
    if (data?.errcode !== 0) throw new Error(data?.errmsg || 'YAPI 返回错误');
    const list = data?.data?.list ?? [];
    const interfaces: ApiInterface[] = list.map((it: any) => ({
      id: String(it._id),
      title: it.title,
      path: it.path,
      method: (it.method || 'GET').toUpperCase(),
      catName: it.catname || '',
      status: it.status,
      reqParams: [],
      reqQuery: [],
      reqHeaders: [],
      updatedAt: it.up_time ? it.up_time * 1000 : undefined,
    }));
    return { demo: false, source: base, interfaces };
  } catch (err: any) {
    return {
      demo: true,
      source: `YAPI 连接失败，已降级为演示数据：${err.message}`,
      interfaces: demoInterfaces(),
    };
  }
}

/** 获取单个接口详情（含降级） */
export async function fetchInterfaceDetail(
  project: Project,
  interfaceId: string
): Promise<{ demo: boolean; interface: ApiInterface }> {
  if (!hasYApi(project)) {
    const found = demoInterfaces().find((i) => i.id === interfaceId) ?? demoInterfaces()[0];
    return { demo: true, interface: found };
  }
  const base = project.yapi.baseUrl.replace(/\/$/, '');
  try {
    const { data } = await axios.get(`${base}/api/interface/get`, {
      params: { id: interfaceId, token: project.yapi.token },
      timeout: 15000,
    });
    if (data?.errcode !== 0) throw new Error(data?.errmsg || 'YAPI 返回错误');
    const d = data.data;
    const reqBodyType = d.req_body_type === 'form' ? 'form' : d.req_body_other ? 'json' : 'none';
    const detail: ApiInterface = {
      id: String(d._id),
      title: d.title,
      path: d.path,
      method: (d.method || 'GET').toUpperCase(),
      catName: d.catname || '',
      status: d.status,
      reqParams: mapParams(d.req_params),
      reqQuery: mapParams(d.req_query),
      reqHeaders: mapParams(d.req_headers),
      reqBodyType,
      reqBody: reqBodyType === 'json' ? parseSchema(d.req_body_other) : mapParams(d.req_body_form),
      resBody: parseSchema(d.res_body),
      updatedAt: d.up_time ? d.up_time * 1000 : undefined,
    };
    return { demo: false, interface: detail };
  } catch {
    const found = demoInterfaces().find((i) => i.id === interfaceId) ?? demoInterfaces()[0];
    return { demo: true, interface: found };
  }
}
