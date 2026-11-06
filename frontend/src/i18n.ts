import { createI18n } from 'vue-i18n'
import { watch } from 'vue'
import pt from './locales/pt-BR.json'
import en from './locales/en.json'
import es from './locales/es.json'

export const locales = ['pt-BR', 'en', 'es'] as const
export type Locale = typeof locales[number]
export function isLocale(value: unknown): value is Locale { return locales.includes(value as Locale) }
function initialLocale(): Locale {
  try {
    const stored = localStorage.getItem('coleta.locale')
    if (isLocale(stored)) return stored
  } catch { /* Preference storage is optional. */ }
  return navigator.language.startsWith('es') ? 'es' : navigator.language.startsWith('en') ? 'en' : 'pt-BR'
}
export const i18n = createI18n({ legacy: false, locale: initialLocale(), fallbackLocale: 'pt-BR',
  messages: { 'pt-BR': pt, en, es }, missingWarn: false, fallbackWarn: false })
watch(i18n.global.locale, (locale) => {
  document.documentElement.lang = locale
  try { localStorage.setItem('coleta.locale', locale) } catch { /* Do not block the UI if storage is unavailable. */ }
}, { immediate: true })
