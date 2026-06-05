import { buildPromptContext } from "./codeScanner.js";
import { fillParameters } from "./parameterFiller.js";

function extractJsonObject(content) {
  if (!content) {
    return null;
  }
  const fenced = content.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const candidate = fenced?.[1] || content;
  const start = candidate.indexOf("{");
  const end = candidate.lastIndexOf("}");
  if (start === -1 || end === -1 || end <= start) {
    return null;
  }
  try {
    return JSON.parse(candidate.slice(start, end + 1));
  } catch {
    return null;
  }
}

function canUseAi(config) {
  return Boolean(config?.enabled && config?.endpoint && config?.apiKey && config?.model);
}

export async function fillWithAiOrRules({ aiConfig, api, codeScan, overrides = {} }) {
  const ruleBased = fillParameters(api, overrides);
  if (!canUseAi(aiConfig)) {
    return {
      ...ruleBased,
      aiUsed: false,
      strategy: "rule-based",
      notes: [
        ...ruleBased.notes,
        "未配置 AI 密钥或未启用 AI，已使用本地规则填充。"
      ]
    };
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 30000);
  try {
    const prompt = [
      "你是一个资深前后端接口测试助手。",
      "请根据 YAPI 接口定义和项目代码上下文，生成一份可直接用于开发环境测试的请求参数。",
      "只返回 JSON，不要解释。JSON 结构必须是：",
      '{"request":{"method":"POST","path":"/api/demo","headers":{},"query":{},"pathParams":{},"body":{}},"confidence":0.9,"notes":["..."]}',
      "",
      "# YAPI interface",
      JSON.stringify(api, null, 2),
      "",
      buildPromptContext(codeScan || { endpoints: [], models: [], snippets: [] })
    ].join("\n");

    const response = await fetch(aiConfig.endpoint, {
      method: "POST",
      signal: controller.signal,
      headers: {
        Authorization: `Bearer ${aiConfig.apiKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: aiConfig.model,
        temperature: 0.2,
        messages: [
          {
            role: "system",
            content: "You fill API request parameters from API docs and source code. Return strict JSON only."
          },
          { role: "user", content: prompt }
        ]
      })
    });

    const payload = await response.json();
    if (!response.ok) {
      throw new Error(payload?.error?.message || `AI HTTP ${response.status}`);
    }

    const content = payload?.choices?.[0]?.message?.content || "";
    const parsed = extractJsonObject(content);
    if (!parsed?.request) {
      throw new Error("AI response did not contain a request object");
    }

    return {
      ...ruleBased,
      request: {
        ...ruleBased.request,
        ...parsed.request,
        headers: {
          ...ruleBased.request.headers,
          ...(parsed.request.headers || {}),
          ...(overrides.headers || {})
        },
        query: {
          ...ruleBased.request.query,
          ...(parsed.request.query || {}),
          ...(overrides.query || {})
        },
        pathParams: {
          ...ruleBased.request.pathParams,
          ...(parsed.request.pathParams || {}),
          ...(overrides.pathParams || {})
        },
        body: {
          ...ruleBased.request.body,
          ...(parsed.request.body || {}),
          ...(overrides.body || {})
        }
      },
      confidence: parsed.confidence ?? 0.86,
      aiUsed: true,
      strategy: "ai-assisted",
      notes: parsed.notes || ["AI 已结合 YAPI 与代码上下文生成参数。"]
    };
  } catch (error) {
    return {
      ...ruleBased,
      aiUsed: false,
      strategy: "rule-based-fallback",
      notes: [
        ...ruleBased.notes,
        `AI 调用失败，已回退到本地规则：${error.message}`
      ]
    };
  } finally {
    clearTimeout(timer);
  }
}
