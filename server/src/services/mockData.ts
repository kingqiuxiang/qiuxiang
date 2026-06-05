import type { ApiInterface, ApiProject } from "../types/index.js";

/**
 * 当未配置 YAPI 时使用的演示数据，保证平台开箱即用、可完整体验全流程。
 */
export const mockProject: ApiProject = {
  id: "demo",
  name: "演示项目 (Demo)",
  desc: "未配置 YAPI 时的内置示例数据，可直接体验 AI 一键填充与自动化测试。",
  basepath: "/api",
};

export const mockInterfaces: ApiInterface[] = [
  {
    id: "10001",
    title: "用户登录",
    path: "/api/user/login",
    method: "POST",
    catName: "用户中心",
    desc: "使用账号密码登录，返回 token。",
    params: [
      { name: "username", in: "body", type: "string", required: true, desc: "用户名" },
      { name: "password", in: "body", type: "string", required: true, desc: "密码" },
      { name: "remember", in: "body", type: "boolean", required: false, desc: "记住登录" },
    ],
    reqBodySchema: {
      type: "object",
      required: ["username", "password"],
      properties: {
        username: { type: "string", description: "用户名" },
        password: { type: "string", description: "密码" },
        remember: { type: "boolean", description: "记住登录" },
      },
    },
    resBodySchema: {
      type: "object",
      properties: {
        code: { type: "integer" },
        data: { type: "object", properties: { token: { type: "string" } } },
      },
    },
  },
  {
    id: "10002",
    title: "用户列表（分页）",
    path: "/api/user/list",
    method: "GET",
    catName: "用户中心",
    desc: "分页查询用户列表，支持关键字搜索。",
    params: [
      { name: "pageNum", in: "query", type: "integer", required: true, desc: "页码" },
      { name: "pageSize", in: "query", type: "integer", required: true, desc: "每页数量" },
      { name: "keyword", in: "query", type: "string", required: false, desc: "搜索关键字" },
      { name: "Authorization", in: "header", type: "string", required: true, desc: "登录 token" },
    ],
  },
  {
    id: "10003",
    title: "创建订单",
    path: "/api/order/create",
    method: "POST",
    catName: "订单中心",
    desc: "提交订单，包含商品列表与收货地址。",
    params: [{ name: "Authorization", in: "header", type: "string", required: true, desc: "登录 token" }],
    reqBodySchema: {
      type: "object",
      required: ["items", "address"],
      properties: {
        items: {
          type: "array",
          items: {
            type: "object",
            properties: {
              skuId: { type: "integer" },
              quantity: { type: "integer" },
              price: { type: "number" },
            },
          },
        },
        address: {
          type: "object",
          properties: {
            receiver: { type: "string" },
            phone: { type: "string" },
            detail: { type: "string" },
          },
        },
        remark: { type: "string", description: "备注" },
      },
    },
  },
  {
    id: "10004",
    title: "获取用户详情",
    path: "/api/user/{id}",
    method: "GET",
    catName: "用户中心",
    desc: "根据用户 ID 获取详情。",
    params: [
      { name: "id", in: "path", type: "integer", required: true, desc: "用户 ID" },
      { name: "Authorization", in: "header", type: "string", required: true, desc: "登录 token" },
    ],
  },
];
