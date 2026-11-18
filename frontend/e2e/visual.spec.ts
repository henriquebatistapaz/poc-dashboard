import { expect, test } from '@playwright/test'
import { DEFAULT_HISTORY, installMockApi, loginInUi } from './helpers/mock-api'

test.describe('Visual regression baselines', () => {
  for (const theme of ['light', 'dark'] as const) {
    test(`captures and compares main visual states (${theme})`, async ({ page }, testInfo) => {
      const isMobile = testInfo.project.name.includes('mobile')
      await page.clock.install({ time: new Date('2026-11-17T12:00:00Z') })
      await page.emulateMedia({ reducedMotion: 'reduce' })
      const isolation = await installMockApi(page, { initialHistory: DEFAULT_HISTORY })

      await page.addInitScript((selectedTheme) => {
        localStorage.setItem('poc-dashboard:theme', selectedTheme)
        localStorage.setItem('coleta.locale', 'pt-BR')
      }, theme)

      await page.goto('/')
      await page.evaluate(() => document.fonts.ready)
      await expect(page).toHaveScreenshot(`login-${theme}.png`, { animations: 'disabled' })

      await loginInUi(page)
      await expect(page.locator('.item-title').first()).toBeVisible()
      await page.evaluate(() => document.fonts.ready)

      // 1. Library section / dashboard top
      await page.locator('#library').scrollIntoViewIfNeeded()
      await expect(page).toHaveScreenshot(`library-${theme}.png`, { animations: 'disabled' })

      // 2. Collection panel
      await page.locator('#collect').scrollIntoViewIfNeeded()
      await expect(page).toHaveScreenshot(`collection-${theme}.png`, { animations: 'disabled' })

      // 3. Collection history
      await page.locator('#activity').scrollIntoViewIfNeeded()
      await expect(page).toHaveScreenshot(`history-${theme}.png`, { animations: 'disabled' })

      // 4. ItemDetails modal
      await page.locator('.item-title').first().click()
      await expect(page.locator('dialog.detail-dialog[open]')).toBeVisible()
      await expect(page).toHaveScreenshot(`details-${theme}.png`, { animations: 'disabled' })
      await page.keyboard.press('Escape')

      // 5. Mobile menu open (only on mobile viewport)
      if (isMobile) {
        await page.getByTestId('mobile-menu-toggle').click()
        await expect(page.locator('#mobile-nav-drawer')).toBeVisible()
        await expect(page).toHaveScreenshot(`mobile-menu-${theme}.png`, { animations: 'disabled' })
        await page.keyboard.press('Escape')
      }

      isolation.assertIsolated()
    })
  }
})
