import axios from "axios";
import { config } from "../config.js";

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value || {}));
}

function flattenObjectKeys(obj, prefix = "", keys = []) {
  if (!obj || typeof obj !== "object") {
    return keys;
  }
  Object.entries(obj).forEach(([key, value]) => {
    const fullKey = prefix ? `${prefix}.${key}` : key;
    keys.push(fullKey);
    if (value && typeof value === "object" && !Array.isArray(value)) {
      flattenObjectKeys(value, fullKey, keys);
    }
  });
  return keys;
}

function defaultValueByKey(key) {
  const lower = key.toLowerCase();
  if (lower.includes("id")) {
    return "10001";
  }
  if (lower.includes("phone") || lower.includes("mobile")) {
    return "13800000000";
  }
  if (lower.includes("email")) {
    return "demo@example.com";
  }
  if (lower.includes("name")) {
    return "demo-user";
  }
  if (lower.includes("token")) {
    return "token-demo-value";
  }
  if (lower.includes("time") || lower.includes("date")) {
    return new Date().toISOString();
  }
  return "demo";
}

function fillByHeuristic(template, paramHints = {}) {
  function walker(value, keyPath = "") {
    if (Array.isArray(value)) {
      if (value.length === 0) {
        return [defaultValueByKey(keyPath || "item")];
      }
      return value.map((it) => walker(it, keyPath));
    }
    if (value && typeof value === "object") {
      const next = {};
      Object.entries(value).forEach(([k, v]) => {
        const full = keyPath ? `${keyPath}.${k}` : k;
        next[k] = walker(v, full);
      });
      return next;
    }

    const leaf = keyPath.split(".").pop() || keyPath || "field";
    const hints = paramHints[leaf] || [];
    if (hints.length > 0) {
      return hints[0];
    }

    if (typeof value === "number") {
      return Number.parseInt(defaultValueByKey(leaf), 10) || 1;
    }
    if (typeof value === "boolean") {
      return true;
    }
    return defaultValueByKey(leaf);
  }

  return walker(cloneJson(template));
}

function extractJsonFromText(raw) {
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch (_error) {
    const match = raw.match(/\{[\s\S]*\}/);
    if (!match) {
      return null;
    }
    try {
      return JSON.parse(match[0]);
    } catch (_error2) {
      return null;
    }
  }
}

export async function generateFilledParams({
  interfaceInfo,
  template,
  codeContext,
  aiModel
}) {
  const heuristicPayload = fillByHeuristic(template, codeContext.paramHints);

  if (!config.ai.apiKey) {
    return {
      payload: heuristicPayload,
      mode: "heuristic",
      reason: "AI_API_KEY not configured"
    };
  }

  const keys = flattenObjectKeys(template);
  const prompt = [
    "你是接口参数生成助手。",
    "请基于接口定义和项目代码上下文，返回可直接请求接口的 JSON 参数。",
    "必须只返回 JSON 对象，不要输出解释文字。",
    `接口: ${interfaceInfo.method} ${interfaceInfo.path}`,
    `接口标题: ${interfaceInfo.title}`,
    `参数键: ${keys.join(", ")}`,
    `默认模板: ${JSON.stringify(template)}`,
    `代码参数提示: ${JSON.stringify(codeContext.paramHints)}`,
    `代码片段(截断): ${JSON.stringify(codeContext.snippets.slice(0, 8))}`
  ].join("\n");

  const endpoint = `${config.ai.baseUrl.replace(/\/$/, "")}/chat/completions`;
  const response = await axios.post(
    endpoint,
    {
      model: aiModel || config.ai.model,
      temperature: 0.2,
      messages: [
        { role: "system", content: "你是资深测试开发工程师，输出严格 JSON。" },
        { role: "user", content: prompt }
      ]
    },
    {
      headers: {
        Authorization: `Bearer ${config.ai.apiKey}`,
        "Content-Type": "application/json"
      },
      timeout: 45000
    }
  );

  const text = response.data?.choices?.[0]?.message?.content || "";
  const parsed = extractJsonFromText(text);

  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    return {
      payload: heuristicPayload,
      mode: "heuristic-fallback",
      reason: "AI response could not be parsed as JSON"
    };
  }

  return {
    payload: parsed,
    mode: "llm"
  };
}
