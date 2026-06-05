import { create } from 'zustand';
import { api } from './api';
import type { Project } from './types';

interface Toast {
  id: number;
  type: 'success' | 'error' | 'info';
  message: string;
}

interface AppState {
  projects: Project[];
  activeId: string | null;
  loading: boolean;
  toasts: Toast[];
  defaults: { ai: any; yapi: any } | null;
  loadDefaults: () => Promise<void>;
  loadProjects: () => Promise<void>;
  setActive: (id: string | null) => void;
  activeProject: () => Project | undefined;
  toast: (type: Toast['type'], message: string) => void;
  dismiss: (id: number) => void;
}

let toastSeq = 1;

export const useApp = create<AppState>((set, get) => ({
  projects: [],
  activeId: localStorage.getItem('lingce.active') || null,
  loading: false,
  toasts: [],
  defaults: null,
  loadDefaults: async () => {
    try {
      const h = await api.health();
      set({ defaults: h.defaults });
    } catch {
      /* noop */
    }
  },
  loadProjects: async () => {
    set({ loading: true });
    try {
      const projects = await api.listProjects();
      let activeId = get().activeId;
      if (!activeId || !projects.find((p) => p.id === activeId)) {
        activeId = projects[0]?.id ?? null;
      }
      set({ projects, activeId });
      if (activeId) localStorage.setItem('lingce.active', activeId);
    } finally {
      set({ loading: false });
    }
  },
  setActive: (id) => {
    set({ activeId: id });
    if (id) localStorage.setItem('lingce.active', id);
    else localStorage.removeItem('lingce.active');
  },
  activeProject: () => get().projects.find((p) => p.id === get().activeId),
  toast: (type, message) => {
    const id = toastSeq++;
    set((s) => ({ toasts: [...s.toasts, { id, type, message }] }));
    setTimeout(() => get().dismiss(id), 4200);
  },
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));
