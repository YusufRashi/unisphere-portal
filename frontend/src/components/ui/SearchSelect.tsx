import { useQuery } from '@tanstack/react-query'
import { ChevronsUpDown, X } from 'lucide-react'
import { useId, useState, type KeyboardEvent } from 'react'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { cn } from '@/lib/cn'

export interface SelectOption {
  value: number
  label: string
  description?: string
}

export interface SearchSelectProps {
  label: string
  /** Distinguishes the cache of different selects. */
  queryKey: string
  loadOptions: (query: string) => Promise<SelectOption[]>
  value: SelectOption | null
  onChange: (option: SelectOption | null) => void
  placeholder?: string
  error?: string
  hint?: string
  disabled?: boolean
  /** Option values that must not be offered (e.g. already selected prerequisites). */
  exclude?: number[]
  /** Keep the input empty after choosing (for "add to list" usage). */
  clearOnSelect?: boolean
}

/**
 * Accessible async combobox (WAI-ARIA combobox pattern): type to search the server, arrow keys to move,
 * Enter to choose, Escape to close.
 */
export function SearchSelect({
  label,
  queryKey,
  loadOptions,
  value,
  onChange,
  placeholder = 'Type to search…',
  error,
  hint,
  disabled,
  exclude = [],
  clearOnSelect = false,
}: SearchSelectProps) {
  const id = useId()
  const listId = `${id}-list`
  const [open, setOpen] = useState(false)
  const [input, setInput] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const query = useDebouncedValue(input.trim(), 250)

  const { data, isFetching } = useQuery({
    queryKey: ['search-select', queryKey, query],
    queryFn: () => loadOptions(query),
    enabled: open && !disabled,
    staleTime: 30_000,
  })
  const options = (data ?? []).filter((o) => !exclude.includes(o.value))

  function choose(option: SelectOption) {
    onChange(option)
    setInput('')
    setOpen(false)
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
      setActiveIndex((i) => Math.min(i + 1, Math.max(options.length - 1, 0)))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((i) => Math.max(i - 1, 0))
    } else if (event.key === 'Enter' && open) {
      event.preventDefault()
      const option = options[activeIndex]
      if (option) choose(option)
    } else if (event.key === 'Escape' && open) {
      event.preventDefault()
      event.stopPropagation()
      setOpen(false)
      setInput('')
    }
  }

  const showValue = !open && value && !clearOnSelect
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined

  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={open && options[activeIndex] ? `${id}-opt-${options[activeIndex].value}` : undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          autoComplete="off"
          disabled={disabled}
          placeholder={value && !clearOnSelect ? value.label : placeholder}
          value={showValue ? value.label : input}
          onChange={(e) => {
            setInput(e.target.value)
            setOpen(true)
            setActiveIndex(0)
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => {
            setOpen(false)
            setInput('')
          }}
          onKeyDown={onKeyDown}
          className={cn(
            'block h-9 w-full rounded-md border bg-white pr-14 pl-3 text-sm shadow-xs placeholder:text-slate-400',
            'focus:border-brand-600 focus:ring-2 focus:ring-brand-600/20 focus:outline-none disabled:bg-slate-50 disabled:text-slate-500',
            error ? 'border-red-500' : 'border-slate-300',
          )}
        />
        <div className="absolute inset-y-0 right-2 flex items-center gap-1 text-slate-400">
          {value && !disabled && !clearOnSelect && (
            <button
              type="button"
              aria-label={`Clear ${label}`}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => onChange(null)}
              className="rounded p-0.5 hover:bg-slate-100 hover:text-slate-600"
            >
              <X aria-hidden="true" className="size-3.5" />
            </button>
          )}
          <ChevronsUpDown aria-hidden="true" className="size-4" />
        </div>
        {open && (
          <ul
            id={listId}
            role="listbox"
            aria-label={label}
            className="absolute z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-md border border-slate-200 bg-white py-1 text-sm shadow-lg"
          >
            {options.length === 0 ? (
              <li className="px-3 py-2 text-slate-500" role="presentation">
                {isFetching ? 'Searching…' : 'No matches'}
              </li>
            ) : (
              options.map((option, index) => (
                <li
                  key={option.value}
                  id={`${id}-opt-${option.value}`}
                  role="option"
                  aria-selected={value?.value === option.value}
                  onMouseDown={(e) => e.preventDefault()}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => choose(option)}
                  className={cn('cursor-pointer px-3 py-2', index === activeIndex && 'bg-brand-50')}
                >
                  <p className="font-medium text-slate-900">{option.label}</p>
                  {option.description && <p className="text-xs text-slate-500">{option.description}</p>}
                </li>
              ))
            )}
          </ul>
        )}
      </div>
      {hint && !error && (
        <p id={`${id}-hint`} className="text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-xs font-medium text-red-700">
          {error}
        </p>
      )}
    </div>
  )
}
