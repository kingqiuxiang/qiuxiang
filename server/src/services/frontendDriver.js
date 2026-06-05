import axios from 'axios';
import { getConfig } from '../config.js';

let playwright = null;
let playwrightChecked = false;

async function getPlaywright() {
  if (playwrightChecked) return playwright;
  playwrightChecked = true;
  try {
    const mod = await import('playwright');
    playwright = mod;
  } catch {
    playwright = null;
  }
  return playwright;
}

/**
 * Access a frontend page of the dev environment and run light checks.
 * Uses Playwright (real browser) when available, otherwise falls back to a
 * static HTML fetch + analysis so it always works offline.
 */
export async function testFrontend({ url, path: pagePath = '/' } = {}) {
  const { project } = getConfig();
  let target = url;
  if (!target) {
    const base = (project.devWebUrl || '').replace(/\/$/, '');
    if (!base) throw Object.assign(new Error('未配置开发环境前端地址 devWebUrl。'), { code: 'NO_WEB_URL' });
    target = base + (pagePath.startsWith('/') ? pagePath : `/${pagePath}`);
  }

  const pw = await getPlaywright();
  if (pw) {
    const result = await runWithPlaywright(pw, target);
    if (result) return result;
    // Browser binary not installed -> gracefully degrade to static fetch.
  }
  return runWithFetch(target);
}

async function runWithPlaywright(pw, target) {
  const startedAt = Date.now();
  const consoleErrors = [];
  const failedRequests = [];
  let browser;
  try {
    browser = await pw.chromium.launch({ args: ['--no-sandbox'] });
    const page = await browser.newPage();
    page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });
    page.on('requestfailed', (req) => failedRequests.push(`${req.method()} ${req.url()} (${req.failure()?.errorText})`));
    const resp = await page.goto(target, { waitUntil: 'networkidle', timeout: 30000 });
    const title = await page.title();
    const text = (await page.evaluate(() => document.body?.innerText || '')).slice(0, 2000);
    await browser.close();
    return {
      engine: 'playwright',
      url: target,
      ok: (resp?.status() || 0) < 400 && consoleErrors.length === 0,
      status: resp?.status() || 0,
      title,
      textPreview: text,
      consoleErrors,
      failedRequests,
      durationMs: Date.now() - startedAt,
    };
  } catch (err) {
    if (browser) await browser.close().catch(() => {});
    // Signal the caller to fall back to static fetch when the browser binary is missing.
    if (/Executable doesn't exist|browserType\.launch|install/i.test(err.message)) return null;
    return { engine: 'playwright', url: target, ok: false, status: 0, error: err.message, consoleErrors, failedRequests, durationMs: Date.now() - startedAt };
  }
}

async function runWithFetch(target) {
  const startedAt = Date.now();
  try {
    const res = await axios.get(target, { timeout: 15000, validateStatus: () => true });
    const html = typeof res.data === 'string' ? res.data : '';
    const title = (html.match(/<title>([^<]*)<\/title>/i) || [])[1] || '';
    const hasRoot = /id=["'](root|app|__next)["']/.test(html);
    const scripts = (html.match(/<script/gi) || []).length;
    return {
      engine: 'http',
      url: target,
      ok: res.status < 400,
      status: res.status,
      title,
      hasAppRoot: hasRoot,
      scriptCount: scripts,
      textPreview: html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 1200),
      note: '未安装 Playwright，使用静态 HTML 分析（安装 playwright 可启用真实浏览器驱动）。',
      durationMs: Date.now() - startedAt,
    };
  } catch (err) {
    return { engine: 'http', url: target, ok: false, status: 0, error: err.message, durationMs: Date.now() - startedAt };
  }
}

export async function frontendCapabilities() {
  const pw = await getPlaywright();
  return { playwright: Boolean(pw) };
}
