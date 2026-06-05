import fs from "node:fs/promises";
import path from "node:path";
import axios from "axios";

function normalizeMethod(method) {
  if (!method) {
    return "GET";
  }
  return String(method).toUpperCase();
}

function tryParseJson(raw) {
  if (!raw) {
    return null;
  }

  if (typeof raw === "object") {
    return raw;
  }

  try {
    return JSON.parse(raw);
  } catch (_error) {
    return null;
  }
}

function parseReqBodyTemplate(rawTemplate) {
  const parsed = tryParseJson(rawTemplate);
  if (parsed && typeof parsed === "object") {
    return parsed;
  }
  return {};
}

function flattenYapiInterfaces(raw) {
  const result = [];

  if (Array.isArray(raw?.list)) {
    raw.list.forEach((it) => result.push(it));
  }

  if (Array.isArray(raw?.data?.list)) {
    raw.data.list.forEach((it) => result.push(it));
  }

  if (Array.isArray(raw)) {
    raw.forEach((it) => result.push(it));
  }

  if (Array.isArray(raw?.data) && raw.data.length > 0 && raw.data[0]?.list) {
    raw.data.forEach((group) => {
      if (Array.isArray(group.list)) {
        group.list.forEach((it) => result.push(it));
      }
    });
  }

  return result
    .filter((item) => item && (item.path || item._id || item.title))
    .map((item, index) => {
      const id = item._id || item.id || item._id_str || `${item.method || "GET"}:${item.path || index}`;
      return {
        id: String(id),
        title: item.title || item.desc || `API-${index + 1}`,
        path: item.path || item.url || "",
        method: normalizeMethod(item.method),
        desc: item.desc || "",
        reqHeaders: Array.isArray(item.req_headers) ? item.req_headers : [],
        reqQuery: Array.isArray(item.req_query) ? item.req_query : [],
        reqBodyTemplate: parseReqBodyTemplate(item.req_body_other),
        raw: item
      };
    });
}

export async function loadYapiFromInput({ sourceType, value }) {
  let raw;
  if (sourceType === "file") {
    const absolutePath = path.isAbsolute(value) ? value : path.resolve(process.cwd(), value);
    const content = await fs.readFile(absolutePath, "utf-8");
    raw = JSON.parse(content);
  } else if (sourceType === "url") {
    const response = await axios.get(value, { timeout: 15000 });
    raw = response.data;
  } else {
    raw = JSON.parse(value);
  }

  const interfaces = flattenYapiInterfaces(raw);
  return { raw, interfaces };
}

export function findInterface(interfaces, { interfaceId, method, path: interfacePath }) {
  if (interfaceId) {
    const found = interfaces.find((it) => it.id === String(interfaceId));
    if (found) {
      return found;
    }
  }

  if (method && interfacePath) {
    const targetMethod = normalizeMethod(method);
    return (
      interfaces.find(
        (it) =>
          it.path === interfacePath &&
          normalizeMethod(it.method) === targetMethod
      ) || null
    );
  }

  return null;
}
