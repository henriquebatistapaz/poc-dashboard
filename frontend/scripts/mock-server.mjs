import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
}

export const SYNTHETIC_ITEMS = [
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

export const SYNTHETIC_RUNS = [
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

export async function startMockServer(distDir) {
  const server = http.createServer((req, res) => {
    const reqUrl = new URL(req.url || '/', 'http://127.0.0.1')
    const pathname = reqUrl.pathname

    if (pathname.startsWith('/v1/')) {
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.setHeader('Cache-Control', 'no-store')
      if (pathname === '/v1/auth/login' && req.method === 'POST') {
        res.end(JSON.stringify({ accessToken: 'synthetic-audit-token', tokenType: 'Bearer', expiresIn: 900 }))
        return
      }
      if (pathname === '/v1/collections' && req.method === 'GET') {
        res.end(JSON.stringify(SYNTHETIC_RUNS))
        return
      }
      if (pathname === '/v1/items' && req.method === 'GET') {
        res.end(JSON.stringify({ items: SYNTHETIC_ITEMS, total: SYNTHETIC_ITEMS.length, page: 0, size: 10 }))
        return
      }
      if (pathname.startsWith('/v1/items/') && req.method === 'GET') {
        const id = Number(pathname.split('/').pop())
        const item = SYNTHETIC_ITEMS.find((entry) => entry.id === id)
        if (!item) {
          res.statusCode = 404
          res.end(JSON.stringify({ error: 'Not found' }))
          return
        }
        res.end(JSON.stringify(item))
        return
      }
      res.statusCode = 404
      res.end(JSON.stringify({ error: 'Unhandled mock API endpoint' }))
      return
    }

    const safeRel = path.normalize(pathname).replace(/^(\.\.[/\\])+/, '')
    let filePath = path.join(distDir, safeRel === '/' ? 'index.html' : safeRel)
    if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
      filePath = path.join(distDir, 'index.html')
    }
    const ext = path.extname(filePath)
    res.setHeader('Content-Type', MIME_TYPES[ext] || 'application/octet-stream')
    fs.createReadStream(filePath).pipe(res)
  })

  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const address = server.address()
  return {
    baseUrl: `http://127.0.0.1:${address.port}`,
    close: () => new Promise((resolve) => server.close(resolve)),
  }
}
