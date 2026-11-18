import { i18n } from '../i18n'
export function safeLink(value: string): string | null {
  try {
    const url = new URL(value)
    return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password ? url.href : null
  } catch { return null }
}

export function displayDate(value: string | null): string {
  if (!value) return i18n.global.t('common.none')
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? i18n.global.t('common.none') : date.toLocaleString(i18n.global.locale.value, { dateStyle: 'short', timeStyle: 'short' })
}

const sources = ['rss', 'google-news', 'google-alerts', 'wordpress', 'alpha', 'beta']
export const sourceLabel = (source: string) => {
  const normalized = source.toLowerCase().replaceAll('_', '-')
  return sources.includes(normalized) ? i18n.global.t(`sources.${normalized}`) : source
}

export function hostLabel(value: string): string {
  const safe = safeLink(value)
  return safe ? new URL(safe).hostname.replace(/^www\./, '') : i18n.global.t('common.unavailableLink')
}
