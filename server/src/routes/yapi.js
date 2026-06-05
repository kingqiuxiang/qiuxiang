import { Router } from 'express';
import { getMenu, getInterfaceById } from '../services/interfaceSource.js';

const router = Router();

router.get('/menu', async (req, res, next) => {
  try {
    const { source, menu } = await getMenu();
    res.json({ source, menu });
  } catch (err) { next(err); }
});

router.get('/interface/:id', async (req, res, next) => {
  try {
    const { source, interface: iface } = await getInterfaceById(req.params.id, req.query.catName || '');
    if (!iface) return res.status(404).json({ error: '未找到接口' });
    res.json({ source, interface: iface });
  } catch (err) { next(err); }
});

export default router;
