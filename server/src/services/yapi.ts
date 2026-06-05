import { getConfig } from "../config.js";
import type { ApiInterface, ParamDef } from "../types.js";
import { demoInterfaces } from "./demoData.js";

interface YapiListItem {
  _id: number;
  title: string;
  method: string;
  path: string;
  catid?: number;
}

function safeJsonParse(text: unknown): unknown {
  if (typeof text !== "string" || !text.trim()) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

/** Convert a JSON schema (YAPI stores req_body_other as schema) into an example object. */
function schemaToExample(schema: any): unknown {
  if (!schema || typeof schema !== "object") return schema;
  if (schema.type === "object" && schema.properties) {
    const out: Record<string, unknown> = {};
    for (const key of Object.keys(schema.properties)) {
      out[key] = schemaToExample(schema.properties[key]);
    }
    return out;
  }
  if (schema.type === "array") {
    return [schemaToExample(schema.items)];
  }
  if (schema.default !== undefined) return schema.default;
  if (schema.mock?.mock) return schema.mock.mock;
  switch (schema.type) {
    case "string":
      return schema.description || "";
    case "integer":
    case "number":
      return 0;
    case "boolean":
      return false;
    default:
      return null;
  }
}

function mapParams(arr: any[] | undefined): ParamDef[] {
  if (!Array.isArray(arr)) return [];
  return arr.map((p) => ({
    name: p.name,
    type: p.type || "string",
    required: p.required === "1" || p.required === 1 || p.required === true,
    desc: p.desc || p.description || "",
    example: p.example || "",
  }));
}

function normalizeDetail(detail: any): ApiInterface {
  let reqBody: unknown;
  let reqBodyType = detail.req_body_type || "none";
  if (detail.req_body_type === "json") {
    reqBody =
      safeJsonParse(detail.req_body_other) !== undefined
        ? schemaToExample(safeJsonParse(detail.req_body_other))
        : undefined;
    if (reqBody === undefined && detail.req_body_other) {
      reqBody = safeJsonParse(detail.req_body_other);
    }
  } else if (detail.req_body_type === "form") {
    reqBody = mapParams(detail.req_body_form);
  }

  return {
    id: String(detail._id),
    title: detail.title,
    method: (detail.method || "GET").toUpperCase(),
    path: detail.path,
    catName: detail.catname,
    reqParams: mapParams(detail.req_params),
    reqQuery: mapParams(detail.req_query),
    reqHeaders: mapParams(detail.req_headers).filter(
      (h) => h.name?.toLowerCase() !== "content-type"
    ),
    reqBodyType,
    reqBody,
    resBody: safeJsonParse(detail.res_body),
    updatedAt: detail.up_time ? detail.up_time * 1000 : Date.now(),
  };
}

async function yapiGet(pathname: string, params: Record<string, string>): Promise<any> {
  const cfg = getConfig();
  const url = new URL(pathname, cfg.yapi.baseUrl);
  url.searchParams.set("token", cfg.yapi.token);
  for (const [k, v] of Object.entries(params)) {
    if (v) url.searchParams.set(k, v);
  }
  const res = await fetch(url.toString(), { signal: AbortSignal.timeout(15000) });
  const data = await res.json();
  if (data.errcode !== 0) {
    throw new Error(data.errmsg || `YAPI request failed (${data.errcode})`);
  }
  return data.data;
}

export interface YapiStatus {
  connected: boolean;
  mode: "yapi" | "demo";
  message: string;
}

export function yapiConfigured(): boolean {
  const cfg = getConfig();
  return Boolean(cfg.yapi.baseUrl && cfg.yapi.token);
}

export async function listInterfaces(): Promise<{ items: ApiInterface[]; status: YapiStatus }> {
  if (!yapiConfigured()) {
    return {
      items: demoInterfaces,
      status: {
        connected: false,
        mode: "demo",
        message: "未配置 YAPI，正在使用内置演示接口。在「设置」中填入 YAPI 地址与 Token 即可连接真实项目。",
      },
    };
  }
  try {
    const cfg = getConfig();
    const list: YapiListItem[] = await yapiGet("/api/interface/list", {
      project_id: cfg.yapi.projectId,
      page: "1",
      limit: "200",
    }).then((d) => d.list || d);

    const details = await Promise.all(
      list.map((it) => yapiGet("/api/interface/get", { id: String(it._id) }).catch(() => null))
    );
    const items = details.filter(Boolean).map(normalizeDetail);
    return {
      items,
      status: { connected: true, mode: "yapi", message: `已连接 YAPI，加载 ${items.length} 个接口。` },
    };
  } catch (err: any) {
    return {
      items: demoInterfaces,
      status: {
        connected: false,
        mode: "demo",
        message: `连接 YAPI 失败：${err.message}。已回退到演示数据。`,
      },
    };
  }
}

export async function getInterface(id: string): Promise<ApiInterface | undefined> {
  if (!yapiConfigured()) {
    return demoInterfaces.find((i) => i.id === id);
  }
  try {
    const detail = await yapiGet("/api/interface/get", { id });
    return normalizeDetail(detail);
  } catch {
    return demoInterfaces.find((i) => i.id === id);
  }
}
