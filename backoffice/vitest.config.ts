import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
    // the product form tests type a lot (2.5 s alone); with every file running in parallel 5 s was too tight
    testTimeout: 20000,
  },
});
