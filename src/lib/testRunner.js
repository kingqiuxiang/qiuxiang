function applyPathParams(pathTemplate, pathParams = {}) {
  let output = pathTemplate || "/";
  for (const [name, value] of Object.entries(pathParams)) {
    output = output
      .replace(new RegExp(`:${name}\\b`, "g"), encodeURIComponent(String(value)))
      .replace(new RegExp(`\\{${name}\\}`, "g"), encodeURIComponent(String(value)));
  }
  return output;
}

function buildTargetUrl(baseUrl, request) {
  const pathWithParams = applyPathParams(request.path, request.pathParams);
  const url = new URL(pathWithParams, `${baseUrl.replace(/\/$/, "")}/`);
  for (const [key, value] of Object.entries(request.query || {})) {
    if (value !== undefined && value !== null && value !== "") {
      url.searchParams.set(key, String(value));
    }
  }
  return url;
}

function maybeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export async function runApiTest({ baseUrl, request, timeoutMs = 15000 }) {
  if (!baseUrl) {
    throw new Error("缺少 targetBaseUrl，请配置开发环境后端地址。");
  }
  if (!request?.path) {
    throw new Error("缺少 request.path，无法测试接口。");
  }

  const url = buildTargetUrl(baseUrl, request);
  const method = String(request.method || "GET").toUpperCase();
  const headers = { ...(request.headers || {}) };
  const hasBody = !["GET", "HEAD"].includes(method) && request.body && Object.keys(request.body).length > 0;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const startedAt = performance.now();

  try {
    const response = await fetch(url, {
      method,
      headers,
      signal: controller.signal,
      body: hasBody ? JSON.stringify(request.body) : undefined
    });
    const text = await response.text();
    const durationMs = Math.round(performance.now() - startedAt);
    return {
      ok: response.ok,
      status: response.status,
      statusText: response.statusText,
      durationMs,
      url: url.toString(),
      request: {
        method,
        headers,
        query: request.query || {},
        pathParams: request.pathParams || {},
        body: hasBody ? request.body : undefined
      },
      response: {
        headers: Object.fromEntries(response.headers.entries()),
        body: maybeJson(text)
      },
      assertions: [
        {
          name: "HTTP 状态码小于 500",
          pass: response.status < 500
        },
        {
          name: "接口在超时时间内返回",
          pass: durationMs <= timeoutMs
        }
      ]
    };
  } finally {
    clearTimeout(timer);
  }
}
