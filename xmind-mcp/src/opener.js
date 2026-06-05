import { spawn, execFile } from "node:child_process";
import { existsSync } from "node:fs";
import os from "node:os";
import path from "node:path";

/**
 * Windows 上常见的 XMind 安装路径（用于在「文件关联缺失」时兜底直接拉起 XMind.exe）。
 */
function windowsXmindCandidates() {
  const candidates = [];
  const programFiles = process.env["ProgramFiles"] || "C:/Program Files";
  const programFilesX86 =
    process.env["ProgramFiles(x86)"] || "C:/Program Files (x86)";
  const localAppData =
    process.env["LOCALAPPDATA"] ||
    path.join(os.homedir(), "AppData", "Local");

  for (const base of [programFiles, programFilesX86, localAppData]) {
    candidates.push(path.join(base, "XMind", "XMind.exe"));
    candidates.push(path.join(base, "Xmind", "XMind.exe"));
    candidates.push(path.join(base, "XMind", "XMind 8", "XMind.exe"));
    candidates.push(path.join(base, "Programs", "XMind", "XMind.exe"));
  }
  return candidates;
}

/**
 * 用本地 XMind（或系统默认关联程序）打开指定文件。
 *
 * - Windows：优先用 `cmd /c start` 走文件关联；失败则尝试已知 XMind.exe 路径。
 * - macOS：优先 `open -a XMind`，否则 `open`。
 * - Linux：`xdg-open`。
 *
 * @param {string} filePath 绝对路径
 * @param {string} [explicitApp] 用户显式指定的 XMind 可执行文件路径
 * @returns {Promise<{ok: boolean, method: string, detail?: string}>}
 */
export function openWithXmind(filePath, explicitApp) {
  const platform = os.platform();

  return new Promise((resolve) => {
    const done = (ok, method, detail) => resolve({ ok, method, detail });

    // 用户显式指定可执行文件，直接拉起
    if (explicitApp && existsSync(explicitApp)) {
      const child = spawn(explicitApp, [filePath], {
        detached: true,
        stdio: "ignore",
      });
      child.on("error", (e) => done(false, "explicit-app", e.message));
      child.unref();
      // spawn 不会立刻报错时认为成功
      setTimeout(() => done(true, "explicit-app", explicitApp), 150);
      return;
    }

    if (platform === "win32") {
      // start 的第一个引号参数是窗口标题，必须保留空标题占位
      const child = spawn(
        "cmd",
        ["/c", "start", "", filePath],
        { detached: true, stdio: "ignore", windowsHide: true }
      );
      child.on("error", () => tryWindowsExe());
      child.unref();
      let settled = false;
      child.on("exit", (code) => {
        settled = true;
        if (code === 0) done(true, "cmd-start");
        else tryWindowsExe();
      });
      setTimeout(() => {
        if (!settled) done(true, "cmd-start");
      }, 400);

      function tryWindowsExe() {
        const exe = windowsXmindCandidates().find((p) => existsSync(p));
        if (!exe) {
          return done(
            false,
            "windows",
            "未能通过文件关联打开，且未找到 XMind.exe，请确认已安装 XMind 或传入 xmindAppPath"
          );
        }
        const c2 = spawn(exe, [filePath], {
          detached: true,
          stdio: "ignore",
        });
        c2.on("error", (e) => done(false, "windows-exe", e.message));
        c2.unref();
        setTimeout(() => done(true, "windows-exe", exe), 150);
      }
      return;
    }

    if (platform === "darwin") {
      // 先尝试指定 XMind 应用
      execFile("open", ["-a", "XMind", filePath], (err) => {
        if (!err) return done(true, "open-a-xmind");
        execFile("open", [filePath], (err2) => {
          if (!err2) return done(true, "open-default");
          done(false, "darwin", err2.message);
        });
      });
      return;
    }

    // linux & 其它
    execFile("xdg-open", [filePath], (err) => {
      if (!err) return done(true, "xdg-open");
      done(false, "linux", err.message);
    });
  });
}
