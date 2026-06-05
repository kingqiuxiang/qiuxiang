import axios from 'axios';
import type { Project } from '../types.js';

export interface PageTestResult {
  url: string;
  reachable: boolean;
  status: number;
  durationMs: number;
  title?: string;
  contentLength?: number;
  scriptCount?: number;
  rootMounted?: boolean;
  engine: 'http' | 'playwright';
  notes: string[];
  error?: string;
}

async function tryPlaywright(url: string): Promise<PageTestResult | null> {
  try {
    // 可选依赖：安装 playwright 后自动启用真实浏览器渲染检测
    const moduleName = 'playwright';
    const mod: any = await import(moduleName).catch(() => null);
    if (!mod?.chromium) return null;
    const start = Date.now();
    const browser = await mod.chromium.launch({ headless: true });
    const page = await browser.newPage();
    const errors: string[] = [];
    page.on('pageerror', (e: any) => errors.push(String(e?.message || e)));
    const resp = await page.goto(url, { waitUntil: 'networkidle', timeout: 20000 });
    const title = await page.title();
    const rootMounted = await page
      .$eval('#root, #app, body', (el: any) => (el?.children?.length ?? 0) > 0)
      .catch(() => false);
    const durationMs = Date.now() - start;
    await browser.close();
    const notes = ['使用 Playwright 真实浏览器渲染检测'];
    if (errors.length) notes.push(`页面 JS 报错 ${errors.length} 处：${errors.slice(0, 3).join('; ')}`);
    return {
      url,
      reachable: Boolean(resp && resp.ok()),
      status: resp?.status() ?? 0,
      durationMs,
      title,
      rootMounted,
      engine: 'playwright',
      notes,
    };
  } catch (e: any) {
    return null;
  }
}

/** 访问开发环境前端页面进行可达性 / 渲染检测 */
export async function testFrontendPage(project: Project, pagePath = '/'): Promise<PageTestResult> {
  const base = (project.devWebUrl || '').replace(/\/$/, '');
  if (!base) {
    return {
      url: '',
      reachable: false,
      status: 0,
      durationMs: 0,
      engine: 'http',
      notes: [],
      error: '未配置开发环境前端地址',
    };
  }
  const url = base + (pagePath.startsWith('/') ? pagePath : `/${pagePath}`);

  const pw = await tryPlaywright(url);
  if (pw) return pw;

  const start = Date.now();
  try {
    const resp = await axios.get(url, { timeout: 15000, validateStatus: () => true });
    const durationMs = Date.now() - start;
    const html = typeof resp.data === 'string' ? resp.data : '';
    const titleMatch = html.match(/<title>([^<]*)<\/title>/i);
    const scriptCount = (html.match(/<script/gi) || []).length;
    const rootMounted = /<div[^>]+id=["'](root|app)["']/.test(html);
    return {
      url,
      reachable: resp.status >= 200 && resp.status < 400,
      status: resp.status,
      durationMs,
      title: titleMatch?.[1]?.trim(),
      contentLength: html.length,
      scriptCount,
      rootMounted,
      engine: 'http',
      notes: [
        'HTTP 静态检测（安装 playwright 可启用真实浏览器渲染）',
        rootMounted ? '检测到前端挂载根节点' : '未检测到 #root/#app 挂载点',
      ],
    };
  } catch (err: any) {
    return {
      url,
      reachable: false,
      status: 0,
      durationMs: Date.now() - start,
      engine: 'http',
      notes: [],
      error: err?.message || '页面无法访问（开发环境是否已启动？）',
    };
  }
}
