import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, ArrowUpDown, Search, UserX, Users } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { deactivateUser, reactivateUser, searchUsers, type UserSearchParams } from '@/api/adminUsers'
import { errorMessage } from '@/api/errors'
import { useAuth } from '@/auth/authContext'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState } from '@/components/ui/EmptyState'
import { SelectField, TextField } from '@/components/ui/fields'
import { Pagination } from '@/components/ui/Pagination'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { cn } from '@/lib/cn'
import { formatDateTime, ROLE_LABELS, STATUS_LABELS } from '@/lib/format'
import { ROLES, USER_STATUSES, type Role, type UserAccount, type UserStatus } from '@/types/api'

const PAGE_SIZE = 20

const STATUS_TONES: Record<UserStatus, BadgeTone> = {
  ACTIVE: 'success',
  PENDING_ACTIVATION: 'warning',
  DEACTIVATED: 'neutral',
}

type SortField = 'lastName' | 'email' | 'status' | 'lastLoginAt' | 'createdAt'

function SortHeader({ field, label, sort, onSort, className }: {
  field: SortField
  label: string
  sort: string
  onSort: (sort: string) => void
  className?: string
}) {
  const [current, direction] = sort.split(',')
  const active = current === field
  const next = active && direction === 'asc' ? `${field},desc` : `${field},asc`
  const Icon = !active ? ArrowUpDown : direction === 'asc' ? ArrowUp : ArrowDown
  return (
    <th
      scope="col"
      aria-sort={active ? (direction === 'asc' ? 'ascending' : 'descending') : 'none'}
      className={cn('px-5 py-3 text-left text-xs font-semibold tracking-wide text-slate-600 uppercase', className)}
    >
      <button type="button" onClick={() => onSort(next)} className="inline-flex items-center gap-1 tracking-wide uppercase hover:text-slate-900">
        {label}
        <Icon aria-hidden="true" className={active ? 'size-3.5 text-slate-900' : 'size-3.5 text-slate-400'} />
      </button>
    </th>
  )
}

