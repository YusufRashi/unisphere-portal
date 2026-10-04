import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, Pencil, Plus } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { createSemester, semesterAction, updateSemester, type SemesterAction } from '@/api/calendar'
import { errorMessage } from '@/api/errors'
import { applyServerErrors } from '@/api/formErrors'
import { DataTable, Td, Th } from '@/components/table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { CheckboxField, SelectField, TextField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { useToast } from '@/components/ui/toastContext'
import { fromLocalInput, toLocalInput } from '@/lib/datetime'
import { formatDate, formatDateTime, SEMESTER_STATUS_LABELS } from '@/lib/format'
import type { Semester, SemesterStatus } from '@/types/api'
import { useSemesters } from '../queries'

const STATUS_TONES: Record<SemesterStatus, BadgeTone> = {
  UPCOMING: 'neutral',
  REGISTRATION_OPEN: 'success',
  IN_PROGRESS: 'brand',
  COMPLETED: 'neutral',
}

const required = (label: string) => z.string().min(1, `Enter the ${label}.`)

const schema = z
  .object({
    code: z.string().trim().min(1, 'Enter a code.').max(20).regex(/^[A-Za-z0-9][A-Za-z0-9-]*$/, 'Letters, digits and hyphens only.'),
    name: z.string().trim().min(1, 'Enter a name.').max(100),
    academicYear: z.string().trim().regex(/^\d{4}\/\d{4}$/, 'Use the format 2026/2027.'),
    term: z.coerce.number<string>().int().min(1).max(3),
    startDate: required('start date'),
    endDate: required('end date'),
    registrationOpensAt: required('opening time'),
    registrationClosesAt: required('closing time'),
    addDropDeadline: required('add/drop deadline'),
    requiresApproval: z.boolean(),
    minCredits: z.coerce.number<string>().int().min(0, 'Cannot be negative.').max(60),
    maxCredits: z.coerce.number<string>().int().min(1).max(60, 'At most 60.'),
  })
  .superRefine((v, ctx) => {
    if (v.startDate && v.endDate && v.endDate <= v.startDate) {
      ctx.addIssue({ code: 'custom', path: ['endDate'], message: 'Must be after the start date.' })
    }
    if (v.registrationOpensAt && v.registrationClosesAt && v.registrationClosesAt <= v.registrationOpensAt) {
      ctx.addIssue({ code: 'custom', path: ['registrationClosesAt'], message: 'Must be after registration opens.' })
    }
    if (v.maxCredits < v.minCredits) {
      ctx.addIssue({ code: 'custom', path: ['maxCredits'], message: 'Must be at least the minimum.' })
    }
  })
type FormInput = z.input<typeof schema>
type FormValues = z.output<typeof schema>
const FIELDS = ['code', 'name', 'academicYear', 'term', 'startDate', 'endDate', 'registrationOpensAt',
  'registrationClosesAt', 'addDropDeadline', 'minCredits', 'maxCredits'] as const

