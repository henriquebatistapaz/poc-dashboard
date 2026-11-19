/* global document, HTMLElement */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { navigation, snapshot } from 'lighthouse'
import puppeteer from 'puppeteer-core'
import { chromium } from 'playwright'
import { startMockServer } from './mock-server.mjs'

const require = createRequire(import.meta.url)
const lhPkg = require('lighthouse/package.json')
const __dirname = path.dirname(fileURLToPath(import.meta.url))
const frontendDir = path.resolve(__dirname, '..')
const rootDir = path.resolve(frontendDir, '..')

const args = process.argv.slice(2)
const enforce = args.includes('--enforce')
const phaseArg = args.find((a) => a.startsWith('--phase='))?.split('=')[1]
const phase = phaseArg || process.env.LIGHTHOUSE_PHASE || 'final'
const dateStamp = process.env.AUDIT_DATE || new Date().toISOString().slice(0, 10)
const outDir = path.join(rootDir, 'docs', 'auditorias', `${dateStamp}-${phase}`)
fs.mkdirSync(outDir, { recursive: true })

function resolveChromeExecutable() {
  if (process.env.CHROME_PATH && fs.existsSync(process.env.CHROME_PATH)) {
    return process.env.CHROME_PATH
  }
  const pwExec = chromium.executablePath()
  if (pwExec && fs.existsSync(pwExec)) {
    return pwExec
  }
  const cacheBase = path.join(os.homedir(), 'Library', 'Caches', 'ms-playwright')
  if (fs.existsSync(cacheBase)) {
    const dirs = fs.readdirSync(cacheBase).filter((d) => d.startsWith('chromium-')).sort().reverse()
    for (const dir of dirs) {
      const candidate = path.join(cacheBase, dir, 'chrome-mac-arm64', 'Google Chrome for Testing.app', 'Contents', 'MacOS', 'Google Chrome for Testing')
      if (fs.existsSync(candidate)) return candidate
    }
  }
  throw new Error('Chromium executable not found. Run npx playwright install chromium or set CHROME_PATH.')
}

const MOBILE_CONFIG = {
  extends: 'lighthouse:default',
  settings: {
    onlyCategories: ['performance', 'accessibility'],
    formFactor: 'mobile',
    screenEmulation: {
      mobile: true,
      width: 412,
      height: 823,
      deviceScaleFactor: 1.75,
      disabled: false,
    },
    throttlingMethod: 'simulate',
    throttling: {
      rttMs: 150,
      throughputKbps: 1638.4,
      requestLatencyMs: 562.5,
      downloadThroughputKbps: 1474.56,
      uploadThroughputKbps: 675,
      cpuSlowdownMultiplier: 4,
    },
  },
}

const SNAPSHOT_CONFIG = {
  ...MOBILE_CONFIG,
  settings: {
    ...MOBILE_CONFIG.settings,
    onlyCategories: ['accessibility'],
  },
}

const median = (values) => {
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 !== 0 ? sorted[mid] : Math.round((sorted[mid - 1] + sorted[mid]) / 2)
}

async function withFreshBrowser(fn) {
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'lh-poc-dashboard-'))
  const browser = await puppeteer.launch({
    executablePath: resolveChromeExecutable(),
    headless: 'new',
    userDataDir,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'],
  })
  try {
    const page = await browser.newPage()
    const version = await browser.version()
    return await fn(page, version)
  } finally {
    await browser.close()
    fs.rmSync(userDataDir, { recursive: true, force: true })
  }
}

async function performLogin(page, baseUrl) {
  await page.goto(baseUrl, { waitUntil: 'networkidle0' })
  await page.waitForSelector('#login-email')
  await page.type('#login-email', 'admin@example.test')
  await page.type('#login-password', 'synthetic-pw')
  await page.click('button.login-submit')
  await page.waitForSelector('.library-card')
  await page.waitForSelector('.item-title')
}

