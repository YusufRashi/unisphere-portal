import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router'

/**
 * Reads a one-time token from the "?token=" query parameter, then removes it from the address bar so it
 * does not linger in browser history or get shared accidentally.
 */
export function useTokenFromUrl(): string | null {
  const [params, setParams] = useSearchParams()
  const [token] = useState(() => params.get('token'))

  useEffect(() => {
    if (params.has('token')) {
      setParams({}, { replace: true })
    }
  }, [params, setParams])

  return token
}
