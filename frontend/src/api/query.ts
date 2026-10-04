/** Removes undefined/empty values so they are not sent as "?role=" query parameters. */
export function cleanParams<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  ) as Partial<T>
}

export interface ListParams {
  q?: string
  page?: number
  size?: number
  sort?: string
}
