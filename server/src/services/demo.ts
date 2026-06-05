import type { ApiInterface } from '../types.js';

/** 内置演示接口：在未配置 YAPI 时让平台完整可用 */
export function demoInterfaces(): ApiInterface[] {
  return [
    {
      id: 'demo-1001',
      title: '用户登录',
      path: '/api/user/login',
      method: 'POST',
      catName: '用户中心',
      status: 'done',
      reqParams: [],
      reqQuery: [],
      reqHeaders: [{ name: 'Content-Type', required: true, type: 'string', example: 'application/json' }],
      reqBodyType: 'json',
      reqBody: {
        type: 'object',
        required: ['username', 'password'],
        properties: {
          username: { type: 'string', description: '用户名/手机号' },
          password: { type: 'string', description: '密码（明文，后端加密）' },
          captcha: { type: 'string', description: '图形验证码' },
        },
      },
      resBody: {
        type: 'object',
        properties: {
          code: { type: 'integer' },
          message: { type: 'string' },
          data: {
            type: 'object',
            properties: { token: { type: 'string' }, userId: { type: 'integer' } },
          },
        },
      },
    },
    {
      id: 'demo-1002',
      title: '获取用户详情',
      path: '/api/user/{userId}',
      method: 'GET',
      catName: '用户中心',
      status: 'done',
      reqParams: [{ name: 'userId', required: true, type: 'integer', desc: '用户 ID', example: '10086' }],
      reqQuery: [{ name: 'fields', required: false, type: 'string', desc: '返回字段', example: 'base,profile' }],
      reqHeaders: [{ name: 'Authorization', required: true, type: 'string', example: 'Bearer <token>' }],
      reqBodyType: 'none',
      resBody: {
        type: 'object',
        properties: {
          code: { type: 'integer' },
          data: {
            type: 'object',
            properties: {
              userId: { type: 'integer' },
              nickname: { type: 'string' },
              avatar: { type: 'string' },
              level: { type: 'integer' },
            },
          },
        },
      },
    },
    {
      id: 'demo-2001',
      title: '创建订单',
      path: '/api/order/create',
      method: 'POST',
      catName: '交易中心',
      status: 'done',
      reqParams: [],
      reqQuery: [],
      reqHeaders: [{ name: 'Authorization', required: true, type: 'string', example: 'Bearer <token>' }],
      reqBodyType: 'json',
      reqBody: {
        type: 'object',
        required: ['skuId', 'quantity', 'addressId'],
        properties: {
          skuId: { type: 'integer', description: '商品 SKU' },
          quantity: { type: 'integer', description: '购买数量' },
          addressId: { type: 'integer', description: '收货地址 ID' },
          couponId: { type: 'integer', description: '优惠券 ID' },
          remark: { type: 'string', description: '订单备注' },
        },
      },
      resBody: {
        type: 'object',
        properties: {
          code: { type: 'integer' },
          data: { type: 'object', properties: { orderNo: { type: 'string' }, payAmount: { type: 'number' } } },
        },
      },
    },
    {
      id: 'demo-2002',
      title: '订单列表分页查询',
      path: '/api/order/list',
      method: 'GET',
      catName: '交易中心',
      status: 'undone',
      reqParams: [],
      reqQuery: [
        { name: 'page', required: true, type: 'integer', desc: '页码', example: '1' },
        { name: 'pageSize', required: true, type: 'integer', desc: '每页数量', example: '20' },
        { name: 'status', required: false, type: 'string', desc: '订单状态', example: 'paid' },
        { name: 'keyword', required: false, type: 'string', desc: '搜索关键字' },
      ],
      reqHeaders: [{ name: 'Authorization', required: true, type: 'string', example: 'Bearer <token>' }],
      reqBodyType: 'none',
      resBody: {
        type: 'object',
        properties: {
          code: { type: 'integer' },
          data: {
            type: 'object',
            properties: { total: { type: 'integer' }, list: { type: 'array' } },
          },
        },
      },
    },
    {
      id: 'demo-3001',
      title: '商品搜索',
      path: '/api/product/search',
      method: 'GET',
      catName: '商品中心',
      status: 'done',
      reqParams: [],
      reqQuery: [
        { name: 'q', required: true, type: 'string', desc: '搜索词', example: '机械键盘' },
        { name: 'category', required: false, type: 'integer', desc: '分类 ID' },
        { name: 'minPrice', required: false, type: 'number', desc: '最低价' },
        { name: 'maxPrice', required: false, type: 'number', desc: '最高价' },
        { name: 'sort', required: false, type: 'string', desc: '排序: price/sales', example: 'sales' },
      ],
      reqHeaders: [],
      reqBodyType: 'none',
      resBody: {
        type: 'object',
        properties: { code: { type: 'integer' }, data: { type: 'array' } },
      },
    },
  ];
}
