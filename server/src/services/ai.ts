import { getConfig } from "../config.js";

export interface ChatMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

export function aiConfigured(): boolean {
  const cfg = getConfig();
  return Boolean(cfg.ai.apiKey && cfg.ai.baseUrl && cfg.ai.model);
}

export async function chat(messages: ChatMessage[], opts?: { json?: boolean; temperature?: number }): Promise<string> {
  const cfg = getConfig();
  if (!aiConfigured()) {
    throw new Error("AI_NOT_CONFIGURED");
  }
  const url = cfg.ai.baseUrl.replace(/\/$/, "") + "/chat/completions";
  const body: Record<string, unknown> = {
    model: cfg.ai.model,
    messages,
    temperature: opts?.temperature ?? 0.4,
  };
  if (opts?.json) {
    body.response_format = { type: "json_object" };
  }
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${cfg.ai.apiKey}`,
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(60000),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`AI provider error ${res.status}: ${text.slice(0, 300)}`);
  }
  const data = await res.json();
  return data.choices?.[0]?.message?.content ?? "";
}

/** Pull the first JSON object/array out of a possibly fenced AI response. */
export function extractJson(text: string): unknown {
  if (!text) return undefined;
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/);
  const candidate = fenced ? fenced[1] : text;
  const start = candidate.search(/[[{]/);
  if (start === -1) {
    try {
      return JSON.parse(candidate);
    } catch {
      return undefined;
    }
  }
  // Try to find a balanced JSON region from the first bracket.
  for (let end = candidate.length; end > start; end--) {
    const slice = candidate.slice(start, end);
    try {
      return JSON.parse(slice);
    } catch {
      /* keep shrinking */
    }
  }
  return undefined;
}
