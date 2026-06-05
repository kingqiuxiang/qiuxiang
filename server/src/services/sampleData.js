import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { normalizeInterface } from './yapiService.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SAMPLE_FILE = path.resolve(__dirname, '../../sample-data/yapi-sample.json');

let cache = null;
function load() {
  if (!cache) cache = JSON.parse(fs.readFileSync(SAMPLE_FILE, 'utf-8'));
  return cache;
}

export function listMenu() {
  return load().map((cat) => ({
    catId: cat._id,
    name: cat.name,
    desc: cat.desc || '',
    list: (cat.list || []).map((it) => ({
      id: it._id,
      title: it.title,
      method: (it.method || 'GET').toUpperCase(),
      path: it.path,
      status: it.status,
      catName: cat.name,
    })),
  }));
}

export function getInterface(id) {
  for (const cat of load()) {
    const raw = (cat.list || []).find((it) => String(it._id) === String(id));
    if (raw) return normalizeInterface(raw, { catName: cat.name });
  }
  return null;
}
