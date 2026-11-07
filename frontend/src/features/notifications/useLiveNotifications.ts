import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { listenToNotifications } from '@/api/notificationStream'
import { NOTIFICATIONS_KEY } from './useNotificationActions'

/**
 * Keeps the unread badge current as notifications arrive: the server pushes the new count, which goes straight
 * into the cache, and open notification lists are refreshed. Mounted once, in the header bell.
 */
export function useLiveNotifications() {
  const queryClient = useQueryClient()
  useEffect(() => {
    const controller = new AbortController()
    void listenToNotifications(({ event, data }) => {
      if (event !== 'unread') return
      const { count } = JSON.parse(data) as { count: number }
      queryClient.setQueryData([...NOTIFICATIONS_KEY, 'unread-count'], count)
      void queryClient.invalidateQueries({ queryKey: [...NOTIFICATIONS_KEY, 'list'] })
    }, controller.signal)
    return () => controller.abort()
  }, [queryClient])
}
