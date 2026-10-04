import { useCallback, useState } from 'react'
import { useSearchParams } from 'react-router'
import { useDebouncedValue } from './useDebouncedValue'

/**
 * List-screen state (search, filters, sort, page) kept in the URL so views can be bookmarked, shared and
 * survive reloads. Changing any filter resets to the first page.
 */
export function useListParams(defaultSort: string) {
  const [params, setParams] = useSearchParams()
  const [searchInput, setSearchInputState] = useState(params.get('q') ?? '')
  const q = useDebouncedValue(searchInput.trim())

  const update = useCallback(
    (changes: Record<string, string | number | undefined>) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          if (!('page' in changes)) next.delete('page')
          Object.entries(changes).forEach(([key, value]) => {
            if (value === undefined || value === '' || (key === 'page' && value === 0)) next.delete(key)
            else next.set(key, String(value))
          })
          return next
        },
        { replace: true },
      )
    },
    [setParams],
  )

  const setSearchInput = useCallback(
    (value: string) => {
      setSearchInputState(value)
      update({ q: value.trim() })
    },
    [update],
  )

  const clear = useCallback(() => {
    setSearchInputState('')
    setParams({}, { replace: true })
  }, [setParams])

  const numberParam = (key: string) => {
    const value = params.get(key)
    return value === null || value === '' || Number.isNaN(Number(value)) ? undefined : Number(value)
  }

  return {
    q,
    searchInput,
    setSearchInput,
    sort: params.get('sort') ?? defaultSort,
    page: numberParam('page') ?? 0,
    get: (key: string) => params.get(key) ?? '',
    getNumber: numberParam,
    update,
    clear,
    hasFilters: Array.from(params.keys()).some((k) => k !== 'sort' && k !== 'page'),
  }
}
