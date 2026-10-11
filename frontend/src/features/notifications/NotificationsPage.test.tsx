import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import * as notificationsApi from '@/api/notifications'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import type { AppNotification, PageResponse } from '@/types/api'
import { NotificationsPage } from './NotificationsPage'

vi.mock('@/api/notifications')

const unread: AppNotification = {
  id: 3,
  type: 'FEE_REMINDER',
  title: 'Fee balance reminder',
  message: 'Your fee balance is KES 12,500.00. You can pay online from the Fees page.',
  link: '/student/fees',
  read: false,
  createdAt: new Date(Date.now() - 2 * 3600_000).toISOString(),
}

const read: AppNotification = {
  id: 2,
  type: 'REGISTRATION_APPROVED',
  title: 'Registration confirmed',
  message: 'Your registration for Semester 1 is confirmed. Your timetable is ready.',
  link: null,
  read: true,
  createdAt: '2026-01-05T08:00:00Z',
}

function page(content: AppNotification[]): PageResponse<AppNotification> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 }
}

function renderPage(path = '/notifications') {
  return renderWithProviders([{ path: '/notifications', element: <NotificationsPage /> }], {
    auth: makeAuth({ user: makeUser(['STUDENT']) }),
    initialPath: path,
  })
}

describe('NotificationsPage', () => {
  it('lists notifications with their state and actions', async () => {
    vi.mocked(notificationsApi.getNotifications).mockResolvedValue(page([unread, read]))
    renderPage()

    const list = await screen.findByRole('list', { name: 'Notifications' })
    const [first, second] = within(list).getAllByRole('listitem') as [HTMLElement, HTMLElement]
    expect(within(first).getByText('Unread:', { exact: false })).toBeInTheDocument()
    expect(within(first).getByText('2 hours ago')).toBeInTheDocument()
    expect(within(first).getByRole('link', { name: 'Open: Fee balance reminder' })).toHaveAttribute('href', '/student/fees')
    expect(within(second).queryByRole('button', { name: /Mark as read/ })).not.toBeInTheDocument()
    expect(within(second).queryByRole('link')).not.toBeInTheDocument()
    expect(notificationsApi.getNotifications).toHaveBeenCalledWith({ unreadOnly: false, page: 0, size: 20 })
  })

  it('filters to unread and marks one read', async () => {
    const user = userEvent.setup()
    vi.mocked(notificationsApi.getNotifications).mockResolvedValue(page([unread]))
    vi.mocked(notificationsApi.markNotificationRead).mockResolvedValue()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'Unread' }))
    expect(screen.getByRole('button', { name: 'Unread' })).toHaveAttribute('aria-pressed', 'true')
    expect(notificationsApi.getNotifications).toHaveBeenLastCalledWith({ unreadOnly: true, page: 0, size: 20 })

    await user.click(await screen.findByRole('button', { name: 'Mark as read: Fee balance reminder' }))
    expect(vi.mocked(notificationsApi.markNotificationRead).mock.calls[0]?.[0]).toBe(3)
  })

  it('shows an empty state for the unread filter', async () => {
    vi.mocked(notificationsApi.getNotifications).mockResolvedValue(page([]))
    renderPage('/notifications?filter=unread')

    expect(await screen.findByText('No unread notifications')).toBeInTheDocument()
    expect(screen.getByText("You're all caught up.")).toBeInTheDocument()
  })
})
