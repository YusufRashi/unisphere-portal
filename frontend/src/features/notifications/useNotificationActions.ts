import { useMutation, useQueryClient } from '@tanstack/react-query'
import { markAllNotificationsRead, markNotificationRead } from '@/api/notifications'

/** Prefix of every notification query (badge count and lists), so one invalidation refreshes them all. */
export const NOTIFICATIONS_KEY = ['notifications'] as const

/** Marks one notification read and refreshes the badge and every list. */
export function useMarkRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: markNotificationRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY }),
  })
}

export function useMarkAllRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY }),
  })
}
