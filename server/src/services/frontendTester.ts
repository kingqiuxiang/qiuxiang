import axios from "axios";
import { config } from "../config/index.js";
import { bus } from "../utils/events.js";

export interface PageCheck {
  url: string;
  ok: boolean;
  status?: number;
  durationMs: number;
  title?: string;
  /** 页面中检测到的报错关键字（如 error/exception 等） */
  detectedErrors: string[];
  /** 渲染引擎：playwright（真实浏览器）或 http（纯请求） */
  engine: "playwright" | "http";
  screenshot?: string; // base64（仅 playwright）
  detail?: string;
}

const ERROR_SIGNATURES = [
  "Uncaught", "TypeError", "ReferenceError", "is not defined",
  "Cannot read", "500 Internal Server Error", "Application error",
];

/**
 * 前端页面测试：优先用 Playwright 打开真实页面（捕获控制台错误、截图），
 * 未安装时回退到 HTTP 抓取做基础冒烟检查。
 * 体现「访问开发环境前端页面的方式去测试」。
 */
export async function testFrontendPage(pathOrUrl: string, runId: string): Promise<PageCheck> {
  const base = config.project.devWebBaseUrl.replace(/\/$/, "");
  const url = /^https?:\/\//.test(pathOrUrl) ? pathOrUrl : base + (pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl);

  bus.log(runId, `打开前端页面：${url}`, "info");

  const pw = await tryPlaywright(url, runId);
  if (pw) return pw;

  // 回退：HTTP 冒烟检查
  const t0 = Date.now();
  try {
    const res = await axios.get(url, { timeout: 15000, validateStatus: () => true });
    const html = typeof res.data === "string" ? res.data : "";
    const title = html.match(/<title>([^<]*)<\/title>/i)?.[1]?.trim();
    const detectedErrors = ERROR_SIGNATURES.filter((s) => html.includes(s));
    const ok = res.status < 400 && detectedErrors.length === 0;
    bus.log(runId, `页面响应 ${res.status}${title ? `，标题：${title}` : ""}`, ok ? "success" : "warn");
    return {
      url, ok, status: res.status, durationMs: Date.now() - t0, title, detectedErrors,
      engine: "http",
      detail: "Playwright 未安装，已使用 HTTP 冒烟检查（如需真实渲染请安装 playwright）。",
    };
  } catch (err) {
    bus.log(runId, `页面访问失败：${(err as Error).message}`, "error");
    return { url, ok: false, durationMs: Date.now() - t0, detectedErrors: [], engine: "http", detail: (err as Error).message };
  }
}

async function tryPlaywright(url: string, runId: string): Promise<PageCheck | null> {
  let chromium: any;
  try {
    // 用变量规避静态解析：未安装 playwright 时不会导致编译失败，运行期回退到 HTTP
    const moduleName = "playwright";
    ({ chromium } = await import(moduleName));
  } catch {
    return null; // 未安装，回退
  }
  const t0 = Date.now();
  let browser: any;
  try {
    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage();
    const consoleErrors: string[] = [];
    page.on("console", (msg: any) => {
      if (msg.type() === "error") consoleErrors.push(msg.text());
    });
    page.on("pageerror", (err: Error) => consoleErrors.push(err.message));
    const resp = await page.goto(url, { waitUntil: "networkidle", timeout: 20000 });
    const title = await page.title();
    const shot = await page.screenshot({ type: "png" });
    const status = resp?.status();
    const ok = (status ?? 200) < 400 && consoleErrors.length === 0;
    bus.log(runId, `Playwright 渲染完成，标题：${title}，控制台错误 ${consoleErrors.length} 条`, ok ? "success" : "warn");
    return {
      url, ok, status, durationMs: Date.now() - t0, title,
      detectedErrors: consoleErrors.slice(0, 20), engine: "playwright",
      screenshot: `data:image/png;base64,${shot.toString("base64")}`,
    };
  } catch (err) {
    bus.log(runId, `Playwright 执行失败：${(err as Error).message}`, "error");
    return { url, ok: false, durationMs: Date.now() - t0, detectedErrors: [], engine: "playwright", detail: (err as Error).message };
  } finally {
    if (browser) await browser.close().catch(() => {});
  }
}
