import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  use: { baseURL: process.env.UI_BASE_URL || 'http://127.0.0.1:5173', locale: 'pt-BR',
    viewport: { width: 1440, height: 1050 },
    launchOptions: process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {},
    trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: process.env.UI_BASE_URL ? undefined : { command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://127.0.0.1:5173', reuseExistingServer: !process.env.CI },
})
