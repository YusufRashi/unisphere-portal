import { useQuery } from '@tanstack/react-query'
import { BellOff } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { errorMessage } from '@/api/errors'
import { getNotifications } from '@/api/notifications'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { Pagination } from '@/components/ui/Pagination'
import { Spinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/cn'
import { NotificationContent } from './NotificationContent'
import { NOTIFICATIONS_KEY, useMarkAllRead, useMarkRead } from './useNotificationActions'

const PAGE_SIZE = 20

/** The user's inbox: every notification, or only the unread ones, newest first. */
export function NotificationsPage() {
  const [params, setParams] = useSearchParams()
  const unreadOnly = params.get('filter') === 'unread'
  const page = Math.max(Number(params.get('page')) || 0, 0)
  const query = useQuery({
    queryKey: [...NOTIFICATIONS_KEY, 'list', { unreadOnly, page, size: PAGE_SIZE }],
    queryFn: () => getNotifications({ unreadOnly, page, size: PAGE_SIZE }),
  })
  const markRead = useMarkRead()
  const markAllRead = useMarkAllRead()
  const hasUnread = query.data?.content.some((n) => !n.read) ?? false

  function setFilter(unread: boolean) {
    setParams(unread ? { filter: 'unread' } : {}, { replace: true })
  }

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Updates about your registration, results, fees and courses."
        actions={
          <Button variant="secondary" onClick={() => markAllRead.mutate()} loading={markAllRead.isPending} disabled={!hasUnread && !unreadOnly}>
            Mark all as read
          </Button>
        }
      />
      <div role="group" aria-label="Show" className="mb-4 inline-flex rounded-md border border-neutral-300 bg-surface p-0.5">
        {[false, true].map((unread) => (
          <button
            key={String(unread)}
            type="button"
            aria-pressed={unreadOnly === unread}
            onClick={() => setFilter(unread)}
            className={cn(
              'rounded px-3 py-1.5 text-sm font-medium',
              unreadOnly === unread ? 'bg-brand-800 text-white' : 'text-neutral-700 hover:bg-neutral-100',
            )}
          >
            {unread ? 'Unread' : 'All'}
          </button>
        ))}
      </div>

      {query.error ? (
        <Alert tone="error">{errorMessage(query.error)}</Alert>
      ) : !query.data ? (
        <div className="flex justify-center p-12 text-brand-700">
          <Spinner label="Loading notifications…" />
        </div>
      ) : query.data.content.length === 0 ? (
        <Card>
          <EmptyState
            icon={BellOff}
            title={unreadOnly ? 'No unread notifications' : 'No notifications yet'}
            description={unreadOnly ? "You're all caught up." : 'Updates about your registration, results and fees will appear here.'}
          />
        </Card>
      ) : (
        <Card>
          <ul aria-label="Notifications" className="divide-y divide-neutral-100">
            {query.data.content.map((n) => (
              <li key={n.id} className={cn('flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-start', !n.read && 'bg-brand-50/40')}>
                <div className="min-w-0 flex-1">
                  <NotificationContent notification={n} />
                </div>
                <div className="flex shrink-0 gap-2 pl-11 sm:pl-0">
                  {n.link && (
                    <Link
                      to={n.link}
                      onClick={() => !n.read && markRead.mutate(n.id)}
                      className="inline-flex h-8 items-center rounded-md border border-neutral-300 bg-surface px-3 text-xs font-medium text-neutral-800 hover:bg-neutral-50"
                    >
                      Open<span className="sr-only">: {n.title}</span>
                    </Link>
                  )}
                  {!n.read && (
                    <Button variant="ghost" size="sm" onClick={() => markRead.mutate(n.id)} disabled={markRead.isPending}>
                      Mark as read<span className="sr-only">: {n.title}</span>
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
          <Pagination
            page={query.data.page}
            totalPages={query.data.totalPages}
            totalElements={query.data.totalElements}
            size={query.data.size}
            onPageChange={(p) => setParams({ ...(unreadOnly ? { filter: 'unread' } : {}), page: String(p) })}
          />
        </Card>
      )}
    </>
  )
}
