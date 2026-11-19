import fs from 'node:fs'
import path from 'node:path'
import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { DEFAULT_HISTORY, installMockApi, loginInUi } from './helpers/mock-api'

interface ViewportCase {
  name: string
  width: number
  height: number
  isTouchProxy?: boolean
}

const CHROMIUM_VIEWPORTS: ViewportCase[] = [
  { name: 'portrait-320', width: 320, height: 640 },
  { name: 'portrait-360', width: 360, height: 800 },
  { name: 'portrait-375', width: 375, height: 812 },
  { name: 'portrait-390', width: 390, height: 844 },
  { name: 'portrait-412', width: 412, height: 915 },
  { name: 'portrait-428', width: 428, height: 926 },
  { name: 'portrait-480', width: 480, height: 960 },
  { name: 'portrait-600', width: 600, height: 960 },
  { name: 'landscape-568', width: 568, height: 320 },
  { name: 'landscape-667', width: 667, height: 375 },
  { name: 'landscape-844', width: 844, height: 390 },
  { name: 'landscape-932', width: 932, height: 430 },
  { name: 'tablet-768', width: 768, height: 1024 },
  { name: 'tablet-820', width: 820, height: 1180 },
  { name: 'tablet-1024x768', width: 1024, height: 768 },
  { name: 'tablet-1024x1366', width: 1024, height: 1366 },
  { name: 'desktop-1280', width: 1280, height: 720 },
  { name: 'desktop-1440', width: 1440, height: 900 },
  { name: 'desktop-1920', width: 1920, height: 1080 },
  { name: 'transition-640', width: 640, height: 900 },
  { name: 'transition-641', width: 641, height: 900 },
  { name: 'zoom200-160', width: 160, height: 320, isTouchProxy: true },
  { name: 'zoom200-188', width: 188, height: 406, isTouchProxy: true },
]

const WEBKIT_VIEWPORTS: ViewportCase[] = [
  { name: 'webkit-320', width: 320, height: 640 },
  { name: 'webkit-375', width: 375, height: 812 },
  { name: 'webkit-844', width: 844, height: 390 },
  { name: 'webkit-768', width: 768, height: 1024 },
  { name: 'webkit-1024', width: 1024, height: 1366 },
  { name: 'desktop-1280', width: 1280, height: 720 },
  { name: 'desktop-1440', width: 1440, height: 900 },
  { name: 'desktop-1920', width: 1920, height: 1080 },
  { name: 'transition-640', width: 640, height: 900 },
  { name: 'transition-641', width: 641, height: 900 },
  { name: 'zoom200-160', width: 160, height: 320, isTouchProxy: true },
  { name: 'zoom200-188', width: 188, height: 406, isTouchProxy: true },
]

const FIREFOX_VIEWPORTS: ViewportCase[] = [
  { name: 'desktop-1280', width: 1280, height: 720 },
  { name: 'desktop-1440', width: 1440, height: 900 },
  { name: 'desktop-1920', width: 1920, height: 1080 },
]

