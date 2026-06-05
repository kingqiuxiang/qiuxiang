import { spawn } from 'node:child_process';
import { createWriteStream } from 'node:fs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const XMIND_BIN = join(__dirname, '..', 'node_modules', '.bin', process.platform === 'win32' ? 'xmind.cmd' : 'xmind');

export type XmindCliResult = Record<string, unknown>;

async function runXmind(args: string[]): Promise<XmindCliResult> {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(XMIND_BIN, [...args, '-o', 'json'], {
      shell: process.platform === 'win32',
      windowsHide: true,
    });

    let stdout = '';
    let stderr = '';

    child.stdout.on('data', (chunk: Buffer) => {
      stdout += chunk.toString();
    });
    child.stderr.on('data', (chunk: Buffer) => {
      stderr += chunk.toString();
    });

    child.on('error', reject);
    child.on('close', (code) => {
      const text = stdout.trim() || stderr.trim();
      if (!text) {
        reject(new Error(stderr.trim() || `xmind CLI exited with code ${code ?? 'unknown'}`));
        return;
      }

      try {
        const parsed = JSON.parse(text) as XmindCliResult;
        if (parsed.status === 'error') {
          reject(new Error(String(parsed.message ?? text)));
          return;
        }
        resolvePromise(parsed);
      } catch {
        if (code !== 0) {
          reject(new Error(stderr.trim() || stdout.trim() || `xmind CLI exited with code ${code}`));
          return;
        }
        resolvePromise({ status: 'success', raw: text });
      }
    });
  });
}

export async function ensureParentDir(filePath: string): Promise<void> {
  await mkdir(dirname(filePath), { recursive: true });
}

export async function createMap(filePath: string, rootTopic: string): Promise<XmindCliResult> {
  const abs = resolve(filePath);
  await ensureParentDir(abs);
  return runXmind(['map', 'create', '-f', abs, '--root-topic', rootTopic]);
}

export async function readMap(filePath: string, depth?: number): Promise<XmindCliResult> {
  const args = ['map', 'read', '-f', resolve(filePath)];
  if (depth !== undefined) args.push('--depth', String(depth));
  return runXmind(args);
}

export async function addNode(filePath: string, parentId: string, topic: string): Promise<XmindCliResult> {
  return runXmind(['node', 'add', '-f', resolve(filePath), '--parent', parentId, '--topic', topic]);
}

export async function addNodesBatch(filePath: string, parentId: string, topics: string[]): Promise<XmindCliResult> {
  return runXmind([
    'node',
    'add-batch',
    '-f',
    resolve(filePath),
    '--parent',
    parentId,
    '--data',
    JSON.stringify(topics),
  ]);
}

export async function updateNode(
  filePath: string,
  targetId: string,
  fields: { topic?: string; label?: string; note?: string },
): Promise<XmindCliResult> {
  const args = ['node', 'update', '-f', resolve(filePath), '--target', targetId];
  if (fields.topic) args.push('--topic', fields.topic);
  if (fields.label) args.push('--label', fields.label);
  if (fields.note) args.push('--note', fields.note);
  return runXmind(args);
}

export async function deleteNode(filePath: string, targetId: string): Promise<XmindCliResult> {
  return runXmind(['node', 'delete', '-f', resolve(filePath), '--target', targetId]);
}

export async function exportMap(
  filePath: string,
  format: 'md' | 'txt',
  outputFile?: string,
): Promise<string> {
  const args = ['export', '-f', resolve(filePath), '--format', format];
  if (outputFile) args.push('--output-file', resolve(outputFile));

  return new Promise((resolvePromise, reject) => {
    const child = spawn(XMIND_BIN, args, {
      shell: process.platform === 'win32',
      windowsHide: true,
    });

    let stdout = '';
    let stderr = '';

    child.stdout.on('data', (chunk: Buffer) => {
      stdout += chunk.toString();
    });
    child.stderr.on('data', (chunk: Buffer) => {
      stderr += chunk.toString();
    });

    child.on('error', reject);
    child.on('close', async (code) => {
      if (outputFile) {
        try {
          resolvePromise(await readFile(resolve(outputFile), 'utf8'));
          return;
        } catch (error) {
          reject(error);
          return;
        }
      }

      if (code !== 0) {
        reject(new Error(stderr.trim() || stdout.trim() || `export failed with code ${code}`));
        return;
      }
      resolvePromise(stdout.trim());
    });
  });
}

export async function importMarkdown(filePath: string, markdown: string): Promise<XmindCliResult> {
  const abs = resolve(filePath);
  await ensureParentDir(abs);

  const tempSource = `${abs}.import.md`;
  await writeFile(tempSource, markdown, 'utf8');

  try {
    return await runXmind(['import', '-f', abs, '--source', tempSource]);
  } finally {
    await writeFile(tempSource, '', 'utf8').catch(() => undefined);
  }
}

export interface OutlineNode {
  title: string;
  note?: string;
  children?: OutlineNode[];
}

export async function createFromOutline(
  filePath: string,
  rootTitle: string,
  children: OutlineNode[] = [],
): Promise<{ rootId: string; filePath: string }> {
  const created = await createMap(filePath, rootTitle);
  const rootId = String(created.root_id ?? '');

  async function walk(parentId: string, nodes: OutlineNode[]): Promise<void> {
    for (const node of nodes) {
      const added = await addNode(filePath, parentId, node.title);
      const nodeId = String(added.new_node_id ?? added.id ?? '');
      if (node.note && nodeId) {
        await updateNode(filePath, nodeId, { note: node.note });
      }
      if (node.children?.length && nodeId) {
        await walk(nodeId, node.children);
      }
    }
  }

  await walk(rootId, children);
  return { rootId, filePath: resolve(filePath) };
}

export async function writeMarkdownToFile(markdown: string, filePath: string): Promise<string> {
  const abs = resolve(filePath);
  await ensureParentDir(abs);
  await writeFile(abs, markdown, 'utf8');
  return abs;
}

export async function streamExportToFile(
  filePath: string,
  format: 'md' | 'txt',
  outputFile: string,
): Promise<string> {
  await ensureParentDir(outputFile);
  await exportMap(filePath, format, outputFile);
  return resolve(outputFile);
}

export function defaultOutputPath(name: string, outputDir?: string): string {
  const dir = outputDir || process.env.XMIND_OUTPUT_DIR || join(process.cwd(), 'xmind-output');
  const safe = name.replace(/[<>:"/\\|?*]+/g, '-').trim() || 'mindmap';
  return join(dir, `${safe}.xmind`);
}

export async function saveMarkdownTemp(content: string, prefix = 'xmind'): Promise<string> {
  const dir = join(process.cwd(), '.xmind-mcp-temp');
  await mkdir(dir, { recursive: true });
  const file = join(dir, `${prefix}-${Date.now()}.md`);
  await writeFile(file, content, 'utf8');
  return file;
}

export async function importMarkdownFile(filePath: string, sourcePath: string): Promise<XmindCliResult> {
  const abs = resolve(filePath);
  await ensureParentDir(abs);
  return runXmind(['import', '-f', abs, '--source', resolve(sourcePath)]);
}

export async function copyMarkdownToStream(content: string, destPath: string): Promise<void> {
  await ensureParentDir(destPath);
  await new Promise<void>((resolvePromise, reject) => {
    const stream = createWriteStream(destPath, { encoding: 'utf8' });
    stream.on('error', reject);
    stream.on('finish', () => resolvePromise());
    stream.end(content);
  });
}
