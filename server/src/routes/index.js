import { Router } from 'express';
import configRoutes from './config.js';
import yapiRoutes from './yapi.js';
import codeRoutes from './code.js';
import aiRoutes from './ai.js';
import testRoutes from './test.js';
import projectRoutes from './project.js';

const router = Router();

router.get('/health', (req, res) => res.json({ ok: true, name: 'ApiPilot', ts: Date.now() }));

router.use('/config', configRoutes);
router.use('/yapi', yapiRoutes);
router.use('/code', codeRoutes);
router.use('/ai', aiRoutes);
router.use('/test', testRoutes);
router.use('/project', projectRoutes);

export default router;
