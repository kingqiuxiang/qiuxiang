import { mkdirSync } from "node:fs";
import path from "node:path";
import { createMindMapFile, readMindMapFile } from "../dist/xmind-io.js";

const outDir = path.join(process.cwd(), "tmp");
mkdirSync(outDir, { recursive: true });

const filePath = await createMindMapFile(
  {
    title: "项目计划",
    sheetTitle: "Q3",
    children: [
      {
        title: "开发",
        children: [{ title: "后端" }, { title: "前端" }],
      },
      {
        title: "测试",
        note: "包含集成测试",
        labels: ["重要"],
      },
    ],
  },
  "smoke-test.xmind",
  outDir,
);

const parsed = readMindMapFile(filePath);
console.log(JSON.stringify(parsed, null, 2));
