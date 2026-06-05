import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { AppConfig } from "./types.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.resolve(__dirname, "../data");
const CONFIG_FILE = path.join(DATA_DIR, "config.json");

function defaults(): AppConfig {
  return {
    yapi: {
      baseUrl: process.env.YAPI_BASE_URL || "",
      token: process.env.YAPI_TOKEN || "",
      projectId: process.env.YAPI_PROJECT_ID || "",
    },
    ai: {
      baseUrl: process.env.AI_BASE_URL || "https://api.openai.com/v1",
      apiKey: process.env.AI_API_KEY || "",
      model: process.env.AI_MODEL || "gpt-4o-mini",
    },
    project: {
      rootPath: process.env.PROJECT_ROOT || "",
      startCommand: process.env.PROJECT_START_COMMAND || "npm run dev",
      healthUrl: process.env.PROJECT_HEALTH_URL || "http://localhost:3000",
    },
    devEnv: {
      apiBaseUrl: process.env.DEV_API_BASE_URL || "http://localhost:3000",
      webBaseUrl: process.env.DEV_WEB_BASE_URL || "http://localhost:5173",
    },
  };
}

let cache: AppConfig | null = null;

function deepMerge<T>(base: T, override: Partial<T>): T {
  const out: any = Array.isArray(base) ? [...(base as any)] : { ...base };
  for (const key of Object.keys(override || {})) {
    const val = (override as any)[key];
    if (val && typeof val === "object" && !Array.isArray(val)) {
      out[key] = deepMerge((base as any)[key] ?? {}, val);
    } else if (val !== undefined) {
      out[key] = val;
    }
  }
  return out;
}

export function getConfig(): AppConfig {
  if (cache) return cache;
  const base = defaults();
  if (fs.existsSync(CONFIG_FILE)) {
    try {
      const saved = JSON.parse(fs.readFileSync(CONFIG_FILE, "utf-8"));
      cache = deepMerge(base, saved);
    } catch {
      cache = base;
    }
  } else {
    cache = base;
  }
  return cache;
}

export function saveConfig(patch: Partial<AppConfig>): AppConfig {
  const merged = deepMerge(getConfig(), patch);
  cache = merged;
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
  fs.writeFileSync(CONFIG_FILE, JSON.stringify(merged, null, 2), "utf-8");
  return merged;
}

/** Redact secrets before sending config to the client. */
export function publicConfig(): AppConfig {
  const c = getConfig();
  return {
    ...c,
    yapi: { ...c.yapi, token: mask(c.yapi.token) },
    ai: { ...c.ai, apiKey: mask(c.ai.apiKey) },
  };
}

function mask(secret: string): string {
  if (!secret) return "";
  if (secret.length <= 6) return "••••";
  return secret.slice(0, 3) + "••••" + secret.slice(-3);
}
