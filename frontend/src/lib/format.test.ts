import { describe, expect, it } from 'vitest'
import { displayDate, hostLabel, safeLink, sourceLabel } from './format'
import { i18n } from '../i18n'

describe('untrusted content formatting', () => {
  it('rejects script URLs, data URLs and URL credentials', () => {
    for (const url of ['javascript:alert(1)', 'data:text/html,test', 'https://user:password@example.org', 'invalid']) {
      expect(safeLink(url)).toBeNull()
    }
    expect(safeLink('https://example.org/post')).toBe('https://example.org/post')
    expect(safeLink('http://example.org/post')).toBe('http://example.org/post')
  })

  it('formats hosts, dates and fallback labels for the active locale', () => {
    i18n.global.locale.value = 'es'
    expect(displayDate(null)).toBe('No indicada')
    expect(displayDate('2026-11-17T12:00:00Z')).toContain('26')
    expect(sourceLabel('alpha')).toContain('prueba')
    expect(sourceLabel('GOOGLE_NEWS')).toBe('Google News')
    expect(hostLabel('https://www.example.org/item')).toBe('example.org')
    expect(hostLabel('javascript:alert(1)')).toBe('Enlace no disponible')
    i18n.global.locale.value = 'en'
    expect(displayDate('invalid')).toBe('Not provided')
    expect(sourceLabel('unexpected')).toBe('unexpected')
  })
})
