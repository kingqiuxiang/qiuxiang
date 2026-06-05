import { Router } from 'express';
import { getInterfaceById } from '../services/interfaceSource.js';
import { runRequest } from '../services/testRunner.js';
import { testFrontend } from '../services/frontendDriver.js';
import { autoTest } from '../services/orchestrator.js';

const router = Router();

/** Send a single request with user/AI-provided params. */
router.post('/run', async (req, res, next) => {
  try {
    const { interfaceId, catName, filled, overrides } = req.body || {};
    const { interface: iface } = await getInterfaceById(interfaceId, catName || '');
    if (!iface) return res.status(404).json({ error: '未找到接口' });
    const result = await runRequest(iface, filled || {}, overrides || {});
    res.json(result);
  } catch (err) { next(err); }
});

/** Access & check a frontend page of the dev environment. */
router.post('/frontend', async (req, res, next) => {
  try {
    const result = await testFrontend(req.body || {});
    res.json(result);
  } catch (err) { next(err); }
});

/** Full AI auto-test (non-streaming). */
router.post('/auto', async (req, res, next) => {
  try {
    const report = await autoTest(req.body || {});
    res.json(report);
  } catch (err) { next(err); }
});

/** Full AI auto-test with live SSE progress. */
router.get('/auto/stream', async (req, res) => {
  res.set({
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  });
  res.flushHeaders?.();
  const send = (event, data) => {
    res.write(`event: ${event}\n`);
    res.write(`data: ${JSON.stringify(data)}\n\n`);
  };
  try {
    const params = {
      interfaceId: req.query.interfaceId,
      catName: req.query.catName || '',
      overrides: req.query.baseUrl ? { baseUrl: req.query.baseUrl } : {},
    };
    const report = await autoTest(params, (step) => send('step', step));
    send('done', report);
  } catch (err) {
    send('error', { message: err.message });
  } finally {
    res.end();
  }
});

export default router;
