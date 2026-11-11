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
    vi.unstubAllGlobals()
  })

  it('maps a failed run to a fixed 502 without the server message', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 4, status: 'FAILED', errorMessage: 'https://secret.example/feed' }), { status: 200 })))
    await expect(waitForRun(4, 'token', undefined, 1)).rejects.toMatchObject({ status: 502 })
    vi.unstubAllGlobals()
  })

  it('stops polling when the user cancels a run', async () => {
    const controller = new AbortController()
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ id: 8, status: 'RUNNING' }), { status: 200 })))
    const polling = waitForRun(8, 'token', controller.signal, 10000)
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(1))
    controller.abort()
    await expect(polling).rejects.toMatchObject({ name: 'AbortError' })
  })
})
