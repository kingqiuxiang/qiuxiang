const state = {
  config: {},
  codeScan: null,
  interfaces: [],
  selectedInterface: null,
  filled: null
};

const elements = {
  statusTitle: document.querySelector("#statusTitle"),
  statusText: document.querySelector("#statusText"),
  yapiBaseUrl: document.querySelector("#yapiBaseUrl"),
  yapiProjectId: document.querySelector("#yapiProjectId"),
  yapiToken: document.querySelector("#yapiToken"),
  targetBaseUrl: document.querySelector("#targetBaseUrl"),
  frontendUrl: document.querySelector("#frontendUrl"),
  startCommand: document.querySelector("#startCommand"),
  aiEndpoint: document.querySelector("#aiEndpoint"),
  aiModel: document.querySelector("#aiModel"),
  aiApiKey: document.querySelector("#aiApiKey"),
  interfaceList: document.querySelector("#interfaceList"),
  requestEditor: document.querySelector("#requestEditor"),
  timeline: document.querySelector("#timeline"),
  resultBox: document.querySelector("#resultBox"),
  frontendPreview: document.querySelector("#frontendPreview")
};

const buttons = {
  saveConfig: document.querySelector("#saveConfig"),
  scanCode: document.querySelector("#scanCode"),
  startProject: document.querySelector("#startProject"),
  loadInterfaces: document.querySelector("#loadInterfaces"),
  fillParams: document.querySelector("#fillParams"),
  runTest: document.querySelector("#runTest"),
  runWorkflow: document.querySelector("#runWorkflow"),
  openFrontend: document.querySelector("#openFrontend"),
  refreshPreview: document.querySelector("#refreshPreview")
};

function pretty(value) {
  return JSON.stringify(value, null, 2);
}

function setStatus(title, text) {
  elements.statusTitle.textContent = title;
  elements.statusText.textContent = text;
}

function setResult(value) {
  elements.resultBox.textContent = typeof value === "string" ? value : pretty(value);
}

function addTimeline(items) {
  elements.timeline.innerHTML = "";
  for (const item of items) {
    const row = document.createElement("div");
    row.className = `timeline-item ${item.ok === false ? "fail" : ""}`;
    row.innerHTML = `
      <span class="timeline-dot"></span>
      <span>
        <strong>${item.name}</strong>
        <small>${item.detail || ""}</small>
      </span>
    `;
    elements.timeline.append(row);
  }
}

function collectConfig() {
  return {
    yapi: {
      baseUrl: elements.yapiBaseUrl.value.trim(),
      projectId: elements.yapiProjectId.value.trim(),
      token: elements.yapiToken.value.trim()
    },
    project: {
      startCommand: elements.startCommand.value.trim(),
      frontendUrl: elements.frontendUrl.value.trim()
    },
    ai: {
      enabled: Boolean(elements.aiApiKey.value.trim()),
      endpoint: elements.aiEndpoint.value.trim(),
      model: elements.aiModel.value.trim(),
      apiKey: elements.aiApiKey.value.trim()
    },
    targetBaseUrl: elements.targetBaseUrl.value.trim()
  };
}

function saveConfig() {
  localStorage.setItem("ai-yapi-runner-config", JSON.stringify(collectConfig()));
  setStatus("配置已保存", "敏感信息仅保存在当前浏览器 localStorage。");
}

function applyConfig(config) {
  elements.yapiBaseUrl.value = config.yapi?.baseUrl || "";
  elements.yapiProjectId.value = config.yapi?.projectId || "";
  elements.yapiToken.value = config.yapi?.token === "********" ? "" : config.yapi?.token || "";
  elements.targetBaseUrl.value = config.runner?.targetBaseUrl || config.targetBaseUrl || "";
  elements.frontendUrl.value = config.project?.frontendUrl || "";
  elements.startCommand.value = config.project?.startCommand || "";
  elements.aiEndpoint.value = config.ai?.endpoint || "";
  elements.aiModel.value = config.ai?.model || "";
  elements.aiApiKey.value = config.ai?.apiKey === "********" ? "" : config.ai?.apiKey || "";
}

async function api(path, body) {
  const response = await fetch(path, {
    method: body ? "POST" : "GET",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined
  });
  const payload = await response.json();
  if (!response.ok || payload.ok === false) {
    throw new Error(payload.error || `HTTP ${response.status}`);
  }
  return payload;
}

function renderInterfaces() {
  elements.interfaceList.classList.toggle("empty", state.interfaces.length === 0);
  elements.interfaceList.innerHTML = "";
  if (!state.interfaces.length) {
    elements.interfaceList.textContent = "暂无接口，点击“读取接口列表”。";
    return;
  }

  const template = document.querySelector("#interfaceItemTemplate");
  for (const item of state.interfaces) {
    const node = template.content.firstElementChild.cloneNode(true);
    node.classList.toggle("active", state.selectedInterface?._id === item._id);
    node.querySelector(".method").textContent = String(item.method || "GET").toUpperCase();
    node.querySelector(".name").textContent = item.title || item.name || "未命名接口";
    node.querySelector(".path").textContent = item.path || item.url || "/";
    node.addEventListener("click", async () => {
      state.selectedInterface = item;
      renderInterfaces();
      setStatus("已选择接口", `${item.method || "GET"} ${item.path || item.url || "/"}`);
      setResult(item);
    });
    elements.interfaceList.append(node);
  }
}

