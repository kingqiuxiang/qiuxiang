import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';

dotenv.config();

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.resolve(__dirname, '../data');
const CONFIG_FILE = path.join(DATA_DIR, 'config.json');

fs.mkdirSync(DATA_DIR, { recursive: true });

/**
 * Runtime configuration.
 * Defaults come from environment variables; the UI can override and persist
 * everything (except secrets are stored locally only) into data/config.json.
 */
const defaults = {
  ai: {
    baseUrl: process.env.AI_BASE_URL || 'https://api.openai.com/v1',
    apiKey: process.env.AI_API_KEY || '',
    model: process.env.AI_MODEL || 'gpt-4o-mini',
    temperature: 0.2,
  },
  yapi: {
    baseUrl: process.env.YAPI_BASE_URL || '',
    token: process.env.YAPI_TOKEN || '',
  },
  project: {
    path: process.env.PROJECT_PATH || '',
    startCmd: process.env.PROJECT_START_CMD || '',
    devApiBaseUrl: process.env.DEV_API_BASE_URL || '',
    devWebUrl: process.env.DEV_WEB_URL || '',
    readyPath: '/',
  },
};

function deepMerge(base, override) {
  const out = Array.isArray(base) ? [...base] : { ...base };
  for (const key of Object.keys(override || {})) {
    const value = override[key];
    if (value && typeof value === 'object' && !Array.isArray(value) && typeof out[key] === 'object') {
      out[key] = deepMerge(out[key], value);
    } else if (value !== undefined) {
      out[key] = value;
    }
  }
  return out;
}

let current = defaults;
try {
  if (fs.existsSync(CONFIG_FILE)) {
    const saved = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
    current = deepMerge(defaults, saved);
  }
} catch (err) {
  console.warn('[config] failed to read persisted config:', err.message);
}

export const paths = { DATA_DIR, CONFIG_FILE };

export function getConfig() {
  return current;
}

/** Returns config safe for the client (secrets masked). */
export function getPublicConfig() {
  const c = current;
  const mask = (s) => (s ? '••••••••' + String(s).slice(-4) : '');
  return {
    ai: { baseUrl: c.ai.baseUrl, model: c.ai.model, temperature: c.ai.temperature, apiKeyMask: mask(c.ai.apiKey), hasKey: Boolean(c.ai.apiKey) },
    yapi: { baseUrl: c.yapi.baseUrl, tokenMask: mask(c.yapi.token), hasToken: Boolean(c.yapi.token) },
    project: { ...c.project },
  };
}

export function updateConfig(patch) {
  current = deepMerge(current, patch || {});
  try {
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(current, null, 2));
  } catch (err) {
    console.warn('[config] failed to persist config:', err.message);
  }
  return getPublicConfig();
}
