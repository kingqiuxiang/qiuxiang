const demoInterface = {
  _id: "demo-login",
  title: "演示登录接口",
  method: "POST",
  path: "/api/login",
  status: "undone",
  req_headers: [
    { name: "Content-Type", value: "application/json", required: "1" }
  ],
  req_query: [],
  req_params: [],
  req_body_form: [],
  req_body_other: JSON.stringify({
    type: "object",
    required: ["username", "password"],
    properties: {
      username: { type: "string", description: "登录用户名" },
      password: { type: "string", description: "登录密码" },
      rememberMe: { type: "boolean", description: "是否保持登录" }
    }
  })
};

function hasYapiConfig(config) {
  return Boolean(config?.baseUrl && config?.projectId && config?.token);
}

function normalizeBaseUrl(baseUrl) {
  return String(baseUrl || "").replace(/\/$/, "");
}

function normalizeYapiResponse(payload) {
  if (payload?.errcode && payload.errcode !== 0) {
    throw new Error(payload.errmsg || `YAPI request failed with errcode ${payload.errcode}`);
  }
  return payload?.data ?? payload;
}

async function fetchJson(url, timeoutMs = 15000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { signal: controller.signal });
    const text = await response.text();
    const payload = text ? JSON.parse(text) : {};
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${text.slice(0, 300)}`);
    }
    return normalizeYapiResponse(payload);
  } finally {
    clearTimeout(timer);
  }
}

function yapiUrl(config, apiPath, params) {
  const url = new URL(`${normalizeBaseUrl(config.baseUrl)}${apiPath}`);
  url.searchParams.set("token", config.token);
  for (const [key, value] of Object.entries(params || {})) {
    if (value !== undefined && value !== null && value !== "") {
      url.searchParams.set(key, String(value));
    }
  }
  return url;
}

export async function listInterfaces(config, options = {}) {
  if (!hasYapiConfig(config)) {
    return {
      source: "demo",
      message: "未配置 YAPI，已返回演示接口。请在界面或 config/ai-yapi-runner.local.json 中配置 YAPI。",
      list: [demoInterface]
    };
  }

  const page = options.page || 1;
  const limit = options.limit || 100;
  const url = yapiUrl(config, "/api/interface/list", {
    project_id: config.projectId,
    page,
    limit
  });
  const data = await fetchJson(url, options.timeoutMs);
  const list = Array.isArray(data) ? data : data?.list || [];

  return {
    source: "yapi",
    total: data?.count || list.length,
    list
  };
}

export async function getInterface(config, interfaceId, options = {}) {
  if (!interfaceId || interfaceId === demoInterface._id || !hasYapiConfig(config)) {
    return {
      source: "demo",
      interface: demoInterface
    };
  }

  const url = yapiUrl(config, "/api/interface/get", { id: interfaceId });
  const data = await fetchJson(url, options.timeoutMs);
  return {
    source: "yapi",
    interface: data
  };
}
