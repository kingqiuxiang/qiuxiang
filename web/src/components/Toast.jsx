import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { CheckCircle2, XCircle, Info } from 'lucide-react';
import clsx from 'clsx';

const ICON = { good: CheckCircle2, bad: XCircle, default: Info };
const TONE = { good: 'border-emerald-500/40 text-emerald-200', bad: 'border-rose-500/40 text-rose-200', default: 'border-white/15 text-slate-200' };

export default function Toaster({ toasts }) {
  return (
    <div className="pointer-events-none fixed bottom-5 right-5 z-[60] flex flex-col gap-2">
      <AnimatePresence>
        {toasts.map((t) => {
          const Icon = ICON[t.tone] || Info;
          return (
            <motion.div
              key={t.id}
              initial={{ opacity: 0, x: 40, scale: 0.95 }}
              animate={{ opacity: 1, x: 0, scale: 1 }}
              exit={{ opacity: 0, x: 40, scale: 0.95 }}
              className={clsx('glass pointer-events-auto flex max-w-sm items-start gap-2 rounded-xl border px-4 py-3 text-sm shadow-card', TONE[t.tone] || TONE.default)}
            >
              <Icon size={16} className="mt-0.5 shrink-0" />
              <span>{t.message}</span>
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
