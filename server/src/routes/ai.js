import { Router } from 'express';
import { getConfig } from '../config.js';
import { getInterfaceById } from '../services/interfaceSource.js';
import { findContextForInterface } from '../services/codeService.js';
import { fillParams, analyzeResult, aiEnabled } from '../services/aiService.js';

const router = Router();

/** One-click AI parameter fill grounded in project code. */
router.post('/fill', async (req, res, next) => {
  try {
    const { interfaceId, catName } = req.body || {};
    const { interface: iface } = await getInterfaceById(interfaceId, catName || '');
    if (!iface) return res.status(404).json({ error: '未找到接口' });

    let codeContext = null;
    const projectPath = getConfig().project.path;
    if (projectPath) {
      try { codeContext = findContextForInterface(projectPath, iface); } catch { /* optional */ }
    }
    const filled = await fillParams(iface, codeContext);
    res.json({ filled, aiEnabled: aiEnabled(), codeMatched: Boolean(codeContext?.matched) });
  } catch (err) { next(err); }
});

router.post('/analyze', async (req, res, next) => {
  try {
    const { interfaceId, catName, request, response } = req.body || {};
    const { interface: iface } = await getInterfaceById(interfaceId, catName || '');
    if (!iface) return res.status(404).json({ error: '未找到接口' });
    const analysis = await analyzeResult(iface, request || {}, response || { status: 0, data: null });
    res.json({ analysis });
  } catch (err) { next(err); }
});

export default router;
