import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FileText, Pencil, Search, UserPlus, Users } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { applyServerErrors } from '@/api/formErrors'
import { createStudent, searchStudents, updateStudent } from '@/api/people'
import { DataTable, SortTh, TableToolbar, Td, Th } from '@/components/table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { SelectField, TextField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { useToast } from '@/components/ui/toastContext'
import { useListParams } from '@/hooks/useListParams'
import { emptyToNull, STATUS_LABELS, STUDENT_STATUS_LABELS } from '@/lib/format'
import { STUDENT_STATUSES, type Student, type StudentStatus } from '@/types/api'
import { PersonFields } from '../people/PersonFields'
import { PERSON_FIELDS, personSchema } from '../people/personSchema'
import { ResendActivationButton } from '../people/ResendActivationButton'
import { usePrograms } from '../queries'

const PAGE_SIZE = 20

const STATUS_TONES: Record<StudentStatus, BadgeTone> = {
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  GRADUATED: 'brand',
  WITHDRAWN: 'neutral',
}

const year = z.coerce.number<string>().int().min(1, 'Between 1 and 8.').max(8, 'Between 1 and 8.')
const program = z.coerce.number<string>().int().positive('Choose a program.')

const createSchema = personSchema.extend({
  programId: program,
  yearOfStudy: year,
  admissionDate: z.string().min(1, 'Enter the admission date.'),
})
type CreateInput = z.input<typeof createSchema>
type CreateValues = z.output<typeof createSchema>

const editSchema = z.object({ programId: program, yearOfStudy: year, status: z.enum(STUDENT_STATUSES) })
type EditInput = z.input<typeof editSchema>
type EditValues = z.output<typeof editSchema>

function ProgramOptions() {
  const programs = usePrograms({ active: true })
  return (
    <>
      <option value="">Choose a program…</option>
      {programs.data?.map((p) => (
        <option key={p.id} value={p.id}>
          {p.name} ({p.code})
        </option>
      ))}
    </>
  )
}

function CreateStudentModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [formError, setFormError] = useState<string | null>(null)
  const [today] = useState(() => new Date().toISOString().slice(0, 10))
  const { register, handleSubmit, setError, formState } = useForm<CreateInput, unknown, CreateValues>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      firstName: '', lastName: '', email: '', phone: '', programId: '', yearOfStudy: '1',
      admissionDate: today,
    },
  })
  const errors = formState.errors

  const mutation = useMutation({
    mutationFn: (v: CreateValues) => createStudent({ ...v, phone: emptyToNull(v.phone) }),
    onSuccess: (s) => {
      toast.success(`${s.firstName} ${s.lastName} added as ${s.studentNumber}. An activation link was sent to ${s.email}.`)
      void queryClient.invalidateQueries({ queryKey: ['students'] })
      void queryClient.invalidateQueries({ queryKey: ['programs'] })
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, setError, [...PERSON_FIELDS, 'programId', 'yearOfStudy', 'admissionDate'])),
  })

  return (
    <Modal
      open
      size="lg"
      title="Add student"
      description="The student receives an email to activate the account and choose a password. The student number is generated automatically."
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="create-student" loading={mutation.isPending}>
            Add student
          </Button>
        </>
      }
    >
      <form id="create-student" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <PersonFields register={register} errors={errors} />
        <SelectField label="Program" error={errors.programId?.message} {...register('programId')}>
          <ProgramOptions />
        </SelectField>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Year of study" type="number" min={1} max={8} error={errors.yearOfStudy?.message} {...register('yearOfStudy')} />
          <TextField label="Admission date" type="date" error={errors.admissionDate?.message} {...register('admissionDate')} />
        </div>
      </form>
    </Modal>
  )
}

function EditStudentModal({ student, onClose }: { student: Student; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState } = useForm<EditInput, unknown, EditValues>({
    resolver: zodResolver(editSchema),
    defaultValues: { programId: String(student.program.id), yearOfStudy: String(student.yearOfStudy), status: student.status },
  })
  const errors = formState.errors

  const mutation = useMutation({
    mutationFn: (v: EditValues) => updateStudent(student.id, v),
    onSuccess: () => {
      toast.success(`${student.firstName} ${student.lastName}'s record was updated.`)
      void queryClient.invalidateQueries({ queryKey: ['students'] })
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, setError, ['programId', 'yearOfStudy', 'status'])),
  })

  return (
    <Modal
      open
      title={`${student.firstName} ${student.lastName}`}
      description={`${student.studentNumber} · ${student.email}`}
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="edit-student" loading={mutation.isPending}>
            Save changes
          </Button>
        </>
      }
    >
      <form id="edit-student" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <SelectField label="Program" error={errors.programId?.message} {...register('programId')}>
          <ProgramOptions />
        </SelectField>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Year of study" type="number" min={1} max={8} error={errors.yearOfStudy?.message} {...register('yearOfStudy')} />
          <SelectField label="Academic status" error={errors.status?.message} {...register('status')}>
            {STUDENT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {STUDENT_STATUS_LABELS[s]}
              </option>
            ))}
          </SelectField>
        </div>
        <p className="text-xs text-slate-500">
          Academic status is separate from the login account. To block sign-in, deactivate the account on the Users page.
        </p>
      </form>
    </Modal>
  )
}

