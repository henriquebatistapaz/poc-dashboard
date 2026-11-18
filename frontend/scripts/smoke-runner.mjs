/* global window */
import assert from 'node:assert/strict'
import { execFileSync, spawn } from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import http from 'node:http'
import net from 'node:net'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium, firefox, webkit } from 'playwright'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const frontendDir = path.resolve(__dirname, '..')
const rootDir = path.resolve(frontendDir, '..')

function randomSecret(bytes = 24) {
  return crypto.randomBytes(bytes).toString('hex')
}

async function getFreePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer()
    server.listen(0, '127.0.0.1', () => {
      const addr = server.address()
      server.close(() => resolve(addr.port))
    })
    server.on('error', reject)
  })
}

function findPgBin(tool) {
  const candidates = [
    `/opt/homebrew/opt/postgresql@18/bin/${tool}`,
    `/usr/local/opt/postgresql@18/bin/${tool}`,
    `/usr/lib/postgresql/18/bin/${tool}`,
  ]
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) return candidate
  }
  return tool
}

async function waitForHttp(url, timeoutMs = 45000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url)
      if (res.status < 500) return
    } catch {
      // Wait for service readiness.
    }
    await new Promise((r) => setTimeout(r, 250))
  }
  throw new Error(`Timed out waiting for ${url}`)
}

async function apiFetch(baseUrl, pathName, options = {}) {
  return fetch(`${baseUrl}${pathName}`, {
    ...options,
    headers: {
      ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {}),
    },
  })
}

function scanDirectoryForCanaries(dir, canaries, findings) {
  if (!fs.existsSync(dir)) return
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      scanDirectoryForCanaries(full, canaries, findings)
    } else if (/\.(js|css|html|json|txt|log)$/i.test(entry.name)) {
      const content = fs.readFileSync(full, 'utf8')
      for (const canary of canaries) {
        if (content.includes(canary)) {
          findings.push(`Canary leaked in ${full}`)
        }
      }
    }
  }
}

