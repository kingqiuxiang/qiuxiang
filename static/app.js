const state = {
  activeTab: "summary",
  summary: {},
  yapi: null,
  selectedInterface: null,
  project: null,
  fill: null,
  test: null,
  logs: null,
  processes: []
};

const $ = (id) => document.getElementById(id);
const output = $("output");
const timeline = $("timeline");
const toast = $("toast");

function value(id) {
  return $(id).value.trim();
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add("show");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 2600);
}

function pushStep(message, type = "done") {
  if (timeline.children.length === 1 && timeline.children[0].textContent === "等待开始") {
    timeline.innerHTML = "";
  }
  const item = document.createElement("li");
  item.className = type;
  item.textContent = `${new Date().toLocaleTimeString()} · ${message}`;
  timeline.prepend(item);
}

function setBusy(button, busy) {
  if (!button) return;
  button.disabled = busy;
  if (busy) {
    button.dataset.originalText = button.textContent;
    button.textContent = "处理中...";
  } else {
    button.textContent = button.dataset.originalText || button.textContent;
  }
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    method: options.method || "POST",
    headers: { "Content-Type": "application/json" },
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw new Error(data.error || `HTTP ${response.status}`);
  }
  return data;
}

function render() {
  const views = {
    summary: buildSummary(),
    yapi: state.yapi || { empty: "尚未读取 YAPI" },
    project: state.project || { empty: "尚未扫描项目" },
    fill: state.fill || { empty: "尚未生成参数" },
    test: state.test || { empty: "尚未执行测试" },
    logs: state.logs || { processes: state.processes }
  };
  output.textContent = JSON.stringify(views[state.activeTab], null, 2);
}

function buildSummary() {
  const selected = state.selectedInterface || {};
  const result = state.fill?.result || {};
  return {
    service: state.summary.service || "AI YAPI Dev Assistant",
    yapiInterface: selected.title ? {
      title: selected.title,
      method: selected.method,
      path: selected.path
    } : "未导入",
    project: state.project ? {
      root: state.project.root,
      fileCount: state.project.fileCount,
      endpointCount: state.project.endpointCount
    } : "未扫描",
    generatedRequest: result.path ? {
      method: result.method,
      path: result.path,
      query: result.query,
      body: result.body
    } : "未生成",
    lastTest: state.test ? {
      requestStatus: state.test.request?.status,
      pageStatus: state.test.page?.status
    } : "未测试"
  };
}

function selectTab(tab) {
  state.activeTab = tab;
  document.querySelectorAll(".tab").forEach((button) => {
    button.classList.toggle("active", button.dataset.tab === tab);
  });
  render();
}

async function healthCheck() {
  const data = await api("/api/health", { method: "GET" });
  state.summary = data;
  $("serviceState").textContent = data.ok ? "已连接" : "异常";
  pushStep("服务状态正常");
  render();
}

async function importYapi(button) {
  setBusy(button, true);
  try {
    const data = await api("/api/yapi/import", {
      body: {
        baseUrl: value("yapiBaseUrl"),
        token: value("yapiToken"),
        interfaceId: value("interfaceId"),
        projectId: value("projectId")
      }
    });
    state.yapi = data;
    state.selectedInterface = data.interface || data.interfaces?.[0] || null;
    state.activeTab = "yapi";
    selectTab("yapi");
    pushStep("YAPI 读取完成");
    showToast("YAPI 接口已导入");
  } catch (error) {
    pushStep(`YAPI 读取失败：${error.message}`, "error");
    showToast(error.message);
  } finally {
    setBusy(button, false);
  }
}

async function scanProject(button) {
  setBusy(button, true);
  try {
    const data = await api("/api/project/scan", {
      body: {
        root: value("scanRoot") || ".",
        maxFiles: Number(value("maxFiles") || 350)
      }
    });
    state.project = data;
    selectTab("project");
    pushStep(`项目扫描完成：${data.fileCount} 个文件，${data.endpointCount} 个接口信号`);
    showToast("项目扫描完成");
  } catch (error) {
    pushStep(`项目扫描失败：${error.message}`, "error");
    showToast(error.message);
  } finally {
    setBusy(button, false);
  }
}

async function fillParams(button) {
  setBusy(button, true);
  try {
    if (!state.selectedInterface) {
      throw new Error("请先读取 YAPI 接口");
    }
    const data = await api("/api/ai/fill", {
      body: {
        yapi: state.selectedInterface,
        project: state.project || {},
        extraPrompt: value("extraPrompt")
      }
    });
    state.fill = data;
    selectTab("fill");
    pushStep(data.mode === "ai" ? "AI 参数生成完成" : "本地规则参数生成完成");
    showToast("参数已生成，可直接测试接口");
  } catch (error) {
    pushStep(`参数生成失败：${error.message}`, "error");
    showToast(error.message);
  } finally {
    setBusy(button, false);
  }
}

