import { afterEach, describe, expect, it, vi } from 'vitest'

describe('main entry', () => {
  afterEach(() => {
    document.body.innerHTML = ''
    vi.resetModules()
  })

  it('creates the Vue app without mounting when #app is absent and mounts when present', async () => {
    vi.resetModules()
    const modWithoutTarget = await import('./main')
    const app = modWithoutTarget.createDashboardApp()
    expect(app).toBeDefined()

    const root = document.createElement('div')
    root.id = 'app'
    document.body.appendChild(root)
    vi.resetModules()
    await import('./main')
    expect(root.innerHTML).toContain('login-shell')
  })
})
