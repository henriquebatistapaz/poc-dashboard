import { i18n } from '../i18n'
import type { CollectionRun } from '../types'
export class ApiError extends Error {
  constructor(public readonly status: number) { super(`Request failed (${status})`) }
}

// No credentials in URLs, browser storage or error messages. All API calls stay on the same origin.
export async function request<T>(path: string, token?: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'omit', cache: 'no-store', signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0)
  }
  if (!response.ok) throw new ApiError(response.status)
  try { return await response.json() as T }
  catch { throw new ApiError(502) }
}

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, ms)
    signal?.addEventListener('abort', () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }, { once: true })
  })
}

// Polls a background collection run until it finishes. A failed run is reported with a fixed status only.
export async function waitForRun(id: number, token: string, signal?: AbortSignal,
                                 intervalMs = 500, maxAttempts = 120): Promise<CollectionRun> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const run = await request<CollectionRun>(`/v1/collections/${id}`, token, undefined, signal)
    if (run.status === 'SUCCEEDED') return run
    if (run.status === 'FAILED') throw new ApiError(502)
    await sleep(intervalMs, signal)
  }
  throw new ApiError(504)
}

export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return i18n.global.t('errors.generic')
  const known = [0, 400, 401, 403, 404, 413, 422, 429, 502, 503, 504]
  return i18n.global.t(known.includes(error.status) ? `errors.${error.status}` : 'errors.server')
}
