import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { GraduationCap, Pencil, Plus } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useSearchParams } from 'react-router'
import { z } from 'zod'
import { createProgram, updateProgram } from '@/api/academic'
import { applyServerErrors } from '@/api/formErrors'
import { DataTable, TableToolbar, Td, Th } from '@/components/table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { CheckboxField, SelectField, TextField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { useToast } from '@/components/ui/toastContext'
import { AWARD_LABELS } from '@/lib/format'
import { AWARD_LEVELS, type Program } from '@/types/api'
import { useDepartments, usePrograms } from '../queries'

const schema = z.object({
  departmentId: z.coerce.number<string>().int().positive('Choose a department.'),
  code: z.string().trim().min(1, 'Enter a code.').max(20).regex(/^[A-Za-z0-9][A-Za-z0-9-]*$/, 'Letters, digits and hyphens only.'),
  name: z.string().trim().min(1, 'Enter a name.').max(150),
  awardLevel: z.enum(AWARD_LEVELS),
  durationYears: z.coerce.number<string>().int().min(1, 'At least 1 year.').max(8, 'At most 8 years.'),
  active: z.boolean(),
})
type FormInput = z.input<typeof schema>
type FormValues = z.output<typeof schema>
const FIELDS = ['departmentId', 'code', 'name', 'awardLevel', 'durationYears'] as const

function ProgramFormModal({ program, onClose }: { program: Program | 'new'; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const departments = useDepartments()
  const editing = program === 'new' ? null : program
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState } = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      departmentId: editing ? String(editing.department.id) : '',
      code: editing?.code ?? '',
      name: editing?.name ?? '',
      awardLevel: editing?.awardLevel ?? 'BACHELOR',
      durationYears: String(editing?.durationYears ?? 4),
      active: editing?.active ?? true,
    },
  })
  const errors = formState.errors

  const mutation = useMutation({
    mutationFn: (v: FormValues) => (editing ? updateProgram(editing.id, v) : createProgram(v)),
    onSuccess: (saved) => {
      toast.success(`${saved.name} ${editing ? 'updated' : 'created'}.`)
      void queryClient.invalidateQueries({ queryKey: ['programs'] })
      void queryClient.invalidateQueries({ queryKey: ['departments'] })
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, setError, FIELDS)),
  })

  return (
    <Modal
      open
      title={editing ? `Edit ${editing.code}` : 'New program'}
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="program-form" loading={mutation.isPending}>
            {editing ? 'Save changes' : 'Create program'}
          </Button>
        </>
      }
    >
      <form id="program-form" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <SelectField label="Department" error={errors.departmentId?.message} {...register('departmentId')}>
          <option value="">Choose a department…</option>
          {departments.data?.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </SelectField>
        <div className="grid gap-4 sm:grid-cols-[10rem_1fr]">
          <TextField label="Code" placeholder="BSC-CS" error={errors.code?.message} {...register('code')} />
          <TextField label="Name" placeholder="BSc Computer Science" error={errors.name?.message} {...register('name')} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField label="Award" error={errors.awardLevel?.message} {...register('awardLevel')}>
            {AWARD_LEVELS.map((a) => (
              <option key={a} value={a}>
                {AWARD_LABELS[a]}
              </option>
            ))}
          </SelectField>
          <TextField label="Duration (years)" type="number" min={1} max={8} error={errors.durationYears?.message} {...register('durationYears')} />
        </div>
        <CheckboxField label="Accepting students" description="Inactive programs keep their students but cannot admit new ones." {...register('active')} />
      </form>
    </Modal>
  )
}

export function ProgramsPage() {
  const [params, setParams] = useSearchParams()
  const departmentId = params.get('departmentId') ? Number(params.get('departmentId')) : undefined
  const status = params.get('status') ?? ''
  const departments = useDepartments()
  const programs = usePrograms({ departmentId, active: status === '' ? undefined : status === 'active' })
  const [editing, setEditing] = useState<Program | 'new' | null>(null)

  function setFilter(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: true })
  }

  const rows = programs.data ?? []

  return (
    <>
      <PageHeader
        title="Programs"
        description="Degrees and diplomas that students are admitted to."
        actions={
          <Button onClick={() => setEditing('new')}>
            <Plus aria-hidden="true" className="size-4" /> New program
          </Button>
        }
      />
      <Card>
        <TableToolbar className="sm:grid-cols-[minmax(0,18rem)_12rem]">
          <SelectField label="Department" hideLabel value={departmentId ?? ''} onChange={(e) => setFilter('departmentId', e.target.value)}>
            <option value="">All departments</option>
            {departments.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </SelectField>
          <SelectField label="Status" hideLabel value={status} onChange={(e) => setFilter('status', e.target.value)}>
            <option value="">All statuses</option>
            <option value="active">Accepting students</option>
            <option value="inactive">Inactive</option>
          </SelectField>
        </TableToolbar>
        <DataTable
          caption="Programs"
          isLoading={programs.isPending}
          error={programs.error}
          onRetry={() => programs.refetch()}
          isFetching={programs.isFetching}
          isEmpty={rows.length === 0}
          emptyIcon={GraduationCap}
          emptyTitle={departmentId || status ? 'No programs match these filters' : 'No programs yet'}
          head={
            <>
              <Th>Program</Th>
              <Th className="hidden md:table-cell">Department</Th>
              <Th>Award</Th>
              <Th className="hidden text-right sm:table-cell">Students</Th>
              <Th>Status</Th>
              <Th>
                <span className="sr-only">Actions</span>
              </Th>
            </>
          }
        >
          {rows.map((p) => (
            <tr key={p.id} className="hover:bg-neutral-50/60">
              <Td>
                <p className="font-medium text-neutral-900">{p.name}</p>
                <p className="text-xs text-neutral-500">{p.code}</p>
              </Td>
              <Td className="hidden text-neutral-600 md:table-cell">{p.department.name}</Td>
              <Td className="whitespace-nowrap text-neutral-600">
                {AWARD_LABELS[p.awardLevel]} · {p.durationYears} yr
              </Td>
              <Td className="hidden text-right tabular-nums sm:table-cell">{p.studentCount}</Td>
              <Td>{p.active ? <Badge tone="success">Active</Badge> : <Badge>Inactive</Badge>}</Td>
              <Td className="text-right">
                <Button variant="ghost" size="sm" onClick={() => setEditing(p)} aria-label={`Edit ${p.name}`}>
                  <Pencil aria-hidden="true" className="size-4" />
                </Button>
              </Td>
            </tr>
          ))}
        </DataTable>
      </Card>
      {editing !== null && <ProgramFormModal program={editing} onClose={() => setEditing(null)} />}
    </>
  )
}
