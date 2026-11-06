import { expect, test, type Page } from '@playwright/test'

const item = { id: 1, source: 'rss', externalId: 'sample-1', title: 'Novas perspectivas para cidades mais verdes',
  link: 'https://example.org/cidades', publishedAt: '2026-11-06T12:00:00Z', collectedAt: '2026-11-06T13:00:00Z' }
async function mockApi(page: Page) {
  let collected = false
  let runs = 0
  await page.route('**/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    if (url.pathname === '/v1/auth/login') {
      if (request.postDataJSON().password === 'wrong') return route.fulfill({ status: 401, json: {} })
      return route.fulfill({ json: { accessToken: 'memory-only-test-token', tokenType: 'Bearer', expiresIn: 900 } })
    }
    if (url.pathname === '/v1/collections' && route.request().method() === 'POST') {
      runs++
      return route.fulfill({ status: 202, json: { id: runs, source: 'RSS', status: 'PENDING', fetched: null, inserted: null, duplicates: null, errorMessage: null } })
    }
    if (url.pathname === '/v1/collections/1') {
      collected = true
      return route.fulfill({ json: { id: 1, source: 'RSS', status: 'SUCCEEDED', fetched: 1, inserted: 1, duplicates: 0, errorMessage: null } })
    }
    if (url.pathname === '/v1/collections/2') {
      return route.fulfill({ json: { id: 2, source: 'RSS', status: 'SUCCEEDED', fetched: 1, inserted: 0, duplicates: 1, errorMessage: null } })
    }
    if (url.pathname === '/v1/items/1') return route.fulfill({ json: item })
    return route.fulfill({ json: { items: collected ? [item] : [], total: collected ? 1 : 0, page: 0, size: 10 } })
  })
}
async function login(page: Page) {
  await page.goto('/')
  await page.getByLabel('E-mail', { exact: true }).fill('admin@example.org')
  await page.getByLabel('Senha', { exact: true }).fill('test-password')
  await page.getByRole('button', { name: 'Entrar no workspace' }).click()
  await expect(page.getByRole('heading', { name: 'Seu radar de conteúdo.' })).toBeVisible()
}

