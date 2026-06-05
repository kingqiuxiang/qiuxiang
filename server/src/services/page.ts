import { getConfig } from "../config.js";

export interface PageCheck {
  ok: boolean;
  url: string;
  status?: number;
  durationMs: number;
  title?: string;
  contentType?: string;
  sizeBytes?: number;
  links?: number;
  scripts?: number;
  hasRootEl?: boolean;
  errorHints?: string[];
  error?: string;
}

function extract(html: string) {
  const titleMatch = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
  const links = (html.match(/<a\s/gi) || []).length;
  const scripts = (html.match(/<script\s/gi) || []).length;
  const hasRootEl = /id=["'](root|app|__next)["']/i.test(html);
  const errorHints: string[] = [];
  const lower = html.toLowerCase();
  for (const sig of ["cannot get", "internal server error", "application error", "runtime error", "uncaught"]) {
    if (lower.includes(sig)) errorHints.push(sig);
  }
  return {
    title: titleMatch ? titleMatch[1].trim().slice(0, 120) : undefined,
    links,
    scripts,
    hasRootEl,
    errorHints,
  };
}

export async function checkPage(rawUrl?: string): Promise<PageCheck> {
  const cfg = getConfig();
  const url = rawUrl || cfg.devEnv.webBaseUrl;
  const started = Date.now();
  try {
    const res = await fetch(url, {
      signal: AbortSignal.timeout(15000),
      headers: { "User-Agent": "APIPilot-PageChecker/1.0" },
    });
    const text = await res.text();
    const meta = extract(text);
    return {
      ok: res.ok && meta.errorHints.length === 0,
      url,
      status: res.status,
      durationMs: Date.now() - started,
      contentType: res.headers.get("content-type") || undefined,
      sizeBytes: text.length,
      ...meta,
    };
  } catch (err: any) {
    return {
      ok: false,
      url,
      durationMs: Date.now() - started,
      error: err.name === "TimeoutError" ? "页面访问超时(15s)" : err.message,
    };
  }
}
