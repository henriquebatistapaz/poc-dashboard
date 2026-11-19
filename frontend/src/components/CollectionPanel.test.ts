import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import CollectionPanel from './CollectionPanel.vue'
import CollectionHistory from './CollectionHistory.vue'
import { i18n } from '../i18n'

beforeEach(() => {
  i18n.global.locale.value = 'pt-BR'
})
const render = () =>
  mount(CollectionPanel, { props: { busy: false, result: null, error: '' }, global: { plugins: [i18n] } })

describe('manual collection', () => {
  it('submits a pasted RSS URL and blocks unsafe URL schemes', async () => {
    const wrapper = render()
    await wrapper.get('#collection-input').setValue('javascript:alert(1)')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('collect')).toBeUndefined()
    expect(wrapper.get('[role="alert"]').text()).toContain('URL completa')
    await wrapper.get('#collection-input').setValue(' https://example.org/feed ')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('collect')![0]).toEqual(['RSS', 'https://example.org/feed'])
  })

  it('validates empty and oversized input and blocks duplicate submit while busy', async () => {
    const wrapper = render()
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('Informe um link')

    await wrapper.get('#collection-input').setValue('https://example.org/' + 'a'.repeat(4100))
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('4.096')

    const busyWrapper = mount(CollectionPanel, {
      props: { busy: true, result: null, error: 'Erro externo' },
      global: { plugins: [i18n] },
    })
    expect(busyWrapper.get('[role="status"]').text()).toContain('Buscando')
    expect(busyWrapper.get('[role="alert"]').text()).toContain('Erro externo')
    await busyWrapper.get('form').trigger('submit')
    expect(busyWrapper.emitted('collect')).toBeUndefined()
  })

  it('supports all source types, empty results, dismiss and view actions', async () => {
    const wrapper = render()
    for (const source of ['ALPHA', 'BETA', 'GOOGLE_NEWS', 'WORDPRESS', 'MASTODON', 'LEMMY', 'DEV_TO', 'HACKER_NEWS', 'STACK_EXCHANGE', 'GITHUB', 'TABNEWS', 'WIKIPEDIA', 'GITLAB', 'LOBSTERS'] as const) {
      await wrapper.get('#collection-source').setValue(source)
      expect(wrapper.find('#collection-input').exists()).toBe(true)
    }
    await wrapper.get('#collection-source').setValue('WORDPRESS')
    expect(wrapper.get('label[for="collection-input"]').text()).toContain('Termo de busca')
    await wrapper.findAll('.feed-examples button')[0]!.trigger('click')
    expect((wrapper.get('#collection-input').element as HTMLInputElement).value).toBe('java')
    await wrapper.get('#collection-source').setValue('ALPHA')
    await wrapper.findAll('.feed-examples button')[0]!.trigger('click')
    expect((wrapper.get('#collection-input').element as HTMLTextAreaElement).value).toContain('"posts"')
    await wrapper.get('#collection-input').setValue('{"posts":[]}')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('collect')![0]).toEqual(['ALPHA', '{"posts":[]}'])

    await wrapper.setProps({
      result: { id: 9, source: 'RSS', status: 'SUCCEEDED', fetched: 0, inserted: 0, duplicates: 0, errorMessage: null },
    })
    expect(wrapper.get('.collection-result').text()).toContain('não retornou itens')
    await wrapper.get('.collection-result .text-button').trigger('click')
    expect(wrapper.emitted('view')).toHaveLength(1)
    await wrapper.get('button[aria-label="Fechar resultado"]').trigger('click')
    expect(wrapper.find('.collection-result').exists()).toBe(false)
  })

  it('shows two localized REST examples and help for every new source', async () => {
    const wrapper = render()
    const sources = ['MASTODON', 'LEMMY', 'DEV_TO', 'HACKER_NEWS', 'STACK_EXCHANGE', 'GITHUB', 'TABNEWS', 'WIKIPEDIA', 'GITLAB', 'LOBSTERS'] as const
    for (const source of sources) {
      await wrapper.get('#collection-source').setValue(source)
      expect(wrapper.findAll('.feed-examples button')).toHaveLength(2)
      expect(wrapper.find('.field-help')?.text()).toContain('APIs públicas')
      await wrapper.findAll('.feed-examples button')[0]!.trigger('click')
      expect((wrapper.get('#collection-input').element as HTMLInputElement).value).not.toBe('')
      await wrapper.findAll('.feed-examples button')[1]!.trigger('click')
      expect((wrapper.get('#collection-input').element as HTMLInputElement).value).not.toBe('')
    }
    for (const locale of ['en', 'es'] as const) {
      i18n.global.locale.value = locale
      await wrapper.get('#collection-source').setValue('WIKIPEDIA')
      await nextTick()
      expect(wrapper.find('.field-help')?.text()).toContain(locale === 'en' ? 'no credentials' : 'sin credenciales')
      expect(wrapper.findAll('.feed-examples button')).toHaveLength(2)
      await wrapper.findAll('.feed-examples button')[0]!.trigger('click')
      expect((wrapper.get('#collection-input').element as HTMLInputElement).value).not.toBe('')
    }
  })

  it('fills real examples without starting an unsolicited collection', async () => {
    const wrapper = render()
    await wrapper.findAll('.feed-examples button')[0]!.trigger('click')
    expect((wrapper.get('#collection-input').element as HTMLInputElement).value).toContain('bbci.co.uk')
    await wrapper.findAll('.feed-examples button')[1]!.trigger('click')
    expect((wrapper.get('#collection-input').element as HTMLInputElement).value).toBe('https://xkcd.com/atom.xml')
    await wrapper.findAll('.feed-examples button')[2]!.trigger('click')
    expect((wrapper.get('#collection-input').element as HTMLInputElement).value).toContain('tabnews.com.br')
    expect(wrapper.emitted('collect')).toBeUndefined()
  })

  it('clears input after success and reports duplicate-only results', async () => {
    const wrapper = render()
    await wrapper.get('#collection-source').setValue('RSS')
    await wrapper.get('#collection-input').setValue('https://example.org/feed.xml')
    expect(wrapper.get('#collection-input').attributes('type')).toBe('url')
    await wrapper.setProps({
      result: { id: 1, source: 'RSS', status: 'SUCCEEDED', fetched: 2, inserted: 0, duplicates: 2, errorMessage: null },
    })
    expect((wrapper.get('#collection-input').element as HTMLInputElement).value).toBe('')
    expect(wrapper.get('[role="status"]').text()).toContain('Nenhuma duplicata')
  })

  it('translates existing results and labels without reloading', async () => {
    const wrapper = render()
    await wrapper.setProps({
      result: { id: 2, source: 'RSS', status: 'SUCCEEDED', fetched: 2, inserted: 2, duplicates: 0, errorMessage: null },
    })
    i18n.global.locale.value = 'es'
    await nextTick()
    expect(wrapper.text()).toContain('Recopilación completada')
    expect(wrapper.text()).toContain('Recopilar ahora')
    i18n.global.locale.value = 'en'
    await nextTick()
    expect(wrapper.text()).toContain('Collection complete')
  })
})