test('RSS collection, deduplication, details and authenticated UI', async ({ page }, testInfo) => {
  await mockApi(page); await login(page)
  await expect(page.getByRole('heading', { name: 'Um bom começo está a um link de distância.' })).toBeVisible()
  await page.getByRole('button', { name: 'XKCD · Atom' }).click()
  await expect(page.locator('#collection-input')).toHaveValue('https://xkcd.com/atom.xml')
  const sent = page.waitForRequest(request => request.url().endsWith('/v1/collections'))
  await page.getByRole('button', { name: 'Coletar agora' }).click()
  expect((await sent).postDataJSON()).toEqual({ source: 'RSS', input: 'https://xkcd.com/atom.xml' })
  await expect(page.locator('.collection-result')).toContainText('1 encontrados · 1 novos · 0 duplicados')
  await expect(page.getByRole('button', { name: item.title })).toBeVisible()
  await page.getByRole('button', { name: item.title }).click()
  await expect(page.getByRole('dialog')).toContainText('sample-1')
  await expect(page.getByRole('link', { name: 'Abrir original' })).toHaveAttribute('href', item.link)
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('button', { name: 'XKCD · Atom' }).click()
  await page.getByRole('button', { name: 'Coletar agora' }).click()
  await expect(page.locator('.collection-result')).toContainText('Nenhuma duplicata foi criada')
  const storage = await page.evaluate(() => ({ local: { ...localStorage }, session: { ...sessionStorage } }))
  expect(Object.keys(storage.local)).toEqual(['coleta.locale'])
  expect(storage.session).toEqual({})
  await page.screenshot({ path: testInfo.outputPath('dashboard-pt.png'), fullPage: true })
  await page.getByRole('button', { name: 'Sair', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Entre no seu workspace' })).toBeVisible()
})

test('switches all three languages at login and in the workspace and remembers preference only', async ({ page }, testInfo) => {
  await mockApi(page); await page.goto('/')
  await page.getByLabel('Idioma', { exact: true }).selectOption('en')
  await expect(page.getByRole('heading', { name: 'Sign in to your workspace' })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Enter your email and password')
  await page.getByLabel('Language', { exact: true }).selectOption('es')
  await expect(page.getByRole('heading', { name: 'Entra en tu espacio' })).toBeVisible()
  await page.getByLabel('Correo electrónico', { exact: true }).fill('admin@example.org')
  await page.getByLabel('Contraseña', { exact: true }).fill('test-password')
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Tu radar de contenido.' })).toBeVisible()
  await expect(page.getByLabel('Link del feed')).toHaveCount(0)
  await page.getByLabel('Idioma', { exact: true }).selectOption('en')
  await expect(page.getByLabel('Feed URL', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Collect now' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Sign in to your workspace' })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
})

test('handles bad credentials, source errors and session expiry without exposing private input', async ({ page }, testInfo) => {
  await mockApi(page); await page.goto('/')
  await page.getByLabel('E-mail', { exact: true }).fill('admin@example.org')
  await page.getByLabel('Senha', { exact: true }).fill('wrong')
  await page.getByRole('button', { name: 'Entrar no workspace' }).click()
  await expect(page.getByRole('alert')).toContainText('E-mail ou senha incorretos')
  await expect(page.getByLabel('Senha', { exact: true })).toHaveValue('')
  await page.getByLabel('Senha', { exact: true }).fill('test-password')
  await page.getByRole('button', { name: 'Entrar no workspace' }).click()
  await page.route('**/v1/collections', route => route.fulfill({ status: 202, json: { id: 9, source: 'GOOGLE_ALERTS', status: 'PENDING', fetched: null, inserted: null, duplicates: null, errorMessage: null } }))
  await page.route('**/v1/collections/9', route => route.fulfill({ json: { id: 9, source: 'GOOGLE_ALERTS', status: 'FAILED', fetched: null, inserted: null, duplicates: null, errorMessage: 'Source unavailable; check approval and runtime configuration' } }))
  await page.locator('#collection-source').selectOption('GOOGLE_ALERTS')
  await page.locator('#collection-input').fill('https://www.google.com/alerts/feeds/demo/private')
  await page.getByRole('button', { name: 'Coletar agora' }).click()
  await expect(page.getByRole('alert')).toContainText('A fonte não respondeu')
  await expect(page.locator('body')).not.toContainText('do-not-display')
  await page.getByLabel('Idioma', { exact: true }).selectOption('es')
  await expect(page.getByRole('alert')).toContainText('La fuente no respondió')
  await page.route('**/v1/items?*', route => route.fulfill({ status: 401, json: {} }))
  await page.getByRole('button', { name: 'Actualizar', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Entra en tu espacio' })).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('Tu sesión ha caducado')
})

test('applies filters with UTC instants and rejects reversed date ranges', async ({ page }, testInfo) => {
  await mockApi(page); await login(page)
  await page.locator('#filter-query').fill('cidades')
  await page.locator('#filter-source').selectOption('rss')
  await page.locator('#filter-from').fill('2026-11-01T10:00')
  await page.locator('#filter-to').fill('2026-11-06T10:00')
  const filtered = page.waitForRequest(request => request.url().includes('/v1/items?') && request.url().includes('q=cidades'))
  await page.getByRole('button', { name: 'Filtrar', exact: true }).click()
  const query = new URL((await filtered).url()).searchParams
  expect(query.get('source')).toBe('rss')
  expect(query.get('from')).toMatch(/Z$/)
  await page.locator('#filter-from').fill('2026-11-09T10:00')
  await page.getByRole('button', { name: 'Filtrar', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('A data inicial deve ser anterior')
})

test('works on mobile without page overflow', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockApi(page); await login(page)
  await expect(page.getByLabel('Link do feed', { exact: true })).toBeVisible()
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)
  expect(overflow).toBe(false)
  await page.screenshot({ path: testInfo.outputPath('dashboard-mobile.png'), fullPage: true })
})
