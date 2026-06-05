import { spawn } from "node:child_process";
import os from "node:os";

export type OpenXMindOptions = {
  filePath: string;
  xmindExecutable?: string;
};

export async function openXMindFile(options: OpenXMindOptions): Promise<void> {
  const filePath = options.filePath.trim();
  if (!filePath) {
    throw new Error("filePath is required.");
  }

  const executable = options.xmindExecutable?.trim() || process.env.XMIND_EXE?.trim();
  const platform = os.platform();

  if (platform === "win32") {
    await spawnDetached("cmd", ["/c", "start", "", ...(executable ? [executable] : []), filePath]);
    return;
  }

  if (platform === "darwin") {
    await spawnDetached("open", executable ? ["-a", executable, filePath] : [filePath]);
    return;
  }

  await spawnDetached(executable ?? "xdg-open", executable ? [filePath] : [filePath]);
}

function spawnDetached(command: string, args: string[]): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      detached: true,
      stdio: "ignore",
      windowsHide: true
    });

    child.once("error", reject);
    child.once("spawn", () => {
      child.unref();
      resolve();
    });
  });
}