async function assertLayoutAndTargets(page: Page, stateLabel: string) {
  const report = await page.evaluate((label) => {
    const issues: string[] = []
    const viewportMeta = document.querySelector('meta[name="viewport"]')?.getAttribute('content') || ''
    if (!viewportMeta.includes('viewport-fit=cover')) {
      issues.push(`${label}: missing viewport-fit=cover`)
    }

    if (document.documentElement.scrollWidth > window.innerWidth) {
      issues.push(`${label}: document horizontal overflow (${document.documentElement.scrollWidth} > ${window.innerWidth})`)
    }

    const fields = Array.from(document.querySelectorAll<HTMLElement>('input, select, textarea'))
    for (const el of fields) {
      const rect = el.getBoundingClientRect()
      if (rect.width === 0 || rect.height === 0) continue
      const fontSize = parseFloat(getComputedStyle(el).fontSize)
      if (fontSize < 15.9) {
        issues.push(`${label}: field ${el.tagName}#${el.id} font-size ${fontSize}px < 16px`)
      }
    }

    if (window.innerWidth <= 375) {
      for (const form of Array.from(document.querySelectorAll<HTMLFormElement>('form'))) {
        const formFields = Array.from(form.querySelectorAll<HTMLElement>('input, select, textarea'))
          .map((el) => el.getBoundingClientRect())
          .filter((rect) => rect.width > 0 && rect.height > 0)
          .sort((a, b) => a.top - b.top)

        for (let i = 1; i < formFields.length; i++) {
          const previous = formFields[i - 1]!
          const current = formFields[i]!
          if (current.top < previous.bottom - 2) {
            issues.push(`${label}: form fields are not in a single column`)
            break
          }
        }
      }
    }

    const targets = Array.from(document.querySelectorAll<HTMLElement>('button, a[href], select, input, textarea'))
      .filter((el) => {
        if (el.closest('[inert]')) return false
        const style = getComputedStyle(el)
        if (style.display === 'none' || style.visibility === 'hidden') return false
        const rect = el.getBoundingClientRect()
        return rect.width > 0 && rect.height > 0
      })

    for (const el of targets) {
      const rect = el.getBoundingClientRect()
      if (rect.width < 43.5 || rect.height < 43.5) {
        issues.push(`${label}: target ${el.tagName}.${el.className} size ${rect.width.toFixed(1)}x${rect.height.toFixed(1)} < 44x44`)
      }
    }

    // Check spacing >= 8px between interactive siblings sharing the same direct parent
    const byParent = new Map<Element, DOMRect[]>()
    for (const el of targets) {
      const parent = el.parentElement
      if (!parent) continue
      const list = byParent.get(parent) ?? []
      list.push(el.getBoundingClientRect())
      byParent.set(parent, list)
    }
    for (const rects of byParent.values()) {
      for (let i = 0; i < rects.length; i++) {
        for (let j = i + 1; j < rects.length; j++) {
          const a = rects[i]!
          const b = rects[j]!
          const overlapY = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top) > 2
          const overlapX = Math.min(a.right, b.right) - Math.max(a.left, b.left) > 2
          if (overlapY && !overlapX) {
            const gapX = Math.max(b.left - a.right, a.left - b.right)
            if (gapX >= 0 && gapX < 7.5) {
              issues.push(`${label}: horizontal gap ${gapX.toFixed(1)}px < 8px`)
            }
          } else if (overlapX && !overlapY) {
            const gapY = Math.max(b.top - a.bottom, a.top - b.bottom)
            if (gapY >= 0 && gapY < 7.5) {
              issues.push(`${label}: vertical gap ${gapY.toFixed(1)}px < 8px`)
            }
          }
        }
      }
    }

    return issues
  }, stateLabel)

  expect(report, `Layout/target issues in ${stateLabel}`).toEqual([])
}

async function runAxeAudit(page: Page, state: string, theme: string, engine: string, collector: Array<Record<string, unknown>>) {
  const results = await new AxeBuilder({ page }).analyze()
  const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
  collector.push({
    engine,
    theme,
    state,
    violations: results.violations.map((v) => ({
      id: v.id,
      impact: v.impact,
      description: v.description,
      nodes: v.nodes.length,
    })),
  })
  expect(serious, `Serious/critical axe violations on ${engine}/${theme}/${state}`).toEqual([])
}