async function runColdNavigation(stateName, baseUrl, runIndex, theme = 'light') {
  return withFreshBrowser(async (page, chromeVersion) => {
    await page.setViewport({ width: 412, height: 823, isMobile: true, hasTouch: true })
    await page.setRequestInterception(true)
    page.on('request', (req) => {
      const reqUrl = req.url()
      if (reqUrl.startsWith(baseUrl) || reqUrl.startsWith('data:') || reqUrl === 'about:blank') {
        req.continue()
      } else {
        req.abort()
      }
    })
    if (theme === 'dark') {
      await page.evaluateOnNewDocument(() => {
        localStorage.setItem('poc-dashboard:theme', 'dark')
      })
    } else {
      await page.evaluateOnNewDocument(() => {
        localStorage.setItem('poc-dashboard:theme', 'light')
      })
    }

    const runnerResult = await navigation(
      page,
      async () => {
        if (stateName === 'login') {
          await page.goto(baseUrl, { waitUntil: 'networkidle0' })
          await page.waitForSelector('#login-email')
        } else {
          await performLogin(page, baseUrl)
          if (stateName === 'collection') {
            await page.evaluate(() => document.getElementById('collect')?.scrollIntoView({ behavior: 'instant' }))
          } else if (stateName === 'history') {
            await page.evaluate(() => document.getElementById('activity')?.scrollIntoView({ behavior: 'instant' }))
      } else if (stateName === 'details') {
        await page.evaluate(() => {
          const el = document.querySelector('.item-title')
          if (el instanceof HTMLElement) {
            el.scrollIntoView({ block: 'center' })
            el.click()
          }
        })
        await page.waitForSelector('dialog.detail-dialog[open]')
      } else if (stateName === 'mobile-menu') {
        const toggle = await page.$('[data-testid="mobile-menu-toggle"]')
        if (!toggle) throw new Error('mobile-menu toggle not found')
        await toggle.click()
        await page.waitForSelector('#mobile-nav-drawer')
          }
        }
      },
      {
        config: MOBILE_CONFIG,
        flags: { output: ['json', 'html'] },
      },
    )

    const { lhr, report } = runnerResult
    const perf = Math.round((lhr.categories.performance.score ?? 0) * 100)
    const acc = Math.round((lhr.categories.accessibility.score ?? 0) * 100)

    const prefix = `${stateName}-${theme}-run${runIndex}`
    fs.writeFileSync(path.join(outDir, `${prefix}.json`), Array.isArray(report) ? report[0] : JSON.stringify(lhr, null, 2))
    if (Array.isArray(report) && report[1]) {
      fs.writeFileSync(path.join(outDir, `${prefix}.html`), report[1])
    }

    return { perf, acc, chromeVersion }
  })
}

async function runSnapshotAcc(stateName, baseUrl, theme) {
  return withFreshBrowser(async (page, chromeVersion) => {
    await page.setRequestInterception(true)
    page.on('request', (req) => {
      const reqUrl = req.url()
      if (reqUrl.startsWith(baseUrl) || reqUrl.startsWith('data:') || reqUrl === 'about:blank') {
        req.continue()
      } else {
        req.abort()
      }
    })
    await page.setViewport({ width: 375, height: 812, isMobile: true, hasTouch: true })
    await page.evaluateOnNewDocument((selectedTheme) => {
      localStorage.setItem('poc-dashboard:theme', selectedTheme)
    }, theme)

    if (stateName === 'login') {
      await page.goto(baseUrl, { waitUntil: 'networkidle0' })
      await page.waitForSelector('#login-email')
    } else {
      await performLogin(page, baseUrl)
      if (stateName === 'details') {
        await page.evaluate(() => {
          const el = document.querySelector('.item-title')
          if (el instanceof HTMLElement) {
            el.scrollIntoView({ block: 'center' })
            el.click()
          }
        })
        await page.waitForSelector('dialog.detail-dialog[open]')
      } else if (stateName === 'mobile-menu') {
        const toggle = await page.$('[data-testid="mobile-menu-toggle"]')
        if (!toggle) {
          if (enforce) throw new Error('mobile-menu toggle not found')
          return { acc: null, chromeVersion, skipped: true }
        }
        await toggle.click()
        await page.waitForSelector('#mobile-nav-drawer')
      } else if (stateName === 'collection') {
        await page.evaluate(() => document.getElementById('collect')?.scrollIntoView({ behavior: 'instant' }))
      } else if (stateName === 'history') {
        await page.evaluate(() => document.getElementById('activity')?.scrollIntoView({ behavior: 'instant' }))
      }
    }

    const runnerResult = await snapshot(page, {
      config: SNAPSHOT_CONFIG,
      flags: { output: ['json', 'html'] },
    })
    const { lhr, report } = runnerResult
    const acc = Math.round((lhr.categories.accessibility.score ?? 0) * 100)
    const prefix = `snapshot-${stateName}-${theme}`
    fs.writeFileSync(path.join(outDir, `${prefix}.json`), Array.isArray(report) ? report[0] : JSON.stringify(lhr, null, 2))
    if (Array.isArray(report) && report[1]) {
      fs.writeFileSync(path.join(outDir, `${prefix}.html`), report[1])
    }
    return { acc, chromeVersion }
  })
}

