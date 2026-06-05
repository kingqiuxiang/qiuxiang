import express from 'express';
import cors from 'cors';
import routes from './routes/index.js';

const app = express();
const PORT = process.env.PORT || 4178;

const corsOrigin = process.env.CORS_ORIGIN
  ? process.env.CORS_ORIGIN.split(',').map((s) => s.trim())
  : true;

app.use(cors({ origin: corsOrigin }));
app.use(express.json({ limit: '4mb' }));

app.use((req, res, next) => {
  const t = Date.now();
  res.on('finish', () => {
    if (!req.path.includes('/stream')) {
      console.log(`${req.method} ${req.originalUrl} ${res.statusCode} ${Date.now() - t}ms`);
    }
  });
  next();
});

app.use('/api', routes);

// Central error handler with friendly codes.
app.use((err, req, res, next) => { // eslint-disable-line no-unused-vars
  const status = err.status || (err.code === 'YAPI_NOT_CONFIGURED' ? 400 : 500);
  console.error('[error]', err.code || '', err.message);
  res.status(status).json({ error: err.message, code: err.code || 'INTERNAL_ERROR' });
});

app.listen(PORT, () => {
  console.log(`\n  ⚡ ApiPilot server listening on http://localhost:${PORT}`);
  console.log(`     API base: http://localhost:${PORT}/api\n`);
});
