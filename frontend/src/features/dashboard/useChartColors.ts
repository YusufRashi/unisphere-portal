import { useMemo } from 'react'
import { useTheme } from '@/app/themeContext'

export interface ChartColors {
  series: string
  grid: string
  axis: string
  ink: string
  surface: string
  hover: string
}

function token(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

/**
 * Recharts writes colours into SVG attributes, which cannot use CSS variables, so the chart reads the theme's
 * current values and reads them again whenever the theme changes. The series colour is the accent, checked for
 * contrast and colour-blind safety against the card surface in both themes.
 */
export function useChartColors(): ChartColors {
  const { resolved } = useTheme()
  return useMemo(
    () => ({
      series: token('--color-accent', '#6d47e8'),
      grid: token('--color-neutral-200', '#e4e4e7'),
      axis: token('--color-neutral-500', '#71717a'),
      ink: token('--color-neutral-900', '#18181b'),
      surface: token('--color-surface', '#ffffff'),
      hover: token('--color-neutral-100', '#f0f0f2'),
    }),
    // The theme class changes the computed values; reading them again is the point of this dependency.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [resolved],
  )
}
