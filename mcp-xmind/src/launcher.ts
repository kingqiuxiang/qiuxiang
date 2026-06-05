import { execFile } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { promisify } from 'util';

const execFileAsync = promisify(execFile);

const WINDOWS_XMIND_CANDIDATES = [
  () => process.env.XMIND_EXE_PATH,
  () => path.join(process.env['ProgramFiles'] ?? 'C:\\Program Files', 'XMind', 'XMind.exe'),
  () => path.join(process.env['ProgramFiles(x86)'] ?? 'C:\\Program Files (x86)', 'XMind', 'XMind.exe'),
  () => path.join(process.env.LOCALAPPDATA ?? '', 'Programs', 'XMind', 'XMind.exe'),
  () => {
    const localAppData = process.env.LOCALAPPDATA ?? '';
    const xmindDir = path.join(localAppData, 'XMind');
    if (!fs.existsSync(xmindDir)) return undefined;
    const appDirs = fs.readdirSync(xmindDir).filter((d) => d.startsWith('app-'));
    for (const appDir of appDirs) {
      const exe = path.join(xmindDir, appDir, 'XMind.exe');
      if (fs.existsSync(exe)) return exe;
    }
    return undefined;
  },
];

export function resolveXmindExe(): string | undefined {
  for (const candidate of WINDOWS_XMIND_CANDIDATES) {
    const exe = candidate();
    if (exe && fs.existsSync(exe)) return exe;
  }
  return undefined;
}

export async function openXmindFile(filePath: string): Promise<{ method: string; detail: string }> {
  const resolved = path.resolve(filePath);
  if (!fs.existsSync(resolved)) {
    throw new Error(`File not found: ${resolved}`);
  }

  const platform = process.platform;

  if (platform === 'win32') {
    const xmindExe = resolveXmindExe();
    if (xmindExe) {
      await execFileAsync(xmindExe, [resolved], { windowsHide: true });
      return { method: 'xmind-exe', detail: xmindExe };
    }

    // Fallback: use Windows file association
    await execFileAsync('cmd.exe', ['/c', 'start', '""', resolved], { windowsHide: true });
    return { method: 'start', detail: 'Windows default association' };
  }

  if (platform === 'darwin') {
    const xmindApp = '/Applications/XMind.app';
    if (fs.existsSync(xmindApp)) {
      await execFileAsync('open', ['-a', 'XMind', resolved]);
      return { method: 'open -a XMind', detail: xmindApp };
    }
    await execFileAsync('open', [resolved]);
    return { method: 'open', detail: 'macOS default association' };
  }

  await execFileAsync('xdg-open', [resolved]);
  return { method: 'xdg-open', detail: 'Linux default association' };
}

export async function launchXmindApp(): Promise<{ method: string; detail: string }> {
  const platform = process.platform;

  if (platform === 'win32') {
    const xmindExe = resolveXmindExe();
    if (xmindExe) {
      await execFileAsync(xmindExe, [], { windowsHide: true });
      return { method: 'xmind-exe', detail: xmindExe };
    }
    await execFileAsync('cmd.exe', ['/c', 'start', 'xmind:'], { windowsHide: true });
    return { method: 'start xmind:', detail: 'Windows protocol handler' };
  }

  if (platform === 'darwin') {
    await execFileAsync('open', ['-a', 'XMind']);
    return { method: 'open -a XMind', detail: '/Applications/XMind.app' };
  }

  await execFileAsync('xdg-open', ['xmind:']);
  return { method: 'xdg-open xmind:', detail: 'Linux protocol handler' };
}

export function getDefaultOutputDir(): string {
  const fromEnv = process.env.XMIND_OUTPUT_PATH;
  if (fromEnv) return path.resolve(fromEnv);

  const docs = path.join(os.homedir(), 'Documents', 'XMind');
  return docs;
}

export function shouldAutoOpen(): boolean {
  return process.env.XMIND_AUTO_OPEN !== 'false';
}
