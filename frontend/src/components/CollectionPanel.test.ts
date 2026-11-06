import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import CollectionPanel from './CollectionPanel.vue'
import { i18n } from '../i18n'

beforeEach(() => { i18n.global.locale.value = 'pt-BR' })
const render = () => mount(CollectionPanel, { props: { busy: false, result: null, error: '' }, global: { plugins: [i18n] } })
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
  it('fills real examples without starting an unsolicited collection', async () => {
    const wrapper = render()
    await wrapper.findAll('.feed-examples button')[1]!.trigger('click')
    expect((wrapper.get('#collection-input').element as HTMLInputElement).value).toBe('https://xkcd.com/atom.xml')
    expect(wrapper.emitted('collect')).toBeUndefined()
  })
  it('clears private input after success and reports duplicate-only results', async () => {
    const wrapper = render()
    await wrapper.get('#collection-source').setValue('GOOGLE_ALERTS')
    await wrapper.get('#collection-input').setValue('https://www.google.com/alerts/feeds/demo/secret')
    expect(wrapper.get('#collection-input').attributes('type')).toBe('password')
    await wrapper.setProps({ result: { id: 1, source: 'GOOGLE_ALERTS', status: 'SUCCEEDED', fetched: 2, inserted: 0, duplicates: 2, errorMessage: null } })
    expect((wrapper.get('#collection-input').element as HTMLInputElement).value).toBe('')
    expect(wrapper.get('[role="status"]').text()).toContain('Nenhuma duplicata')
  })
  it('translates existing results and labels without reloading', async () => {
    const wrapper = render()
    await wrapper.setProps({ result: { id: 2, source: 'RSS', status: 'SUCCEEDED', fetched: 2, inserted: 2, duplicates: 0, errorMessage: null } })
    i18n.global.locale.value = 'es'; await nextTick()
    expect(wrapper.text()).toContain('Recopilación completada')
    expect(wrapper.text()).toContain('Recopilar ahora')
    i18n.global.locale.value = 'en'; await nextTick()
    expect(wrapper.text()).toContain('Collection complete')
  })
})
