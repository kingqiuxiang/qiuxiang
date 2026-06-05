import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5178,
    host: true,
    proxy: {
      '/api': {
        target: process.env.SERVER_URL || 'http://localhost:4178',
        changeOrigin: true,
      },
    },
  },
});
