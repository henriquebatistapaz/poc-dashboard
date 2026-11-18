import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { i18n } from './i18n'
import {
  THEME_STORAGE_KEY,
  applyResolvedTheme,
  attachSystemThemeListener,
  isThemePreference,
  readStoredTheme,
  readSystemDark,
  resolvedTheme,
  setThemePreference,
  syncThemeFromEnvironment,
  systemPrefersDark,
  themePreference,
} from './theme'
import ThemePicker from './components/ThemePicker.vue'

describe('theme management', () => {
  beforeEach(() => {
    localStorage.clear()
    delete document.documentElement.dataset.theme
    themePreference.value = 'auto'
    systemPrefersDark.value = false
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('validates supported preferences and defaults to auto when storage is empty or invalid', () => {
    expect(isThemePreference('light')).toBe(true)
    expect(isThemePreference('dark')).toBe(true)
    expect(isThemePreference('auto')).toBe(true)
    expect(isThemePreference('neon')).toBe(false)

    expect(readStoredTheme()).toBe('auto')
    localStorage.setItem(THEME_STORAGE_KEY, 'invalid')
    expect(readStoredTheme()).toBe('auto')
    localStorage.setItem(THEME_STORAGE_KEY, 'dark')
    expect(readStoredTheme()).toBe('dark')
  })

  it('falls back to auto in memory when localStorage throws', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(readStoredTheme()).toBe('auto')
    setThemePreference('dark')
    await nextTick()
    expect(resolvedTheme.value).toBe('dark')
    setThemePreference('invalid' as never)
    await nextTick()
    expect(themePreference.value).toBe('auto')
  })

  it('reacts to system changes only when preference is auto and updates document attributes', async () => {
    let listener: ((event: MediaQueryListEvent) => void) | undefined
    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockImplementation(() => ({
        matches: true,
        addEventListener: (_type: string, cb: (event: MediaQueryListEvent) => void) => {
          listener = cb
        },
        removeEventListener: vi.fn(),
      })),
    )

    const detach = attachSystemThemeListener()
    expect(readSystemDark()).toBe(true)
    syncThemeFromEnvironment()
    await nextTick()
    expect(resolvedTheme.value).toBe('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')

    listener?.({ matches: false } as MediaQueryListEvent)
    await nextTick()
    expect(resolvedTheme.value).toBe('light')
    expect(document.documentElement.dataset.theme).toBe('light')

    setThemePreference('dark')
    await nextTick()
    listener?.({ matches: false } as MediaQueryListEvent)
    await nextTick()
    expect(resolvedTheme.value).toBe('dark')
    detach()
  })

  it('handles missing matchMedia and updates theme-color meta when present', () => {
    vi.stubGlobal('matchMedia', undefined)
    expect(readSystemDark()).toBe(false)
    const detach = attachSystemThemeListener()
    detach()

    const meta = document.createElement('meta')
    meta.setAttribute('name', 'theme-color')
    document.head.appendChild(meta)
    applyResolvedTheme('dark')
    expect(meta.getAttribute('content')).toBe('#0f172a')
    applyResolvedTheme('light')
    expect(meta.getAttribute('content')).toBe('#2463eb')
    meta.remove()
  })

  it('renders ThemePicker and switches themes cleanly', async () => {
    i18n.global.locale.value = 'pt-BR'
    const wrapper = mount(ThemePicker, { global: { plugins: [i18n] } })
    const select = wrapper.get('select')
    await select.setValue('light')
    expect(themePreference.value).toBe('light')
    await select.setValue('dark')
    expect(themePreference.value).toBe('dark')
    await select.setValue('auto')
    expect(themePreference.value).toBe('auto')
  })
})
