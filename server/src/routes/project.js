import { Router } from 'express';
import { projectRunner } from '../services/projectRunner.js';

const router = Router();

router.get('/status', (req, res) => res.json(projectRunner.getState()));
router.get('/logs', (req, res) => res.json({ logs: projectRunner.getLogs() }));

router.post('/start', (req, res, next) => {
  try {
    const state = projectRunner.start(req.body || {});
    res.json(state);
  } catch (err) { next(err); }
});

router.post('/stop', (req, res) => {
  res.json(projectRunner.stop());
});

router.post('/ready', async (req, res, next) => {
  try {
    const result = await projectRunner.waitReady(req.body || {});
    res.json(result);
  } catch (err) { next(err); }
});

/** Live log stream via SSE. */
router.get('/logs/stream', (req, res) => {
  res.set({ 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
  res.flushHeaders?.();
  const send = (event, data) => { res.write(`event: ${event}\n`); res.write(`data: ${JSON.stringify(data)}\n\n`); };

  projectRunner.getLogs().forEach((l) => send('log', l));
  send('state', projectRunner.getState());

  const onLog = (l) => send('log', l);
  const onState = (s) => send('state', s);
  projectRunner.on('log', onLog);
  projectRunner.on('state', onState);
  const keepAlive = setInterval(() => res.write(': ping\n\n'), 15000);

  req.on('close', () => {
    clearInterval(keepAlive);
    projectRunner.off('log', onLog);
    projectRunner.off('state', onState);
  });
});

export default router;
