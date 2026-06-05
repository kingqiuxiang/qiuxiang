import http from 'node:http';

const PORT = process.env.SAMPLE_PORT || 4199;

// Business constants — ApiPilot's code scanner uses these as grounding context.
const VALID_USER = { username: 'demo_user', password: 'P@ssw0rd', userId: 1001, email: 'demo@example.com' };
const ORDER_STATUS = { CREATED: 'created', PAID: 'paid', CANCELLED: 'cancelled' };
const TOKEN_PREFIX = 'Bearer ';

const ok = (data) => ({ code: 0, message: 'success', data });
const fail = (message, code = 400) => ({ code, message, data: null });

function readBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (c) => { raw += c; });
    req.on('end', () => { try { resolve(raw ? JSON.parse(raw) : {}); } catch { resolve({}); } });
  });
}

function send(res, status, payload) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(payload));
}

// POST /api/user/login -> { token, userId }
async function login(req, res) {
  const body = await readBody(req);
  if (body.username === VALID_USER.username && body.password === VALID_USER.password) {
    return send(res, 200, ok({ token: `${TOKEN_PREFIX}sample-${Date.now()}`, userId: VALID_USER.userId }));
  }
  return send(res, 200, fail('用户名或密码错误', 40001));
}

// GET /api/user/profile?userId=
function profile(req, res, url) {
  const auth = req.headers.authorization || '';
  if (!auth.startsWith(TOKEN_PREFIX)) return send(res, 401, fail('未登录', 40100));
  const userId = Number(url.searchParams.get('userId')) || VALID_USER.userId;
  return send(res, 200, ok({ userId, username: VALID_USER.username, email: VALID_USER.email }));
}

// POST /api/order/create -> { orderId, amount }
async function createOrder(req, res) {
  const auth = req.headers.authorization || '';
  if (!auth.startsWith(TOKEN_PREFIX)) return send(res, 401, fail('未登录', 40100));
  const body = await readBody(req);
  if (!body.productId || !body.quantity) return send(res, 200, fail('缺少 productId 或 quantity', 40002));
  const amount = Number(body.quantity) * 99.9;
  return send(res, 200, ok({ orderId: `ORD${Date.now()}`, amount, status: ORDER_STATUS.CREATED }));
}

// GET /api/order/:orderId
function orderDetail(req, res, orderId) {
  return send(res, 200, ok({ orderId, status: ORDER_STATUS.PAID, amount: 199.8 }));
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const { pathname } = url;

  if (pathname === '/' || pathname === '/health') {
    return send(res, 200, ok({ service: 'sample-project', endpoints: ['/api/user/login', '/api/user/profile', '/api/order/create', '/api/order/:orderId'] }));
  }
  if (pathname === '/api/user/login' && req.method === 'POST') return login(req, res);
  if (pathname === '/api/user/profile' && req.method === 'GET') return profile(req, res, url);
  if (pathname === '/api/order/create' && req.method === 'POST') return createOrder(req, res);
  const orderMatch = pathname.match(/^\/api\/order\/([^/]+)$/);
  if (orderMatch && req.method === 'GET') return orderDetail(req, res, orderMatch[1]);

  return send(res, 404, fail('Not Found', 404));
});

server.listen(PORT, () => {
  console.log(`[sample-project] listening on http://localhost:${PORT}`);
});
