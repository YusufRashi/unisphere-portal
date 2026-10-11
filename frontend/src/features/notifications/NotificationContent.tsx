import {
  CalendarClock,
  CircleCheck,
  CircleX,
  ClipboardList,
  GraduationCap,
  Megaphone,
  Send,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { cn } from '@/lib/cn'
import { formatDateTime, timeAgo } from '@/lib/format'
import type { AppNotification, NotificationType } from '@/types/api'

const ICONS: Record<NotificationType, { icon: LucideIcon; className: string }> = {
  REGISTRATION_OPENED: { icon: ClipboardList, className: 'bg-brand-50 text-brand-700' },
  REGISTRATION_DEADLINE: { icon: CalendarClock, className: 'bg-amber-50 text-amber-700' },
  REGISTRATION_SUBMITTED: { icon: Send, className: 'bg-brand-50 text-brand-700' },
  REGISTRATION_APPROVED: { icon: CircleCheck, className: 'bg-emerald-50 text-emerald-700' },
  REGISTRATION_REJECTED: { icon: CircleX, className: 'bg-red-50 text-red-700' },
  RESULTS_RELEASED: { icon: GraduationCap, className: 'bg-emerald-50 text-emerald-700' },
  FEE_REMINDER: { icon: Wallet, className: 'bg-amber-50 text-amber-700' },
  ANNOUNCEMENT: { icon: Megaphone, className: 'bg-brand-50 text-brand-700' },
}

/** Icon, title, message and time of one notification; unread ones are bold with a dot. */
export function NotificationContent({ notification: n, clampMessage = false }: { notification: AppNotification; clampMessage?: boolean }) {
  const { icon: Icon, className } = ICONS[n.type]
  return (
    <div className="flex min-w-0 gap-3">
      <span aria-hidden="true" className={cn('mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full', className)}>
        <Icon className="size-4" />
      </span>
      <div className="min-w-0 flex-1">
        <p className={cn('text-sm text-slate-900', !n.read && 'font-semibold')}>
          {!n.read && <span className="sr-only">Unread: </span>}
          {n.title}
        </p>
        <p className={cn('mt-0.5 text-sm break-words text-slate-600', clampMessage && 'line-clamp-2')}>{n.message}</p>
        <p className="mt-1 text-xs text-slate-500">
          <time dateTime={n.createdAt} title={formatDateTime(n.createdAt)}>
            {timeAgo(n.createdAt)}
          </time>
        </p>
      </div>
      {!n.read && <span aria-hidden="true" className="mt-2 size-2 shrink-0 rounded-full bg-brand-600" />}
    </div>
  )
}
