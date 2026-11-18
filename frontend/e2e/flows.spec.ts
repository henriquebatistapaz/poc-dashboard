import { expect, test } from '@playwright/test'
import { DEFAULT_HISTORY, installMockApi, installNetworkIsolation, loginInUi } from './helpers/mock-api'

const MOBILE_VIEWPORTS = [
  { name: '320x640', width: 320, height: 640 },
  { name: '375x812', width: 375, height: 812 },
]

test.describe('Critical mobile flows, theme persistence and XSS safety', () => {
  test.skip(({ browserName }) => browserName === 'firefox', 'Mobile flow suite runs on Chromium and WebKit')

  for (const vp of MOBILE_VIEWPORTS) {
    test(`handles theme auto/light/dark persistence and storage faults (${vp.name})`, async ({ page }) => {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      const isolation = await installMockApi(page, { initialHistory: DEFAULT_HISTORY })

      await page.emulateMedia({ colorScheme: 'dark' })
      await page.goto('/')
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')

      // Reacts to system changes while auto
      await page.emulateMedia({ colorScheme: 'light' })
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')

      // Explicit dark ignores system changes and persists across reload
      await page.getByLabel('Tema', { exact: true }).selectOption('dark')
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
      await page.emulateMedia({ colorScheme: 'light' })
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
      await page.reload()
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')

      // Invalid stored theme falls back to auto without crashing
      await page.evaluate(() => localStorage.setItem('poc-dashboard:theme', 'corrupted-value'))
      await page.reload()
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')

      // Throwing localStorage does not crash UI and keeps auto in memory
      await page.addInitScript(() => {
        Storage.prototype.getItem = () => {
          throw new Error('storage blocked')
        }
        Storage.prototype.setItem = () => {
          throw new Error('storage blocked')
        }
      })
      await page.goto('/')
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
      await page.getByLabel('Tema', { exact: true }).selectOption('dark')
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')

      isolation.assertIsolated()
    })

    test(`handles pending/empty/500/network errors, retry, no-duplicate submit and focus restore (${vp.name})`, async ({ page }) => {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      await installNetworkIsolation(page)

      let loginCount = 0
      let collectCount = 0
      let itemsAttempt = 0
      await page.route('**/v1/**', async (route) => {
        const url = new URL(route.request().url())
        if (url.pathname === '/v1/auth/login') {
          loginCount++
          if (loginCount === 1) {
            return route.fulfill({ status: 500, json: {} })
          }
          return route.fulfill({ json: { accessToken: 'mem-token', tokenType: 'Bearer', expiresIn: 900 } })
        }
        if (url.pathname === '/v1/items' && route.request().method() === 'GET') {
          itemsAttempt++
          if (itemsAttempt === 1) return route.fulfill({ status: 500, json: {} })
          return route.fulfill({ json: { items: [], total: 0, page: 0, size: 10 } })
        }
        if (url.pathname === '/v1/collections' && route.request().method() === 'GET') {
          return route.fulfill({ json: [] })
        }
        if (url.pathname === '/v1/collections' && route.request().method() === 'POST') {
          collectCount++
          await new Promise((r) => setTimeout(r, 150))
          if (collectCount === 1) return route.abort('failed')
          return route.fulfill({ status: 202, json: { id: 7, source: 'RSS', status: 'PENDING', fetched: null, inserted: null, duplicates: null, errorMessage: null } })
        }
        if (url.pathname === '/v1/collections/7') {
          return route.fulfill({ json: { id: 7, source: 'RSS', status: 'SUCCEEDED', fetched: 0, inserted: 0, duplicates: 0, errorMessage: null } })
        }
        return route.fulfill({ status: 500, json: {} })
      })

      await page.goto('/')
      await page.locator('#login-email').fill('admin@example.test')
      await page.locator('#login-password').fill('synthetic-pw')
      await page.locator('button.login-submit').click()
      await expect(page.locator('#login-error')).toBeVisible()
      await expect(page.locator('#login-email')).toHaveValue('admin@example.test')
      await expect(page.locator('#login-password')).toHaveValue('')

      await page.locator('#login-password').fill('synthetic-pw')
      await page.locator('button.login-submit').click()
      await expect(page.locator('.library-notice')).toBeVisible()

      // Retry items -> empty state
      await page.locator('.library-notice button').click()
      await expect(page.locator('.empty-state')).toBeVisible()

      // Double click collection -> only 1 request while pending -> network failure preserves input -> retry succeeds
      await page.locator('#collection-input').fill('https://example.test/feed.xml')
      await page.locator('button.collect-submit').dblclick()
      await expect(page.locator('#collection-error')).toBeVisible()
      expect(collectCount).toBe(1)
      await expect(page.locator('#collection-input')).toHaveValue('https://example.test/feed.xml')

      await page.locator('button.collect-submit').click()
      await expect(page.locator('.collection-result')).toContainText('não retornou itens')

      // Mobile drawer keyboard navigation & focus restoration
      const toggle = page.getByTestId('mobile-menu-toggle')
      await toggle.click()
      await expect(page.locator('#mobile-nav-drawer')).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(page.locator('#mobile-nav-drawer')).toHaveCount(0)
      await expect(toggle).toBeFocused()

      // Verify JWT never touches browser storage
      const storage = await page.evaluate(() => ({ local: { ...localStorage }, session: { ...sessionStorage } }))
      expect(JSON.stringify(storage)).not.toContain('mem-token')
    })

    test(`escapes malicious feed titles/IDs, blocks unsafe links and fits long trilingual copy (${vp.name})`, async ({ page }) => {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      const maliciousItems = [
        {
          id: 1,
          source: 'rss',
          externalId: '<script>window.__xss=1</script>',
          title: '<img src=x onerror=window.__xss=1> Manchete muito longa em português para testar quebra de linha sem overflow horizontal',
          link: 'javascript:alert(1)',
          publishedAt: '2026-11-17T10:00:00Z',
          collectedAt: '2026-11-17T12:00:00Z',
        },
        {
          id: 2,
          source: 'rss',
          externalId: 'safe-2',
          title: 'Safe link item',
          link: 'https://example.test/safe',
          publishedAt: '2026-11-17T10:00:00Z',
          collectedAt: '2026-11-17T12:00:00Z',
        },
      ]
      const isolation = await installMockApi(page, { items: maliciousItems, initialHistory: DEFAULT_HISTORY })
      await loginInUi(page)

      const xssFlag = await page.evaluate(() => (window as unknown as { __xss?: unknown }).__xss)
      expect(xssFlag).toBeUndefined()

      // Open details of malicious item -> javascript: is not navigable
      const trigger = page.locator('.item-title').first()
      await trigger.click()
      await expect(page.locator('dialog.detail-dialog[open]')).toBeVisible()
      await expect(page.locator('dialog.detail-dialog a')).toHaveCount(0)
      await page.keyboard.press('Escape')
      await expect(trigger).toBeFocused()

      // Open details of safe item -> target="_blank" and rel="noopener noreferrer"
      await page.locator('.item-title').nth(1).click()
      const link = page.locator('dialog.detail-dialog a').first()
      await expect(link).toHaveAttribute('target', '_blank')
      await expect(link).toHaveAttribute('rel', 'noopener noreferrer')
      await page.keyboard.press('Escape')

      // Check pt-BR, en, es without overflow
      for (const locale of ['pt-BR', 'en', 'es']) {
        await page.locator('.language-picker select').selectOption(locale)
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
        expect(overflow, `Overflow in ${locale} at ${vp.name}`).toBe(false)
      }

      isolation.assertIsolated()
    })
  }
})
