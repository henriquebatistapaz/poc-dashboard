import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import MastodonConnection from './MastodonConnection.vue'
import { i18n } from '../i18n'

const json = (body: unknown, status = 200) =>
  new Response(status === 204 ? null : JSON.stringify(body), { status })

beforeEach(() => {
  i18n.global.locale.value = 'pt-BR'
  window.history.replaceState({}, '', '/')
})
afterEach(() => vi.unstubAllGlobals())

const render = (token = 'jwt') =>
  mount(MastodonConnection, { props: { token }, global: { plugins: [i18n] } })

describe('Mastodon connection card', () => {
  it('publishes a post with the chosen visibility and shows its link', async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce(json({ connected: true, instance: 'https://mastodon.social' }))
      .mockResolvedValueOnce(json({ url: 'https://mastodon.social/@me/900' }))
    vi.stubGlobal('fetch', fetch)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('#mastodon-post-text').setValue('  Olá  ')
    await wrapper.get('#mastodon-visibility').setValue('public')
    await wrapper.findAll('form')[1]!.trigger('submit')
    await flushPromises()
    expect(fetch.mock.calls[1]![0]).toContain('/v1/integrations/mastodon/post')
    expect(JSON.parse(fetch.mock.calls[1]![1].body)).toEqual({ text: 'Olá', visibility: 'public' })
    expect(wrapper.text()).toContain('Post publicado:')
    expect(wrapper.get('a').attributes('href')).toBe('https://mastodon.social/@me/900')
    expect((wrapper.get('#mastodon-post-text').element as HTMLTextAreaElement).value).toBe('')
  })

  it('shows the error when the post is rejected', async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce(json({ connected: true, instance: 'https://mastodon.social' }))
      .mockResolvedValueOnce(json({ status: 502 }, 502))
    vi.stubGlobal('fetch', fetch)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('#mastodon-post-text').setValue('Olá')
    await wrapper.findAll('form')[1]!.trigger('submit')
    await flushPromises()
    expect(wrapper.find('a').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })

  it('shows the disconnected state and no actions without a token', async () => {
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)
    const wrapper = render('')
    await flushPromises()
    expect(fetch).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Nenhuma conta conectada.')
    expect((wrapper.get('button[type="submit"]').element as HTMLButtonElement).disabled).toBe(true)
  })

  it('loads the connection status with the bearer token', async () => {
    const fetch = vi.fn().mockResolvedValue(json({ connected: true, instance: 'https://mastodon.social' }))
    vi.stubGlobal('fetch', fetch)
    const wrapper = render()
    await flushPromises()
    expect(fetch.mock.calls[0]![1].headers.Authorization).toBe('Bearer jwt')
    expect(wrapper.text()).toContain('Conectado a https://mastodon.social')
    expect(wrapper.find('#mastodon-query').exists()).toBe(true)
  })

  it('redirects to the authorization URL returned by the backend', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, search: '', assign })
    const fetch = vi.fn()
      .mockResolvedValueOnce(json({ connected: false, instance: null }))
      .mockResolvedValueOnce(json({ authorizationUrl: 'https://mastodon.social/oauth/authorize?state=x' }))
    vi.stubGlobal('fetch', fetch)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(JSON.parse(fetch.mock.calls[1]![1].body)).toEqual({ instance: 'https://mastodon.social' })
    expect(assign).toHaveBeenCalledWith('https://mastodon.social/oauth/authorize?state=x')
  })

  it('searches and reports counts, then disconnects', async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce(json({ connected: true, instance: 'https://mastodon.social' }))
      .mockResolvedValueOnce(json({ fetched: 2, inserted: 1, duplicates: 1 }))
      .mockResolvedValueOnce(json(null, 204))
    vi.stubGlobal('fetch', fetch)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('#mastodon-query').setValue('java')
    await wrapper.get('#mastodon-limit').setValue('5')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(JSON.parse(fetch.mock.calls[1]![1].body)).toEqual({ query: 'java', limit: 5 })
    expect(wrapper.get('[role="status"]').text()).toContain('2 posts encontrados · 1 novos · 1 já existentes')

    const disconnect = wrapper.findAll('button').find(button => button.text() === 'Desconectar')!
    await disconnect.trigger('click')
    await flushPromises()
    expect(fetch.mock.calls[2]![1].method).toBe('DELETE')
    expect(wrapper.text()).toContain('Nenhuma conta conectada.')
  })

  it('explains a search attempted without a connection and shows the OAuth result notice', async () => {
    window.history.replaceState({}, '', '/?mastodon=connected')
    const fetch = vi.fn()
      .mockResolvedValueOnce(json({ connected: true, instance: 'https://mastodon.social' }))
      .mockResolvedValueOnce(json({ error: 'conflict' }, 409))
    vi.stubGlobal('fetch', fetch)
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('Conta Mastodon conectada.')
    await wrapper.get('#mastodon-query').setValue('java')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Conecte uma conta Mastodon antes de buscar.')
  })

  it('shows the error notice when the callback reports a failure', async () => {
    window.history.replaceState({}, '', '/?mastodon=error')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ connected: false, instance: null })))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('Não foi possível conectar a conta Mastodon.')
  })

  it('reports failures from loading, connecting, searching and disconnecting', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ error: 'down' }, 500)))
    const loading = render()
    await flushPromises()
    expect(loading.find('[role="alert"]').exists()).toBe(true)

    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, search: '', assign })
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(json({ connected: false, instance: null }))
      .mockResolvedValueOnce(json({ error: 'unavailable' }, 503)))
    const connecting = render()
    await flushPromises()
    await connecting.get('form').trigger('submit')
    await flushPromises()
    expect(assign).not.toHaveBeenCalled()
    expect(connecting.find('[role="alert"]').exists()).toBe(true)

    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(json({ connected: true, instance: 'https://mastodon.social' }))
      .mockResolvedValueOnce(json({ error: 'bad gateway' }, 502)))
    const searching = render()
    await flushPromises()
    await searching.get('#mastodon-query').setValue('java')
    await searching.get('form').trigger('submit')
    await flushPromises()
    expect(searching.find('[role="alert"]').exists()).toBe(true)

    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(json({ connected: true, instance: 'https://mastodon.social' }))
      .mockResolvedValueOnce(json({ error: 'server' }, 500)))
    const disconnecting = render()
    await flushPromises()
    const disconnect = disconnecting.findAll('button').find(button => button.text() === 'Desconectar')!
    await disconnect.trigger('click')
    await flushPromises()
    expect(disconnecting.find('[role="alert"]').exists()).toBe(true)
  })

  it('disables the search while a request is pending', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(json({ connected: true, instance: 'https://mastodon.social' }))
      .mockReturnValueOnce(new Promise(() => undefined)))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('#mastodon-query').setValue('java')
    void wrapper.get('form').trigger('submit')
    await flushPromises()
    const submit = wrapper.get('button[type="submit"]').element as HTMLButtonElement
    expect(submit.textContent).toContain('Buscando…')
    expect(submit.disabled).toBe(true)
  })

  it('keeps the connect button disabled while the authorization request is pending', async () => {
    vi.stubGlobal('location', { ...window.location, search: '', assign: vi.fn() })
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(json({ connected: false, instance: null }))
      .mockReturnValueOnce(new Promise(() => undefined)))
    const wrapper = render()
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect((wrapper.get('button[type="submit"]').element as HTMLButtonElement).disabled).toBe(true)
    expect((wrapper.get('#mastodon-instance').element as HTMLInputElement).disabled).toBe(true)
  })

  it('lets the user edit the instance and the result limit before acting', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(json({ connected: false, instance: null }))
      .mockResolvedValueOnce(json({ fetched: 0, inserted: 0, duplicates: 0 }))
    vi.stubGlobal('fetch', fetch)
    const wrapper = render()
    await flushPromises()
    await wrapper.get('#mastodon-instance').setValue('https://fosstodon.org')
    expect((wrapper.get('#mastodon-instance').element as HTMLInputElement).value).toBe('https://fosstodon.org')
    await wrapper.get('#mastodon-instance').setValue('https://fosstodon.org')
    vi.stubGlobal('location', { ...window.location, search: '', assign: vi.fn() })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(JSON.parse(fetch.mock.calls[1]![1].body)).toEqual({ instance: 'https://fosstodon.org' })
  })
})
