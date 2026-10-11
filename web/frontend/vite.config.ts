import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The dev server proxies /api to the local backend so the browser always talks
// same-origin (the backend binds loopback only). The port is the measured
// decision recorded in .trae/tasks/T-2026-10-10-web-runtime-feasibility-002.md.
export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8733',
        changeOrigin: false,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setupTests.ts',
    globals: true,
  },
});