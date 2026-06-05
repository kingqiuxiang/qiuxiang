import { Router } from 'express';
import { getPublicConfig, updateConfig } from '../config.js';
import { aiEnabled } from '../services/aiService.js';
import { isConfigured as yapiConfigured } from '../services/yapiService.js';
import { frontendCapabilities } from '../services/frontendDriver.js';

const router = Router();

router.get('/', async (req, res) => {
  res.json({
    config: getPublicConfig(),
    capabilities: {
      ai: aiEnabled(),
      yapi: yapiConfigured(),
      ...(await frontendCapabilities()),
    },
  });
});

router.put('/', (req, res) => {
  // Ignore masked secret placeholders so we don't overwrite real keys with "••••".
  const patch = JSON.parse(JSON.stringify(req.body || {}));
  for (const section of ['ai', 'yapi']) {
    if (patch[section]) {
      for (const k of Object.keys(patch[section])) {
        const v = patch[section][k];
        if (typeof v === 'string' && v.includes('••')) delete patch[section][k];
        if (v === '' && (k === 'apiKey' || k === 'token')) delete patch[section][k];
      }
    }
  }
  const updated = updateConfig(patch);
  res.json({ config: updated });
});

export default router;