function SemesterFormModal({ semester, onClose }: { semester: Semester | 'new'; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const editing = semester === 'new' ? null : semester
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState } = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      code: editing?.code ?? '',
      name: editing?.name ?? '',
      academicYear: editing?.academicYear ?? '',
      term: String(editing?.term ?? 1),
      startDate: editing?.startDate ?? '',
      endDate: editing?.endDate ?? '',
      registrationOpensAt: toLocalInput(editing?.registrationOpensAt),
      registrationClosesAt: toLocalInput(editing?.registrationClosesAt),
      addDropDeadline: toLocalInput(editing?.addDropDeadline),
      requiresApproval: editing?.requiresApproval ?? false,
      minCredits: String(editing?.minCredits ?? 12),
      maxCredits: String(editing?.maxCredits ?? 24),
    },
  })
  const errors = formState.errors

  const mutation = useMutation({
    mutationFn: (v: FormValues) => {
      const input = {
        ...v,
        // datetime-local values are in the browser's time zone; the API expects UTC instants.
        registrationOpensAt: fromLocalInput(v.registrationOpensAt) ?? '',
        registrationClosesAt: fromLocalInput(v.registrationClosesAt) ?? '',
        addDropDeadline: fromLocalInput(v.addDropDeadline) ?? '',
      }
      return editing ? updateSemester(editing.id, input) : createSemester(input)
    },
    onSuccess: (saved) => {
      toast.success(`${saved.name} ${editing ? 'updated' : 'created'}.`)
      void queryClient.invalidateQueries({ queryKey: ['semesters'] })
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, setError, FIELDS)),
  })

  return (
    <Modal
      open
      size="lg"
      title={editing ? `Edit ${editing.code}` : 'New semester'}
      description="Times are shown in your local time zone."
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="semester-form" loading={mutation.isPending}>
            {editing ? 'Save changes' : 'Create semester'}
          </Button>
        </>
      }
    >
      <form id="semester-form" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-5">
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className="grid gap-4 sm:grid-cols-[9rem_1fr]">
          <TextField label="Code" placeholder="2026-S1" error={errors.code?.message} {...register('code')} />
          <TextField label="Name" placeholder="Semester 1, 2026/2027" error={errors.name?.message} {...register('name')} />
        </div>
        <div className="grid gap-4 sm:grid-cols-4">
          <TextField label="Academic year" placeholder="2026/2027" error={errors.academicYear?.message} {...register('academicYear')} />
          <SelectField label="Term" error={errors.term?.message} {...register('term')}>
            <option value="1">Term 1</option>
            <option value="2">Term 2</option>
            <option value="3">Term 3</option>
          </SelectField>
          <TextField label="Starts" type="date" error={errors.startDate?.message} {...register('startDate')} />
          <TextField label="Ends" type="date" error={errors.endDate?.message} {...register('endDate')} />
        </div>
        <fieldset className="space-y-4">
          <legend className="text-sm font-semibold text-slate-900">Registration</legend>
          <div className="grid gap-4 sm:grid-cols-3">
            <TextField label="Opens" type="datetime-local" error={errors.registrationOpensAt?.message} {...register('registrationOpensAt')} />
            <TextField label="Closes" type="datetime-local" error={errors.registrationClosesAt?.message} {...register('registrationClosesAt')} />
            <TextField label="Add/drop deadline" type="datetime-local" error={errors.addDropDeadline?.message} {...register('addDropDeadline')} />
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <TextField label="Minimum credits" type="number" min={0} max={60} error={errors.minCredits?.message} {...register('minCredits')} />
            <TextField label="Maximum credits" type="number" min={1} max={60} error={errors.maxCredits?.message} {...register('maxCredits')} />
          </div>
          <CheckboxField
            label="Registrations require approval"
            description="When enabled, the registrar approves or rejects each submitted registration."
            {...register('requiresApproval')}
          />
        </fieldset>
      </form>
    </Modal>
  )
}

interface PendingAction {
  semester: Semester
  action: SemesterAction
}

const ACTION_COPY: Record<SemesterAction, { title: string; confirm: string; describe: (s: Semester) => string }> = {
  'make-current': {
    title: 'Make this the current semester?',
    confirm: 'Make current',
    describe: (s) => `${s.name} becomes the semester students see by default. The previous current semester is unset.`,
  },
  'registration/open': {
    title: 'Open registration?',
    confirm: 'Open registration',
    describe: (s) =>
      `Students can register for ${s.name} between ${formatDateTime(s.registrationOpensAt)} and ${formatDateTime(s.registrationClosesAt)}.`,
  },
  'registration/close': {
    title: 'Close registration?',
    confirm: 'Close registration',
    describe: (s) => `Students will no longer be able to register for ${s.name}, even inside the registration window.`,
  },
}

