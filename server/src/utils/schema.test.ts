import { describe, it, expect } from "vitest";
import { sampleFromSchema, guessString } from "./schema.js";

describe("sampleFromSchema", () => {
  it("尊重 example/default/enum", () => {
    expect(sampleFromSchema({ type: "string", example: "x" })).toBe("x");
    expect(sampleFromSchema({ type: "number", default: 7 })).toBe(7);
    expect(sampleFromSchema({ type: "string", enum: ["a", "b"] })).toBe("a");
  });

  it("生成对象与嵌套数组", () => {
    const s = {
      type: "object",
      properties: {
        id: { type: "integer" },
        tags: { type: "array", items: { type: "string" } },
      },
    };
    const out = sampleFromSchema(s) as any;
    expect(out).toHaveProperty("id");
    expect(Array.isArray(out.tags)).toBe(true);
  });

  it("布尔与数字类型", () => {
    expect(sampleFromSchema({ type: "boolean" })).toBe(true);
    expect(typeof sampleFromSchema({ type: "integer" })).toBe("number");
  });
});

describe("guessString 语义猜测", () => {
  it("邮箱/手机号/时间", () => {
    expect(guessString("email")).toContain("@");
    expect(guessString("phone")).toMatch(/^\d+$/);
    expect(guessString("createdAt", "date-time")).toContain("T");
  });
});
