import { execFile } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

const WINDOWS_XMIND_PATHS = [
  "C:\\Program Files\\XMind\\XMind.exe",
  "C:\\Program Files (x86)\\XMind\\XMind.exe",
];

function expandWindowsCandidates(): string[] {
  const candidates: string[] = [];

  if (process.env.XMIND_EXECUTABLE) {
    candidates.push(process.env.XMIND_EXECUTABLE);
  }

  candidates.push(...WINDOWS_XMIND_PATHS);

  const localAppData = process.env.LOCALAPPDATA;
  if (localAppData) {
    candidates.push(path.join(localAppData, "Programs", "xmind", "XMind.exe"));
    candidates.push(path.join(localAppData, "Programs", "XMind", "XMind.exe"));
  }

  const appData = process.env.APPDATA;
  if (appData) {
    candidates.push(path.join(appData, "XMind", "XMind.exe"));
  }

  return candidates;
}

function findWindowsExecutable(): string | null {
  for (const candidate of expandWindowsCandidates()) {
    if (candidate && existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}

export interface OpenResult {
  success: boolean;
  method: string;
  message: string;
  filePath: string;
}

export async function openInXMind(filePath: string): Promise<OpenResult> {
  const resolved = path.resolve(filePath);

  if (!existsSync(resolved)) {
    throw new Error(`File not found: ${resolved}`);
  }

  if (process.platform === "win32") {
    const exe = findWindowsExecutable();
    if (exe) {
      await execFileAsync(exe, [resolved], { windowsHide: true });
      return {
        success: true,
        method: "xmind_executable",
        message: `Opened with ${exe}`,
        filePath: resolved,
      };
    }

    await execFileAsync(
      "cmd.exe",
      ["/c", "start", "", resolved],
      { windowsHide: true },
    );

    return {
      success: true,
      method: "windows_file_association",
      message:
        "Opened via Windows file association. Set XMIND_EXECUTABLE if the wrong app opens.",
      filePath: resolved,
    };
  }

  if (process.platform === "darwin") {
    await execFileAsync("open", [resolved]);
    return {
      success: true,
      method: "mac_open",
      message: "Opened with default application",
      filePath: resolved,
    };
  }

  await execFileAsync("xdg-open", [resolved]);
  return {
    success: true,
    method: "xdg_open",
    message: "Opened with default application",
    filePath: resolved,
  };
}

export function getXMindInstallHint(): string {
  if (process.platform !== "win32") {
    return "Install XMind and ensure .xmind files open with it by default.";
  }

  const exe = findWindowsExecutable();
  if (exe) {
    return `Detected XMind at: ${exe}`;
  }

  return [
    "XMind executable not found in common Windows paths.",
    "Install XMind from https://xmind.com or set XMIND_EXECUTABLE to your XMind.exe path.",
    "Example: C:\\\\Program Files\\\\XMind\\\\XMind.exe",
  ].join(" ");
}
