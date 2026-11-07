import { useQuery } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { errorMessage } from '@/api/errors'
import { getNotifications, getUnreadCount } from '@/api/notifications'
import { Spinner } from '@/components/ui/Spinner'
import type { AppNotification } from '@/types/api'
import { NotificationContent } from './NotificationContent'
import { NOTIFICATIONS_KEY, useMarkAllRead, useMarkRead } from './useNotificationActions'

/** How often the unread badge is refreshed while the app is open. */
const POLL_MS = 60_000
const PREVIEW_SIZE = 6

/** Header bell: unread badge, and a panel with the latest notifications. */
export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const panelId = useId()
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const navigate = useNavigate()
  const unread = useQuery({ queryKey: [...NOTIFICATIONS_KEY, 'unread-count'], queryFn: getUnreadCount, refetchInterval: POLL_MS })
  const latest = useQuery({
    queryKey: [...NOTIFICATIONS_KEY, 'list', { size: PREVIEW_SIZE }],
    queryFn: () => getNotifications({ size: PREVIEW_SIZE }),
    enabled: open,
    staleTime: 0,
  })
  const markRead = useMarkRead()
  const markAllRead = useMarkAllRead()

  useEffect(() => {
    if (!open) return
    const onPointerDown = (e: PointerEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false)
        buttonRef.current?.focus()
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const count = unread.data ?? 0

  function openNotification(n: AppNotification) {
    if (!n.read) markRead.mutate(n.id)
    setOpen(false)
    if (n.link) navigate(n.link)
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        aria-label={count > 0 ? `Notifications, ${count} unread` : 'Notifications'}
        className="relative rounded-lg p-2 text-neutral-600 hover:bg-neutral-100 hover:text-neutral-900"
      >
        <Bell aria-hidden="true" className="size-5" />
        {count > 0 && (
          <span
            aria-hidden="true"
            className="absolute top-1 right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] leading-none font-semibold text-white"
          >
            {count > 9 ? '9+' : count}
          </span>
        )}
      </button>

      {open && (
        <div
          id={panelId}
          role="region"
          aria-label="Recent notifications"
          className="absolute right-0 z-40 mt-2 w-96 max-w-[calc(100vw-2rem)] overflow-hidden rounded-lg border border-neutral-200 bg-surface shadow-lg"
        >
          <div className="flex items-center justify-between border-b border-neutral-200 px-4 py-3">
            <p className="text-sm font-semibold text-neutral-900">Notifications</p>
            <button
              type="button"
              onClick={() => markAllRead.mutate()}
              disabled={count === 0 || markAllRead.isPending}
              className="text-xs font-medium text-brand-700 hover:text-brand-900 disabled:text-neutral-400"
            >
              Mark all as read
            </button>
          </div>
          <div className="max-h-[min(28rem,70vh)] overflow-y-auto">
            {latest.error ? (
              <p className="px-4 py-6 text-sm text-red-700">{errorMessage(latest.error)}</p>
            ) : !latest.data ? (
              <div className="flex justify-center py-8 text-brand-700">
                <Spinner label="Loading notifications…" />
              </div>
            ) : latest.data.content.length === 0 ? (
              <p className="px-4 py-8 text-center text-sm text-neutral-500">You have no notifications yet.</p>
            ) : (
              <ul className="divide-y divide-neutral-100">
                {latest.data.content.map((n) => (
                  <li key={n.id}>
                    <button
                      type="button"
                      onClick={() => openNotification(n)}
                      className="block w-full px-4 py-3 text-left hover:bg-neutral-50 focus-visible:bg-neutral-50"
                    >
                      <NotificationContent notification={n} clampMessage />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div className="border-t border-neutral-200 px-4 py-2.5 text-center">
            <Link to="/notifications" onClick={() => setOpen(false)} className="text-sm font-medium text-brand-700 hover:text-brand-900">
              View all notifications
            </Link>
          </div>
        </div>
      )}
    </div>
  )
}
