import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ItemDetails from './ItemDetails.vue'
import { i18n } from '../i18n'

describe('ItemDetails', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'pt-BR'
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.setAttribute('open', '')
    })
  })

  it('renders loading, error with retry, and fallback heading states', async () => {
    const wrapper = mount(ItemDetails, {
      props: { item: null, busy: true, error: '' },
      global: { plugins: [i18n] },
    })
    expect(wrapper.get('[role="status"]').text()).toContain('Carregando')

    await wrapper.setProps({ busy: false, error: 'Falha ao carregar' })
    expect(wrapper.get('[role="alert"]').text()).toContain('Falha ao carregar')
    await wrapper.get('[role="alert"] button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)

    await wrapper.setProps({ error: '', item: null })
    expect(wrapper.get('#detail-title').text()).toBe('Detalhes do item')
  })

  it('escapes HTML titles, blocks unsafe links and keeps safe links isolated', async () => {
    const unsafeWrapper = mount(ItemDetails, {
      props: {
        busy: false,
        error: '',
        item: {
          id: 1,
          source: 'rss',
          externalId: '<script>window.__xss=1</script>',
          title: null,
          link: 'javascript:alert(1)',
          publishedAt: null,
          collectedAt: '2026-11-17T12:00:00Z',
        },
      },
      global: { plugins: [i18n] },
    })
    expect(unsafeWrapper.get('#detail-title').text()).toBe('Sem título')
    expect(unsafeWrapper.html()).not.toContain('<script>window.__xss')
    expect(unsafeWrapper.find('a').exists()).toBe(false)
    expect(unsafeWrapper.text()).toContain('Link indisponível')

    const safeWrapper = mount(ItemDetails, {
      props: {
        busy: false,
        error: '',
        item: {
          id: 2,
          source: 'rss',
          externalId: 'mobile-fixture-1',
          title: '<img src=x onerror=window.__xss=1>',
          link: 'https://example.test/item/1',
          publishedAt: '2026-11-17T10:00:00Z',
          collectedAt: '2026-11-17T12:00:00Z',
        },
      },
      global: { plugins: [i18n] },
    })
    const links = safeWrapper.findAll('a')
    expect(links).toHaveLength(2)
    expect(links[0]!.attributes('rel')).toBe('noopener noreferrer')
    expect(links[0]!.attributes('target')).toBe('_blank')
  })

  it('handles close button, backdrop click, cancel, Escape and Tab focus trapping', async () => {
    const wrapper = mount(ItemDetails, {
      attachTo: document.body,
      props: {
        busy: false,
        error: '',
        item: {
          id: 3,
          source: 'rss',
          externalId: 'id-3',
          title: 'Item',
          link: 'https://example.test/3',
          publishedAt: null,
          collectedAt: '2026-11-17T12:00:00Z',
        },
      },
      global: { plugins: [i18n] },
    })

    const dialog = wrapper.get('dialog')
    const closeBtn = wrapper.get('button[aria-label="Fechar"]').element as HTMLElement
    const links = wrapper.findAll('a')
    const lastLink = links[links.length - 1]!.element as HTMLElement

    closeBtn.focus()
    await dialog.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(lastLink)

    await dialog.trigger('keydown', { key: 'Tab', shiftKey: false })
    expect(document.activeElement).toBe(closeBtn)

    await dialog.trigger('keydown', { key: 'ArrowDown' })
    await dialog.trigger('keydown', { key: 'Escape' })
    await dialog.trigger('cancel')
    await dialog.trigger('click')
    await wrapper.get('.detail-fields').trigger('click')
    await wrapper.get('button[aria-label="Fechar"]').trigger('click')

    const emptyFocusWrapper = mount(ItemDetails, {
      props: { item: null, busy: true, error: '' },
      global: { plugins: [i18n] },
    })
    // Disable close button temporarily or test non-tab / empty focusables
    const closeEl = emptyFocusWrapper.get('button').element as HTMLButtonElement
    closeEl.disabled = true
    await emptyFocusWrapper.get('dialog').trigger('keydown', { key: 'Tab' })

    expect(wrapper.emitted('close')).toHaveLength(4)
    wrapper.unmount()
  })
})
