import express from "express";
import cors from "cors";
import http from "node:http";
import path from "node:path";
import fs from "node:fs";
import { fileURLToPath } from "node:url";
import { config } from "./config/index.js";
import { logger } from "./utils/logger.js";
import { bus } from "./utils/events.js";
import { api } from "./routes/index.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const app = express();
app.use(cors());
app.use(express.json({ limit: "5mb" }));

app.use("/api", api);
app.get("/healthz", (_req, res) => res.json({ ok: true, ts: Date.now() }));

// 生产环境托管前端构建产物
const webDist = path.resolve(__dirname, "../../web/dist");
if (fs.existsSync(webDist)) {
  app.use(express.static(webDist));
  app.get("*", (_req, res) => res.sendFile(path.join(webDist, "index.html")));
  logger.info(`已托管前端静态资源：${webDist}`);
}

const server = http.createServer(app);
bus.attach(server);

server.listen(config.port, () => {
  logger.info(`AI YAPI 测试平台已启动 → http://localhost:${config.port}`);
  logger.info(`YAPI：${config.yapi.configured ? config.yapi.baseUrl : "未配置（使用演示数据）"}`);
  logger.info(`AI：${config.ai.configured ? config.ai.model : "未配置（使用内置启发式）"}`);
  logger.info(`被测项目：${config.project.root || "未配置"}`);
});
