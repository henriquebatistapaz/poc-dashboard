import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import LoginView from './LoginView.vue'
import { i18n } from '../i18n'

describe('LoginView', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'pt-BR'
  })

  it('validates missing credentials and whitespace in email while preserving email', async () => {
    const wrapper = mount(LoginView, {
      props: { busy: false, error: '' },
      global: { plugins: [i18n] },
    })

    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('Informe seu e-mail e sua senha')
    expect(wrapper.emitted('login')).toBeUndefined()

    await wrapper.get('#login-email').setValue('user with spaces')
    await wrapper.get('#login-password').setValue('secret')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('e-mail ou usuário')
    expect((wrapper.get('#login-email').element as HTMLInputElement).value).toBe('user with spaces')
  })

  it('emits trimmed login credentials, clears password and displays server error', async () => {
    const wrapper = mount(LoginView, {
      props: { busy: false, error: '' },
      global: { plugins: [i18n] },
    })

    await wrapper.get('#login-email').setValue('  admin@example.test  ')
    await wrapper.get('#login-password').setValue('synthetic-pw')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('login')?.[0]).toEqual(['admin@example.test', 'synthetic-pw'])
    expect((wrapper.get('#login-email').element as HTMLInputElement).value).toBe('  admin@example.test  ')
    expect((wrapper.get('#login-password').element as HTMLInputElement).value).toBe('')

    await wrapper.setProps({ busy: true, error: 'E-mail ou senha incorretos.' })
    expect(wrapper.get('#login-email').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('#login-email').attributes('aria-describedby')).toBe('login-error')
    expect(wrapper.get('[role="alert"]').text()).toContain('E-mail ou senha incorretos')
    expect(wrapper.get('button.login-submit').attributes('disabled')).toBeDefined()
  })
})
