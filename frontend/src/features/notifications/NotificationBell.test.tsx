import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import * as notificationsApi from '@/api/notifications'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import type { AppNotification, PageResponse } from '@/types/api'
import { NotificationBell } from './NotificationBell'

vi.mock('@/api/notifications')

function notification(id: number, overrides: Partial<AppNotification> = {}): AppNotification {
  return {
    id,
    type: 'RESULTS_RELEASED',
    title: 'Results released: CS101',
    message: 'Your final result for CS101 Programming I (Semester 1) is now available.',
    link: '/student/results',
    read: false,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

function page(content: AppNotification[]): PageResponse<AppNotification> {
  return { content, page: 0, size: 6, totalElements: content.length, totalPages: 1 }
}

function renderBell() {
  return renderWithProviders(
    [
      { path: '/dashboard', element: <NotificationBell /> },
      { path: '/student/results', element: <p>Results page</p> },
      { path: '/notifications', element: <p>Inbox page</p> },
    ],
    { auth: makeAuth({ user: makeUser(['STUDENT']) }), initialPath: '/dashboard' },
  )
}

describe('NotificationBell', () => {
  it('shows the unread count and loads the latest notifications when opened', async () => {
    const user = userEvent.setup()
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue(12)
    vi.mocked(notificationsApi.getNotifications).mockResolvedValue(page([notification(1), notification(2, { read: true, title: 'Registration confirmed' })]))
    renderBell()

    const bell = await screen.findByRole('button', { name: 'Notifications, 12 unread' })
    expect(within(bell).getByText('9+')).toBeInTheDocument()
    expect(notificationsApi.getNotifications).not.toHaveBeenCalled()

    await user.click(bell)
    const panel = screen.getByRole('region', { name: 'Recent notifications' })
    expect(await within(panel).findByText('Results released: CS101')).toBeInTheDocument()
    expect(within(panel).getByText('Unread:', { exact: false })).toBeInTheDocument()
    expect(notificationsApi.getNotifications).toHaveBeenCalledWith({ size: 6 })

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('region', { name: 'Recent notifications' })).not.toBeInTheDocument()
    expect(bell).toHaveFocus()
  })

  it('marks a notification read and follows its link', async () => {
    const user = userEvent.setup()
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue(1)
    vi.mocked(notificationsApi.getNotifications).mockResolvedValue(page([notification(7)]))
    vi.mocked(notificationsApi.markNotificationRead).mockResolvedValue()
    renderBell()

    await user.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }))
    await user.click(await screen.findByRole('button', { name: /Results released: CS101/ }))

    expect(await screen.findByText('Results page')).toBeInTheDocument()
    expect(vi.mocked(notificationsApi.markNotificationRead).mock.calls[0]?.[0]).toBe(7)
  })

  it('marks everything read', async () => {
    const user = userEvent.setup()
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValueOnce(2).mockResolvedValue(0)
    vi.mocked(notificationsApi.getNotifications).mockResolvedValue(page([notification(1), notification(2)]))
    vi.mocked(notificationsApi.markAllNotificationsRead).mockResolvedValue()
    renderBell()

    await user.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }))
    await user.click(screen.getByRole('button', { name: 'Mark all as read' }))

    expect(notificationsApi.markAllNotificationsRead).toHaveBeenCalled()
    expect(await screen.findByRole('button', { name: 'Notifications' })).toBeInTheDocument()
  })

  it('says so when there is nothing yet and links to the inbox', async () => {
    const user = userEvent.setup()
    vi.mocked(notificationsApi.getUnreadCount).mockResolvedValue(0)
    vi.mocked(notificationsApi.getNotifications).mockResolvedValue(page([]))
    renderBell()

    await user.click(await screen.findByRole('button', { name: 'Notifications' }))
    expect(await screen.findByText('You have no notifications yet.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mark all as read' })).toBeDisabled()

    await user.click(screen.getByRole('link', { name: 'View all notifications' }))
    expect(await screen.findByText('Inbox page')).toBeInTheDocument()
  })
})
