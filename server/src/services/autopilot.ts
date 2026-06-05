import { randomUUID } from "node:crypto";
import { yapiClient } from "./yapiClient.js";
import { fillParams } from "./paramFiller.js";
import { runInterfaceTest, type RunOptions } from "./testRunner.js";
import { testFrontendPage } from "./frontendTester.js";
import { bus } from "../utils/events.js";
import type { ApiInterface, TestResult } from "../types/index.js";

export interface AutopilotReport {
  runId: string;
  interface: { id: string; title: string; method: string; path: string };
  fill: { source: string; rationale?: string; evidenceCount: number };
  test: TestResult;
  page?: Awaited<ReturnType<typeof testFrontendPage>>;
  passed: boolean;
}

/**
 * 自动驾驶流程：「写完接口之后 AI 去测试」。
 * 1) 拉取接口定义 → 2) AI 一键填充参数 → 3) 执行接口测试 →
 * 4) 可选地访问相关前端页面做冒烟。整个过程通过 WebSocket 实时回传。
 */
export async function autopilot(
  interfaceId: string,
  opts: RunOptions & { testPage?: string } = {},
): Promise<AutopilotReport> {
  const runId = randomUUID();
  bus.emit({ type: "status", runId, message: "autopilot 开始", ts: new Date().toISOString() });

  const iface = await yapiClient.getInterface(interfaceId);
  if (!iface) throw new Error(`接口 ${interfaceId} 不存在`);
  bus.log(runId, `已读取接口：${iface.method} ${iface.path}`, "info");

  bus.log(runId, "AI 正在依据项目代码生成参数…", "info");
  const filled = await fillParams(iface);
  bus.log(
    runId,
    `参数生成完成（来源：${filled.source}，命中代码证据 ${filled.evidence?.length ?? 0} 处）`,
    "success",
  );
  bus.emit({ type: "result", runId, payload: { kind: "fill", filled }, ts: new Date().toISOString() });

  const test = await runInterfaceTest(iface, filled, runId, opts);
  bus.emit({ type: "result", runId, payload: { kind: "test", test }, ts: new Date().toISOString() });

  let page: AutopilotReport["page"];
  if (opts.testPage) {
    page = await testFrontendPage(opts.testPage, runId);
    bus.emit({ type: "result", runId, payload: { kind: "page", page }, ts: new Date().toISOString() });
  }

  const passed = test.passed && (page ? page.ok : true);
  bus.emit({
    type: "done",
    runId,
    level: passed ? "success" : "error",
    message: passed ? "全部通过 ✅" : "存在失败项 ❌",
    ts: new Date().toISOString(),
  });

  return {
    runId,
    interface: { id: iface.id, title: iface.title, method: iface.method, path: iface.path },
    fill: { source: filled.source, rationale: filled.rationale, evidenceCount: filled.evidence?.length ?? 0 },
    test,
    page,
    passed,
  };
}

/** 批量自动测试整个项目的全部接口。 */
export async function autopilotAll(opts: RunOptions = {}): Promise<AutopilotReport[]> {
  const interfaces = await yapiClient.listInterfaces();
  const reports: AutopilotReport[] = [];
  for (const i of interfaces as ApiInterface[]) {
    reports.push(await autopilot(i.id, opts));
  }
  return reports;
}
