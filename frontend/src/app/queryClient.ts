import { QueryClient } from '@tanstack/react-query'
import { toApiError } from '@/api/errors'

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // Retry only transient failures; 4xx responses will not succeed on retry.
        retry: (failureCount, error) => {
          const status = toApiError(error).status
          return (status === 0 || status >= 500) && failureCount < 2
        },
      },
    },
  })
}
