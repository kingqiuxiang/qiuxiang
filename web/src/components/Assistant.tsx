import { AnimatePresence, motion } from "framer-motion";
import { useRef, useState } from "react";
import { Bot, Send, Sparkles, X } from "lucide-react";
import { api } from "../api";
import { Button, cx } from "./ui";

interface Msg {
  role: "user" | "assistant";
  content: string;
}

const SUGGESTIONS = [
  "如何配置 YAPI 和 AI?",
  "帮我解释一键参数填充的原理",
  "怎样让 AI 在写完接口后自动测试?",
];

export function Assistant({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [messages, setMessages] = useState<Msg[]>([
    {
      role: "assistant",
      content:
        "你好,我是 APIPilot 智能助手 👋\n我可以帮你解读接口、生成测试参数、设计用例。配置 AI 后能力更完整。",
    },
  ]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  const send = async (text: string) => {
    const content = text.trim();
    if (!content || loading) return;
    const next = [...messages, { role: "user" as const, content }];
    setMessages(next);
    setInput("");
    setLoading(true);
    try {
      const res = await api.chat(next.map((m) => ({ role: m.role, content: m.content })));
      setMessages((m) => [...m, { role: "assistant", content: res.reply }]);
    } catch (e: any) {
      setMessages((m) => [...m, { role: "assistant", content: `出错了: ${e.message}` }]);
    } finally {
      setLoading(false);
      requestAnimationFrame(() => scrollRef.current?.scrollTo(0, scrollRef.current.scrollHeight));
    }
  };

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/50 backdrop-blur-sm z-40"
          />
          <motion.div
            initial={{ x: 440, opacity: 0.5 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: 440, opacity: 0 }}
            transition={{ type: "spring", stiffness: 320, damping: 34 }}
            className="fixed right-0 top-0 h-full w-full max-w-[420px] z-50 glass border-l border-white/10 flex flex-col"
          >
            <div className="flex items-center justify-between px-5 py-4 border-b border-white/5">
              <div className="flex items-center gap-2.5">
                <div className="size-8 rounded-lg bg-gradient-to-br from-indigo-500 to-cyan-400 grid place-items-center text-slate-950">
                  <Bot size={17} />
                </div>
                <div>
                  <div className="font-semibold text-sm">智能助手</div>
                  <div className="text-[10px] text-slate-500">AI Copilot</div>
                </div>
              </div>
              <button onClick={onClose} className="text-slate-400 hover:text-white p-1.5 rounded-lg hover:bg-white/10">
                <X size={18} />
              </button>
            </div>

            <div ref={scrollRef} className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
              {messages.map((m, i) => (
                <motion.div
                  key={i}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={cx("flex", m.role === "user" ? "justify-end" : "justify-start")}
                >
                  <div
                    className={cx(
                      "max-w-[85%] rounded-2xl px-3.5 py-2.5 text-sm whitespace-pre-wrap leading-relaxed",
                      m.role === "user"
                        ? "bg-gradient-to-r from-indigo-500 to-cyan-400 text-slate-950 font-medium"
                        : "bg-white/5 border border-white/10 text-slate-200"
                    )}
                  >
                    {m.content}
                  </div>
                </motion.div>
              ))}
              {loading && (
                <div className="flex gap-1.5 px-2">
                  {[0, 1, 2].map((i) => (
                    <motion.span
                      key={i}
                      className="size-2 rounded-full bg-cyan-300"
                      animate={{ opacity: [0.3, 1, 0.3] }}
                      transition={{ duration: 1, repeat: Infinity, delay: i * 0.18 }}
                    />
                  ))}
                </div>
              )}
            </div>

            {messages.length <= 1 && (
              <div className="px-5 pb-2 flex flex-wrap gap-2">
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    onClick={() => send(s)}
                    className="text-[11px] px-2.5 py-1.5 rounded-lg bg-white/5 border border-white/10 text-slate-300 hover:border-indigo-400/40 hover:text-white transition-colors flex items-center gap-1"
                  >
                    <Sparkles size={11} className="text-cyan-300" /> {s}
                  </button>
                ))}
              </div>
            )}

            <div className="p-4 border-t border-white/5">
              <div className="flex items-end gap-2 bg-black/30 rounded-xl border border-white/10 p-2">
                <textarea
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault();
                      send(input);
                    }
                  }}
                  rows={1}
                  placeholder="问点什么…  (Enter 发送)"
                  className="flex-1 bg-transparent resize-none outline-none text-sm text-slate-200 placeholder:text-slate-600 max-h-28 px-1.5 py-1"
                />
                <Button size="sm" onClick={() => send(input)} loading={loading} disabled={!input.trim()}>
                  <Send size={14} />
                </Button>
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
