import { afterEach, describe, expect, it, vi } from 'vitest'
import pt from './locales/pt-BR.json'
import en from './locales/en.json'
import es from './locales/es.json'
import { i18n, isLocale } from './i18n'
import { nextTick } from 'vue'

const flatten = (object: Record<string, unknown>, prefix = ''): string[] =>
  Object.entries(object).flatMap(([key, value]) =>
    typeof value === 'object' && value ? flatten(value as Record<string, unknown>, `${prefix}${key}.`) : [`${prefix}${key}`],
  )

describe('language contract', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.resetModules()
  })

  it('keeps all three locale catalogs complete', () => {
    expect(flatten(en).sort()).toEqual(flatten(pt).sort())
    expect(flatten(es).sort()).toEqual(flatten(pt).sort())
    for (const locale of ['pt-BR', 'en', 'es'] as const) {
      i18n.global.locale.value = locale
      for (const key of flatten(pt)) expect(i18n.global.te(key, locale)).toBe(true)
    }
  })

  it('persists only the supported language preference and updates document language', async () => {
    i18n.global.locale.value = 'es'
    await nextTick()
    expect(document.documentElement.lang).toBe('es')
    expect(localStorage.getItem('coleta.locale')).toBe('es')
    expect(isLocale('fr')).toBe(false)
    expect(isLocale('en')).toBe(true)
  })

  it('handles stored locale, navigator language fallback, and storage errors on startup', async () => {
    localStorage.setItem('coleta.locale', 'en')
    vi.resetModules()
    const modStored = await import('./i18n')
    expect(modStored.i18n.global.locale.value).toBe('en')

    localStorage.clear()
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('es-AR')
    vi.resetModules()
    const modEs = await import('./i18n')
    expect(modEs.i18n.global.locale.value).toBe('es')

    localStorage.clear()
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('en-US')
    vi.resetModules()
    const modEn = await import('./i18n')
    expect(modEn.i18n.global.locale.value).toBe('en')

    localStorage.clear()
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied')
    })
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('pt-BR')
    vi.resetModules()
    const modErr = await import('./i18n')
    expect(modErr.i18n.global.locale.value).toBe('pt-BR')
  })
})
