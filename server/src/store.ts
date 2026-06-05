import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { AppData, Project, TestRecord } from './types.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.resolve(__dirname, '../data');
const DATA_FILE = path.join(DATA_DIR, 'db.json');

const empty: AppData = { projects: [], tests: [] };

function ensure() {
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
  if (!fs.existsSync(DATA_FILE)) fs.writeFileSync(DATA_FILE, JSON.stringify(empty, null, 2));
}

function read(): AppData {
  ensure();
  try {
    const raw = fs.readFileSync(DATA_FILE, 'utf-8');
    const parsed = JSON.parse(raw) as AppData;
    return { projects: parsed.projects ?? [], tests: parsed.tests ?? [] };
  } catch {
    return { ...empty };
  }
}

function write(data: AppData) {
  ensure();
  fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2));
}

export const store = {
  getProjects(): Project[] {
    return read().projects;
  },
  getProject(id: string): Project | undefined {
    return read().projects.find((p) => p.id === id);
  },
  saveProject(project: Project) {
    const data = read();
    const idx = data.projects.findIndex((p) => p.id === project.id);
    if (idx >= 0) data.projects[idx] = project;
    else data.projects.push(project);
    write(data);
    return project;
  },
  deleteProject(id: string) {
    const data = read();
    data.projects = data.projects.filter((p) => p.id !== id);
    data.tests = data.tests.filter((t) => t.projectId !== id);
    write(data);
  },
  getTests(projectId?: string): TestRecord[] {
    const tests = read().tests;
    const list = projectId ? tests.filter((t) => t.projectId === projectId) : tests;
    return list.sort((a, b) => b.createdAt - a.createdAt);
  },
  addTest(record: TestRecord) {
    const data = read();
    data.tests.unshift(record);
    // 仅保留最近 500 条
    data.tests = data.tests.slice(0, 500);
    write(data);
    return record;
  },
  clearTests(projectId?: string) {
    const data = read();
    data.tests = projectId ? data.tests.filter((t) => t.projectId !== projectId) : [];
    write(data);
  },
};
