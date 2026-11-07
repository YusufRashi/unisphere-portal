import { createContext, use } from 'react'

export type ThemePreference = 'system' | 'light' | 'dark'

export interface ThemeApi {
  preference: ThemePreference
  /** What is showing now: the preference, with "system" resolved. */
  resolved: 'light' | 'dark'
  setPreference: (preference: ThemePreference) => void
}

export const ThemeContext = createContext<ThemeApi | null>(null)

export function useTheme(): ThemeApi {
  const ctx = use(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used inside <ThemeProvider>')
  return ctx
}

/** Same key and rules as public/theme-init.js, which applies the theme before React starts. */
export const THEME_STORAGE_KEY = 'unisphere-theme'
