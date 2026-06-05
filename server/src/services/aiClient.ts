import axios from "axios";
import { config } from "../config/index.js";
import { logger } from "../utils/logger.js";

export interface ChatMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

/**
 * OpenAI 兼容的对话客户端。未配置 API Key 时 `configured` 为 false，
 * 上层会自动回退到内置启发式参数生成（无需联网即可使用）。
 */
class AiClient {
  get configured() {
    return config.ai.configured;
  }

  async chat(messages: ChatMessage[], opts: { json?: boolean; temperature?: number } = {}): Promise<string> {
    if (!this.configured) throw new Error("AI 未配置 API Key");
    const { data } = await axios.post(
      `${config.ai.baseUrl.replace(/\/$/, "")}/chat/completions`,
      {
        model: config.ai.model,
        messages,
        temperature: opts.temperature ?? 0.2,
        ...(opts.json ? { response_format: { type: "json_object" } } : {}),
      },
      {
        headers: {
          Authorization: `Bearer ${config.ai.apiKey}`,
          "Content-Type": "application/json",
        },
        timeout: 60000,
      },
    );
    return data?.choices?.[0]?.message?.content ?? "";
  }

  /** 调用 AI 并尽力解析为 JSON 对象 */
  async chatJson<T = unknown>(messages: ChatMessage[]): Promise<T> {
    const raw = await this.chat(messages, { json: true });
    return extractJson<T>(raw);
  }
}

export function extractJson<T = unknown>(text: string): T {
  const trimmed = text.trim();
  try {
    return JSON.parse(trimmed) as T;
  } catch {
    // 容错：从 ```json ... ``` 或第一个 { 到最后一个 } 中提取
    const fenced = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/);
    if (fenced) {
      try {
        return JSON.parse(fenced[1]) as T;
      } catch {
        /* fallthrough */
      }
    }
    const first = trimmed.indexOf("{");
    const last = trimmed.lastIndexOf("}");
    if (first >= 0 && last > first) {
      return JSON.parse(trimmed.slice(first, last + 1)) as T;
    }
    logger.error("无法从 AI 响应解析 JSON：", text.slice(0, 200));
    throw new Error("AI 响应不是合法 JSON");
  }
}

export const aiClient = new AiClient();
