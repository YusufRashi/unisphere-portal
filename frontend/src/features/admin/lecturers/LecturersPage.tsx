import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Presentation, Search, UserPlus } from 'lucide-react'
import { useState } from 'react'
import { useForm, type FieldErrors, type UseFormRegister } from 'react-hook-form'
import { z } from 'zod'
import { applyServerErrors } from '@/api/formErrors'
import { createLecturer, searchLecturers, updateLecturer } from '@/api/people'
import { DataTable, SortTh, TableToolbar, Td, Th } from '@/components/table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { SelectField, TextField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { useToast } from '@/components/ui/toastContext'
import { useListParams } from '@/hooks/useListParams'
import { emptyToNull, STATUS_LABELS } from '@/lib/format'
import type { Lecturer } from '@/types/api'
import { PersonFields } from '../people/PersonFields'
import { PERSON_FIELDS, personSchema } from '../people/personSchema'
import { ResendActivationButton } from '../people/ResendActivationButton'
import { useDepartments } from '../queries'

const PAGE_SIZE = 20

const profileSchema = z.object({
  departmentId: z.coerce.number<string>().int().positive('Choose a department.'),
  title: z.string().trim().max(30, 'At most 30 characters.'),
  office: z.string().trim().max(60, 'At most 60 characters.'),
})
const createSchema = personSchema.extend(profileSchema.shape)
type ProfileInput = z.input<typeof profileSchema>
type ProfileValues = z.output<typeof profileSchema>
type CreateInput = z.input<typeof createSchema>
type CreateValues = z.output<typeof createSchema>
const PROFILE_FIELDS = ['departmentId', 'title', 'office'] as const

function ProfileFields<T extends ProfileInput>({ register: registerAny, errors: errorsAny }: {
  register: UseFormRegister<T>
  errors: FieldErrors<T>
}) {
  // The generic constraint guarantees these field names exist on T.
  const register = registerAny as unknown as UseFormRegister<ProfileInput>
  const errors = errorsAny as FieldErrors<ProfileInput>
  const departments = useDepartments()
  return (
    <>
      <SelectField label="Department" error={errors.departmentId?.message} {...register('departmentId')}>
        <option value="">Choose a department…</option>
        {departments.data?.map((d) => (
          <option key={d.id} value={d.id}>
            {d.name}
          </option>
        ))}
      </SelectField>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Title (optional)" placeholder="Dr." error={errors.title?.message} {...register('title')} />
        <TextField label="Office (optional)" placeholder="Block B, Room 204" error={errors.office?.message} {...register('office')} />
      </div>
    </>
  )
}

function CreateLecturerModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [formError, setFormError] = useState<string | null>(null)
  const form = useForm<CreateInput, unknown, CreateValues>({
    resolver: zodResolver(createSchema),
    defaultValues: { firstName: '', lastName: '', email: '', phone: '', departmentId: '', title: '', office: '' },
  })

  const mutation = useMutation({
    mutationFn: (v: CreateValues) =>
      createLecturer({ ...v, phone: emptyToNull(v.phone), title: emptyToNull(v.title), office: emptyToNull(v.office) }),
    onSuccess: (l) => {
      toast.success(`${l.displayName} added as ${l.staffNumber}. An activation link was sent to ${l.email}.`)
      void queryClient.invalidateQueries({ queryKey: ['lecturers'] })
      void queryClient.invalidateQueries({ queryKey: ['departments'] })
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, form.setError, [...PERSON_FIELDS, ...PROFILE_FIELDS])),
  })

  return (
    <Modal
      open
      size="lg"
      title="Add lecturer"
      description="The lecturer receives an email to activate the account. The staff number is generated automatically."
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="create-lecturer" loading={mutation.isPending}>
            Add lecturer
          </Button>
        </>
      }
    >
      <form id="create-lecturer" noValidate onSubmit={form.handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <PersonFields register={form.register} errors={form.formState.errors} />
        <ProfileFields register={form.register} errors={form.formState.errors} />
      </form>
    </Modal>
  )
}

