import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { createDepartment, deleteDepartment, listDepartments, updateDepartment } from '@/api/academic'
import { errorMessage } from '@/api/errors'
import { applyServerErrors } from '@/api/formErrors'
import { DataTable, Td, Th } from '@/components/table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { TextField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { useToast } from '@/components/ui/toastContext'
import { emptyToNull } from '@/lib/format'
import type { Department } from '@/types/api'

const departmentSchema = z.object({
  code: z
    .string()
    .trim()
    .min(1, 'Enter a code.')
    .max(10, 'At most 10 characters.')
    .regex(/^[A-Za-z0-9][A-Za-z0-9-]*$/, 'Letters, digits and hyphens only.'),
  name: z.string().trim().min(1, 'Enter a name.').max(150),
  faculty: z.string().trim().max(150),
})
type FormValues = z.infer<typeof departmentSchema>
const FIELDS = ['code', 'name', 'faculty'] as const

function DepartmentFormModal({ department, onClose }: { department: Department | null | 'new'; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const editing = department !== 'new' && department !== null ? department : null
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState } = useForm<FormValues>({
    resolver: zodResolver(departmentSchema),
    values: { code: editing?.code ?? '', name: editing?.name ?? '', faculty: editing?.faculty ?? '' },
  })

  const mutation = useMutation({
    mutationFn: (v: FormValues) => {
      const input = { code: v.code, name: v.name, faculty: emptyToNull(v.faculty) }
      return editing ? updateDepartment(editing.id, input) : createDepartment(input)
    },
    onSuccess: (saved) => {
      toast.success(`${saved.name} ${editing ? 'updated' : 'created'}.`)
      void queryClient.invalidateQueries({ queryKey: ['departments'] })
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, setError, FIELDS)),
  })

  return (
    <Modal
      open={department !== null}
      title={editing ? `Edit ${editing.code}` : 'New department'}
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="department-form" loading={mutation.isPending}>
            {editing ? 'Save changes' : 'Create department'}
          </Button>
        </>
      }
    >
      <form id="department-form" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className="grid gap-4 sm:grid-cols-[8rem_1fr]">
          <TextField label="Code" placeholder="CS" error={formState.errors.code?.message} {...register('code')} />
          <TextField label="Name" placeholder="Computer Science" error={formState.errors.name?.message} {...register('name')} />
        </div>
        <TextField label="Faculty" placeholder="Faculty of Science (optional)" error={formState.errors.faculty?.message} {...register('faculty')} />
      </form>
    </Modal>
  )
}

export function DepartmentsPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [editing, setEditing] = useState<Department | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Department | null>(null)
  const query = useQuery({ queryKey: ['departments'], queryFn: listDepartments })

  const remove = useMutation({
    mutationFn: (d: Department) => deleteDepartment(d.id),
    onSuccess: (_, d) => {
      toast.success(`${d.name} deleted.`)
      void queryClient.invalidateQueries({ queryKey: ['departments'] })
    },
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => setDeleting(null),
  })

  const departments = query.data ?? []

  return (
    <>
      <PageHeader
        title="Departments"
        description="Academic departments own programs, courses and teaching staff."
        actions={
          <Button onClick={() => setEditing('new')}>
            <Plus aria-hidden="true" className="size-4" /> New department
          </Button>
        }
      />
      <Card>
        <DataTable
          caption="Departments"
          isLoading={query.isPending}
          error={query.error}
          onRetry={() => query.refetch()}
          isEmpty={departments.length === 0}
          emptyIcon={Building2}
          emptyTitle="No departments yet"
          emptyDescription="Create the first department to start building the course catalogue."
          head={
            <>
              <Th>Department</Th>
              <Th className="hidden md:table-cell">Faculty</Th>
              <Th className="text-right">Programs</Th>
              <Th className="text-right">Courses</Th>
              <Th className="hidden text-right sm:table-cell">Lecturers</Th>
              <Th>
                <span className="sr-only">Actions</span>
              </Th>
            </>
          }
        >
          {departments.map((d) => {
            const inUse = d.programCount + d.courseCount + d.lecturerCount > 0
            return (
              <tr key={d.id} className="hover:bg-slate-50/60">
                <Td>
                  <p className="font-medium text-slate-900">{d.name}</p>
                  <p className="text-xs text-slate-500">{d.code}</p>
                </Td>
                <Td className="hidden text-slate-600 md:table-cell">{d.faculty ?? '—'}</Td>
                <Td className="text-right tabular-nums">{d.programCount}</Td>
                <Td className="text-right tabular-nums">{d.courseCount}</Td>
                <Td className="hidden text-right tabular-nums sm:table-cell">{d.lecturerCount}</Td>
                <Td className="text-right whitespace-nowrap">
                  <Button variant="ghost" size="sm" onClick={() => setEditing(d)} aria-label={`Edit ${d.name}`}>
                    <Pencil aria-hidden="true" className="size-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-red-700 hover:bg-red-50"
                    disabled={inUse}
                    title={inUse ? 'Only empty departments can be deleted' : undefined}
                    onClick={() => setDeleting(d)}
                    aria-label={`Delete ${d.name}`}
                  >
                    <Trash2 aria-hidden="true" className="size-4" />
                  </Button>
                </Td>
              </tr>
            )
          })}
        </DataTable>
      </Card>

      {editing !== null && <DepartmentFormModal department={editing} onClose={() => setEditing(null)} />}

      <ConfirmDialog
        open={deleting !== null}
        title="Delete department?"
        description={
          <>
            <strong>{deleting?.name}</strong> will be permanently removed. This cannot be undone.
          </>
        }
        confirmLabel="Delete"
        loading={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting)}
        onCancel={() => setDeleting(null)}
      />
    </>
  )
}
