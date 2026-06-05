import { describe, it, expect } from "vitest";
import { hasPath } from "./testRunner.js";
import { extractJson } from "./aiClient.js";

describe("hasPath", () => {
  it("点语法定位嵌套字段", () => {
    const obj = { data: { token: "abc", user: { id: 1 } } };
    expect(hasPath(obj, "data.token")).toBe(true);
    expect(hasPath(obj, "data.user.id")).toBe(true);
    expect(hasPath(obj, "data.missing")).toBe(false);
    expect(hasPath(obj, "nope")).toBe(false);
  });
});

describe("extractJson", () => {
  it("解析纯 JSON", () => {
    expect(extractJson('{"a":1}')).toEqual({ a: 1 });
  });
  it("解析围栏代码块中的 JSON", () => {
    expect(extractJson('```json\n{"a":2}\n```')).toEqual({ a: 2 });
  });
  it("从噪声文本中提取 JSON", () => {
    expect(extractJson('好的，结果如下：{"a":3} 完毕')).toEqual({ a: 3 });
  });
});
