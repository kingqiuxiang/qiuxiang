import { nanoid } from 'nanoid';
import { getConfig } from '../config.js';
import { getInterfaceById } from './interfaceSource.js';
import { findContextForInterface } from './codeService.js';
import { fillParams, analyzeResult } from './aiService.js';
import { runRequest } from './testRunner.js';

/**
 * End-to-end "write interface -> AI tests it" flow:
 *   resolve interface -> read project code -> AI fill params -> send request -> AI analyze.
 * `onStep(step)` is invoked for live (SSE) progress.
 */
export async function autoTest({ interfaceId, catName, overrides = {} }, onStep = () => {}) {
  const runId = nanoid(10);
  const steps = [];
  const emit = (step) => {
    const enriched = { ...step, ts: Date.now() };
    steps.push(enriched);
    onStep(enriched);
  };

  emit({ key: 'resolve', status: 'running', title: '解析接口定义' });
  const { source, interface: iface } = await getInterfaceById(interfaceId, catName);
  if (!iface) {
    emit({ key: 'resolve', status: 'error', title: '解析接口定义', detail: '未找到该接口' });
    return { runId, ok: false, error: '未找到该接口', steps };
  }
  emit({ key: 'resolve', status: 'done', title: '解析接口定义', detail: `${iface.method} ${iface.path}（来源：${source}）`, data: { iface } });

  // 2. read project code for grounding
  emit({ key: 'code', status: 'running', title: '读取项目代码作为基准' });
  let codeContext = null;
  const { project } = getConfig();
  if (project.path) {
    try {
      codeContext = findContextForInterface(project.path, iface);
      emit({ key: 'code', status: 'done', title: '读取项目代码作为基准', detail: `命中 ${codeContext.snippets.length} 个代码片段`, data: { codeContext } });
    } catch (err) {
      emit({ key: 'code', status: 'warn', title: '读取项目代码作为基准', detail: err.message });
    }
  } else {
    emit({ key: 'code', status: 'skip', title: '读取项目代码作为基准', detail: '未配置项目路径，跳过' });
  }

  // 3. AI fill
  emit({ key: 'fill', status: 'running', title: 'AI 一键参数填充' });
  const filled = await fillParams(iface, codeContext);
  emit({ key: 'fill', status: 'done', title: 'AI 一键参数填充', detail: `引擎：${filled.engine}`, data: { filled } });

  // 4. send request
  emit({ key: 'request', status: 'running', title: '向开发环境发送请求' });
  let result;
  try {
    result = await runRequest(iface, filled, overrides);
  } catch (err) {
    emit({ key: 'request', status: 'error', title: '向开发环境发送请求', detail: err.message });
    return { runId, ok: false, error: err.message, iface, filled, steps };
  }
  emit({
    key: 'request',
    status: result.ok ? 'done' : 'error',
    title: '向开发环境发送请求',
    detail: result.ok ? `HTTP ${result.response.status} · ${result.response.durationMs}ms` : result.error?.message,
    data: { result },
  });

  // 5. AI analyze
  emit({ key: 'analyze', status: 'running', title: 'AI 分析响应并断言' });
  let analysis = null;
  if (result.ok) {
    analysis = await analyzeResult(iface, result.request, result.response);
    emit({ key: 'analyze', status: analysis.verdict === 'pass' ? 'done' : 'warn', title: 'AI 分析响应并断言', detail: `${analysis.verdict} · ${analysis.summary}`, data: { analysis } });
  } else {
    emit({ key: 'analyze', status: 'skip', title: 'AI 分析响应并断言', detail: '请求失败，跳过分析' });
  }

  return {
    runId,
    ok: result.ok && analysis?.verdict !== 'fail',
    source,
    iface,
    codeContext,
    filled,
    result,
    analysis,
    steps,
    finishedAt: Date.now(),
  };
}