export function StudentsPage() {
  const list = useListParams('studentNumber,asc')
  const programs = usePrograms()
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Student | null>(null)
  const programId = list.getNumber('programId')
  const status = (list.get('status') || undefined) as StudentStatus | undefined
  const yearOfStudy = list.getNumber('yearOfStudy')

  const params = { q: list.q, programId, status, yearOfStudy, sort: list.sort, page: list.page, size: PAGE_SIZE }
  const query = useQuery({ queryKey: ['students', params], queryFn: () => searchStudents(params), placeholderData: keepPreviousData })
  const data = query.data
  const onSort = (sort: string) => list.update({ sort })

  return (
    <>
      <PageHeader
        title="Students"
        description="Student records and their login accounts."
        actions={
          <Button onClick={() => setCreating(true)}>
            <UserPlus aria-hidden="true" className="size-4" /> Add student
          </Button>
        }
      />
      <Card>
        <TableToolbar className="sm:grid-cols-[minmax(0,1fr)_16rem_10rem_8rem]">
          <div className="relative">
            <Search aria-hidden="true" className="pointer-events-none absolute top-2.5 left-3 size-4 text-slate-400" />
            <TextField
              label="Search students"
              hideLabel
              type="search"
              placeholder="Name, email or student number"
              className="pl-9"
              value={list.searchInput}
              onChange={(e) => list.setSearchInput(e.target.value)}
            />
          </div>
          <SelectField label="Program" hideLabel value={programId ?? ''} onChange={(e) => list.update({ programId: e.target.value })}>
            <option value="">All programs</option>
            {programs.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </SelectField>
          <SelectField label="Academic status" hideLabel value={status ?? ''} onChange={(e) => list.update({ status: e.target.value })}>
            <option value="">All statuses</option>
            {STUDENT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {STUDENT_STATUS_LABELS[s]}
              </option>
            ))}
          </SelectField>
          <SelectField label="Year" hideLabel value={yearOfStudy ?? ''} onChange={(e) => list.update({ yearOfStudy: e.target.value })}>
            <option value="">All years</option>
            {[1, 2, 3, 4, 5, 6].map((y) => (
              <option key={y} value={y}>
                Year {y}
              </option>
            ))}
          </SelectField>
        </TableToolbar>
        <DataTable
          caption="Students"
          isLoading={!data}
          error={query.error}
          onRetry={() => query.refetch()}
          isFetching={query.isFetching}
          isEmpty={data?.content.length === 0}
          emptyIcon={list.hasFilters ? Search : Users}
          emptyTitle={list.hasFilters ? 'No students match your filters' : 'No students yet'}
          emptyAction={
            list.hasFilters ? (
              <Button variant="secondary" onClick={list.clear}>
                Clear filters
              </Button>
            ) : undefined
          }
          head={
            <>
              <SortTh field="studentNumber" label="Student" sort={list.sort} onSort={onSort} />
              <Th className="hidden md:table-cell">Program</Th>
              <SortTh field="yearOfStudy" label="Year" sort={list.sort} onSort={onSort} className="hidden sm:table-cell" />
              <Th>Status</Th>
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
          {data?.content.map((s) => (
            <tr key={s.id} className="hover:bg-slate-50/60">
              <Td>
                <p className="font-medium text-slate-900">
                  {s.lastName}, {s.firstName}
                </p>
                <p className="text-xs text-slate-500">
                  {s.studentNumber} · {s.email}
                </p>
              </Td>
              <Td className="hidden text-slate-600 md:table-cell">
                <p>{s.program.name}</p>
                <p className="text-xs text-slate-500">{s.department.name}</p>
              </Td>
              <Td className="hidden tabular-nums sm:table-cell">{s.yearOfStudy}</Td>
              <Td>
                <div className="flex flex-col items-start gap-1">
                  <Badge tone={STATUS_TONES[s.status]}>{STUDENT_STATUS_LABELS[s.status]}</Badge>
                  {s.accountStatus !== 'ACTIVE' && (
                    <Badge tone={s.accountStatus === 'PENDING_ACTIVATION' ? 'warning' : 'neutral'}>
                      {STATUS_LABELS[s.accountStatus]}
                    </Badge>
                  )}
                </div>
              </Td>
              <Td className="text-right whitespace-nowrap">
                {s.accountStatus === 'PENDING_ACTIVATION' && (
                  <ResendActivationButton userId={s.userId} name={`${s.firstName} ${s.lastName}`} />
                )}
                <Link
                  to={`/admin/students/${s.id}/transcript`}
                  aria-label={`Transcript of ${s.firstName} ${s.lastName}`}
                  className="inline-flex h-8 items-center rounded-md px-3 text-slate-700 hover:bg-slate-100"
                >
                  <FileText aria-hidden="true" className="size-4" />
                </Link>
                <Button variant="ghost" size="sm" onClick={() => setEditing(s)} aria-label={`Edit ${s.firstName} ${s.lastName}`}>
                  <Pencil aria-hidden="true" className="size-4" />
                </Button>
              </Td>
            </tr>
          ))}
        </DataTable>
      </Card>
      {creating && <CreateStudentModal onClose={() => setCreating(false)} />}
      {editing && <EditStudentModal student={editing} onClose={() => setEditing(null)} />}
    </>
  )
}
