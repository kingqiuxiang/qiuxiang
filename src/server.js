import express from "express";
import cors from "cors";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { config } from "./config.js";
import { apiRouter } from "./routes/api.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();

app.use(cors());
app.use(express.json({ limit: "8mb" }));
app.use(express.urlencoded({ extended: true }));

app.use("/api", apiRouter);
app.use(express.static(path.resolve(__dirname, "../public")));

app.use((_req, res) => {
  res.sendFile(path.resolve(__dirname, "../public/index.html"));
});

app.use((error, _req, res, _next) => {
  const status = error.status || 500;
  res.status(status).json({
    ok: false,
    error: error.message || "Internal Server Error"
  });
});

app.listen(config.port, () => {
  console.log(`[ai-yapi-autotest-studio] listening on :${config.port}`);
});
