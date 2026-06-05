const output = document.getElementById("output");
const yapiInput = document.getElementById("yapiInput");
const interfaceSelect = document.getElementById("interfaceSelect");
const projectRoot = document.getElementById("projectRoot");
const projectName = document.getElementById("projectName");
const projectCwd = document.getElementById("projectCwd");
const projectCommand = document.getElementById("projectCommand");
const readyKeyword = document.getElementById("readyKeyword");
const apiBaseUrl = document.getElementById("apiBaseUrl");
const uiUrl = document.getElementById("uiUrl");

let interfaces = [];
let selectedFilled = null;

function render(value) {
  output.textContent = typeof value === "string" ? value : JSON.stringify(value, null, 2);
}

function currentInterface() {
  const idx = Number(interfaceSelect.value || "0");
  return interfaces[idx];
}

async function postJSON(url, body) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.detail || JSON.stringify(data));
  }
  return data;
}

document.getElementById("parseBtn").addEventListener("click", async () => {
  try {
    render("正在解析 YAPI...");
    const json = JSON.parse(yapiInput.value || "{}");
    const data = await postJSON("/api/yapi/parse", { source_type: "json", yapi_json: json });
    interfaces = data.interfaces || [];
    interfaceSelect.innerHTML = "";
    interfaces.forEach((item, idx) => {
      const option = document.createElement("option");
      option.value = String(idx);
      option.textContent = `[${item.method}] ${item.path} - ${item.title}`;
      interfaceSelect.appendChild(option);
    });
    render(data);
  } catch (err) {
    render(`解析失败: ${err.message}`);
  }
});

document.getElementById("fillBtn").addEventListener("click", async () => {
  const target = currentInterface();
  if (!target) {
    render("请先解析并选择一个接口");
    return;
  }
  try {
    render("正在生成参数...");
    selectedFilled = await postJSON("/api/ai/fill-params", {
      interface: target,
      project_root: projectRoot.value || ".",
    });
    render(selectedFilled);
  } catch (err) {
    render(`填充失败: ${err.message}`);
  }
});

document.getElementById("startBtn").addEventListener("click", async () => {
  try {
    render("正在启动项目...");
    const data = await postJSON("/api/project/start", {
      project_name: projectName.value || "default-project",
      command: projectCommand.value,
      cwd: projectCwd.value || ".",
      ready_keyword: readyKeyword.value || null,
      timeout_seconds: 25,
    });
    render(data);
  } catch (err) {
    render(`启动失败: ${err.message}`);
  }
});

document.getElementById("runWorkflowBtn").addEventListener("click", async () => {
  const target = currentInterface();
  if (!target) {
    render("请先选择接口");
    return;
  }
  try {
    render("正在执行一键编排...");
    const body = {
      interface: target,
      project_root: projectRoot.value || ".",
      api_test_base_url: apiBaseUrl.value || null,
      run_ui_test_url: uiUrl.value || null,
    };
    if (projectCommand.value) {
      body.start_project = {
        project_name: projectName.value || "default-project",
        command: projectCommand.value,
        cwd: projectCwd.value || ".",
        ready_keyword: readyKeyword.value || null,
        timeout_seconds: 25,
      };
    }
    const data = await postJSON("/api/workflow/after-interface", body);
    selectedFilled = data.filled_params;
    render(data);
  } catch (err) {
    render(`编排失败: ${err.message}`);
  }
});
