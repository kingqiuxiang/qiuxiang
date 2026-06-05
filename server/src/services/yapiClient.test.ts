import { describe, it, expect } from "vitest";
import { normalizeInterface } from "./yapiClient.js";

describe("normalizeInterface", () => {
  it("转换 query/header/path 与 json body", () => {
    const raw = {
      _id: 42,
      title: "测试接口",
      path: "/api/foo/{id}",
      method: "post",
      req_query: [{ name: "page", required: "1", desc: "页码" }],
      req_headers: [{ name: "Authorization", required: "1", value: "Bearer x" }],
      req_params: [{ name: "id", desc: "主键" }],
      req_body_type: "json",
      req_body_other: JSON.stringify({ type: "object", properties: { a: { type: "string" } } }),
      res_body_type: "json",
      res_body: JSON.stringify({ type: "object", properties: { code: { type: "integer" } } }),
    };
    const iface = normalizeInterface(raw as any);
    expect(iface.id).toBe("42");
    expect(iface.method).toBe("POST");
    expect(iface.params.find((p) => p.in === "query")?.name).toBe("page");
    expect(iface.params.find((p) => p.in === "header")?.required).toBe(true);
    expect(iface.params.find((p) => p.in === "path")?.name).toBe("id");
    expect(iface.reqBodySchema).toHaveProperty("properties");
    expect(iface.resBodySchema).toHaveProperty("properties");
  });

  it("容错非法 JSON body", () => {
    const iface = normalizeInterface({
      _id: 1, title: "x", path: "/x", method: "get",
      req_body_type: "json", req_body_other: "{bad json",
    } as any);
    expect(iface.reqBodySchema).toBeUndefined();
  });
});
