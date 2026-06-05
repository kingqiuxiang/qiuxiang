import dotenv from "dotenv";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// 优先加载仓库根目录的 .env，方便单一配置文件管理
dotenv.config({ path: path.resolve(__dirname, "../../../.env") });
dotenv.config();

function str(key: string, fallback = ""): string {
  return (process.env[key] ?? fallback).trim();
}

export const config = {
  port: Number(str("PORT", "8787")),

  yapi: {
    baseUrl: str("YAPI_BASE_URL"),
    token: str("YAPI_TOKEN"),
    projectId: str("YAPI_PROJECT_ID"),
    get configured() {
      return Boolean(this.baseUrl && this.token);
    },
  },

  ai: {
    baseUrl: str("AI_BASE_URL", "https://api.openai.com/v1"),
    apiKey: str("AI_API_KEY"),
    model: str("AI_MODEL", "gpt-4o-mini"),
    get configured() {
      return Boolean(this.apiKey);
    },
  },

  project: {
    root: str("PROJECT_ROOT"),
    devApiBaseUrl: str("DEV_API_BASE_URL", "http://localhost:3000"),
    devWebBaseUrl: str("DEV_WEB_BASE_URL", "http://localhost:5173"),
    startCommand: str("PROJECT_START_COMMAND"),
  },
};

export type AppConfig = typeof config;
