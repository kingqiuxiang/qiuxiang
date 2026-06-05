const state = {
  interfaces: []
};

function $(id) {
  return document.getElementById(id);
}

function setOutput(targetId, value) {
  $(targetId).textContent =
    typeof value === "string" ? value : JSON.stringify(value, null, 2);
}

function safeJsonParse(text, fallback = {}) {
  try {
    return JSON.parse(text || "{}");
  } catch (_error) {
    return fallback;
  }
}

async function request(url, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || "请求失败");
  }
  return data;
}

function renderInterfaceList(items) {
  state.interfaces = items || [];
  if (state.interfaces.length === 0) {
    setOutput("interfaceList", "暂无接口");
    return;
  }
  const rows = state.interfaces.map(
    (it) => `${it.id} | ${it.method.padEnd(6, " ")} | ${it.path} | ${it.title}`
  );
  setOutput("interfaceList", rows.join("\n"));
}

async function loadYapi() {
  const sourceType = $("yapiSourceType").value;
  const value = $("yapiValue").value.trim();
  setOutput("interfaceList", "导入中...");
  const result = await request("/api/yapi/load", { sourceType, value });
  renderInterfaceList(result.interfaces);
}

async function fillParams() {
  const interfaceId = $("interfaceId").value.trim();
  const projectPath = $("projectPath").value.trim() || ".";
  setOutput("fillOutput", "AI 填充中...");
  const result = await request("/api/params/fill", {
    interfaceId,
    projectPath
  });
  setOutput("fillOutput", result);
  $("invokeBody").value = JSON.stringify(result.payload, null, 2);
}

async function startRunner() {
  const command = $("runCommand").value.trim();
  const cwd = $("runCwd").value.trim() || ".";
  setOutput("runOutput", "启动中...");
  const result = await request("/api/run/start", { command, cwd });
  setOutput("runOutput", result);
}

async function executeTest() {
  const frontendUrl = $("frontendUrl").value.trim();
  const expectText = $("expectText").value.trim();
  const url = $("invokeUrl").value.trim();
  const method = $("invokeMethod").value;
  const body = safeJsonParse($("invokeBody").value, {});
  setOutput("testOutput", "自动测试执行中...");
  const result = await request("/api/test/execute", {
    frontendUrl,
    expectText,
    apiRequest: {
      url,
      method,
      body,
      timeoutMs: 20000
    }
  });
  setOutput("testOutput", result);
}

$("loadYapiBtn").addEventListener("click", () => {
  loadYapi().catch((error) => setOutput("interfaceList", `错误: ${error.message}`));
});

$("fillBtn").addEventListener("click", () => {
  fillParams().catch((error) => setOutput("fillOutput", `错误: ${error.message}`));
});

$("runStartBtn").addEventListener("click", () => {
  startRunner().catch((error) => setOutput("runOutput", `错误: ${error.message}`));
});

$("testBtn").addEventListener("click", () => {
  executeTest().catch((error) => setOutput("testOutput", `错误: ${error.message}`));
});
