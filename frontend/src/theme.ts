import { computed, ref, watch } from 'vue'

export const THEME_STORAGE_KEY = 'poc-dashboard:theme'
export const themes = ['light', 'dark', 'auto'] as const
export type ThemePreference = typeof themes[number]
export type ResolvedTheme = 'light' | 'dark'

export function isThemePreference(value: unknown): value is ThemePreference {
  return themes.includes(value as ThemePreference)
}

export function readStoredTheme(): ThemePreference {
  try {
    const stored = localStorage.getItem(THEME_STORAGE_KEY)
    return isThemePreference(stored) ? stored : 'auto'
  } catch {
    return 'auto'
  }
}

export function readSystemDark(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export const themePreference = ref<ThemePreference>(readStoredTheme())
export const systemPrefersDark = ref<boolean>(readSystemDark())

export const resolvedTheme = computed<ResolvedTheme>(() =>
  themePreference.value === 'auto' ? (systemPrefersDark.value ? 'dark' : 'light') : themePreference.value,
)

export function applyResolvedTheme(resolved: ResolvedTheme): void {
  if (typeof document === 'undefined') return
  document.documentElement.dataset.theme = resolved
  document.documentElement.style.colorScheme = resolved
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) {
    meta.setAttribute('content', resolved === 'dark' ? '#0f172a' : '#2463eb')
  }
}

export function setThemePreference(value: ThemePreference): void {
  themePreference.value = isThemePreference(value) ? value : 'auto'
}

export function syncThemeFromEnvironment(): void {
  themePreference.value = readStoredTheme()
  systemPrefersDark.value = readSystemDark()
  applyResolvedTheme(resolvedTheme.value)
}

watch(themePreference, (preference) => {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, preference)
  } catch {
    /* Preference storage is optional. */
  }
})

watch(
  resolvedTheme,
  (resolved) => {
    applyResolvedTheme(resolved)
  },
  { immediate: true },
)

export function attachSystemThemeListener(): () => void {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return () => {}
  }
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  const handleChange = (event: MediaQueryListEvent) => {
    systemPrefersDark.value = event.matches
  }
  media.addEventListener('change', handleChange)
  return () => media.removeEventListener('change', handleChange)
}

attachSystemThemeListener()