export function UsersPage() {
  const { user: me } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  // Filters live in the URL so a filtered view can be bookmarked and survives a reload.
  const [params, setParams] = useSearchParams()
  const [searchInput, setSearchInput] = useState(params.get('q') ?? '')
  const q = useDebouncedValue(searchInput.trim())
  const role = (params.get('role') ?? '') as Role | ''
  const status = (params.get('status') ?? '') as UserStatus | ''
  const sort = params.get('sort') ?? 'lastName,asc'
  const page = Number(params.get('page') ?? 0) || 0

  const [pendingAction, setPendingAction] = useState<{ user: UserAccount; action: 'deactivate' | 'reactivate' } | null>(null)

  function updateParams(changes: Record<string, string | number | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => {
      if (value === undefined || value === '' || (key === 'page' && value === 0)) next.delete(key)
      else next.set(key, String(value))
    })
    setParams(next, { replace: true })
  }

  const query: UserSearchParams = { q, role: role || undefined, status: status || undefined, sort, page, size: PAGE_SIZE }
  const usersQuery = useQuery({
    queryKey: ['admin', 'users', query],
    queryFn: () => searchUsers(query),
    placeholderData: keepPreviousData,
  })

  const mutation = useMutation({
    mutationFn: ({ user, action }: { user: UserAccount; action: 'deactivate' | 'reactivate' }) =>
      action === 'deactivate' ? deactivateUser(user.id) : reactivateUser(user.id),
    onSuccess: (_, { user, action }) => {
      toast.success(`${user.firstName} ${user.lastName} was ${action === 'deactivate' ? 'deactivated' : 'reactivated'}.`)
      setPendingAction(null)
      return queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
    onError: (error) => {
      toast.error(errorMessage(error))
      setPendingAction(null)
    },
  })

  const data = usersQuery.data
  const hasFilters = !!(q || role || status)

  return (
    <>
      <PageHeader title="Users" description="All student, lecturer and staff accounts." />

      <Card>
        <div className="grid gap-3 border-b border-slate-200 p-4 sm:grid-cols-[minmax(0,1fr)_12rem_12rem]">
          <div className="relative">
            <Search aria-hidden="true" className="pointer-events-none absolute top-2.5 left-3 size-4 text-slate-400" />
            <TextField
              label="Search users"
              hideLabel
              type="search"
              placeholder="Search by name or email"
              className="pl-9"
              value={searchInput}
              onChange={(e) => {
                setSearchInput(e.target.value)
                updateParams({ q: e.target.value.trim(), page: 0 })
              }}
            />
          </div>
          <SelectField label="Role" hideLabel value={role} onChange={(e) => updateParams({ role: e.target.value, page: 0 })}>
            <option value="">All roles</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {ROLE_LABELS[r]}
              </option>
            ))}
          </SelectField>
          <SelectField label="Status" hideLabel value={status} onChange={(e) => updateParams({ status: e.target.value, page: 0 })}>
            <option value="">All statuses</option>
            {USER_STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </SelectField>
        </div>

        {usersQuery.isError ? (
          <div className="p-5">
            <Alert tone="error" title="Could not load users">
              {errorMessage(usersQuery.error)}{' '}
              <button type="button" className="font-medium underline" onClick={() => usersQuery.refetch()}>
                Try again
              </button>
            </Alert>
          </div>
        ) : !data ? (
          <div className="flex justify-center p-12 text-brand-700">
            <Spinner label="Loading users…" />
          </div>
        ) : data.content.length === 0 ? (
          <EmptyState
            icon={hasFilters ? Search : Users}
            title={hasFilters ? 'No users match your filters' : 'No users yet'}
            description={hasFilters ? 'Try a different search term or clear the filters.' : undefined}
            action={
              hasFilters && (
                <Button
                  variant="secondary"
                  onClick={() => {
                    setSearchInput('')
                    setParams({}, { replace: true })
                  }}
                >
                  Clear filters
                </Button>
              )
            }
          />
        ) : (
          <>
            {/* relative: keeps absolutely positioned sr-only text inside the scroll area on narrow screens */}
            <div className="relative overflow-x-auto" aria-busy={usersQuery.isFetching}>
              <table className="min-w-full divide-y divide-slate-200">
                <caption className="sr-only">User accounts</caption>
                <thead className="bg-slate-50">
                  <tr>
                    <SortHeader field="lastName" label="Name" sort={sort} onSort={(s) => updateParams({ sort: s, page: 0 })} />
                    <th scope="col" className="px-5 py-3 text-left text-xs font-semibold tracking-wide text-slate-600 uppercase">
                      Roles
                    </th>
                    <SortHeader field="status" label="Status" className="hidden md:table-cell" sort={sort} onSort={(s) => updateParams({ sort: s, page: 0 })} />
                    <SortHeader field="lastLoginAt" label="Last sign-in" className="hidden md:table-cell" sort={sort} onSort={(s) => updateParams({ sort: s, page: 0 })} />
                    <th scope="col" className="px-5 py-3">
                      <span className="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 bg-white">
                  {data.content.map((u) => (
                    <tr key={u.id} className="hover:bg-slate-50/60">
                      <td className="px-5 py-3">
                        <p className="font-medium text-slate-900">
                          {u.lastName}, {u.firstName}
                        </p>
                        <p className="text-xs text-slate-500">{u.email}</p>
                        {/* On small screens the status column is hidden; show it under the name instead. */}
                        <div className="mt-1 md:hidden">
                          <Badge tone={STATUS_TONES[u.status]}>{STATUS_LABELS[u.status]}</Badge>
                        </div>
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex flex-wrap gap-1">
                          {u.roles.map((r) => (
                            <Badge key={r} tone="brand">
                              {ROLE_LABELS[r]}
                            </Badge>
                          ))}
                        </div>
                      </td>
                      <td className="hidden px-5 py-3 md:table-cell">
                        <Badge tone={STATUS_TONES[u.status]}>{STATUS_LABELS[u.status]}</Badge>
                      </td>
                      <td className="hidden px-5 py-3 whitespace-nowrap text-slate-600 md:table-cell">{formatDateTime(u.lastLoginAt, 'Never')}</td>
                      <td className="px-5 py-3 text-right">
                        {u.id !== me?.id &&
                          (u.status === 'DEACTIVATED' ? (
                            <Button variant="secondary" size="sm" onClick={() => setPendingAction({ user: u, action: 'reactivate' })}>
                              Reactivate
                            </Button>
                          ) : (
                            <Button variant="ghost" size="sm" className="text-red-700 hover:bg-red-50" onClick={() => setPendingAction({ user: u, action: 'deactivate' })}>
                              <UserX aria-hidden="true" className="size-4" /> Deactivate
                            </Button>
                          ))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={data.page}
              size={data.size}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPageChange={(p) => updateParams({ page: p })}
            />
          </>
        )}
      </Card>

      <ConfirmDialog
        open={pendingAction !== null}
        tone={pendingAction?.action === 'deactivate' ? 'danger' : 'primary'}
        title={pendingAction?.action === 'deactivate' ? 'Deactivate account?' : 'Reactivate account?'}
        description={
          pendingAction?.action === 'deactivate' ? (
            <>
              <strong>
                {pendingAction.user.firstName} {pendingAction.user.lastName}
              </strong>{' '}
              will be signed out everywhere immediately and will not be able to sign in. Their records are kept and the
              account can be reactivated later.
            </>
          ) : (
            <>
              <strong>
                {pendingAction?.user.firstName} {pendingAction?.user.lastName}
              </strong>{' '}
              will be able to sign in again.
            </>
          )
        }
        confirmLabel={pendingAction?.action === 'deactivate' ? 'Deactivate' : 'Reactivate'}
        loading={mutation.isPending}
        onConfirm={() => pendingAction && mutation.mutate(pendingAction)}
        onCancel={() => setPendingAction(null)}
      />
    </>
  )
}
