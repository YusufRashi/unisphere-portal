import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, UserPlus, Users, UserX } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { deactivateUser, reactivateUser, searchUsers } from '@/api/adminUsers'
import { errorMessage } from '@/api/errors'
import { applyServerErrors } from '@/api/formErrors'
import { createStaff } from '@/api/people'
import { useAuth } from '@/auth/authContext'
import { DataTable, SortTh, TableToolbar, Td, Th } from '@/components/table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { CheckboxField, SelectField, TextField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { useToast } from '@/components/ui/toastContext'
import { useListParams } from '@/hooks/useListParams'
import { emptyToNull, formatDateTime, ROLE_LABELS, STATUS_LABELS } from '@/lib/format'
import { ROLES, USER_STATUSES, type Role, type UserAccount, type UserStatus } from '@/types/api'
import { PersonFields } from '../people/PersonFields'
import { PERSON_FIELDS, personSchema } from '../people/personSchema'
import { ResendActivationButton } from '../people/ResendActivationButton'

const PAGE_SIZE = 20

const STATUS_TONES: Record<UserStatus, BadgeTone> = {
  ACTIVE: 'success',
  PENDING_ACTIVATION: 'warning',
  DEACTIVATED: 'neutral',
}

const staffSchema = personSchema
  .extend({ admin: z.boolean(), finance: z.boolean() })
  .refine((v) => v.admin || v.finance, { path: ['finance'], message: 'Choose at least one role.' })
type StaffValues = z.infer<typeof staffSchema>

function CreateStaffModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [formError, setFormError] = useState<string | null>(null)
  const form = useForm<StaffValues>({
    resolver: zodResolver(staffSchema),
    defaultValues: { firstName: '', lastName: '', email: '', phone: '', admin: false, finance: true },
  })

  const mutation = useMutation({
    mutationFn: (v: StaffValues) => {
      const roles: Role[] = [...(v.admin ? ['ADMIN' as const] : []), ...(v.finance ? ['FINANCE' as const] : [])]
      return createStaff({ firstName: v.firstName, lastName: v.lastName, email: v.email, phone: emptyToNull(v.phone), roles })
    },
    onSuccess: (u) => {
      toast.success(`${u.firstName} ${u.lastName} was added. An activation link was sent to ${u.email}.`)
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, form.setError, PERSON_FIELDS)),
  })

  return (
    <Modal
      open
      size="lg"
      title="Add staff member"
      description="For registrar and finance staff. Students and lecturers are added from their own pages."
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="create-staff" loading={mutation.isPending}>
            Add staff member
          </Button>
        </>
      }
    >
      <form id="create-staff" noValidate onSubmit={form.handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <PersonFields register={form.register} errors={form.formState.errors} />
        <fieldset className="space-y-3">
          <legend className="text-sm font-medium text-neutral-700">Roles</legend>
          <CheckboxField label="Registrar / Admin" description="Full access to academic administration and accounts." {...form.register('admin')} />
          <CheckboxField label="Finance" description="Fee accounts, payments and financial reports." {...form.register('finance')} />
          {form.formState.errors.finance && <p className="text-xs font-medium text-red-700">{form.formState.errors.finance.message}</p>}
        </fieldset>
      </form>
    </Modal>
  )
}

