import { access } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';

export type LaunchXmindOptions = {
  filePath?: string;
  xmindPath?: string;
};

export type LaunchXmindResult = {
  launched: boolean;
  method?: 'executable' | 'file-association' | 'open-command';
  command?: string;
  message: string;
};

export async function findXmindExecutable(explicitPath?: string): Promise<string | null> {
  const candidates = [
    explicitPath,
    process.env.XMIND_PATH,
    ...windowsCandidates(),
  ].filter((candidate): candidate is string => Boolean(candidate?.trim()));

  for (const candidate of candidates) {
    if (await exists(candidate)) {
      return candidate;
    }
  }

  return null;
}

export async function launchXmind(options: LaunchXmindOptions): Promise<LaunchXmindResult> {
  const executable = await findXmindExecutable(options.xmindPath);

  if (executable) {
    spawnDetached(executable, options.filePath ? [options.filePath] : []);
    return {
      launched: true,
      method: 'executable',
      command: executable,
      message: options.filePath ? `已用 XMind 打开：${options.filePath}` : '已启动 XMind。',
    };
  }

  if (!options.filePath) {
    return {
      launched: false,
      message: '未找到 XMind 可执行文件。请设置 XMIND_PATH，或传入 xmindPath。',
    };
  }

  if (process.platform === 'win32') {
    spawnDetached('cmd.exe', ['/c', 'start', '', options.filePath]);
    return {
      launched: true,
      method: 'file-association',
      command: 'cmd.exe /c start',
      message: `未定位到 XMind.exe，已通过 Windows 文件关联打开：${options.filePath}`,
    };
  }

  if (process.platform === 'darwin') {
    spawnDetached('open', ['-a', 'XMind', options.filePath]);
    return {
      launched: true,
      method: 'open-command',
      command: 'open -a XMind',
      message: `已调用 macOS open 命令打开：${options.filePath}`,
    };
  }

  spawnDetached('xdg-open', [options.filePath]);
  return {
    launched: true,
    method: 'open-command',
    command: 'xdg-open',
    message: `已调用 xdg-open 打开：${options.filePath}`,
  };
}

function spawnDetached(command: string, args: string[]): void {
  const child = spawn(command, args, {
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
  });
  child.unref();
}

async function exists(filePath: string): Promise<boolean> {
  try {
    await access(filePath);
    return true;
  } catch {
    return false;
  }
}

function windowsCandidates(): string[] {
  if (process.platform !== 'win32') {
    return [];
  }

  const localAppData = process.env.LOCALAPPDATA ?? path.join(os.homedir(), 'AppData', 'Local');
  const programFiles = process.env.ProgramFiles ?? 'C:\\Program Files';
  const programFilesX86 = process.env['ProgramFiles(x86)'] ?? 'C:\\Program Files (x86)';

  return [
    path.join(programFiles, 'Xmind', 'Xmind.exe'),
    path.join(programFiles, 'XMind', 'XMind.exe'),
    path.join(programFiles, 'XMind', 'Xmind.exe'),
    path.join(programFilesX86, 'Xmind', 'Xmind.exe'),
    path.join(programFilesX86, 'XMind', 'XMind.exe'),
    path.join(localAppData, 'Programs', 'Xmind', 'Xmind.exe'),
    path.join(localAppData, 'Programs', 'XMind', 'XMind.exe'),
    path.join(localAppData, 'Programs', 'Xmind', 'Xmind', 'Xmind.exe'),
    path.join(localAppData, 'Programs', 'XMind', 'XMind', 'XMind.exe'),
  ];
}
