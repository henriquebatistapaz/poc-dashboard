import { defineConfig, devices } from '@playwright/test'

const PREVIEW_PORT = Number(process.env.PLAYWRIGHT_PORT || 4179)
const BASE_URL = process.env.UI_BASE_URL || `http://127.0.0.1:${PREVIEW_PORT}`

export default defineConfig({
  testDir: './e2e',
  testMatch: ['**/visual.spec.ts'],
  snapshotPathTemplate: '{testDir}/__screenshots__/{platform}/{projectName}/{arg}{ext}',
  fullyParallel: false,
  expect: {
    toHaveScreenshot: {
      threshold: 0.2,
      maxDiffPixelRatio: 0.001,
    },
  },
  use: {
    baseURL: BASE_URL,
    locale: 'pt-BR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'mobile-webkit-375x812',
      use: {
        ...devices['Desktop Safari'],
        viewport: { width: 375, height: 812 },
        deviceScaleFactor: 2,
      },
    },
    {
      name: 'desktop-chromium-1440x900',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        deviceScaleFactor: 1,
      },
    },
  ],
  webServer: process.env.UI_BASE_URL
    ? undefined
    : {
        command: `npm run preview -- --port ${PREVIEW_PORT} --strictPort`,
        url: BASE_URL,
        reuseExistingServer: !process.env.CI,
      },
})
