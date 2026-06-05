import { AnimatePresence, motion } from 'framer-motion';
import { CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';
import { useApp } from '../lib/store';

const icons = {
  success: <CheckCircle2 size={18} className="text-emerald-400" />,
  error: <AlertTriangle size={18} className="text-rose-400" />,
  info: <Info size={18} className="text-brand-400" />,
};

export default function Toasts() {
  const { toasts, dismiss } = useApp();
  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-3 w-80">
      <AnimatePresence>
        {toasts.map((t) => (
          <motion.div
            key={t.id}
            layout
            initial={{ opacity: 0, x: 60, scale: 0.9 }}
            animate={{ opacity: 1, x: 0, scale: 1 }}
            exit={{ opacity: 0, x: 60, scale: 0.9 }}
            transition={{ type: 'spring', stiffness: 380, damping: 30 }}
            className="card px-4 py-3 flex items-start gap-3 shadow-glow"
          >
            <div className="mt-0.5">{icons[t.type]}</div>
            <p className="text-sm text-slate-200 flex-1 leading-snug">{t.message}</p>
            <button onClick={() => dismiss(t.id)} className="text-slate-500 hover:text-slate-300">
              <X size={15} />
            </button>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}