export function SemestersPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const query = useSemesters()
  const [editing, setEditing] = useState<Semester | 'new' | null>(null)
  const [pending, setPending] = useState<PendingAction | null>(null)
  // Captured once per visit; used only for a descriptive hint.
  const [now] = useState(() => Date.now())

  const action = useMutation({
    mutationFn: ({ semester, action }: PendingAction) => semesterAction(semester.id, action),
    onSuccess: (saved, { action }) => {
      toast.success(
        action === 'make-current' ? `${saved.name} is now the current semester.`
          : action === 'registration/open' ? `Registration opened for ${saved.name}.`
            : `Registration closed for ${saved.name}.`,
      )
      void queryClient.invalidateQueries({ queryKey: ['semesters'] })
    },
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => setPending(null),
  })

  const semesters = query.data ?? []

  return (
    <>
      <PageHeader
        title="Semesters"
        description="Academic calendar and registration windows."
        actions={
          <Button onClick={() => setEditing('new')}>
            <Plus aria-hidden="true" className="size-4" /> New semester
          </Button>
        }
      />
      <Card>
        <DataTable
          caption="Semesters"
          isLoading={query.isPending}
          error={query.error}
          onRetry={() => query.refetch()}
          isEmpty={semesters.length === 0}
          emptyIcon={CalendarDays}
          emptyTitle="No semesters yet"
          emptyDescription="Create a semester to start scheduling course offerings."
          head={
            <>
              <Th>Semester</Th>
              <Th className="hidden md:table-cell">Dates</Th>
              <Th className="hidden lg:table-cell">Registration window</Th>
              <Th>Status</Th>
              <Th className="hidden text-right sm:table-cell">Offerings</Th>
              <Th>
                <span className="sr-only">Actions</span>
              </Th>
            </>
          }
        >
          {semesters.map((s) => {
            const completed = s.status === 'COMPLETED'
            return (
              <tr key={s.id} className="hover:bg-slate-50/60">
                <Td>
                  <p className="flex items-center gap-2 font-medium text-slate-900">
                    {s.name}
                    {s.current && <Badge tone="brand">Current</Badge>}
                  </p>
                  <p className="text-xs text-slate-500">
                    {s.code} · {s.minCredits}–{s.maxCredits} credits{s.requiresApproval ? ' · approval required' : ''}
                  </p>
                </Td>
                <Td className="hidden whitespace-nowrap text-slate-600 md:table-cell">
                  {formatDate(s.startDate)} – {formatDate(s.endDate)}
                </Td>
                <Td className="hidden text-slate-600 lg:table-cell">
                  <p className="whitespace-nowrap">{formatDateTime(s.registrationOpensAt)}</p>
                  <p className="whitespace-nowrap">to {formatDateTime(s.registrationClosesAt)}</p>
                </Td>
                <Td>
                  <Badge tone={STATUS_TONES[s.status]}>{SEMESTER_STATUS_LABELS[s.status]}</Badge>
                  {s.registrationOpenFlag && !s.registrationOpen && !completed && (
                    <p className="mt-1 text-xs text-slate-500">
                      {new Date(s.registrationOpensAt).getTime() > now
                        ? `Registration starts ${formatDateTime(s.registrationOpensAt)}`
                        : 'Registration window has ended'}
                    </p>
                  )}
                </Td>
                <Td className="hidden text-right tabular-nums sm:table-cell">{s.offeringCount}</Td>
                <Td className="text-right">
                  <div className="flex flex-wrap justify-end gap-1">
                    {!completed && !s.current && (
                      <Button variant="secondary" size="sm" onClick={() => setPending({ semester: s, action: 'make-current' })}>
                        Make current
                      </Button>
                    )}
                    {!completed &&
                      (s.registrationOpenFlag ? (
                        <Button variant="secondary" size="sm" onClick={() => setPending({ semester: s, action: 'registration/close' })}>
                          Close registration
                        </Button>
                      ) : (
                        <Button variant="secondary" size="sm" onClick={() => setPending({ semester: s, action: 'registration/open' })}>
                          Open registration
                        </Button>
                      ))}
                    {!completed && (
                      <Button variant="ghost" size="sm" onClick={() => setEditing(s)} aria-label={`Edit ${s.name}`}>
                        <Pencil aria-hidden="true" className="size-4" />
                      </Button>
                    )}
                  </div>
                </Td>
              </tr>
            )
          })}
        </DataTable>
      </Card>

      {editing !== null && <SemesterFormModal semester={editing} onClose={() => setEditing(null)} />}

      <ConfirmDialog
        open={pending !== null}
        tone={pending?.action === 'registration/close' ? 'danger' : 'primary'}
        title={pending ? ACTION_COPY[pending.action].title : ''}
        description={pending ? ACTION_COPY[pending.action].describe(pending.semester) : ''}
        confirmLabel={pending ? ACTION_COPY[pending.action].confirm : ''}
        loading={action.isPending}
        onConfirm={() => pending && action.mutate(pending)}
        onCancel={() => setPending(null)}
      />
    </>
  )
}
