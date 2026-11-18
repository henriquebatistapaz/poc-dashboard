import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from './App.vue'
import { i18n } from './i18n'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('App', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'pt-BR'
    HTMLDialogElement.prototype.showModal = vi.fn()
    Element.prototype.scrollIntoView = vi.fn()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  it('handles login errors (401, 503, 500), prevents double submit, and logs out on token expiration', async () => {
    vi.useFakeTimers()
    let resolveLogin!: (res: Response) => void
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({}, 401))
      .mockResolvedValueOnce(jsonResponse({}, 503))
      .mockResolvedValueOnce(jsonResponse({}, 500))
      .mockImplementationOnce(
        () =>
          new Promise<Response>((resolve) => {
            resolveLogin = resolve
          }),
      )
      .mockResolvedValueOnce(jsonResponse({ items: [], total: 0, page: 0, size: 10 }))
      .mockResolvedValueOnce(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(App, { global: { plugins: [i18n] } })

    const submitLogin = async () => {
      await wrapper.get('#login-email').setValue('admin@example.test')
      await wrapper.get('#login-password').setValue('pw')
      await wrapper.get('form.login-form').trigger('submit')
      await flushPromises()
    }

    await submitLogin()
    expect(wrapper.get('[role="alert"]').text()).toContain('E-mail ou senha incorretos')

    await submitLogin()
    expect(wrapper.get('[role="alert"]').text()).toContain('Login indisponível')

    await submitLogin()
    expect(wrapper.get('[role="alert"]').text()).toContain('encontrou um erro')

    await wrapper.get('#login-email').setValue('admin@example.test')
    await wrapper.get('#login-password').setValue('pw')
    await wrapper.get('form.login-form').trigger('submit')
    await wrapper.get('form.login-form').trigger('submit')
    expect(fetchMock).toHaveBeenCalledTimes(4)

    resolveLogin(jsonResponse({ accessToken: 'tok-1', tokenType: 'Bearer', expiresIn: 10 }))
    await flushPromises()
    expect(wrapper.find('.workspace-shell').exists()).toBe(true)

    vi.advanceTimersByTime(10000)
    await flushPromises()
    expect(wrapper.find('form.login-form').exists()).toBe(true)
    expect(wrapper.get('[role="alert"]').text()).toContain('sessão expirou')
  })

  it('handles filters, pagination, item details with retry/focus restore, and list/history errors', async () => {
    const itemsPage0 = {
      items: [
        {
          id: 1,
          source: 'rss',
          externalId: 'ext-1',
          title: 'Item Um',
          link: 'https://example.test/1',
          publishedAt: '2026-11-17T10:00:00Z',
          collectedAt: '2026-11-17T12:00:00Z',
        },
        {
          id: 2,
          source: 'rss',
          externalId: 'ext-2',
          title: null,
          link: 'javascript:alert(1)',
          publishedAt: null,
          collectedAt: '2026-11-17T12:00:00Z',
        },
      ],
      total: 25,
      page: 0,
      size: 10,
    }

    let detailAttempts = 0
    let listAttempts = 0
    let historyAttempts = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url === '/v1/auth/login') {
        return jsonResponse({ accessToken: 'tok-2', tokenType: 'Bearer', expiresIn: 900 })
      }
      if (url.startsWith('/v1/items?')) {
        listAttempts++
        if (listAttempts === 3) return jsonResponse({}, 500)
        return jsonResponse(itemsPage0)
      }
      if (url.startsWith('/v1/collections?')) {
        historyAttempts++
        if (historyAttempts === 2) return jsonResponse({}, 502)
        return jsonResponse([
          { id: 5, source: 'RSS', status: 'PENDING', createdAt: '2026-11-17T12:00:00Z', fetched: null, inserted: null, duplicates: null, errorMessage: null },
        ])
      }
      if (url === '/v1/items/1') {
        detailAttempts++
        if (detailAttempts === 1) return jsonResponse({}, 500)
        return jsonResponse(itemsPage0.items[0])
      }
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(App, { attachTo: document.body, global: { plugins: [i18n] } })
    await wrapper.get('#login-email').setValue('admin@example.test')
    await wrapper.get('#login-password').setValue('pw')
    await wrapper.get('form.login-form').trigger('submit')
    await flushPromises()

    // Valid filters with q, source, from, to
    await wrapper.get('#filter-query').setValue('cidades')
    await wrapper.get('#filter-source').setValue('rss')
    await wrapper.get('#filter-from').setValue('2026-11-01T10:00')
    await wrapper.get('#filter-to').setValue('2026-11-17T10:00')
    await wrapper.get('form.filters').trigger('submit')
    await flushPromises()

    // Reversed date range rejected
    await wrapper.get('#filter-from').setValue('2026-11-18T10:00')
    await wrapper.get('#filter-to').setValue('2026-11-10T10:00')
    await wrapper.get('form.filters').trigger('submit')
    expect(wrapper.get('#filter-error').text()).toContain('data inicial')

    // Clear filters -> triggers listAttempts === 2 (500), then retry succeeds
    await wrapper.get('button.clear-filters').trigger('click')
    await flushPromises()
    expect(wrapper.get('.library-notice').text()).toContain('encontrou um erro')
    await wrapper.get('.library-notice button').trigger('click')
    await flushPromises()
    expect(wrapper.get('.item-title').text()).toBe('Item Um')

    // Switch language & theme via pickers
    await wrapper.get('.language-picker select').setValue('en')
    await wrapper.get('.theme-picker select').setValue('dark')
    await wrapper.get('.language-picker select').setValue('pt-BR')
    await wrapper.get('.theme-picker select').setValue('light')

    // History error + retry
    await wrapper.get('.history-heading button').trigger('click')
    await flushPromises()
    expect(wrapper.get('.history-notice').text()).toContain('não respondeu')
    await wrapper.get('.history-notice button').trigger('click')
    await flushPromises()

    // Pagination + previous + page size
    await wrapper.get('button[aria-label="Próxima"]').trigger('click')
    await flushPromises()
    await wrapper.get('button[aria-label="Anterior"]').trigger('click')
    await flushPromises()
    await wrapper.get('#page-size').setValue('20')
    await flushPromises()

    // Item details error -> retry -> close restores focus, plus chevron trigger
    const itemBtn = wrapper.get('.item-title')
    ;(itemBtn.element as HTMLElement).focus()
    await itemBtn.trigger('click')
    await flushPromises()
    expect(wrapper.get('.detail-notice').text()).toContain('encontrou um erro')
    await wrapper.get('.detail-notice button').trigger('click')
    await flushPromises()
    expect(wrapper.get('#detail-title').text()).toBe('Item Um')
    await wrapper.get('dialog button[aria-label="Fechar"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('dialog').exists()).toBe(false)
    expect(document.activeElement).toBe(itemBtn.element)

    await wrapper.get('tbody button[aria-label="Ver detalhes"]').trigger('click')
    await flushPromises()
    await wrapper.get('dialog button[aria-label="Fechar"]').trigger('click')
    await flushPromises()

    // Scroll button and logout
    await wrapper.get('.page-heading > button').trigger('click')
    await wrapper.get('button[aria-label="Sair"]').trigger('click')
    expect(wrapper.find('form.login-form').exists()).toBe(true)
    wrapper.unmount()
  })

  it('collects, resumes runs, handles collection failures, 401 expiry, and mobile drawer navigation', async () => {
    let runPoll = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url === '/v1/auth/login') {
        return jsonResponse({ accessToken: 'tok-3', tokenType: 'Bearer', expiresIn: 900 })
      }
      if (url.startsWith('/v1/items?')) {
        return jsonResponse({ items: [], total: 0, page: 0, size: 10 })
      }
      if (url.startsWith('/v1/collections?')) {
        return jsonResponse([
          { id: 10, source: 'RSS', status: 'RUNNING', createdAt: '2026-11-17T12:00:00Z', fetched: null, inserted: null, duplicates: null, errorMessage: null },
        ])
      }
      if (url === '/v1/collections' && init?.method === 'POST') {
        return jsonResponse({ id: 20, source: 'RSS', status: 'PENDING', fetched: null, inserted: null, duplicates: null, errorMessage: null }, 202)
      }
      if (url === '/v1/collections/20') {
        return jsonResponse({ id: 20, source: 'RSS', status: 'SUCCEEDED', fetched: 1, inserted: null, duplicates: null, errorMessage: null })
      }
      if (url === '/v1/collections/10') {
        runPoll++
        if (runPoll === 1) {
          return jsonResponse({ id: 10, source: 'RSS', status: 'SUCCEEDED', fetched: 2, inserted: 1, duplicates: 1, errorMessage: null })
        }
        return jsonResponse({}, 401)
      }
      return jsonResponse({}, 401)
    })
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(App, { attachTo: document.body, global: { plugins: [i18n] } })
    await wrapper.get('#login-email').setValue('admin@example.test')
    await wrapper.get('#login-password').setValue('pw')
    await wrapper.get('form.login-form').trigger('submit')
    await flushPromises()

    // Mobile drawer open, Tab trap, Escape close, reopen & navigate
    const toggleBtn = wrapper.get('[data-testid="mobile-menu-toggle"]')
    await toggleBtn.trigger('click')
    await flushPromises()
    const drawer = wrapper.get('#mobile-nav-drawer')
    const closeDrawerBtn = drawer.get('button[aria-label="Fechar menu de navegação"]').element as HTMLElement
    expect(document.activeElement).toBe(closeDrawerBtn)

    await drawer.trigger('keydown', { key: 'Tab', shiftKey: false })
    await drawer.trigger('keydown', { key: 'Tab', shiftKey: true })
    const links = drawer.findAll('a')
    const firstFocusable = links[0]!.element as HTMLElement
    firstFocusable.focus()
    await drawer.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(links[links.length - 1]!.element)
    await drawer.trigger('keydown', { key: 'Tab', shiftKey: false })
    expect(document.activeElement).toBe(firstFocusable)

    await drawer.trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(wrapper.find('#mobile-nav-drawer').exists()).toBe(false)
    expect(document.activeElement).toBe(toggleBtn.element)

    await toggleBtn.trigger('click')
    await flushPromises()
    await wrapper.get('.mobile-drawer-backdrop').trigger('click')
    await flushPromises()
    expect(wrapper.find('#mobile-nav-drawer').exists()).toBe(false)

    await toggleBtn.trigger('click')
    await flushPromises()
    await wrapper.get('#mobile-nav-drawer button[aria-label="Fechar menu de navegação"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('#mobile-nav-drawer').exists()).toBe(false)

    await toggleBtn.trigger('click')
    await flushPromises()
    await wrapper.get('#mobile-nav-drawer').trigger('keydown', { key: 'ArrowDown' })
    await wrapper.get('#mobile-nav-drawer a.brand[href="#library"]').trigger('click')
    await flushPromises()

    await toggleBtn.trigger('click')
    await flushPromises()
    await wrapper.get('#mobile-nav-drawer a.nav-item[href="#library"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('#mobile-nav-drawer').exists()).toBe(false)

    await toggleBtn.trigger('click')
    await flushPromises()
    await wrapper.get('#mobile-nav-drawer a[href="#activity"]').trigger('click')
    await flushPromises()

    await toggleBtn.trigger('click')
    await flushPromises()
    await wrapper.get('#mobile-nav-drawer a[href="/swagger-ui.html"]').trigger('click')
    await flushPromises()

    await toggleBtn.trigger('click')
    await flushPromises()
    await wrapper.get('#mobile-nav-drawer a[href="#collect"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('#mobile-nav-drawer').exists()).toBe(false)

    // Empty state button scroll
    await wrapper.get('.empty-state button').trigger('click')

    // Collect success & view button
    await wrapper.get('#collection-input').setValue('https://example.test/feed.xml')
    await wrapper.get('form.collection-form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('.collection-result').text()).toContain('Coleta concluída')
    await wrapper.get('.collection-result .text-button').trigger('click')

    // Resume run success
    await wrapper.get('button.history-resume').trigger('click')
    await flushPromises()

    // Resume run -> 401 session expired
    await wrapper.get('button.history-resume').trigger('click')
    await flushPromises()
    expect(wrapper.find('form.login-form').exists()).toBe(true)
    expect(wrapper.get('[role="alert"]').text()).toContain('sessão expirou')

    wrapper.unmount()
  })

  it('handles 401 during list/history/detail and collection error', async () => {
    let mode: 'collect-fail' | 'collect-401' | 'detail-401' | 'list-401' | 'history-401' = 'collect-fail'
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url === '/v1/auth/login') {
        return jsonResponse({ accessToken: 'tok-4', tokenType: 'Bearer', expiresIn: 900 })
      }
      if (url.startsWith('/v1/items?')) {
        if (mode === 'list-401') return jsonResponse({}, 401)
        return jsonResponse({
          items: [{ id: 1, source: 'rss', externalId: 'e1', title: 'T', link: 'https://example.test/1', publishedAt: null, collectedAt: '2026-11-17T12:00:00Z' }],
          total: 1,
          page: 0,
          size: 10,
        })
      }
      if (url.startsWith('/v1/collections?')) {
        if (mode === 'history-401') return jsonResponse({}, 401)
        return jsonResponse([])
      }
      if (url === '/v1/collections' && init?.method === 'POST') {
        if (mode === 'collect-401') return jsonResponse({}, 401)
        return jsonResponse({}, 502)
      }
      if (url === '/v1/items/1') {
        if (mode === 'detail-401') return jsonResponse({}, 401)
        return jsonResponse({}, 500)
      }
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(App, { global: { plugins: [i18n] } })
    const doLogin = async () => {
      await wrapper.get('#login-email').setValue('admin@example.test')
      await wrapper.get('#login-password').setValue('pw')
      await wrapper.get('form.login-form').trigger('submit')
      await flushPromises()
    }

    await doLogin()
    await wrapper.get('#collection-input').setValue('https://example.test/feed.xml')
    await wrapper.get('form.collection-form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('#collection-error').text()).toContain('não respondeu')

    mode = 'collect-401'
    await wrapper.get('#collection-input').setValue('https://example.test/feed.xml')
    await wrapper.get('form.collection-form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('form.login-form').exists()).toBe(true)

    mode = 'detail-401'
    await doLogin()
    await wrapper.get('.item-title').trigger('click')
    await flushPromises()
    expect(wrapper.find('form.login-form').exists()).toBe(true)

    mode = 'list-401'
    await doLogin()
    expect(wrapper.find('form.login-form').exists()).toBe(true)

    mode = 'history-401'
    await doLogin()
    expect(wrapper.find('form.login-form').exists()).toBe(true)
  })
})
