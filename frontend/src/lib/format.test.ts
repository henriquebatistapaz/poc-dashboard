import { describe, expect, it } from 'vitest'
import { displayDate, safeLink, sourceLabel } from './format'
import { i18n } from '../i18n'

describe('untrusted content formatting', () => {
  it('rejects script URLs, data URLs and URL credentials', () => {
    for (const url of ['javascript:alert(1)', 'data:text/html,test', 'https://user:password@example.org', 'invalid']) expect(safeLink(url)).toBeNull()
    expect(safeLink('https://example.org/post')).toBe('https://example.org/post')
  })
  it('formats dates and fallback labels for the active locale', () => {
    i18n.global.locale.value = 'es'
    expect(displayDate(null)).toBe('No indicada')
    expect(sourceLabel('alpha')).toContain('prueba')
    expect(sourceLabel('GOOGLE_NEWS')).toBe('Google News')
    i18n.global.locale.value = 'en'
    expect(displayDate('invalid')).toBe('Not provided')
    expect(sourceLabel('unexpected')).toBe('unexpected')
  })
})