async function main() {
  const distDir = path.join(frontendDir, 'dist')
  if (!fs.existsSync(distDir)) {
    throw new Error('dist/ not found. Run npm run build first.')
  }

  const server = await startMockServer(distDir)
  try {
    const states = ['login', 'library', 'collection', 'history', 'details', 'mobile-menu']
    const performanceSummary = {}
    let chromeVersion = ''

    for (const state of states) {
      performanceSummary[state] = {}
      for (const theme of ['light', 'dark']) {
        const runs = []
        for (let i = 1; i <= 3; i++) {
          const res = await runColdNavigation(state, server.baseUrl, i, theme)
          chromeVersion = res.chromeVersion
          runs.push({ run: i, performance: res.perf, accessibility: res.acc })
        }
        const perfScores = runs.map((r) => r.performance)
        const accScores = runs.map((r) => r.accessibility)
        performanceSummary[state][theme] = {
          runs,
          performanceScores: perfScores,
          performanceMedian: median(perfScores),
          accessibilityScores: accScores,
          accessibilityMedian: median(accScores),
        }
      }
    }

    const accessibilitySnapshots = {}
    for (const state of states) {
      accessibilitySnapshots[state] = {}
      for (const theme of ['light', 'dark']) {
        const res = await runSnapshotAcc(state, server.baseUrl, theme)
        accessibilitySnapshots[state][theme] = res.acc
      }
    }

    const summary = {
      phase,
      date: new Date().toISOString(),
      environment: {
        lighthouseVersion: lhPkg.version,
        chromeVersion,
        nodeVersion: process.version,
        platform: `${os.platform()} ${os.release()} (${os.arch()})`,
        preset: 'mobile (412x823, dpr 1.75, 4x CPU, simulated slow 4G)',
      },
      performanceSummary,
      accessibilitySnapshots,
    }

    fs.writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(summary, null, 2))
    console.log(JSON.stringify(summary, null, 2))

    if (enforce) {
      const failures = []
      for (const [state, themes] of Object.entries(performanceSummary)) {
        for (const [theme, data] of Object.entries(themes)) {
          if (data.performanceMedian <= 95) {
            failures.push(`${state} (${theme}) performance median ${data.performanceMedian} <= 95`)
          }
          if (data.accessibilityMedian <= 95) {
            failures.push(`${state} (${theme}) accessibility median ${data.accessibilityMedian} <= 95`)
          }
        }
      }
      for (const [state, themes] of Object.entries(accessibilitySnapshots)) {
        for (const [theme, score] of Object.entries(themes)) {
          if (score == null || score <= 95) {
            failures.push(`${state} (${theme}) accessibility snapshot ${score} <= 95`)
          }
        }
      }
      if (failures.length) {
        console.error('Lighthouse gate failed:\n - ' + failures.join('\n - '))
        process.exitCode = 1
      }
    }
  } finally {
    await server.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
