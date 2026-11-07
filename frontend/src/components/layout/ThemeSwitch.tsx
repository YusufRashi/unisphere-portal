import { Monitor, Moon, Sun } from 'lucide-react'
import { useTheme, type ThemePreference } from '@/app/themeContext'
import { cn } from '@/lib/cn'

const OPTIONS: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'Match system', icon: Monitor },
]

/** Light, dark or the system setting, as three small toggle buttons. */
export function ThemeSwitch() {
  const { preference, setPreference } = useTheme()
  return (
    <div role="group" aria-label="Colour theme" className="inline-flex rounded-lg border border-neutral-200 bg-surface p-0.5">
      {OPTIONS.map(({ value, label, icon: Icon }) => (
        <button
          key={value}
          type="button"
          aria-pressed={preference === value}
          title={label}
          onClick={() => setPreference(value)}
          className={cn(
            'rounded-md p-1.5 transition-colors',
            preference === value ? 'bg-neutral-100 text-neutral-900' : 'text-neutral-400 hover:text-neutral-700',
          )}
        >
          <Icon aria-hidden="true" className="size-3.5" />
          <span className="sr-only">{label}</span>
        </button>
      ))}
    </div>
  )
}
