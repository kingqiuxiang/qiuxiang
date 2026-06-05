import { Router } from 'express';
import { getConfig } from '../config.js';
import { scanProject, findContextForInterface } from '../services/codeService.js';
import { getInterfaceById } from '../services/interfaceSource.js';

const router = Router();

router.get('/scan', (req, res, next) => {
  try {
    const projectPath = req.query.path || getConfig().project.path;
    res.json(scanProject(projectPath));
  } catch (err) { next(err); }
});

router.get('/context/:interfaceId', async (req, res, next) => {
  try {
    const { interface: iface } = await getInterfaceById(req.params.interfaceId, req.query.catName || '');
    if (!iface) return res.status(404).json({ error: '未找到接口' });
    const projectPath = req.query.path || getConfig().project.path;
    res.json(findContextForInterface(projectPath, iface));
  } catch (err) { next(err); }
});

export default router;
