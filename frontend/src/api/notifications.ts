import { api } from './client'
import { cleanParams } from './query'
import type { Announcement, AnnouncementPublished, AppNotification, PageResponse } from '@/types/api'

// ---- Notifications ----

export interface NotificationParams {
  unreadOnly?: boolean
  page?: number
  size?: number
}

export async function getNotifications(params: NotificationParams = {}): Promise<PageResponse<AppNotification>> {
  return (await api.get<PageResponse<AppNotification>>('/notifications', { params: cleanParams(params) })).data
}

export async function getUnreadCount(): Promise<number> {
  return (await api.get<{ count: number }>('/notifications/unread-count')).data.count
}

export async function markNotificationRead(id: number): Promise<void> {
  await api.post(`/notifications/${id}/read`)
}

export async function markAllNotificationsRead(): Promise<void> {
  await api.post('/notifications/read-all')
}

// ---- Announcements ----

export async function getAnnouncements(params: { offeringId?: number; page?: number; size?: number } = {}): Promise<PageResponse<Announcement>> {
  return (await api.get<PageResponse<Announcement>>('/announcements', { params: cleanParams(params) })).data
}

export async function publishAnnouncement(offeringId: number, input: { title: string; body: string }): Promise<AnnouncementPublished> {
  return (await api.post<AnnouncementPublished>(`/lecturer/offerings/${offeringId}/announcements`, input)).data
}
