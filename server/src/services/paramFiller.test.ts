import { describe, it, expect } from "vitest";
import { fillHeuristic } from "./paramFiller.js";
import { mockInterfaces } from "./mockData.js";
import type { ApiInterface } from "../types/index.js";

const login = mockInterfaces.find((i) => i.id === "10001") as ApiInterface;
const list = mockInterfaces.find((i) => i.id === "10002") as ApiInterface;
const order = mockInterfaces.find((i) => i.id === "10003") as ApiInterface;
const detail = mockInterfaces.find((i) => i.id === "10004") as ApiInterface;

describe("fillHeuristic", () => {
  it("从 reqBodySchema 生成 body 对象", () => {
    const f = fillHeuristic(login);
    expect(f.source).toBe("heuristic");
    expect(f.body).toBeTypeOf("object");
    expect(f.body).toHaveProperty("username");
    expect(f.body).toHaveProperty("password");
  });

  it("为 query 与 header 生成示例", () => {
    const f = fillHeuristic(list);
    expect(f.query).toHaveProperty("pageNum");
    expect(f.query).toHaveProperty("pageSize");
    expect(Object.keys(f.headers)).toContain("Authorization");
  });

  it("为分页字段生成数值", () => {
    const f = fillHeuristic(list);
    expect(typeof f.query.pageNum === "string" || typeof f.query.pageNum === "number").toBe(true);
  });

  it("处理嵌套数组/对象 schema", () => {
    const f = fillHeuristic(order) as any;
    expect(Array.isArray(f.body.items)).toBe(true);
    expect(f.body.items[0]).toHaveProperty("skuId");
    expect(f.body.address).toHaveProperty("receiver");
  });

  it("为 path 参数生成值", () => {
    const f = fillHeuristic(detail);
    expect(f.path).toHaveProperty("id");
  });
});
