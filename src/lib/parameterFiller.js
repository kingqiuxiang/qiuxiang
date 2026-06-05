function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function parseJson(value) {
  if (!value || typeof value !== "string") {
    return null;
  }
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function isRequired(value) {
  return value === true || value === "1" || value === 1 || value === "true";
}

function normalizeType(type) {
  const lower = String(type || "").toLowerCase();
  if (lower.includes("int") || lower.includes("long") || lower.includes("number")) {
    return "number";
  }
  if (lower.includes("bool")) {
    return "boolean";
  }
  if (lower.includes("array") || lower.includes("list")) {
    return "array";
  }
  if (lower.includes("object")) {
    return "object";
  }
  return "string";
}

function exampleValue(field) {
  return field?.example ?? field?.value ?? field?.mock ?? field?.default ?? undefined;
}

export function guessValue(name, type = "string", description = "") {
  const key = `${name} ${description}`.toLowerCase();
  const normalizedType = normalizeType(type);

  if (key.includes("email")) return "tester@example.com";
  if (key.includes("phone") || key.includes("mobile")) return "13800138000";
  if (key.includes("url") || key.includes("link")) return "https://example.com";
  if (key.includes("token")) return "dev-token";
  if (key.includes("password") || key.includes("pwd")) return "Passw0rd!";
  if (key.includes("username") || key.includes("user_name")) return "ai_tester";
  if (key === "name" || key.endsWith(" name") || key.includes("昵称")) return "AI测试用户";
  if (key.includes("title")) return "AI自动化测试标题";
  if (key.includes("desc") || key.includes("remark") || key.includes("comment")) {
    return "由 AI YAPI Runner 自动生成的测试数据";
  }
  if (key.includes("date")) return "2026-06-05";
  if (key.includes("time")) return "2026-06-05T03:25:00Z";
  if (key.endsWith("id") || key.includes("_id") || key.includes(" id")) return 10001;
  if (key.includes("count") || key.includes("size") || key.includes("page")) return 1;
  if (key.includes("enabled") || key.includes("active") || key.includes("flag")) return true;

  if (normalizedType === "number") return 1;
  if (normalizedType === "boolean") return true;
  if (normalizedType === "array") return [];
  if (normalizedType === "object") return {};
  return "test";
}

function fillObjectFromSchema(schema, requiredNames = []) {
  if (!schema || schema.type !== "object" || !schema.properties) {
    return {};
  }
  const required = new Set([...(schema.required || []), ...requiredNames]);
  const output = {};
  for (const [name, property] of Object.entries(schema.properties)) {
    const type = normalizeType(property?.type);
    if (type === "object") {
      output[name] = fillObjectFromSchema(property);
    } else if (type === "array") {
      const item = property?.items;
      output[name] = item?.type === "object" ? [fillObjectFromSchema(item)] : [guessValue(name, item?.type || "string", property?.description)];
    } else {
      output[name] =
        exampleValue(property) ?? guessValue(name, property?.type, property?.description || "");
    }
    if (!required.has(name) && property?.required === false) {
      continue;
    }
  }
  return output;
}

function fillCollection(fields) {
  const output = {};
  for (const field of asArray(fields)) {
    const name = field.name || field._id;
    if (!name) {
      continue;
    }
    output[name] = exampleValue(field) ?? guessValue(name, field.type, field.desc || field.description);
  }
  return output;
}

function normalizeMethod(method) {
  return String(method || "GET").toUpperCase();
}

export function normalizeInterface(api) {
  return {
    id: api?._id || api?.id || api?.interfaceId || "",
    title: api?.title || api?.name || "未命名接口",
    method: normalizeMethod(api?.method),
    path: api?.path || api?.url || "/",
    headers: asArray(api?.req_headers),
    query: asArray(api?.req_query),
    pathParams: asArray(api?.req_params),
    form: asArray(api?.req_body_form),
    bodySchema: parseJson(api?.req_body_other),
    raw: api || {}
  };
}

export function fillParameters(api, overrides = {}) {
  const normalized = normalizeInterface(api);
  const headers = {
    ...fillCollection(normalized.headers),
    ...(overrides.headers || {})
  };
  const query = {
    ...fillCollection(normalized.query),
    ...(overrides.query || {})
  };
  const pathParams = {
    ...fillCollection(normalized.pathParams),
    ...(overrides.pathParams || {})
  };
  const bodyFromSchema = fillObjectFromSchema(normalized.bodySchema);
  const form = fillCollection(normalized.form);
  const body =
    Object.keys(bodyFromSchema).length > 0
      ? bodyFromSchema
      : Object.keys(form).length > 0
        ? form
        : {};

  if (Object.keys(body).length > 0 && !headers["Content-Type"] && !headers["content-type"]) {
    headers["Content-Type"] = "application/json";
  }

  return {
    interface: {
      id: normalized.id,
      title: normalized.title,
      method: normalized.method,
      path: normalized.path
    },
    request: {
      method: normalized.method,
      path: normalized.path,
      headers,
      query,
      pathParams,
      body: {
        ...body,
        ...(overrides.body || {})
      }
    },
    confidence: Object.keys(body).length || normalized.query.length ? 0.72 : 0.55,
    strategy: "rule-based",
    notes: [
      "优先使用 YAPI 示例值/default/mock 字段。",
      "缺失示例时根据字段名、类型和描述生成安全的开发测试数据。"
    ],
    requiredFields: {
      headers: normalized.headers.filter((field) => isRequired(field.required)).map((field) => field.name),
      query: normalized.query.filter((field) => isRequired(field.required)).map((field) => field.name),
      body: normalized.bodySchema?.required || []
    }
  };
}
