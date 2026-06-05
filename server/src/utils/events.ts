import { WebSocketServer, WebSocket } from "ws";
import type { Server } from "node:http";
import type { RunEvent } from "../types/index.js";
import { logger } from "./logger.js";

/**
 * 极简事件总线：将运行期日志/结果通过 WebSocket 实时推送到前端。
 * 客户端连接 ws://host/ws 后，会收到所有 RunEvent；通过 runId 过滤即可。
 */
class EventBus {
  private wss?: WebSocketServer;
  private clients = new Set<WebSocket>();

  attach(server: Server) {
    this.wss = new WebSocketServer({ server, path: "/ws" });
    this.wss.on("connection", (ws) => {
      this.clients.add(ws);
      ws.on("close", () => this.clients.delete(ws));
      ws.on("error", () => this.clients.delete(ws));
    });
    logger.info("WebSocket 事件通道已挂载于 /ws");
  }

  emit(event: RunEvent) {
    const data = JSON.stringify(event);
    for (const ws of this.clients) {
      if (ws.readyState === WebSocket.OPEN) ws.send(data);
    }
  }

  log(runId: string, message: string, level: RunEvent["level"] = "info") {
    this.emit({ type: "log", runId, message, level, ts: new Date().toISOString() });
  }
}

export const bus = new EventBus();