async function initialize() {
  const status = await api("/api/status");
  state.config = status.config;
  applyConfig(status.config);
  const saved = localStorage.getItem("ai-yapi-runner-config");
  if (saved) {
    applyConfig(JSON.parse(saved));
  }
  setStatus("系统就绪", "可以扫描代码、读取 YAPI 并执行测试闭环。");
  setResult(status);
}

async function scanCode() {
  setStatus("正在扫描代码", "提取接口、DTO、前端 fetch 和关键源码片段。");
  const payload = await api("/api/code/scan", collectConfig());
  state.codeScan = payload.scan;
  addTimeline([{ name: "代码扫描完成", ok: true, detail: `${payload.scan.filesScanned} 个文件` }]);
  setStatus("代码上下文已准备", `${payload.scan.summary.endpointCount} 个接口线索，${payload.scan.summary.modelCount} 个模型线索。`);
  setResult(payload.scan);
}

async function loadInterfaces() {
  setStatus("正在读取 YAPI", "如果未配置 YAPI，将加载演示接口。");
  const payload = await api("/api/yapi/interfaces", collectConfig());
  state.interfaces = payload.list || [];
  state.selectedInterface = state.interfaces[0] || null;
  renderInterfaces();
  addTimeline([{ name: "YAPI 接口读取完成", ok: true, detail: `${state.interfaces.length} 个接口，来源：${payload.source}` }]);
  setStatus("接口列表已更新", `${state.interfaces.length} 个接口可用于 AI 填参。`);
  setResult(payload);
}

async function fillParams() {
  if (!state.selectedInterface) {
    await loadInterfaces();
  }
  if (!state.codeScan) {
    await scanCode();
  }
  setStatus("AI 正在填充参数", "结合 YAPI 和项目代码生成开发环境请求。");
  const payload = await api("/api/ai/fill", {
    ...collectConfig(),
    interface: state.selectedInterface,
    codeScan: state.codeScan
  });
  state.filled = payload.filled;
  elements.requestEditor.value = pretty(payload.filled.request);
  addTimeline([{ name: "参数填充完成", ok: true, detail: payload.filled.strategy }]);
  setStatus("请求参数已生成", payload.filled.aiUsed ? "AI 已参与生成。" : "已使用本地规则生成。");
  setResult(payload.filled);
}

function readRequestEditor() {
  if (!elements.requestEditor.value.trim()) {
    throw new Error("请求参数为空，请先执行 AI 一键填参。");
  }
  return JSON.parse(elements.requestEditor.value);
}

async function startProject() {
  setStatus("正在启动项目", "执行配置的启动命令并检查健康状态。");
  const payload = await api("/api/project/start", collectConfig());
  addTimeline([
    { name: "快捷启动项目", ok: true, detail: payload.message },
    { name: "健康检查", ok: payload.health?.ok || !payload.health?.checked, detail: payload.health?.message }
  ]);
  setStatus("启动流程已执行", payload.message);
  setResult(payload);
}

async function runTest() {
  const request = readRequestEditor();
  setStatus("正在调用接口", "由本地后端代理请求开发环境接口。");
  const payload = await api("/api/test/run", {
    ...collectConfig(),
    request
  });
  addTimeline([
    {
      name: "接口调用完成",
      ok: payload.result.ok,
      detail: `${payload.result.status} ${payload.result.statusText} (${payload.result.durationMs}ms)`
    }
  ]);
  setStatus(payload.result.ok ? "测试通过" : "测试未通过", payload.result.url);
  setResult(payload.result);
}

async function runWorkflow() {
  setStatus("一键闭环执行中", "扫描代码、读取接口、AI 填参、启动项目并调用接口。");
  const payload = await api("/api/workflow/run", {
    ...collectConfig(),
    interface: state.selectedInterface,
    startProject: Boolean(elements.startCommand.value.trim()),
    runTest: Boolean(elements.targetBaseUrl.value.trim())
  });
  state.codeScan = payload.codeScan;
  state.filled = payload.filled;
  elements.requestEditor.value = pretty(payload.filled.request);
  addTimeline(payload.steps);
  setStatus("一键闭环完成", payload.test ? `${payload.test.status} ${payload.test.statusText}` : "已完成填参与启动流程。");
  setResult(payload);
}

function openFrontend() {
  const url = elements.frontendUrl.value.trim();
  if (!url) {
    setStatus("未配置前端地址", "请先填写前端页面地址。");
    return;
  }
  window.open(url, "_blank", "noopener,noreferrer");
}

function refreshPreview() {
  const url = elements.frontendUrl.value.trim();
  elements.frontendPreview.src = url || "about:blank";
  setStatus(url ? "前端预览已刷新" : "未配置前端地址", url || "请填写前端页面地址。");
}

function bind(button, handler) {
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      await handler();
    } catch (error) {
      setStatus("操作失败", error.message);
      addTimeline([{ name: "操作失败", ok: false, detail: error.message }]);
      setResult({ error: error.message, stack: error.stack });
    } finally {
      button.disabled = false;
    }
  });
}

bind(buttons.saveConfig, saveConfig);
bind(buttons.scanCode, scanCode);
bind(buttons.startProject, startProject);
bind(buttons.loadInterfaces, loadInterfaces);
bind(buttons.fillParams, fillParams);
bind(buttons.runTest, runTest);
bind(buttons.runWorkflow, runWorkflow);
bind(buttons.openFrontend, openFrontend);
bind(buttons.refreshPreview, refreshPreview);

initialize().catch((error) => {
  setStatus("系统初始化失败", error.message);
  setResult({ error: error.message });
});
