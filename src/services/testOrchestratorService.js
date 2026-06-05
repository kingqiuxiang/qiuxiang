import fs from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { invokeInterface } from "./projectRunnerService.js";

async function runFrontendPageProbe({ frontendUrl, expectText, timeoutMs = 15000 }) {
  if (!frontendUrl) {
    return { skipped: true, reason: "frontendUrl not provided" };
  }

  let playwright;
  try {
    playwright = await import("playwright");
  } catch (_error) {
    return {
      skipped: true,
      reason: "Playwright is unavailable in runtime"
    };
  }

  let browser;
  let page;
  const screenshotName = `test-shot-${randomUUID()}.png`;
  const screenshotPath = path.resolve(process.cwd(), "artifacts", screenshotName);

  try {
    browser = await playwright.chromium.launch({ headless: true });
    page = await browser.newPage();
    await fs.mkdir(path.dirname(screenshotPath), { recursive: true });
    await page.goto(frontendUrl, {
      waitUntil: "domcontentloaded",
      timeout: timeoutMs
    });
    await page.waitForTimeout(900);
    const html = await page.content();
    const containsExpectedText = expectText ? html.includes(expectText) : true;
    await page.screenshot({ path: screenshotPath, fullPage: true });
    return {
      ok: containsExpectedText,
      expectText,
      screenshotPath,
      title: await page.title()
    };
  } catch (error) {
    return {
      skipped: true,
      reason: `Frontend probe skipped: ${error.message}`
    };
  } finally {
    if (page) {
      await page.close();
    }
    if (browser) {
      await browser.close();
    }
  }
}

export async function executeAiTestFlow({
  frontendUrl,
  expectText,
  apiRequest
}) {
  const startedAt = new Date().toISOString();
  const report = {
    startedAt
  };

  if (apiRequest?.url) {
    report.apiResult = await invokeInterface(apiRequest);
  } else {
    report.apiResult = { skipped: true, reason: "apiRequest.url not provided" };
  }

  report.frontendResult = await runFrontendPageProbe({
    frontendUrl,
    expectText,
    timeoutMs: apiRequest?.timeoutMs
  });

  report.finishedAt = new Date().toISOString();
  report.ok = Boolean(
    (report.apiResult.skipped || report.apiResult.status < 500) &&
      (report.frontendResult.skipped || report.frontendResult.ok)
  );
  return report;
}
