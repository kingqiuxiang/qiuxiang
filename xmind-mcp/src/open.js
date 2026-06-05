import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import os from "node:os";
import path from "node:path";

/**
 * 在常见安装路径中尝试定位 Windows 上的 XMind.exe。
 * 也可通过环境变量 XMIND_PATH 显式指定可执行文件路径。
 * @returns {string|null}
 */
export function findWindowsXmind() {
  const env = process.env.XMIND_PATH;
  if (env && existsSync(env)) return env;

  const home = os.homedir();
  const candidates = [
    process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, "Programs", "XMind", "XMind.exe"),
    process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, "XMind", "XMind.exe"),
    process.env["ProgramFiles"] && path.join(process.env["ProgramFiles"], "XMind", "XMind.exe"),
    process.env["ProgramFiles(x86)"] && path.join(process.env["ProgramFiles(x86)"], "XMind", "XMind.exe"),
    path.join(home, "AppData", "Local", "Programs", "XMind", "XMind.exe"),
  ].filter(Boolean);

  for (const c of candidates) {
    try {
      if (existsSync(c)) return c;
    } catch {
      /* ignore */
    }
  }
  return null;
}

/**
 * 用本地 XMind 打开指定文件。Windows 优先，同时兼容 macOS / Linux。
 * @param {string} filePath  .xmind 文件绝对路径
 * @returns {{opened:boolean, via:string, detail?:string}}
 */
export function openWithXmind(filePath) {
  const platform = os.platform();

  try {
    if (platform === "win32") {
      const exe = findWindowsXmind();
      if (exe) {
        spawn(exe, [filePath], { detached: true, stdio: "ignore" }).unref();
        return { opened: true, via: exe };
      }
      // 回退：用系统默认关联程序打开（通常即 XMind）
      // start 是 cmd 内建命令，第一个引号参数为窗口标题占位
      spawn("cmd", ["/c", "start", "", filePath], { detached: true, stdio: "ignore", windowsHide: true }).unref();
      return { opened: true, via: "cmd start (系统默认关联程序)" };
    }

    if (platform === "darwin") {
      // 优先尝试用 XMind 应用打开；失败则用默认程序
      spawn("open", ["-a", "XMind", filePath], { detached: true, stdio: "ignore" }).unref();
      return { opened: true, via: "open -a XMind" };
    }

    // Linux 及其他
    const env = process.env.XMIND_PATH;
    if (env && existsSync(env)) {
      spawn(env, [filePath], { detached: true, stdio: "ignore" }).unref();
      return { opened: true, via: env };
    }
    spawn("xdg-open", [filePath], { detached: true, stdio: "ignore" }).unref();
    return { opened: true, via: "xdg-open" };
  } catch (err) {
    return { opened: false, via: platform, detail: String(err?.message || err) };
  }
}
