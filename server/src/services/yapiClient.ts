import axios from "axios";
import { config } from "../config/index.js";
import { logger } from "../utils/logger.js";
import type { ApiInterface, ApiParam, ApiProject, HttpMethod } from "../types/index.js";
import { mockInterfaces, mockProject } from "./mockData.js";

interface YapiRawParam {
  name: string;
  required?: string; // "1" | "0"
  desc?: string;
  example?: string;
  type?: string;
  value?: string;
}

interface YapiRawInterface {
  _id: number;
  title: string;
  path: string;
  method: string;
  desc?: string;
  catid?: number;
  req_query?: YapiRawParam[];
  req_headers?: YapiRawParam[];
  req_params?: YapiRawParam[];
  req_body_type?: string;
  req_body_other?: string;
  req_body_form?: YapiRawParam[];
  res_body_type?: string;
  res_body?: string;
}

/**
 * YAPI 客户端：封装 YAPI Open API，并在未配置时回退到内置演示数据，
 * 保证「读取 YAPI 参数」这一核心能力始终可用、可演示。
 */
class YapiClient {
  get isMock() {
    return !config.yapi.configured;
  }

  private http() {
    return axios.create({
      baseURL: config.yapi.baseUrl,
      timeout: 15000,
      params: { token: config.yapi.token },
    });
  }

  async getProject(): Promise<ApiProject> {
    if (this.isMock) return mockProject;
    try {
      const { data } = await this.http().get("/api/project/get");
      const p = data?.data ?? {};
      return {
        id: String(p._id ?? config.yapi.projectId ?? "unknown"),
        name: p.name ?? "YAPI 项目",
        desc: p.desc,
        basepath: p.basepath,
      };
    } catch (err) {
      logger.error("获取 YAPI 项目失败，回退演示数据：", (err as Error).message);
      return mockProject;
    }
  }

  async listInterfaces(): Promise<ApiInterface[]> {
    if (this.isMock) return mockInterfaces;
    try {
      const menu = await this.http().get("/api/interface/list_menu");
      const cats = (menu.data?.data ?? []) as Array<{ _id: number; name: string; list?: any[] }>;
      const result: ApiInterface[] = [];
      for (const cat of cats) {
        for (const item of cat.list ?? []) {
          const full = await this.getInterface(String(item._id));
          if (full) {
            full.catName = cat.name;
            result.push(full);
          }
        }
      }
      return result;
    } catch (err) {
      logger.error("获取 YAPI 接口列表失败，回退演示数据：", (err as Error).message);
      return mockInterfaces;
    }
  }

  async getInterface(id: string): Promise<ApiInterface | null> {
    if (this.isMock) return mockInterfaces.find((i) => i.id === id) ?? null;
    try {
      const { data } = await this.http().get("/api/interface/get", { params: { id } });
      const raw = data?.data as YapiRawInterface | undefined;
      if (!raw) return null;
      return normalizeInterface(raw);
    } catch (err) {
      logger.error(`获取 YAPI 接口 ${id} 失败：`, (err as Error).message);
      return null;
    }
  }
}

function safeJson(text?: string): unknown {
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export function normalizeInterface(raw: YapiRawInterface): ApiInterface {
  const params: ApiParam[] = [];

  for (const q of raw.req_query ?? []) {
    params.push({ name: q.name, in: "query", type: q.type ?? "string", required: q.required === "1", desc: q.desc, example: q.example });
  }
  for (const h of raw.req_headers ?? []) {
    params.push({ name: h.name, in: "header", type: "string", required: h.required === "1", desc: h.desc, example: h.value ?? h.example });
  }
  for (const p of raw.req_params ?? []) {
    params.push({ name: p.name, in: "path", type: "string", required: true, desc: p.desc, example: p.example });
  }
  if (raw.req_body_type === "form") {
    for (const f of raw.req_body_form ?? []) {
      params.push({ name: f.name, in: "form", type: f.type ?? "string", required: f.required === "1", desc: f.desc, example: f.example });
    }
  }

  const reqBodySchema = raw.req_body_type === "json" ? safeJson(raw.req_body_other) : undefined;
  const resBodySchema = raw.res_body_type === "json" ? safeJson(raw.res_body) : undefined;

  return {
    id: String(raw._id),
    title: raw.title,
    path: raw.path,
    method: (raw.method?.toUpperCase() as HttpMethod) ?? "GET",
    desc: raw.desc,
    params,
    reqBodySchema,
    resBodySchema,
    raw,
  };
}

export const yapiClient = new YapiClient();
