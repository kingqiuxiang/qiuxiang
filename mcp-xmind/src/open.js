// 跨平台「用本地 XMind 打开文件 / 拉起 XMind 应用」工具。
// 主要面向 Windows，同时兼容 macOS / Linux。

import { spawn, execFile } from "node:child_process";
import { existsSync } from "node:fs";
import os from "node:os";
import path from "node:path";

/**
 * Windows 上 XMind 常见安装路径（用于在无文件关联时直接拉起应用）。
 */
function windowsXmindCandidates() {
  const candidates = [];
  const envs = [
    process.env.LOCALAPPDATA,
    process.env.ProgramFiles,
    process.env["ProgramFiles(x86)"],
    process.env.ProgramW6432,
  ].filter(Boolean);

  const relPaths = [
    "XMind\\XMind.exe",
    "Programs\\XMind\\XMind.exe",
    "XMind\\xmind.exe",
  ];
  for (const base of envs) {
    for (const rel of relPaths) {
      candidates.push(path.join(base, rel));
    }
  }
  return candidates;
}

function macXmindCandidates() {
  return ["/Applications/XMind.app", "/Applications/XMind 2024.app", "/Applications/XMind 2023.app"];
}

/**
 * 用系统默认关联程序（一般即 XMind）打开 .xmind 文件。
 * 若提供 xmindPath，则强制用该 XMind 可执行文件打开。
 * @returns {Promise<{opened: boolean, via: string, detail?: string}>}
 */
export function openWithXmind(filePath, xmindPath) {
  return new Promise((resolve) => {
    const platform = os.platform();

    // 1) 指定了 XMind 可执行文件路径
    if (xmindPath && existsSync(xmindPath)) {
      try {
        const child =
          platform === "darwin" && xmindPath.endsWith(".app")
            ? spawn("open", ["-a", xmindPath, filePath], { detached: true, stdio: "ignore" })
            : spawn(xmindPath, [filePath], { detached: true, stdio: "ignore" });
        child.unref();
        return resolve({ opened: true, via: `explicit:${xmindPath}` });
      } catch (e) {
        // 落到下面的默认方式
      }
    }

    if (platform === "win32") {
      // 优先用文件关联：cmd /c start "" "file"
      try {
        const child = spawn("cmd", ["/c", "start", "", filePath], {
          detached: true,
          stdio: "ignore",
          windowsHide: true,
        });
        child.unref();
        return resolve({ opened: true, via: "win:start" });
      } catch (e) {
        // 尝试已知安装路径
        const candidate = windowsXmindCandidates().find((p) => existsSync(p));
        if (candidate) {
          try {
            const child = spawn(candidate, [filePath], { detached: true, stdio: "ignore" });
            child.unref();
            return resolve({ opened: true, via: `win:${candidate}` });
          } catch (err) {
            return resolve({ opened: false, via: "win", detail: String(err) });
          }
        }
        return resolve({ opened: false, via: "win:start", detail: String(e) });
      }
    }

    if (platform === "darwin") {
      const app = macXmindCandidates().find((p) => existsSync(p));
      const args = app ? ["-a", app, filePath] : [filePath];
      try {
        const child = spawn("open", args, { detached: true, stdio: "ignore" });
        child.unref();
        return resolve({ opened: true, via: app ? `mac:${app}` : "mac:open" });
      } catch (e) {
        return resolve({ opened: false, via: "mac:open", detail: String(e) });
      }
    }

    // Linux 等
    try {
      const child = spawn("xdg-open", [filePath], { detached: true, stdio: "ignore" });
      child.unref();
      child.on("error", () => {});
      return resolve({ opened: true, via: "linux:xdg-open" });
    } catch (e) {
      return resolve({ opened: false, via: "linux:xdg-open", detail: String(e) });
    }
  });
}

/**
 * 仅拉起 XMind 应用（不打开具体文件）。
 */
export function launchXmind(xmindPath) {
  return new Promise((resolve) => {
    const platform = os.platform();
    const candidates =
      xmindPath
        ? [xmindPath]
        : platform === "win32"
          ? windowsXmindCandidates()
          : platform === "darwin"
            ? macXmindCandidates()
            : [];

    const target = candidates.find((p) => existsSync(p));
    if (!target) {
      return resolve({ opened: false, detail: "未找到 XMind 可执行文件，请通过 xmindPath 指定。" });
    }
    try {
      const child =
        platform === "darwin" && target.endsWith(".app")
          ? spawn("open", ["-a", target], { detached: true, stdio: "ignore" })
          : spawn(target, [], { detached: true, stdio: "ignore" });
      child.unref();
      resolve({ opened: true, via: target });
    } catch (e) {
      resolve({ opened: false, detail: String(e) });
    }
  });
}

/**
 * 计算默认输出目录：优先桌面，其次用户主目录，最后临时目录。
 */
export function defaultOutputDir() {
  const home = os.homedir();
  const desktops = [
    process.env.USERPROFILE && path.join(process.env.USERPROFILE, "Desktop"),
    home && path.join(home, "Desktop"),
    home && path.join(home, "桌面"),
  ].filter(Boolean);
  const desktop = desktops.find((p) => existsSync(p));
  return desktop || home || os.tmpdir();
}