export function UsersPage() {
  const { user: me } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const list = useListParams('lastName,asc')
  const role = (list.get('role') || undefined) as Role | undefined
  const status = (list.get('status') || undefined) as UserStatus | undefined
  const [creating, setCreating] = useState(false)
  const [pendingAction, setPendingAction] = useState<{ user: UserAccount; action: 'deactivate' | 'reactivate' } | null>(null)

  const params = { q: list.q, role, status, sort: list.sort, page: list.page, size: PAGE_SIZE }
  const query = useQuery({ queryKey: ['admin', 'users', params], queryFn: () => searchUsers(params), placeholderData: keepPreviousData })

  const mutation = useMutation({
    mutationFn: ({ user, action }: { user: UserAccount; action: 'deactivate' | 'reactivate' }) =>
      action === 'deactivate' ? deactivateUser(user.id) : reactivateUser(user.id),
    onSuccess: (_, { user, action }) => {
      toast.success(`${user.firstName} ${user.lastName} was ${action === 'deactivate' ? 'deactivated' : 'reactivated'}.`)
      return queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => setPendingAction(null),
  })

  const data = query.data
  const onSort = (sort: string) => list.update({ sort })

  return (
    <>
      <PageHeader
        title="Users"
        description="All student, lecturer and staff accounts."
        actions={
          <Button onClick={() => setCreating(true)}>
            <UserPlus aria-hidden="true" className="size-4" /> Add staff member
          </Button>
        }
      />

      <Card>
        <TableToolbar className="sm:grid-cols-[minmax(0,1fr)_12rem_12rem]">
          <div className="relative">
            <Search aria-hidden="true" className="pointer-events-none absolute top-2.5 left-3 size-4 text-neutral-400" />
            <TextField
              label="Search users"
              hideLabel
              type="search"
              placeholder="Search by name or email"
              className="pl-9"
              value={list.searchInput}
              onChange={(e) => list.setSearchInput(e.target.value)}
            />
          </div>
          <SelectField label="Role" hideLabel value={role ?? ''} onChange={(e) => list.update({ role: e.target.value })}>
            <option value="">All roles</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {ROLE_LABELS[r]}
              </option>
            ))}
          </SelectField>
          <SelectField label="Status" hideLabel value={status ?? ''} onChange={(e) => list.update({ status: e.target.value })}>
            <option value="">All statuses</option>
            {USER_STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </SelectField>
        </TableToolbar>

        <DataTable
          caption="User accounts"
          isLoading={!data}
          error={query.error}
          onRetry={() => query.refetch()}
          isFetching={query.isFetching}
          isEmpty={data?.content.length === 0}
          emptyIcon={list.hasFilters ? Search : Users}
          emptyTitle={list.hasFilters ? 'No users match your filters' : 'No users yet'}
          emptyDescription={list.hasFilters ? 'Try a different search term or clear the filters.' : undefined}
          emptyAction={
            list.hasFilters ? (
              <Button variant="secondary" onClick={list.clear}>
                Clear filters
              </Button>
            ) : undefined
          }
          head={
            <>
              <SortTh field="lastName" label="Name" sort={list.sort} onSort={onSort} />
              <Th>Roles</Th>
              <SortTh field="status" label="Status" sort={list.sort} onSort={onSort} className="hidden md:table-cell" />
              <SortTh field="lastLoginAt" label="Last sign-in" sort={list.sort} onSort={onSort} className="hidden md:table-cell" />
              <Th>
                <span className="sr-only">Actions</span>
              </Th>
            </>
          }
          footer={
            data && (
              <Pagination
                page={data.page}
                size={data.size}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                onPageChange={(page) => list.update({ page })}
              />
            )
          }
        >
          {data?.content.map((u) => (
            <tr key={u.id} className="hover:bg-neutral-50/60">
              <Td>
                <p className="font-medium text-neutral-900">
                  {u.lastName}, {u.firstName}
                </p>
                <p className="text-xs text-neutral-500">{u.email}</p>
                {/* On small screens the status column is hidden; show it under the name instead. */}
                <div className="mt-1 md:hidden">
                  <Badge tone={STATUS_TONES[u.status]}>{STATUS_LABELS[u.status]}</Badge>
                </div>
              </Td>
              <Td>
                <div className="flex flex-wrap gap-1">
                  {u.roles.map((r) => (
                    <Badge key={r} tone="brand">
                      {ROLE_LABELS[r]}
                    </Badge>
                  ))}
                </div>
              </Td>
              <Td className="hidden md:table-cell">
                <Badge tone={STATUS_TONES[u.status]}>{STATUS_LABELS[u.status]}</Badge>
              </Td>
              <Td className="hidden whitespace-nowrap text-neutral-600 md:table-cell">{formatDateTime(u.lastLoginAt, 'Never')}</Td>
              <Td className="text-right whitespace-nowrap">
                {u.status === 'PENDING_ACTIVATION' && <ResendActivationButton userId={u.id} name={`${u.firstName} ${u.lastName}`} />}
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
              </Td>
            </tr>
          ))}
        </DataTable>
      </Card>

      {creating && <CreateStaffModal onClose={() => setCreating(false)} />}

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
