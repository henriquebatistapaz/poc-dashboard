import { defineConfig, devices } from '@playwright/test'

const PREVIEW_PORT = Number(process.env.PLAYWRIGHT_PORT || 4179)
const BASE_URL = process.env.UI_BASE_URL || `http://127.0.0.1:${PREVIEW_PORT}`

export default defineConfig({
  testDir: './e2e',
  testIgnore: ['**/visual.spec.ts'],
  fullyParallel: false,
  use: {
    baseURL: BASE_URL,
    locale: 'pt-BR',
    viewport: { width: 1440, height: 1050 },
    launchOptions: process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {},
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
  ],
  webServer: process.env.UI_BASE_URL
    ? undefined
    : {
        command: `npm run preview -- --port ${PREVIEW_PORT} --strictPort`,
        url: BASE_URL,
        reuseExistingServer: !process.env.CI,
      },
})
