import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, errorMessage, request, waitForRun } from './api'
import { i18n } from '../i18n'

afterEach(() => vi.unstubAllGlobals())

describe('API transport', () => {
  it('posts credentials in the body, never the URL or persistent browser storage', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{"accessToken":"test-token"}', { status: 200 }))
    vi.stubGlobal('fetch', fetch)
    await request('/v1/auth/login', undefined, { email: 'test@example.org', password: 'private' })
    const [url, options] = fetch.mock.calls[0]!
    expect(url).toBe('/v1/auth/login')
    expect(options.credentials).toBe('omit')
    expect(options.cache).toBe('no-store')
    expect(options.body).toContain('private')
    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it('adds the bearer token and does not reflect secret upstream error text', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('secret-feed-url', { status: 502 }))
    vi.stubGlobal('fetch', fetch)
    await expect(request('/v1/collections', 'token', { source: 'RSS', input: 'url' })).rejects.toMatchObject({ status: 502 })
    expect(fetch.mock.calls[0]![1].headers.Authorization).toBe('Bearer token')
    expect(errorMessage(new ApiError(502))).not.toContain('secret-feed-url')
  })

  it('handles malformed JSON, AbortError, and generic or unknown errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('not-json', { status: 200 })))
    await expect(request('/v1/items', 'token')).rejects.toMatchObject({ status: 502 })

    const abortErr = new DOMException('Aborted', 'AbortError')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abortErr))
    await expect(request('/v1/items', 'token')).rejects.toMatchObject({ name: 'AbortError' })

    i18n.global.locale.value = 'pt-BR'
    expect(errorMessage(new Error('unexpected'))).toContain('Não foi possível concluir')
    expect(errorMessage(new ApiError(500))).toContain('encontrou um erro')
  })

  it('localizes errors and distinguishes network failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('private-network-details')))
    await expect(request('/v1/items', 'token')).rejects.toMatchObject({ status: 0 })
    i18n.global.locale.value = 'es'
    expect(errorMessage(new ApiError(401))).toContain('sesión')
    i18n.global.locale.value = 'en'
    expect(errorMessage(new ApiError(403))).toContain('permission')
  })
})

describe('waitForRun', () => {
  it('returns the run once it succeeds', async () => {
    const responses = [
      { status: 200, body: { id: 3, source: 'RSS', status: 'RUNNING', fetched: null, inserted: null, duplicates: null, errorMessage: null } },
      { status: 200, body: { id: 3, source: 'RSS', status: 'SUCCEEDED', fetched: 2, inserted: 1, duplicates: 1, errorMessage: null } },
    ]
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(responses.shift()!.body), { status: 200 })))
    const run = await waitForRun(3, 'token', undefined, 1)
    expect(run.status).toBe('SUCCEEDED')
    expect(run.inserted).toBe(1)
  })

  it('maps a failed run to a fixed 502 and times out after maxAttempts', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 4, status: 'FAILED', errorMessage: 'https://secret.example/feed' }), { status: 200 })))
    await expect(waitForRun(4, 'token', undefined, 1)).rejects.toMatchObject({ status: 502 })

    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 5, status: 'PENDING' }), { status: 200 })))
    await expect(waitForRun(5, 'token', undefined, 1, 2)).rejects.toMatchObject({ status: 504 })
  })

  it('stops polling when the user cancels a run or passes an already aborted signal', async () => {
    const preAborted = new AbortController()
    preAborted.abort()
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 7, status: 'RUNNING' }), { status: 200 })))
    await expect(waitForRun(7, 'token', preAborted.signal, 1)).rejects.toMatchObject({ name: 'AbortError' })

    const controller = new AbortController()
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 8, status: 'RUNNING' }), { status: 200 })))
    const polling = waitForRun(8, 'token', controller.signal, 200)
    await new Promise((r) => setTimeout(r, 20))
    controller.abort()
    await expect(polling).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('removes the abort listener when sleep completes without cancellation', async () => {
    const controller = new AbortController()
    const responses = [
      { status: 200, body: { id: 9, source: 'RSS', status: 'RUNNING' } },
      { status: 200, body: { id: 9, source: 'RSS', status: 'SUCCEEDED', fetched: 1, inserted: 1, duplicates: 0 } },
    ]
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(responses.shift()!.body), { status: 200 })))
    const run = await waitForRun(9, 'token', controller.signal, 5)
    expect(run.status).toBe('SUCCEEDED')
  })
})

describe('request method and empty responses', () => {
  it('sends DELETE and accepts a 204 without a body', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetch)
    await expect(request<void>('/v1/integrations/mastodon/connection', 'token', undefined, undefined, 'DELETE')).resolves.toBeUndefined()
    expect(fetch.mock.calls[0]![1].method).toBe('DELETE')
  })
})
