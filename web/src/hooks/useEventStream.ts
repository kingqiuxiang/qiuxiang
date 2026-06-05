import { useEffect, useRef, useState, useCallback } from "react";
import type { RunEvent } from "../lib/api";

/**
 * 订阅后端 WebSocket 实时事件流（/ws）。
 * 自动重连，并保留最近的日志条目。
 */
export function useEventStream(maxLogs = 400) {
  const [events, setEvents] = useState<RunEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    let closed = false;
    let retry: ReturnType<typeof setTimeout>;

    const connect = () => {
      const proto = location.protocol === "https:" ? "wss" : "ws";
      const ws = new WebSocket(`${proto}://${location.host}/ws`);
      wsRef.current = ws;
      ws.onopen = () => setConnected(true);
      ws.onclose = () => {
        setConnected(false);
        if (!closed) retry = setTimeout(connect, 1500);
      };
      ws.onerror = () => ws.close();
      ws.onmessage = (e) => {
        try {
          const ev = JSON.parse(e.data) as RunEvent;
          setEvents((prev) => [...prev.slice(-(maxLogs - 1)), ev]);
        } catch {
          /* ignore */
        }
      };
    };

    connect();
    return () => {
      closed = true;
      clearTimeout(retry);
      wsRef.current?.close();
    };
  }, [maxLogs]);

  const clear = useCallback(() => setEvents([]), []);
  return { events, connected, clear };
}