async function startProject(button) {
  setBusy(button, true);
  try {
    const command = value("startCommand");
    if (!command) {
      throw new Error("请输入启动命令");
    }
    const data = await api("/api/project/start", {
      body: { command, cwd: value("commandCwd") || "." }
    });
    state.processes.unshift(data);
    state.logs = data;
    selectTab("logs");
    pushStep(`项目启动命令已执行：${data.id}`);
    showToast(`进程已启动：${data.id}`);
  } catch (error) {
    pushStep(`启动失败：${error.message}`, "error");
    showToast(error.message);
  } finally {
    setBusy(button, false);
  }
}

async function refreshProcesses(button) {
  setBusy(button, true);
  try {
    const data = await api("/api/project/processes", { method: "GET" });
    state.processes = data.processes || [];
    state.logs = data;
    selectTab("logs");
    pushStep("进程状态已刷新");
  } catch (error) {
    pushStep(`刷新进程失败：${error.message}`, "error");
    showToast(error.message);
  } finally {
    setBusy(button, false);
  }
}

async function inspectPage(button) {
  setBusy(button, true);
  try {
    const pageUrl = value("pageUrl");
    if (!pageUrl) {
      throw new Error("请输入前端页面 URL");
    }
    const data = await api("/api/test/page", { body: { url: pageUrl } });
    state.test = { ...(state.test || {}), page: data };
    selectTab("test");
    pushStep(`页面检查完成：HTTP ${data.status}`);
    showToast("页面检查完成");
  } catch (error) {
    pushStep(`页面检查失败：${error.message}`, "error");
    showToast(error.message);
  } finally {
    setBusy(button, false);
  }
}

async function testApi(button) {
  setBusy(button, true);
  try {
    if (!state.fill?.result) {
      throw new Error("请先生成接口参数");
    }
    const base = value("apiBaseUrl").replace(/\/$/, "");
    if (!base) {
      throw new Error("请输入接口 Base URL");
    }
    const request = state.fill.result;
    const path = request.path?.startsWith("/") ? request.path : `/${request.path || ""}`;
    const data = await api("/api/test/request", {
      body: {
        url: `${base}${path}`,
        method: request.method || "GET",
        headers: request.headers || {},
        query: request.query || {},
        body: request.body || {}
      }
    });
    state.test = { ...(state.test || {}), request: data };
    selectTab("test");
    pushStep(`接口测试完成：HTTP ${data.status}，${data.durationMs}ms`);
    showToast(data.ok ? "接口调用成功" : "接口已返回非 2xx，请查看详情");
  } catch (error) {
    pushStep(`接口测试失败：${error.message}`, "error");
    showToast(error.message);
  } finally {
    setBusy(button, false);
  }
}

async function runPipeline(button) {
  setBusy(button, true);
  try {
    await healthCheck();
    if (value("yapiBaseUrl") && (value("interfaceId") || value("projectId"))) {
      await importYapi($("importYapi"));
    } else {
      pushStep("跳过 YAPI：未填写地址或 ID");
    }
    await scanProject($("scanProject"));
    if (state.selectedInterface) {
      await fillParams($("fillParams"));
    }
    if (value("pageUrl")) {
      await inspectPage($("inspectPage"));
    }
    if (value("apiBaseUrl") && state.fill?.result) {
      await testApi($("testApi"));
    }
    selectTab("summary");
    showToast("推荐流程执行完成");
  } finally {
    setBusy(button, false);
  }
}

document.querySelectorAll(".tab").forEach((button) => {
  button.addEventListener("click", () => selectTab(button.dataset.tab));
});

$("healthCheck").addEventListener("click", () => healthCheck().catch((error) => showToast(error.message)));
$("importYapi").addEventListener("click", (event) => importYapi(event.currentTarget));
$("scanProject").addEventListener("click", (event) => scanProject(event.currentTarget));
$("fillParams").addEventListener("click", (event) => fillParams(event.currentTarget));
$("startProject").addEventListener("click", (event) => startProject(event.currentTarget));
$("refreshProcesses").addEventListener("click", (event) => refreshProcesses(event.currentTarget));
$("inspectPage").addEventListener("click", (event) => inspectPage(event.currentTarget));
$("testApi").addEventListener("click", (event) => testApi(event.currentTarget));
$("runPipeline").addEventListener("click", (event) => runPipeline(event.currentTarget));

healthCheck().catch(() => {
  $("serviceState").textContent = "未连接";
});
render();
