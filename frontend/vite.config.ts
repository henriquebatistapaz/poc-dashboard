import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

const apiProxy = {
  '/v1': { target: process.env.API_PROXY_TARGET || 'http://127.0.0.1:8084' },
  '/swagger-ui': { target: process.env.API_PROXY_TARGET || 'http://127.0.0.1:8084' },
  '/v3': { target: process.env.API_PROXY_TARGET || 'http://127.0.0.1:8084' },
}

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    proxy: apiProxy,
  },
  preview: {
    host: '127.0.0.1',
    proxy: apiProxy,
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,vue}'],
      exclude: ['src/**/*.test.ts', 'src/**/*.d.ts'],
      reporter: ['text', 'text-summary', 'json-summary', 'html'],
      thresholds: {
        statements: 100,
        branches: 100,
        functions: 100,
        lines: 100,
        perFile: true,
      },
    },
  },
})
