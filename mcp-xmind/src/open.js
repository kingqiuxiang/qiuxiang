import { spawn } from "node:child_process";
import { promises as fs } from "node:fs";

/**
 * 用本地 XMind（或系统默认的 .xmind 关联程序）打开文件。
 *
 * 优先级：
 *   1. 环境变量 XMIND_PATH 指定的 XMind 可执行文件；
 *   2. 各平台默认打开方式（Windows: start / macOS: open / Linux: xdg-open）。
 *
 * @param {string} filePath .xmind 文件绝对路径
 * @returns {Promise<{launched:boolean, via:string}>}
 */
export async function openInXmind(filePath) {
  await fs.access(filePath); // 不存在会抛错

  const xmindPath = process.env.XMIND_PATH?.trim();
  if (xmindPath) {
    const child = spawn(xmindPath, [filePath], {
      detached: true,
      stdio: "ignore",
    });
    child.unref();
    return { launched: true, via: `XMIND_PATH (${xmindPath})` };
  }

  const platform = process.platform;
  if (platform === "win32") {
    // 通过 cmd 的 start 调用默认关联程序（即本地 XMind）。
    // 第一个空字符串参数是 start 的“窗口标题”占位，避免把路径当成标题。
    const child = spawn("cmd", ["/c", "start", "", filePath], {
      detached: true,
      stdio: "ignore",
      windowsHide: true,
    });
    child.unref();
    return { launched: true, via: "windows start (默认关联程序)" };
  }

  if (platform === "darwin") {
    const child = spawn("open", ["-a", "XMind", filePath], {
      detached: true,
      stdio: "ignore",
    });
    child.on("error", () => {
      // 没装名为 XMind 的 app 时退回系统默认
      spawn("open", [filePath], { detached: true, stdio: "ignore" }).unref();
    });
    child.unref();
    return { launched: true, via: "macOS open" };
  }

  // Linux / 其他
  const child = spawn("xdg-open", [filePath], {
    detached: true,
    stdio: "ignore",
  });
  child.unref();
  return { launched: true, via: "xdg-open" };
}
