import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const workspaceRoot = path.resolve(__dirname, "../..");

const defaultConfig = {
  project: {
    name: "qiuxiang",
    root: ".",
    startCommand: "",
    healthUrl: "",
    frontendUrl: ""
  },
  yapi: {
    baseUrl: "",
    token: "",
    projectId: ""
  },
  ai: {
    enabled: false,
    endpoint: "https://api.openai.com/v1/chat/completions",
    apiKey: "",
    model: "gpt-4o-mini"
  },
  runner: {
    targetBaseUrl: "http://localhost:8080",
    timeoutMs: 15000
  }
};

async function readJsonIfExists(filePath) {
  try {
    return JSON.parse(await readFile(filePath, "utf8"));
  } catch (error) {
    if (error.code === "ENOENT") {
      return {};
    }
    throw new Error(`Failed to read ${filePath}: ${error.message}`);
  }
}

export function mergeDeep(base, override) {
  if (!override || typeof override !== "object") {
    return structuredClone(base);
  }

  const result = Array.isArray(base) ? [...base] : { ...base };
  for (const [key, value] of Object.entries(override)) {
    if (
      value &&
      typeof value === "object" &&
      !Array.isArray(value) &&
      base?.[key] &&
      typeof base[key] === "object" &&
      !Array.isArray(base[key])
    ) {
      result[key] = mergeDeep(base[key], value);
    } else if (value !== undefined) {
      result[key] = value;
    }
  }
  return result;
}

function envConfig() {
  return {
    project: {
      startCommand: process.env.PROJECT_START_COMMAND || undefined,
      healthUrl: process.env.PROJECT_HEALTH_URL || undefined,
      frontendUrl: process.env.FRONTEND_URL || undefined
    },
    yapi: {
      baseUrl: process.env.YAPI_BASE_URL || undefined,
      token: process.env.YAPI_TOKEN || undefined,
      projectId: process.env.YAPI_PROJECT_ID || undefined
    },
    ai: {
      enabled: process.env.AI_ENABLED
        ? ["1", "true", "yes"].includes(process.env.AI_ENABLED.toLowerCase())
        : undefined,
      endpoint: process.env.AI_API_ENDPOINT || process.env.OPENAI_API_BASE_URL
        ? `${(process.env.AI_API_ENDPOINT || process.env.OPENAI_API_BASE_URL).replace(/\/$/, "")}/chat/completions`
        : undefined,
      apiKey: process.env.AI_API_KEY || process.env.OPENAI_API_KEY || undefined,
      model: process.env.AI_MODEL || process.env.OPENAI_MODEL || undefined
    },
    runner: {
      targetBaseUrl: process.env.TARGET_BASE_URL || undefined,
      timeoutMs: process.env.RUNNER_TIMEOUT_MS
        ? Number(process.env.RUNNER_TIMEOUT_MS)
        : undefined
    }
  };
}

function stripUndefined(value) {
  if (!value || typeof value !== "object") {
    return value;
  }
  const output = Array.isArray(value) ? [] : {};
  for (const [key, nested] of Object.entries(value)) {
    if (nested === undefined) {
      continue;
    }
    output[key] = stripUndefined(nested);
  }
  return output;
}

export async function loadConfig(overrides = {}) {
  const localConfigPath = path.join(workspaceRoot, "config/ai-yapi-runner.local.json");
  const fileConfig = await readJsonIfExists(localConfigPath);
  const withFile = mergeDeep(defaultConfig, fileConfig);
  const withEnv = mergeDeep(withFile, stripUndefined(envConfig()));
  const withOverrides = mergeDeep(withEnv, overrides);

  withOverrides.project.root = path.resolve(workspaceRoot, withOverrides.project.root || ".");
  withOverrides.runner.timeoutMs = Number(withOverrides.runner.timeoutMs || 15000);
  return withOverrides;
}

export function safeConfig(config) {
  const safe = structuredClone(config);
  if (safe.yapi?.token) {
    safe.yapi.token = "********";
  }
  if (safe.ai?.apiKey) {
    safe.ai.apiKey = "********";
  }
  return safe;
}
