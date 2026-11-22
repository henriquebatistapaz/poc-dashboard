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
          { id: 10, source: 'RSS', status: 'RUNNING', createdAt: '2026-11-17T12:00:00Z', input: 'https://example.test/feed.xml', fetched: null, inserted: null, duplicates: null, errorMessage: null },
        ])
      }
      if (url === '/v1/collections' && init?.method === 'POST') {
        return jsonResponse({ id: 20, source: 'RSS', status: 'PENDING', input: 'https://example.test/feed.xml', fetched: null, inserted: null, duplicates: null, errorMessage: null }, 202)
      }
      if (url === '/v1/collections/20') {
        return jsonResponse({ id: 20, source: 'RSS', status: 'SUCCEEDED', input: 'https://example.test/feed.xml', fetched: 1, inserted: null, duplicates: null, errorMessage: null })
      }
      if (url === '/v1/collections/10') {
        runPoll++
        if (runPoll === 1) {
          return jsonResponse({ id: 10, source: 'RSS', status: 'SUCCEEDED', input: 'https://example.test/feed.xml', fetched: 2, inserted: 1, duplicates: 1, errorMessage: null })
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
    expect(wrapper.get('.collection-context').text()).toContain('https://example.test/feed.xml')

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

  it('covers defensive guard branches and superseded controller flows', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(App, { attachTo: document.body, global: { plugins: [i18n] } })
    type AppVm = {
      [key: string]: unknown
      loginBusy?: boolean
      token?: string
      collectionBusy?: boolean
      collectionFailure?: unknown
      detail?: { id: number }
      login: (e: string, p: string) => Promise<void>
      loadItems: () => Promise<void>
      loadHistory: () => Promise<void>
      collect: (s: string, i: string) => Promise<void>
      resumeRun: (r: unknown) => Promise<void>
      retryDetail: () => Promise<void>
      logout: () => void
      openDetail: (id: number, ev?: unknown) => Promise<void>
    }
    const vm = wrapper.vm as unknown as AppVm

    // Guard branches before login
    await vm.loadItems()
    await vm.loadHistory()
    await vm.collect('RSS', 'https://example.org')
    await vm.resumeRun({ id: 1, status: 'SUCCEEDED', source: 'RSS' })
    await vm.retryDetail()

    // 1. Login guard when already busy
    let resolveLogin!: (res: Response) => void
    fetchMock.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          resolveLogin = resolve
        }),
    )
    void vm.login('user@test.org', 'pw')
    expect(vm.loginBusy).toBe(true)
    await vm.login('user@test.org', 'pw')

    fetchMock
      .mockResolvedValueOnce(jsonResponse({ items: [], total: 0, page: 0, size: 10 }))
      .mockResolvedValueOnce(jsonResponse([]))
    resolveLogin(jsonResponse({ accessToken: 'tok-5', tokenType: 'Bearer', expiresIn: 900 }))
    await flushPromises()
    expect(vm.token).toBe('tok-5')

    // 2. loadItems: abort error and superseded controller in finally
    let rejectItems1!: (err: unknown) => void
    let resolveItems2!: (res: Response) => void
    fetchMock
      .mockImplementationOnce(
        () =>
          new Promise<Response>((_, reject) => {
            rejectItems1 = reject
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise<Response>((resolve) => {
            resolveItems2 = resolve
          }),
      )

    const pItems1 = vm.loadItems()
    const pItems2 = vm.loadItems()
    rejectItems1(new DOMException('Aborted', 'AbortError'))
    resolveItems2(jsonResponse({ items: [], total: 0, page: 0, size: 10 }))
    await Promise.allSettled([pItems1, pItems2])
    await flushPromises()

    // 3. loadHistory: superseded controller in finally
    let resolveHist1!: (res: Response) => void
    let resolveHist2!: (res: Response) => void
    fetchMock
      .mockImplementationOnce(
        () =>
          new Promise<Response>((resolve) => {
            resolveHist1 = resolve
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise<Response>((resolve) => {
            resolveHist2 = resolve
          }),
      )
    const pHist1 = vm.loadHistory()
    const pHist2 = vm.loadHistory()
    resolveHist1(jsonResponse([]))
    resolveHist2(jsonResponse([]))
    await Promise.allSettled([pHist1, pHist2])
    await flushPromises()

    // 4. collect():
    // a) busy guard
    vm.collectionBusy = true
    await vm.collect('RSS', 'https://example.org')
    vm.collectionBusy = false

    // b) logout while request('/v1/collections') is pending
    let resolveCollectPost!: (res: Response) => void
    fetchMock.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          resolveCollectPost = resolve
        }),
    )
    const pCollect1 = vm.collect('RSS', 'https://example.org')
    vm.logout()
    resolveCollectPost(jsonResponse({ id: 101, source: 'RSS', status: 'PENDING' }))
    await pCollect1
    await flushPromises()

    // Log back in
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ accessToken: 'tok-6', tokenType: 'Bearer', expiresIn: 900 }))
      .mockResolvedValueOnce(jsonResponse({ items: [], total: 0, page: 0, size: 10 }))
      .mockResolvedValueOnce(jsonResponse([]))
    await vm.login('user@test.org', 'pw')
    await flushPromises()

    // c) logout while waitForRun is pending in collect
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ id: 102, source: 'RSS', status: 'PENDING' }))
      .mockResolvedValueOnce(jsonResponse([]))
    let resolveWaitRun1!: (res: Response) => void
    fetchMock.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          resolveWaitRun1 = resolve
        }),
    )
    const pCollect2 = vm.collect('RSS', 'https://example.org')
    await flushPromises()
    vm.logout()
    resolveWaitRun1(jsonResponse({ id: 102, source: 'RSS', status: 'SUCCEEDED', fetched: 1, inserted: 1, duplicates: 0, errorMessage: null }))
    await pCollect2
    await flushPromises()

    // Log back in
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ accessToken: 'tok-7', tokenType: 'Bearer', expiresIn: 900 }))
      .mockResolvedValueOnce(jsonResponse({ items: [], total: 0, page: 0, size: 10 }))
      .mockResolvedValueOnce(jsonResponse([]))
    await vm.login('user@test.org', 'pw')
    await flushPromises()

    // d) logout while waitForRun rejects in collect
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ id: 103, source: 'RSS', status: 'PENDING' }))
      .mockResolvedValueOnce(jsonResponse([]))
    let rejectWaitRun2!: (err: unknown) => void
    fetchMock.mockImplementationOnce(
      () =>
        new Promise<Response>((_, reject) => {
          rejectWaitRun2 = reject
        }),
    )
    const pCollect3 = vm.collect('RSS', 'https://example.org')
    await flushPromises()
    vm.logout()
    rejectWaitRun2(new Error('Network fail'))
    await pCollect3
    await flushPromises()

    // 5. resumeRun:
    // Log back in
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ accessToken: 'tok-8', tokenType: 'Bearer', expiresIn: 900 }))
      .mockResolvedValueOnce(jsonResponse({ items: [], total: 0, page: 0, size: 10 }))
      .mockResolvedValueOnce(jsonResponse([]))
    await vm.login('user@test.org', 'pw')
    await flushPromises()

    // a) guards: invalid status & busy guard
    await vm.resumeRun({ id: 104, status: 'SUCCEEDED', source: 'RSS' })
    vm.collectionBusy = true
    await vm.resumeRun({ id: 104, status: 'RUNNING', source: 'RSS' })
    vm.collectionBusy = false

    // b) logout while waitForRun is pending in resumeRun
    let resolveResume1!: (res: Response) => void
    fetchMock.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          resolveResume1 = resolve
        }),
    )
    const pResume1 = vm.resumeRun({ id: 105, status: 'RUNNING', source: 'RSS' })
    vm.logout()
    resolveResume1(jsonResponse({ id: 105, source: 'RSS', status: 'SUCCEEDED', fetched: 1, inserted: 1, duplicates: 0, errorMessage: null }))
    await pResume1
    await flushPromises()

    // Log back in
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ accessToken: 'tok-9', tokenType: 'Bearer', expiresIn: 900 }))
      .mockResolvedValueOnce(jsonResponse({ items: [], total: 0, page: 0, size: 10 }))
      .mockResolvedValueOnce(jsonResponse([]))
    await vm.login('user@test.org', 'pw')
    await flushPromises()

    // c) logout while waitForRun rejects in resumeRun
    let rejectResume2!: (err: unknown) => void
    fetchMock.mockImplementationOnce(
      () =>
        new Promise<Response>((_, reject) => {
          rejectResume2 = reject
        }),
    )
    const pResume2 = vm.resumeRun({ id: 106, status: 'RUNNING', source: 'RSS' })
    vm.logout()
    rejectResume2(new Error('Network error'))
    await pResume2
    await flushPromises()

    // Log back in
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ accessToken: 'tok-10', tokenType: 'Bearer', expiresIn: 900 }))
      .mockResolvedValueOnce(jsonResponse({ items: [], total: 0, page: 0, size: 10 }))
      .mockResolvedValueOnce(jsonResponse([]))
    await vm.login('user@test.org', 'pw')
    await flushPromises()

    // d) resumeRun non-401 failure -> collectionFailure.value = error
    fetchMock
      .mockResolvedValueOnce(jsonResponse({}, 500))
      .mockResolvedValueOnce(jsonResponse([]))
    await vm.resumeRun({ id: 107, status: 'RUNNING', source: 'RSS' })
    await flushPromises()
    expect(vm.collectionFailure).toBeDefined()

    // 6. openDetail:
    // a) event with non-HTMLElement currentTarget, while document.activeElement is HTMLElement
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ id: 201, source: 'rss', externalId: 'e201', title: 'T201', link: 'https://example.test/201', publishedAt: null, collectedAt: '2026-11-17T12:00:00Z' }),
    )
    await vm.openDetail(201, { currentTarget: {} } as unknown as MouseEvent)
    await flushPromises()
    expect(vm.detail?.id).toBe(201)

    // b) call openDetail again while dialogOpen is already true
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ id: 202, source: 'rss', externalId: 'e202', title: 'T202', link: 'https://example.test/202', publishedAt: null, collectedAt: '2026-11-17T12:00:00Z' }),
    )
    await vm.openDetail(202)
    await flushPromises()
    expect(vm.detail?.id).toBe(202)

    // c) call openDetail when document.activeElement is not an HTMLElement
    const origActive = Object.getOwnPropertyDescriptor(document, 'activeElement')
    Object.defineProperty(document, 'activeElement', { value: null, configurable: true })
    vm.dialogOpen = false
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ id: 203, source: 'rss', externalId: 'e203', title: 'T203', link: 'https://example.test/203', publishedAt: null, collectedAt: '2026-11-17T12:00:00Z' }),
    )
    await vm.openDetail(203)
    if (origActive) Object.defineProperty(document, 'activeElement', origActive)
    await flushPromises()
    expect(vm.detail?.id).toBe(203)

    // d) logout while openDetail request is pending -> detail is not set
    let resolveDetailPending!: (res: Response) => void
    fetchMock.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          resolveDetailPending = resolve
        }),
    )
    vm.dialogOpen = false
    const pDetail = vm.openDetail(204)
    vm.logout()
    resolveDetailPending(
      jsonResponse({ id: 204, source: 'rss', externalId: 'e204', title: 'T204', link: 'https://example.test/204', publishedAt: null, collectedAt: '2026-11-17T12:00:00Z' }),
    )
    await pDetail
    await flushPromises()
    expect(vm.detail).toBeNull()

    // e) superseded openDetail: detailController !== controller in finally
    let resolveDetail1!: (res: Response) => void
    let resolveDetail2!: (res: Response) => void
    fetchMock
      .mockImplementationOnce(
        () =>
          new Promise<Response>((resolve) => {
            resolveDetail1 = resolve
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise<Response>((resolve) => {
            resolveDetail2 = resolve
          }),
      )
    const pDet1 = vm.openDetail(205)
    const pDet2 = vm.openDetail(206)
    resolveDetail1(jsonResponse({ id: 205, source: 'rss', externalId: 'e205', title: 'T205', link: 'https://example.test/205', publishedAt: null, collectedAt: '2026-11-17T12:00:00Z' }))
    resolveDetail2(jsonResponse({ id: 206, source: 'rss', externalId: 'e206', title: 'T206', link: 'https://example.test/206', publishedAt: null, collectedAt: '2026-11-17T12:00:00Z' }))
    await Promise.allSettled([pDet1, pDet2])
    await flushPromises()

    wrapper.unmount()
  })
  it('shows the Mastodon connection on its own screen and back to the dashboard by hash', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url.includes('/v1/auth/login')) return jsonResponse({ accessToken: 'tok-m', tokenType: 'Bearer', expiresIn: 900 })
      if (url.includes('/mastodon/connection')) return jsonResponse({ connected: false, instance: null })
      if (url.includes('/v1/collections?')) return jsonResponse([])
      return jsonResponse({ items: [], total: 0, page: 0, size: 10 })
    })
    vi.stubGlobal('fetch', fetchMock)
    window.history.replaceState({}, '', '/')
    const wrapper = mount(App, { attachTo: document.body, global: { plugins: [i18n] } })
    await wrapper.get('#login-email').setValue('admin@example.test')
    await wrapper.get('#login-password').setValue('pw')
    await wrapper.get('form.login-form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('#mastodon').exists()).toBe(false)
    expect(wrapper.find('#library').exists()).toBe(true)

    window.location.hash = '#mastodon'
    window.dispatchEvent(new HashChangeEvent('hashchange'))
    await flushPromises()
    expect(wrapper.find('#mastodon').exists()).toBe(true)
    expect(wrapper.find('#library').exists()).toBe(false)
    expect(wrapper.get('.sidebar .nav-item.active').text()).toContain('Mastodon com sua conta')

    window.location.hash = '#library'
    window.dispatchEvent(new HashChangeEvent('hashchange'))
    await flushPromises()
    expect(wrapper.find('#mastodon').exists()).toBe(false)
    expect(wrapper.find('#library').exists()).toBe(true)
    expect(Element.prototype.scrollIntoView).toHaveBeenCalled()

    window.location.hash = '#inexistente'
    window.dispatchEvent(new HashChangeEvent('hashchange'))
    await flushPromises()
    expect(wrapper.find('#mastodon').exists()).toBe(false)

    window.location.hash = ''
    window.dispatchEvent(new HashChangeEvent('hashchange'))
    await flushPromises()
    expect(wrapper.find('#mastodon').exists()).toBe(false)

    await wrapper.get('[data-testid="mobile-menu-toggle"]').trigger('click')
    await flushPromises()
    const drawerLink = wrapper.findAll('#mobile-nav-drawer a').find((link) => link.attributes('href') === '#mastodon')!
    await drawerLink.trigger('click')
    await flushPromises()
    expect(wrapper.find('#mobile-nav-drawer').exists()).toBe(false)
    wrapper.unmount()
    window.history.replaceState({}, '', '/')
  })
})
