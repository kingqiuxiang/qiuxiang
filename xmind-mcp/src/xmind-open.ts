import { spawn } from 'node:child_process';
import { access } from 'node:fs/promises';
import { constants } from 'node:fs';
import { resolve } from 'node:path';

const WINDOWS_CANDIDATES = [
  'C:\\Program Files\\XMind\\XMind.exe',
  'C:\\Program Files (x86)\\XMind\\XMind.exe',
  process.env.LOCALAPPDATA ? `${process.env.LOCALAPPDATA}\\Programs\\XMind\\XMind.exe` : '',
  process.env.LOCALAPPDATA ? `${process.env.LOCALAPPDATA}\\XMind\\XMind.exe` : '',
].filter(Boolean);

async function fileExists(path: string): Promise<boolean> {
  try {
    await access(path, constants.F_OK);
    return true;
  } catch {
    return false;
  }
}

export async function resolveXmindExecutable(customPath?: string): Promise<string | undefined> {
  if (customPath) {
    const abs = resolve(customPath);
    if (await fileExists(abs)) return abs;
    throw new Error(`XMind executable not found: ${abs}`);
  }

  const fromEnv = process.env.XMIND_EXE_PATH;
  if (fromEnv && (await fileExists(fromEnv))) return fromEnv;

  for (const candidate of WINDOWS_CANDIDATES) {
    if (await fileExists(candidate)) return candidate;
  }

  return undefined;
}

export async function openInXmind(filePath: string, options?: { exePath?: string }): Promise<string> {
  const abs = resolve(filePath);
  const exe = await resolveXmindExecutable(options?.exePath);

  return new Promise((resolvePromise, reject) => {
    let child;

    if (process.platform === 'win32') {
      if (exe) {
        child = spawn(exe, [abs], { detached: true, stdio: 'ignore', windowsHide: true });
      } else {
        child = spawn('cmd', ['/c', 'start', '', abs], {
          detached: true,
          stdio: 'ignore',
          windowsHide: true,
        });
      }
    } else if (process.platform === 'darwin') {
      child = spawn('open', [exe ?? abs], { detached: true, stdio: 'ignore' });
    } else {
      child = spawn('xdg-open', [abs], { detached: true, stdio: 'ignore' });
    }

    child.on('error', reject);
    child.unref();
    resolvePromise(exe ? `Opened with XMind: ${exe}` : `Opened with system default app: ${abs}`);
  });
}

export function getPlatformHints(): string {
  if (process.platform === 'win32') {
    return [
      'Windows detected.',
      'Set XMIND_EXE_PATH if XMind is installed in a custom location.',
      'Example: C:\\Users\\<you>\\AppData\\Local\\Programs\\XMind\\XMind.exe',
    ].join(' ');
  }
  return `Platform: ${process.platform}. XMind desktop launch uses the system default handler.`;
}
