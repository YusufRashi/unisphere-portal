import { Search } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '@/auth/authContext'
import { Modal } from '@/components/ui/Modal'
import { cn } from '@/lib/cn'
import { navigationFor } from './navigation'

const isMac = typeof navigator !== 'undefined' && /mac/i.test(navigator.platform)

/**
 * "Go to…" for every screen the user can open, by typing part of its name. Ctrl+K (⌘K on a Mac) opens it from
 * anywhere. A combobox: arrow keys move through the results, Enter opens the highlighted one.
 */
export function QuickSearch() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const listId = useId()

  const items = useMemo(
    () =>
      navigationFor(user?.roles ?? []).flatMap((section) =>
        section.items.map((item) => ({ ...item, section: section.title ?? 'General' })),
      ),
    [user],
  )
  const q = query.trim().toLowerCase()
  const results = q ? items.filter((i) => i.label.toLowerCase().includes(q) || i.section.toLowerCase().includes(q)) : items

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setOpen(true)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  function close() {
    setOpen(false)
    setQuery('')
    setActive(0)
  }

  function go(index: number) {
    const target = results[index]
    if (!target) return
    close()
    navigate(target.to)
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-keyshortcuts={isMac ? 'Meta+K' : 'Control+K'}
        className="flex h-10 w-full max-w-md items-center gap-2 rounded-xl border border-neutral-200 bg-surface px-3 text-sm text-neutral-500 shadow-xs hover:border-neutral-300"
      >
        <Search aria-hidden="true" className="size-4" />
        <span className="flex-1 text-left">Search pages…</span>
        <kbd className="rounded-lg border border-neutral-200 bg-neutral-50 px-1.5 py-0.5 font-mono text-[10px] text-neutral-500">
          {isMac ? '⌘K' : 'Ctrl K'}
        </kbd>
      </button>

      <Modal open={open} title="Go to a page" onClose={close} size="sm" initialFocusRef={inputRef}>
        <div className="pb-3">
          <input
            ref={inputRef}
            role="combobox"
            aria-expanded="true"
            aria-controls={listId}
            aria-activedescendant={results[active] ? `${listId}-${active}` : undefined}
            aria-label="Page name"
            placeholder="Type to search…"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              setActive(0)
            }}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') {
                e.preventDefault()
                setActive((a) => Math.min(a + 1, results.length - 1))
              } else if (e.key === 'ArrowUp') {
                e.preventDefault()
                setActive((a) => Math.max(a - 1, 0))
              } else if (e.key === 'Enter') {
                e.preventDefault()
                go(active)
              }
            }}
            className="block h-10 w-full rounded-lg border border-neutral-200 bg-surface px-3 text-sm text-neutral-900 placeholder:text-neutral-400 focus:border-brand-500 focus:ring-3 focus:ring-brand-500/20 focus:outline-none"
          />
          <ul id={listId} role="listbox" aria-label="Pages" className="mt-3 max-h-72 space-y-0.5 overflow-y-auto">
            {results.length === 0 && <li className="px-3 py-6 text-center text-sm text-neutral-500">No page matches “{query}”.</li>}
            {results.map((r, i) => {
              const Icon = r.icon
              return (
                <li
                  key={r.to}
                  id={`${listId}-${i}`}
                  role="option"
                  aria-selected={i === active}
                  onMouseEnter={() => setActive(i)}
                  onClick={() => go(i)}
                  className={cn('flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-sm', i === active ? 'bg-neutral-100 text-neutral-900' : 'text-neutral-700')}
                >
                  <Icon aria-hidden="true" className="size-4 text-neutral-500" />
                  <span className="flex-1">{r.label}</span>
                  <span className="text-xs text-neutral-400">{r.section}</span>
                </li>
              )
            })}
          </ul>
        </div>
      </Modal>
    </>
  )
}