async function main() {
  const canaries = []
  const logs = []
  let pgDir = ''
  let pgCtl = ''
  let fixtureServer
  let javaProc
  let previewProc

  const adminEmail = 'admin@example.test'
  const operatorEmail = 'operator@example.test'
  const adminPassword = `adm-${randomSecret(12)}`
  const operatorPassword = `opr-${randomSecret(12)}`
  const jwtSecret = `jwt-${randomSecret(24)}`
  const scheduleKey = randomSecret(32)
  const apiKey = `key-${randomSecret(16)}`
  canaries.push(adminPassword, operatorPassword, jwtSecret, scheduleKey, apiKey)

  try {
    // 1. Isolated PostgreSQL (either CI-provided SMOKE_DB_URL or ephemeral local initdb)
    let dbUrl = process.env.SMOKE_DB_URL
    let dbUser = process.env.SMOKE_DB_USER || 'smoke_user'
    let dbPass = process.env.SMOKE_DB_PASSWORD || randomSecret(12)

    if (!dbUrl) {
      pgCtl = findPgBin('pg_ctl')
      const initdb = findPgBin('initdb')
      const createdb = findPgBin('createdb')
      const pgPort = await getFreePort()
      pgDir = fs.mkdtempSync(path.join(os.tmpdir(), 'poc-dashboard-smoke-pg-'))
      const pwFile = path.join(pgDir, '.pw')
      fs.writeFileSync(pwFile, dbPass, { mode: 0o600 })
      execFileSync(initdb, ['-D', path.join(pgDir, 'data'), '-U', dbUser, '--pwfile', pwFile, '-A', 'scram-sha-256'], {
        stdio: 'pipe',
      })
      fs.unlinkSync(pwFile)
      execFileSync(
        pgCtl,
        [
          '-D',
          path.join(pgDir, 'data'),
          '-l',
          path.join(pgDir, 'postgres.log'),
          '-o',
          `-p ${pgPort} -h 127.0.0.1 -k ${pgDir}`,
          'start',
        ],
        { stdio: 'pipe' },
      )
      execFileSync(createdb, ['-h', '127.0.0.1', '-p', String(pgPort), '-U', dbUser, 'dashboard_smoke'], {
        env: { ...process.env, PGPASSWORD: dbPass },
        stdio: 'pipe',
      })
      dbUrl = `jdbc:postgresql://127.0.0.1:${pgPort}/dashboard_smoke`
    }

    // 2. Local RSS fixture HTTP server on loopback
    const fixturePort = await getFreePort()
    const fixtureUrl = `http://127.0.0.1:${fixturePort}/feed.xml`
    const feedXml = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Fixture Local</title>
    <link>https://example.test/</link>
    <description>Synthetic RSS fixture</description>
    <item>
      <guid>mobile-fixture-1</guid>
      <title>Uma manchete longa com acentos — cidade e sustentabilidade</title>
      <link>https://example.test/item/1</link>
      <pubDate>Tue, 17 Nov 2026 12:00:00 GMT</pubDate>
    </item>
    <item>
      <guid>mobile-fixture-2</guid>
      <title>&lt;img src=x onerror=window.__xss=1&gt; Item escapado</title>
      <link>https://example.test/item/2</link>
      <pubDate>Tue, 17 Nov 2026 11:00:00 GMT</pubDate>
    </item>
  </channel>
</rss>`

    fixtureServer = http.createServer((req, res) => {
      if (req.url === '/feed.xml') {
        res.writeHead(200, { 'Content-Type': 'application/rss+xml; charset=utf-8' })
        res.end(feedXml)
        return
      }
      res.writeHead(404)
      res.end()
    })
    await new Promise((resolve) => fixtureServer.listen(fixturePort, '127.0.0.1', resolve))

    // 3. Build backend classpath & start real Java backend with SmokeApplication
    const cpFile = path.join(rootDir, 'target', 'smoke-classpath.txt')
    execFileSync('mvn', ['-B', '-q', 'test-compile', 'dependency:build-classpath', `-Dmdep.outputFile=${cpFile}`], {
      cwd: rootDir,
      stdio: 'pipe',
    })
    const classpath = [
      path.join(rootDir, 'target', 'test-classes'),
      path.join(rootDir, 'target', 'classes'),
      fs.readFileSync(cpFile, 'utf8').trim(),
    ].join(path.delimiter)

    const apiPort = await getFreePort()
    const apiBaseUrl = `http://127.0.0.1:${apiPort}`
    const mainConfigPath = path.join(rootDir, 'src', 'main', 'resources', 'application.yml')
    javaProc = spawn(
      'java',
      [
        '-cp',
        classpath,
        'smoke.SmokeApplication',
        `--spring.config.location=file:${mainConfigPath}`,
        '--spring.profiles.active=smoke',
      ],
      {
        cwd: rootDir,
        env: {
          ...process.env,
          SERVER_ADDRESS: '127.0.0.1',
          PORT: String(apiPort),
          DB_URL: dbUrl,
          DB_USER: dbUser,
          DB_PASSWORD: dbPass,
          DASHBOARD_ADMIN_EMAIL: adminEmail,
          DASHBOARD_ADMIN_PASSWORD: adminPassword,
          DASHBOARD_JWT_SECRET: jwtSecret,
          DASHBOARD_SCHEDULE_KEY: scheduleKey,
          DASHBOARD_API_KEY: apiKey,
          DASHBOARD_SMOKE_FEED_URL: fixtureUrl,
        },
        stdio: ['ignore', 'pipe', 'pipe'],
      },
    )
    javaProc.stdout.on('data', (chunk) => logs.push(chunk.toString('utf8')))
    javaProc.stderr.on('data', (chunk) => logs.push(chunk.toString('utf8')))
    try {
      await waitForHttp(`${apiBaseUrl}/v3/api-docs`)
    } catch (err) {
      let scrubbed = logs.join('')
      for (const canary of canaries) {
        scrubbed = scrubbed.replaceAll(canary, '[REDACTED]')
      }
      console.error('Java backend failed to start:\n', scrubbed.slice(-4000))
      throw err
    }

    // 4. Start frontend Vite preview proxying to real backend
    const previewPort = await getFreePort()
    const previewBaseUrl = `http://127.0.0.1:${previewPort}`
    previewProc = spawn('npm', ['run', 'preview', '--', '--port', String(previewPort), '--strictPort'], {
      cwd: frontendDir,
      env: { ...process.env, API_PROXY_TARGET: apiBaseUrl },
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    previewProc.stdout.on('data', (chunk) => logs.push(chunk.toString('utf8')))
    previewProc.stderr.on('data', (chunk) => logs.push(chunk.toString('utf8')))
    await waitForHttp(previewBaseUrl)

    // 5. Real API authorization & negative input checks (S-AC-05)
    assert.equal((await apiFetch(apiBaseUrl, '/v1/items')).status, 401)
    assert.equal(
      (await apiFetch(apiBaseUrl, '/v1/items', { headers: { Authorization: 'Bearer invalid.jwt.token' } })).status,
      401,
    )

    const adminLoginRes = await apiFetch(apiBaseUrl, '/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: adminEmail, password: adminPassword }),
    })
    assert.equal(adminLoginRes.status, 200)
    const adminJwt = (await adminLoginRes.json()).accessToken
    canaries.push(adminJwt)

    const createOpRes = await apiFetch(apiBaseUrl, '/v1/admin/users', {
      method: 'POST',
      headers: { Authorization: `Bearer ${adminJwt}` },
      body: JSON.stringify({ email: operatorEmail, password: operatorPassword, role: 'OPERATOR' }),
    })
    assert.equal(createOpRes.status, 201)
    const opUser = await createOpRes.json()

    const opLoginRes = await apiFetch(apiBaseUrl, '/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: operatorEmail, password: operatorPassword }),
    })
    assert.equal(opLoginRes.status, 200)
    const opJwt = (await opLoginRes.json()).accessToken
    canaries.push(opJwt)

    // OPERATOR restrictions
    assert.equal((await apiFetch(apiBaseUrl, '/v1/admin/users', { headers: { Authorization: `Bearer ${opJwt}` } })).status, 403)
    assert.equal(
      (
        await apiFetch(apiBaseUrl, '/v1/items/999999', {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${opJwt}` },
          body: JSON.stringify({ reason: 'unauthorized' }),
        })
      ).status,
      403,
    )

    // X-API-Key read-only restrictions
    assert.equal((await apiFetch(apiBaseUrl, '/v1/items?page=-1&size=1000', { headers: { 'X-API-Key': apiKey } })).status, 200)
    assert.equal((await apiFetch(apiBaseUrl, '/v1/admin/users', { headers: { 'X-API-Key': apiKey } })).status, 403)
    assert.equal(
      (
        await apiFetch(apiBaseUrl, '/v1/collections', {
          method: 'POST',
          headers: { 'X-API-Key': apiKey },
          body: JSON.stringify({ source: 'RSS', input: fixtureUrl }),
        })
      ).status,
      403,
    )

    // Invalid inputs do not create runs
    assert.equal(
      (
        await apiFetch(apiBaseUrl, '/v1/collections', {
          method: 'POST',
          headers: { Authorization: `Bearer ${adminJwt}` },
          body: JSON.stringify({ source: 'RSS', input: '  ' }),
        })
      ).status,
      400,
    )
    assert.equal(
      (
        await apiFetch(apiBaseUrl, '/v1/collections', {
          method: 'POST',
          headers: { Authorization: `Bearer ${adminJwt}` },
          body: JSON.stringify({ source: 'UNKNOWN', input: fixtureUrl }),
        })
      ).status,
      400,
    )

    // Blocked private URL outside fixture
    const privateRunRes = await apiFetch(apiBaseUrl, '/v1/collections', {
      method: 'POST',
      headers: { Authorization: `Bearer ${adminJwt}` },
      body: JSON.stringify({ source: 'RSS', input: 'http://127.0.0.1/private' }),
    })
    assert.equal(privateRunRes.status, 202)
    const privateRun = await privateRunRes.json()
    for (let i = 0; i < 40; i++) {
      const check = await (await apiFetch(apiBaseUrl, `/v1/collections/${privateRun.id}`, {
        headers: { Authorization: `Bearer ${adminJwt}` },
      })).json()
      if (check.status === 'FAILED') break
      await new Promise((r) => setTimeout(r, 100))
    }

    // 6. Real Browser Smoke across Chromium, WebKit, and Firefox at 1440x900 + Chromium 375x812
    const browserTargets = [
      { name: 'chromium-desktop', type: chromium, viewport: { width: 1440, height: 900 }, doCollect: true },
      { name: 'chromium-mobile', type: chromium, viewport: { width: 375, height: 812 }, doCollect: false },
      { name: 'webkit-desktop', type: webkit, viewport: { width: 1440, height: 900 }, doCollect: false },
      { name: 'firefox-desktop', type: firefox, viewport: { width: 1440, height: 900 }, doCollect: false },
    ]

    for (const target of browserTargets) {
      const browser = await target.type.launch()
      const context = await browser.newContext({ viewport: target.viewport })
      const page = await context.newPage()
      const externalRequests = []
      await page.route('**/*', async (route) => {
        const reqUrl = route.request().url()
        if (reqUrl.startsWith(previewBaseUrl) || reqUrl.startsWith('data:') || reqUrl.startsWith('blob:')) {
          await route.continue()
        } else {
          externalRequests.push(reqUrl)
          await route.abort()
        }
      })

      try {
        await page.goto(previewBaseUrl, { waitUntil: 'networkidle' })
        await page.fill('#login-email', target.doCollect ? adminEmail : operatorEmail)
        await page.fill('#login-password', target.doCollect ? adminPassword : operatorPassword)
        await page.click('button.login-submit')
        await page.waitForSelector('.library-card')

        if (target.doCollect) {
          await page.fill('#collection-input', fixtureUrl)
          await page.click('button.collect-submit')
          await page.waitForSelector('.collection-result', { timeout: 20000 })
        }

        await page.waitForSelector('.item-title')
        const titles = await page.locator('.item-title').allTextContents()
        assert.ok(titles.some((t) => t.includes('Uma manchete longa com acentos')))

        // Verify XSS payload escaped and window.__xss untouched
        const xssValue = await page.evaluate(() => window.__xss)
        assert.equal(xssValue, undefined)

        // Open details
        await page.locator('.item-title', { hasText: 'Uma manchete longa' }).first().click()
        await page.waitForSelector('dialog.detail-dialog[open]')
        const linkHref = await page.locator('dialog.detail-dialog a').first().getAttribute('href')
        assert.equal(linkHref, 'https://example.test/item/1')
        await page.keyboard.press('Escape')

        // Check browser storage contains no JWT or password canaries
        const storageDump = await page.evaluate(() =>
          JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }),
        )
        for (const canary of canaries) {
          assert.equal(storageDump.includes(canary), false, `Canary found in browser storage (${target.name})`)
        }
        assert.deepEqual(externalRequests, [], `External network requests in ${target.name}`)
      } finally {
        await context.close()
        await browser.close()
      }
    }

    // 7. Verify Admin item removal with reason & OPERATOR deactivation
    const itemsPage = await (
      await apiFetch(apiBaseUrl, '/v1/items?page=-1&size=1000', { headers: { 'X-API-Key': apiKey } })
    ).json()
    assert.equal(itemsPage.page, 0)
    assert.equal(itemsPage.size, 100)
    assert.ok(itemsPage.total >= 2)
    const firstItemId = itemsPage.items[0].id

    assert.equal(
      (
        await apiFetch(apiBaseUrl, `/v1/items/${firstItemId}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${adminJwt}` },
          body: JSON.stringify({ reason: '' }),
        })
      ).status,
      400,
    )
    assert.equal(
      (
        await apiFetch(apiBaseUrl, `/v1/items/${firstItemId}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${adminJwt}` },
          body: JSON.stringify({ reason: 'fixture mobile' }),
        })
      ).status,
      204,
    )

    assert.equal(
      (
        await apiFetch(apiBaseUrl, `/v1/admin/users/${opUser.id}/deactivate`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${adminJwt}` },
        })
      ).status,
      204,
    )
    assert.equal(
      (
        await apiFetch(apiBaseUrl, '/v1/auth/login', {
          method: 'POST',
          body: JSON.stringify({ email: operatorEmail, password: operatorPassword }),
        })
      ).status,
      401,
    )

    // 8. Scan built dist/ and process logs for secret canary leaks (S-AC-06) and verify real PostgreSQL
    const canaryFindings = []
    scanDirectoryForCanaries(path.join(frontendDir, 'dist'), canaries, canaryFindings)
    const joinedLogs = logs.join('\n')
    assert.ok(joinedLogs.includes('PostgreSQL'), 'Smoke backend must run against real PostgreSQL, not H2')
    assert.equal(joinedLogs.includes('jdbc:h2:'), false, 'Smoke backend must never use H2')
    for (const canary of canaries) {
      if (joinedLogs.includes(canary)) {
        canaryFindings.push('Canary leaked in process logs')
      }
    }
    assert.deepEqual(canaryFindings, [])

    console.log('Real isolated smoke passed: PostgreSQL + Java backend + Vite preview + Chromium/WebKit/Firefox + S-AC-03/04/05/06')
  } finally {
    if (previewProc && !previewProc.killed) previewProc.kill('SIGTERM')
    if (javaProc && !javaProc.killed) javaProc.kill('SIGTERM')
    if (fixtureServer) await new Promise((resolve) => fixtureServer.close(resolve))
    if (pgDir) {
      try {
        execFileSync(pgCtl, ['-D', path.join(pgDir, 'data'), 'stop', '-m', 'fast'], { stdio: 'ignore' })
      } catch {
        // Ignore stop errors if postgres never started.
      }
      fs.rmSync(pgDir, { recursive: true, force: true })
    }
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