test.describe('Responsive matrix and accessibility gate', () => {
  const runViewportGroup = async (page: Page, browserName: string, viewports: ViewportCase[]) => {
    for (const theme of ['light', 'dark'] as const) {
      for (const vp of viewports) {
        const isolation = await installMockApi(page, { initialHistory: DEFAULT_HISTORY })
        await page.setViewportSize({ width: vp.width, height: vp.height })
        if (vp.isTouchProxy) {
          await page.emulateMedia({ reducedMotion: 'reduce' })
        }
        await page.addInitScript((selectedTheme) => {
          localStorage.setItem('poc-dashboard:theme', selectedTheme)
        }, theme)

        await page.goto('/')
        await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
        await assertLayoutAndTargets(page, `${browserName}/${theme}/${vp.name}/login`)

        await loginInUi(page)
        await expect(page.locator('.item-title').first()).toBeVisible()
        await assertLayoutAndTargets(page, `${browserName}/${theme}/${vp.name}/workspace`)

        const tableConfined = await page.evaluate(() => {
          const region = document.querySelector('.table-scroll')
          return Boolean(region) && document.documentElement.scrollWidth <= window.innerWidth
        })
        expect(tableConfined).toBe(true)

        if (vp.width <= 850) {
          await page.getByTestId('mobile-menu-toggle').click()
          await expect(page.locator('#mobile-nav-drawer')).toBeVisible()
          await assertLayoutAndTargets(page, `${browserName}/${theme}/${vp.name}/mobile-menu`)
          await page.keyboard.press('Escape')
          await expect(page.locator('#mobile-nav-drawer')).toHaveCount(0)
        }

        await page.locator('.item-title').first().click()
        await expect(page.locator('dialog.detail-dialog[open]')).toBeVisible()
        await assertLayoutAndTargets(page, `${browserName}/${theme}/${vp.name}/details`)
        await page.keyboard.press('Escape')
        await expect(page.locator('dialog.detail-dialog')).toHaveCount(0)

        isolation.assertIsolated()
      }
    }
  }

  test('verifies portrait and safari mobile viewports across light and dark themes', async ({ page, browserName }) => {
    test.setTimeout(120000)
    const viewports =
      browserName === 'chromium'
        ? CHROMIUM_VIEWPORTS.filter((v) => v.name.startsWith('portrait-'))
        : browserName === 'webkit'
          ? WEBKIT_VIEWPORTS.filter((v) => v.name.startsWith('webkit-'))
          : FIREFOX_VIEWPORTS.slice(0, 1)
    await runViewportGroup(page, browserName, viewports)
  })

  test('verifies landscape, tablet, desktop, transition and 200% proxy zoom viewports', async ({ page, browserName }) => {
    test.setTimeout(150000)
    const viewports =
      browserName === 'chromium'
        ? CHROMIUM_VIEWPORTS.filter((v) => !v.name.startsWith('portrait-'))
        : browserName === 'webkit'
          ? WEBKIT_VIEWPORTS.filter((v) => !v.name.startsWith('webkit-'))
          : FIREFOX_VIEWPORTS.slice(1)
    await runViewportGroup(page, browserName, viewports)
  })

  test('passes axe-core across all inventory states in both themes', async ({ page, browserName }) => {
    test.setTimeout(120000)
    const targetViewport =
      browserName === 'firefox' ? { width: 1440, height: 900 } : { width: 375, height: 812 }
    const auditLog: Array<Record<string, unknown>> = []

    for (const theme of ['light', 'dark'] as const) {
      const isolation = await installMockApi(page, { initialHistory: DEFAULT_HISTORY })
      await page.setViewportSize(targetViewport)
      await page.addInitScript((selectedTheme) => {
        localStorage.setItem('poc-dashboard:theme', selectedTheme)
      }, theme)

      // 1. LoginView
      await page.goto('/')
      await runAxeAudit(page, 'login', theme, browserName, auditLog)

      // 2. LoginView with validation error
      await page.locator('button.login-submit').click()
      await expect(page.locator('#login-error')).toBeVisible()
      await runAxeAudit(page, 'login-error', theme, browserName, auditLog)

      // 3. Workspace populated
      await page.locator('#login-email').fill('admin@example.test')
      await page.locator('#login-password').fill('synthetic-pw')
      await page.locator('button.login-submit').click()
      await expect(page.locator('.item-title').first()).toBeVisible()
      await runAxeAudit(page, 'workspace-populated', theme, browserName, auditLog)

      // 4. Mobile menu open (when <= 850)
      if (targetViewport.width <= 850) {
        await page.getByTestId('mobile-menu-toggle').click()
        await expect(page.locator('#mobile-nav-drawer')).toBeVisible()
        await runAxeAudit(page, 'mobile-menu', theme, browserName, auditLog)
        await page.keyboard.press('Escape')
      }

      // 5. ItemDetails open
      await page.locator('.item-title').first().click()
      await expect(page.locator('dialog.detail-dialog[open]')).toBeVisible()
      await runAxeAudit(page, 'details', theme, browserName, auditLog)
      await page.keyboard.press('Escape')

      // 6. Focus visibility check (>= 2px outline)
      await page.locator('button[aria-label="Sair"]').focus()
      const focusOutline = await page.evaluate(() => {
        const el = document.activeElement
        return el ? parseFloat(getComputedStyle(el).outlineWidth) : 0
      })
      expect(focusOutline).toBeGreaterThanOrEqual(2)

      isolation.assertIsolated()

      // 7. Empty workspace state
      const emptyIso = await installMockApi(page, { items: [], initialHistory: [] })
      await page.goto('/')
      await loginInUi(page)
      await expect(page.locator('.empty-state')).toBeVisible()
      await runAxeAudit(page, 'workspace-empty', theme, browserName, auditLog)
      emptyIso.assertIsolated()
    }

    const dateStamp = process.env.AUDIT_DATE || '2026-11-18'
    const outDir = path.resolve(process.cwd(), '..', 'docs', 'auditorias', `${dateStamp}-final`)
    fs.mkdirSync(outDir, { recursive: true })
    fs.writeFileSync(path.join(outDir, `axe-${browserName}.json`), JSON.stringify(auditLog, null, 2))
  })
})
