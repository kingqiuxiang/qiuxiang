import { execFile } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

function windowsXmindCandidates(): string[] {
  const localAppData = process.env.LOCALAPPDATA ?? '';
  const programFiles = process.env.ProgramFiles ?? 'C:\\Program Files';
  const programFilesX86 = process.env['ProgramFiles(x86)'] ?? 'C:\\Program Files (x86)';

  return [
    process.env.XMIND_PATH,
    path.join(localAppData, 'Programs', 'XMind', 'XMind.exe'),
    path.join(localAppData, 'Programs', 'xmind', 'XMind.exe'),
    path.join(programFiles, 'XMind', 'XMind.exe'),
    path.join(programFilesX86, 'XMind', 'XMind.exe'),
  ].filter((p): p is string => Boolean(p));
}

function macXmindCandidates(): string[] {
  return [
    process.env.XMIND_PATH,
    '/Applications/XMind.app/Contents/MacOS/XMind',
    path.join(process.env.HOME ?? '', 'Applications', 'XMind.app', 'Contents', 'MacOS', 'XMind'),
  ].filter((p): p is string => Boolean(p));
}

function linuxXmindCandidates(): string[] {
  return [
    process.env.XMIND_PATH,
    '/usr/bin/xmind',
    '/usr/local/bin/xmind',
    path.join(process.env.HOME ?? '', '.local', 'share', 'xmind', 'XMind'),
  ].filter((p): p is string => Boolean(p));
}

export function resolveXmindExecutable(): string | null {
  const candidates =
    process.platform === 'win32'
      ? windowsXmindCandidates()
      : process.platform === 'darwin'
        ? macXmindCandidates()
        : linuxXmindCandidates();

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }

  return null;
}

export async function openFileInXmind(filePath: string): Promise<string> {
  const resolved = path.resolve(filePath);
  if (!fs.existsSync(resolved)) {
    throw new Error(`文件不存在: ${resolved}`);
  }

  const xmindExe = resolveXmindExecutable();

  if (xmindExe) {
    await execFileAsync(xmindExe, [resolved], { windowsHide: true });
    return `已使用 XMind 打开: ${resolved}`;
  }

  if (process.platform === 'win32') {
    await execFileAsync('cmd.exe', ['/c', 'start', '""', resolved], { windowsHide: true });
    return `已使用系统默认程序打开: ${resolved}`;
  }

  if (process.platform === 'darwin') {
    await execFileAsync('open', [resolved]);
    return `已使用系统默认程序打开: ${resolved}`;
  }

  await execFileAsync('xdg-open', [resolved]);
  return `已使用系统默认程序打开: ${resolved}`;
}

export async function launchXmindApp(): Promise<string> {
  const xmindExe = resolveXmindExecutable();

  if (!xmindExe) {
    throw new Error(
      '未找到 XMind 可执行文件。请安装 XMind，或设置环境变量 XMIND_PATH 指向 XMind.exe 的完整路径。',
    );
  }

  await execFileAsync(xmindExe, [], { windowsHide: true });
  return `已启动 XMind: ${xmindExe}`;
}