function EditLecturerModal({ lecturer, onClose }: { lecturer: Lecturer; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [formError, setFormError] = useState<string | null>(null)
  const form = useForm<ProfileInput, unknown, ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { departmentId: String(lecturer.department.id), title: lecturer.title ?? '', office: lecturer.office ?? '' },
  })

  const mutation = useMutation({
    mutationFn: (v: ProfileValues) => updateLecturer(lecturer.id, { ...v, title: emptyToNull(v.title), office: emptyToNull(v.office) }),
    onSuccess: (l) => {
      toast.success(`${l.displayName}'s profile was updated.`)
      void queryClient.invalidateQueries({ queryKey: ['lecturers'] })
      void queryClient.invalidateQueries({ queryKey: ['departments'] })
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, form.setError, PROFILE_FIELDS)),
  })

  return (
    <Modal
      open
      title={lecturer.displayName}
      description={`${lecturer.staffNumber} · ${lecturer.email}`}
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="edit-lecturer" loading={mutation.isPending}>
            Save changes
          </Button>
        </>
      }
    >
      <form id="edit-lecturer" noValidate onSubmit={form.handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <ProfileFields register={form.register} errors={form.formState.errors} />
      </form>
    </Modal>
  )
}

export function LecturersPage() {
  const list = useListParams('lastName,asc')
  const departments = useDepartments()
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Lecturer | null>(null)
  const departmentId = list.getNumber('departmentId')

  const params = { q: list.q, departmentId, sort: list.sort, page: list.page, size: PAGE_SIZE }
  const query = useQuery({ queryKey: ['lecturers', params], queryFn: () => searchLecturers(params), placeholderData: keepPreviousData })
  const data = query.data
  const onSort = (sort: string) => list.update({ sort })

  return (
    <>
      <PageHeader
        title="Lecturers"
        description="Teaching staff and their departments."
        actions={
          <Button onClick={() => setCreating(true)}>
            <UserPlus aria-hidden="true" className="size-4" /> Add lecturer
          </Button>
        }
      />
      <Card>
        <TableToolbar className="sm:grid-cols-[minmax(0,1fr)_16rem]">
          <div className="relative">
            <Search aria-hidden="true" className="pointer-events-none absolute top-2.5 left-3 size-4 text-slate-400" />
            <TextField
              label="Search lecturers"
              hideLabel
              type="search"
              placeholder="Name, email or staff number"
              className="pl-9"
              value={list.searchInput}
              onChange={(e) => list.setSearchInput(e.target.value)}
            />
          </div>
          <SelectField label="Department" hideLabel value={departmentId ?? ''} onChange={(e) => list.update({ departmentId: e.target.value })}>
            <option value="">All departments</option>
            {departments.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </SelectField>
        </TableToolbar>
        <DataTable
          caption="Lecturers"
          isLoading={!data}
          error={query.error}
          onRetry={() => query.refetch()}
          isFetching={query.isFetching}
          isEmpty={data?.content.length === 0}
          emptyIcon={list.hasFilters ? Search : Presentation}
          emptyTitle={list.hasFilters ? 'No lecturers match your filters' : 'No lecturers yet'}
          head={
            <>
              <SortTh field="lastName" label="Lecturer" sort={list.sort} onSort={onSort} />
              <SortTh field="department" label="Department" sort={list.sort} onSort={onSort} className="hidden md:table-cell" />
              <Th className="hidden lg:table-cell">Office</Th>
              <Th>Account</Th>
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
          {data?.content.map((l) => (
            <tr key={l.id} className="hover:bg-slate-50/60">
              <Td>
                <p className="font-medium text-slate-900">{l.displayName}</p>
                <p className="text-xs text-slate-500">
                  {l.staffNumber} · {l.email}
                </p>
              </Td>
              <Td className="hidden text-slate-600 md:table-cell">{l.department.name}</Td>
              <Td className="hidden text-slate-600 lg:table-cell">{l.office ?? '—'}</Td>
              <Td>
                <Badge tone={l.accountStatus === 'ACTIVE' ? 'success' : l.accountStatus === 'PENDING_ACTIVATION' ? 'warning' : 'neutral'}>
                  {STATUS_LABELS[l.accountStatus]}
                </Badge>
              </Td>
              <Td className="text-right whitespace-nowrap">
                {l.accountStatus === 'PENDING_ACTIVATION' && <ResendActivationButton userId={l.userId} name={l.displayName} />}
                <Button variant="ghost" size="sm" onClick={() => setEditing(l)} aria-label={`Edit ${l.displayName}`}>
                  <Pencil aria-hidden="true" className="size-4" />
                </Button>
              </Td>
            </tr>
          ))}
        </DataTable>
      </Card>
      {creating && <CreateLecturerModal onClose={() => setCreating(false)} />}
      {editing && <EditLecturerModal lecturer={editing} onClose={() => setEditing(null)} />}
    </>
  )
}