describe('collection history', () => {
  it('renders loading, empty, error with retry, succeeded and failed states', async () => {
    const wrapper = mount(CollectionHistory, {
      props: { runs: [], busy: true, tracking: false, error: '' },
      global: { plugins: [i18n] },
    })
    expect(wrapper.get('[role="status"]').text()).toContain('Carregando')

    await wrapper.setProps({ busy: false })
    expect(wrapper.get('[role="status"]').text()).toContain('Nenhuma coleta recente')

    await wrapper.setProps({ error: 'Falha no histórico' })
    expect(wrapper.get('[role="alert"]').text()).toContain('Falha no histórico')
    await wrapper.get('[role="alert"] button').trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)

    await wrapper.setProps({
      error: '',
      tracking: true,
      runs: [
        { id: 1, source: 'RSS', status: 'SUCCEEDED', input: 'https://example.org/feed.xml', fetched: null as never, inserted: null as never, duplicates: null as never, errorMessage: null },
        { id: 2, source: 'WORDPRESS', status: 'FAILED', fetched: null, inserted: null, duplicates: null, errorMessage: 'hidden' },
      ],
    })
    expect(wrapper.text()).toContain('Concluída')
    expect(wrapper.text()).toContain('https://example.org/feed.xml')
    expect(wrapper.text()).toContain('Entrada da coleta')
    expect(wrapper.text()).toContain('Falhou')
    expect(wrapper.text()).not.toContain('hidden')
    expect(wrapper.get('.history-tracking').text()).toContain('Acompanhando')
  })

  it('localizes source and status and lets the user resume an active run', async () => {
    const run = {
      id: 17,
      source: 'GOOGLE_NEWS' as const,
      status: 'RUNNING' as const,
      input: 'spring boot',
      createdAt: '2026-11-06T12:00:00Z',
      fetched: null,
      inserted: null,
      duplicates: null,
      errorMessage: null,
    }
    const wrapper = mount(CollectionHistory, {
      props: { runs: [run], busy: false, tracking: false, error: '' },
      global: { plugins: [i18n] },
    })
    expect(wrapper.text()).toContain('Google News')
    expect(wrapper.text()).toContain('spring boot')
    expect(wrapper.text()).toContain('Em andamento')
    await wrapper.get('button.history-resume').trigger('click')
    expect(wrapper.emitted('resume')![0]).toEqual([run])
    i18n.global.locale.value = 'en'
    await nextTick()
    expect(wrapper.text()).toContain('In progress')
    expect(wrapper.text()).toContain('Track run')
    expect(wrapper.text()).toContain('Collection input')
  })
})
