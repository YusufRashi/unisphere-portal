import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import * as notificationsApi from '@/api/notifications'
import * as resultsApi from '@/api/results'
import { makeAuth, makeUser, renderWithProviders } from '@/test/utils'
import type { Announcement, Offering, PageResponse, Role, TeachingOffering } from '@/types/api'
import { AnnouncementsPage } from './AnnouncementsPage'

vi.mock('@/api/notifications')
vi.mock('@/api/results')

const announcement: Announcement = {
  id: 5,
  offeringId: 11,
  courseCode: 'CS101',
  courseTitle: 'Programming I',
  section: 'A',
  semester: { id: 1, code: '2026-S1', name: 'Semester 1' },
  title: 'Room change',
  body: 'From Monday we meet in LT2.\nBring your laptops.',
  authorName: 'Dr. Grace Wanjiru',
  createdAt: new Date().toISOString(),
}

function page(content: Announcement[]): PageResponse<Announcement> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 }
}

function teaching(id: number, code: string): TeachingOffering {
  return {
    offering: { id, section: 'A', course: { id, code, title: `${code} title` } } as unknown as Offering,
    classSize: 30,
    marksComplete: 0,
  }
}

function renderPage(roles: Role[], path = '/announcements') {
  return renderWithProviders([{ path: '/announcements', element: <AnnouncementsPage /> }], {
    auth: makeAuth({ user: makeUser(roles) }),
    initialPath: path,
  })
}

describe('AnnouncementsPage', () => {
  it('shows students the announcements of their courses', async () => {
    vi.mocked(notificationsApi.getAnnouncements).mockResolvedValue(page([announcement]))
    renderPage(['STUDENT'])

    const item = await screen.findByRole('article', { name: 'Room change' })
    expect(within(item).getByText(/From Monday we meet in LT2/)).toBeInTheDocument()
    expect(within(item).getByText(/Dr. Grace Wanjiru/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /New announcement/ })).not.toBeInTheDocument()
    expect(resultsApi.getMyTeaching).not.toHaveBeenCalled()
  })

  it('filters by course from the URL', async () => {
    vi.mocked(notificationsApi.getAnnouncements).mockResolvedValue(page([announcement]))
    renderPage(['STUDENT'], '/announcements?offeringId=11')

    expect(await screen.findByText(/Showing announcements for CS101 · Section A/)).toBeInTheDocument()
    expect(notificationsApi.getAnnouncements).toHaveBeenCalledWith({ offeringId: 11, page: 0, size: 10 })
  })

  it('lets lecturers post to one of their courses', async () => {
    const user = userEvent.setup()
    vi.mocked(notificationsApi.getAnnouncements).mockResolvedValue(page([]))
    vi.mocked(resultsApi.getMyTeaching).mockResolvedValue([teaching(11, 'CS101'), teaching(12, 'CS201')])
    vi.mocked(notificationsApi.publishAnnouncement).mockResolvedValue({ announcement, notified: 28 })
    renderPage(['LECTURER'], '/announcements?offeringId=12')

    expect(await screen.findByText('No announcements yet')).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'New announcement' }))
    const dialog = screen.getByRole('dialog', { name: 'New announcement' })
    expect(within(dialog).getByLabelText('Course')).toHaveValue('12')

    await user.click(within(dialog).getByRole('button', { name: 'Post announcement' }))
    expect(await within(dialog).findByText('Enter a title.')).toBeInTheDocument()
    expect(notificationsApi.publishAnnouncement).not.toHaveBeenCalled()

    await user.selectOptions(within(dialog).getByLabelText('Course'), '11')
    await user.type(within(dialog).getByLabelText('Title'), 'Room change')
    await user.type(within(dialog).getByLabelText('Message'), 'From Monday we meet in LT2.')
    await user.click(within(dialog).getByRole('button', { name: 'Post announcement' }))

    expect(await screen.findByText('Announcement posted to CS101. 28 students were notified.')).toBeInTheDocument()
    expect(notificationsApi.publishAnnouncement).toHaveBeenCalledWith(11, { title: 'Room change', body: 'From Monday we meet in LT2.' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
