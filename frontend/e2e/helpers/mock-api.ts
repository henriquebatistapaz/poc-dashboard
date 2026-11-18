import { expect, type Page } from '@playwright/test'

export interface MockApiOptions {
  initialHistory?: Array<Record<string, unknown>>
  items?: Array<Record<string, unknown>>
  failListOnce?: boolean
  failHistoryOnce?: boolean
  failDetailOnce?: boolean
  delayMs?: number
}

export const DEFAULT_ITEMS = [
  {
    id: 1,
    source: 'rss',
    externalId: 'mobile-fixture-1',
    title: 'Uma manchete longa com acentos — cidade e sustentabilidade urbana',
    link: 'https://example.test/item/1',
    publishedAt: '2026-11-17T10:00:00Z',
    collectedAt: '2026-11-17T12:00:00Z',
  },
  {
    id: 2,
    source: 'wordpress',
    externalId: 'mobile-fixture-2',
    title: 'Energia limpa e infraestrutura aberta para cidades conectadas',
    link: 'https://example.test/item/2',
    publishedAt: '2026-11-16T09:00:00Z',
    collectedAt: '2026-11-16T11:30:00Z',
  },
  {
    id: 3,
    source: 'google-news',
    externalId: 'mobile-fixture-3',
    title: 'Relatorio tecnico sobre mobilidade e acessibilidade digital',
    link: 'https://example.test/item/3',
    publishedAt: '2026-11-15T14:00:00Z',
    collectedAt: '2026-11-15T15:00:00Z',
  },
]

export const DEFAULT_HISTORY = [
  {
    id: 12,
    source: 'RSS',
    status: 'SUCCEEDED',
    createdAt: '2026-11-17T12:00:00Z',
    fetched: 3,
    inserted: 2,
    duplicates: 1,
    errorMessage: null,
  },
  {
    id: 11,
    source: 'WORDPRESS',
    status: 'FAILED',
    createdAt: '2026-11-16T11:30:00Z',
    fetched: null,
    inserted: null,
    duplicates: null,
    errorMessage: 'Source unavailable; check approval and runtime configuration',
  },
]

export async function installNetworkIsolation(page: Page): Promise<string[]> {
  const externalAttempts: string[] = []
  await page.route('**/*', async (route) => {
    const reqUrl = route.request().url()
    if (reqUrl.startsWith('http://127.0.0.1:') || reqUrl.startsWith('http://localhost:') || reqUrl.startsWith('data:') || reqUrl.startsWith('blob:')) {
      await route.fallback()
      return
    }
    externalAttempts.push(reqUrl)
    await route.abort('blockedbyclient')
  })
  return externalAttempts
}

export async function installMockApi(page: Page, options: MockApiOptions = {}) {
  const externalAttempts = await installNetworkIsolation(page)
  const unhandledApiAttempts: string[] = []
  const items = [...(options.items ?? DEFAULT_ITEMS)]
  const history = [...(options.initialHistory ?? [])]
  let collected = items.length > 0 && !options.items ? true : false
  let runs = history.length
  let listCalls = 0
  let historyCalls = 0
  let detailCalls = 0

  await page.route('**/v1/**', async (route) => {
    if (options.delayMs) {
      await new Promise((r) => setTimeout(r, options.delayMs))
    }
    const request = route.request()
    const url = new URL(request.url())
    const method = request.method()

    if (url.pathname === '/v1/auth/login' && method === 'POST') {
      const payload = request.postDataJSON() as Record<string, unknown>
      if (payload['password'] === 'wrong') {
        return route.fulfill({ status: 401, json: {} })
      }
      return route.fulfill({
        json: { accessToken: 'memory-only-test-token', tokenType: 'Bearer', expiresIn: 900 },
      })
    }

    if (url.pathname === '/v1/collections' && method === 'GET') {
      historyCalls++
      if (options.failHistoryOnce && historyCalls === 1) {
        return route.fulfill({ status: 500, json: {} })
      }
      return route.fulfill({ json: history.slice().reverse() })
    }

    if (url.pathname === '/v1/collections' && method === 'POST') {
      runs++
      const run = {
        id: runs,
        source: 'RSS',
        status: 'SUCCEEDED',
        createdAt: '2026-11-17T12:00:00Z',
        fetched: 1,
        inserted: collected ? 0 : 1,
        duplicates: collected ? 1 : 0,
        errorMessage: null,
      }
      history.push(run)
      return route.fulfill({
        status: 202,
        json: { id: runs, source: 'RSS', status: 'PENDING', fetched: null, inserted: null, duplicates: null, errorMessage: null },
      })
    }

    if (url.pathname.startsWith('/v1/collections/') && method === 'GET') {
      const id = Number(url.pathname.split('/').pop())
      const found = history.find((entry) => entry.id === id)
      if (!found) {
        return route.fulfill({ status: 404, json: {} })
      }
      Object.assign(found, { status: 'SUCCEEDED', fetched: 1, inserted: collected ? 0 : 1, duplicates: collected ? 1 : 0 })
      collected = true
      return route.fulfill({ json: found })
    }

    if (url.pathname === '/v1/items' && method === 'GET') {
      listCalls++
      if (options.failListOnce && listCalls === 1) {
        return route.fulfill({ status: 500, json: {} })
      }
      const pageNum = Number(url.searchParams.get('page') ?? '0')
      const sizeNum = Number(url.searchParams.get('size') ?? '10')
      const start = pageNum * sizeNum
      return route.fulfill({
        json: {
          items: items.slice(start, start + sizeNum),
          total: items.length,
          page: pageNum,
          size: sizeNum,
        },
      })
    }

    if (url.pathname.startsWith('/v1/items/') && method === 'GET') {
      detailCalls++
      if (options.failDetailOnce && detailCalls === 1) {
        return route.fulfill({ status: 500, json: {} })
      }
      const id = Number(url.pathname.split('/').pop())
      const found = items.find((entry) => entry.id === id)
      if (!found) {
        return route.fulfill({ status: 404, json: {} })
      }
      return route.fulfill({ json: found })
    }

    unhandledApiAttempts.push(`${method} ${url.pathname}`)
    return route.fulfill({ status: 500, json: { error: 'Unhandled mock API endpoint' } })
  })

  return {
    assertIsolated: () => {
      expect(externalAttempts, 'External network requests must be blocked').toEqual([])
      expect(unhandledApiAttempts, 'Unhandled API requests must not occur').toEqual([])
    },
  }
}

export async function loginInUi(page: Page, email = 'admin@example.test', secret = 'synthetic-secret') {
  await page.goto('/')
  await page.locator('#login-email').fill(email)
  await page.locator('#login-password').fill(secret)
  await page.locator('button.login-submit').click()
  await expect(page.locator('.library-card')).toBeVisible()
}
