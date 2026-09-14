import { useEffect, useState } from 'react'

type Theme = 'light' | 'dark'

/**
 * Theme, persisted and system-aware.
 *
 * Reads the stored choice first and falls back to the OS preference, so a user
 * who keeps their machine in dark mode gets dark mode without being asked.
 *
 * The initial value is computed synchronously inside useState rather than in an
 * effect. Deciding the theme after first paint produces a white flash before the
 * dark theme applies, which is the single most noticeable way a dark mode
 * implementation looks unfinished.
 */
export function useTheme() {
  const [theme, setTheme] = useState<Theme>(() => {
    const stored = localStorage.getItem('theme') as Theme | null
    if (stored) return stored
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  })

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    localStorage.setItem('theme', theme)
  }, [theme])

  return { theme, toggle: () => setTheme((t) => (t === 'light' ? 'dark' : 'light')) }
}
