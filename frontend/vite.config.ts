import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    proxy: { '/v1': { target: process.env.API_PROXY_TARGET || 'http://127.0.0.1:8084' },
      '/swagger-ui': { target: process.env.API_PROXY_TARGET || 'http://127.0.0.1:8084' },
      '/v3': { target: process.env.API_PROXY_TARGET || 'http://127.0.0.1:8084' } },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,vue}'],
      exclude: ['src/**/*.test.ts', 'src/**/*.d.ts'],
      reporter: ['text-summary', 'json-summary', 'html'],
    },
  },
})
