import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Landmark, Pencil, Receipt, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useSearchParams } from 'react-router'
import type { z } from 'zod'
import { useAuth } from '@/auth/authContext'
import { errorMessage } from '@/api/errors'
import { applyServerErrors } from '@/api/formErrors'
import { deleteFeeStructure, generateCharges, getFeeStructures, previewCharges, saveFeeStructure } from '@/api/fees'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, Td, Th } from '@/components/table/DataTable'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { SelectField, TextField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { useSemesters } from '@/features/admin/queries'
import { formatMoney } from '@/lib/money'
import type { FeeStructureRow, FeeStructures, Semester } from '@/types/api'
import { feeStructureSchema } from '../schemas'

/** The semester being taught, else the one open for registration, else the next one, else the latest. */
function defaultSemester(semesters: Semester[]): Semester | undefined {
  for (const status of ['IN_PROGRESS', 'REGISTRATION_OPEN', 'UPCOMING'] as const) {
    const match = semesters.find((s) => s.status === status)
    if (match) return match
  }
  return semesters[0]
}

function invalidateBilling(queryClient: ReturnType<typeof useQueryClient>, semesterId: number) {
  void queryClient.invalidateQueries({ queryKey: ['fee-structures', semesterId] })
  void queryClient.invalidateQueries({ queryKey: ['charge-preview', semesterId] })
}

function FeeModal({ structures, row, onClose }: { structures: FeeStructures; row: FeeStructureRow; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [formError, setFormError] = useState<string | null>(null)
  const locked = row.chargedCount > 0
  const { register, handleSubmit, setError, formState } = useForm<
    z.input<typeof feeStructureSchema>,
    unknown,
    z.output<typeof feeStructureSchema>
  >({
    resolver: zodResolver(feeStructureSchema),
    defaultValues: {
      amount: row.structure ? row.structure.amount.toFixed(2) : '',
      description: row.structure?.description ?? `Tuition — ${structures.semester.name}`,
    },
  })
  const errors = formState.errors

  const mutation = useMutation({
    mutationFn: (v: z.output<typeof feeStructureSchema>) =>
      saveFeeStructure({ ...v, programId: row.program.id, semesterId: structures.semester.id }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['fee-structures', structures.semester.id], updated)
      invalidateBilling(queryClient, structures.semester.id)
      toast.success(`Fee for ${row.program.code} saved.`)
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, setError, ['amount', 'description'])),
  })

  return (
    <Modal
      open
      title={`${row.program.name} (${row.program.code})`}
      description={`Tuition for ${structures.semester.name}.`}
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="fee-structure" loading={mutation.isPending}>
            Save fee
          </Button>
        </>
      }
    >
      <form id="fee-structure" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField
          label={`Amount (${structures.currency})`}
          inputMode="decimal"
          autoComplete="off"
          readOnly={locked}
          hint={locked ? `Already charged to ${row.chargedCount} student(s), so the amount can no longer change.` : undefined}
          error={errors.amount?.message}
          {...register('amount')}
        />
        <TextField label="Description" hint="Shown on the student's statement." error={errors.description?.message} {...register('description')} />
      </form>
    </Modal>
  )
}

function BillingCard({ semesterId }: { semesterId: number }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [confirming, setConfirming] = useState(false)
  const preview = useQuery({ queryKey: ['charge-preview', semesterId], queryFn: () => previewCharges(semesterId) })
  const generate = useMutation({
    mutationFn: () => generateCharges(semesterId),
    onSuccess: (result) => {
      const currency = preview.data?.currency ?? ''
      toast.success(
        result.charged === 0
          ? 'No new charges: every registered student with a fee set is already billed.'
          : `Charged ${result.charged} student(s) a total of ${formatMoney(result.amountCharged, currency)}.`,
      )
      invalidateBilling(queryClient, semesterId)
      void queryClient.invalidateQueries({ queryKey: ['fee-accounts'] })
      void queryClient.invalidateQueries({ queryKey: ['fee-reports'] })
      setConfirming(false)
    },
    onError: (error) => {
      toast.error(errorMessage(error))
      setConfirming(false)
    },
  })
  const p = preview.data

  return (
    <Card>
      <CardHeader
        title="Billing"
        description="Charge each student registered (approved) for this semester their program's fee. Running it again only bills students not yet charged."
        actions={
          p && (
            <Button onClick={() => setConfirming(true)} disabled={p.toCharge === 0}>
              <Receipt aria-hidden="true" className="size-4" /> Generate charges
            </Button>
          )
        }
      />
      <CardBody>
        {preview.error ? (
          <Alert tone="error">{errorMessage(preview.error)}</Alert>
        ) : !p ? (
          <Spinner label="Checking registrations…" />
        ) : (
          <div className="space-y-3">
            <dl className="grid gap-3 text-sm sm:grid-cols-4">
              {(
                [
                  ['Registered students', String(p.registeredStudents)],
                  ['Already charged', String(p.alreadyCharged)],
                  ['To charge now', String(p.toCharge)],
                  ['Amount to charge', formatMoney(p.amountToCharge, p.currency)],
                ] as const
              ).map(([label, value]) => (
                <div key={label}>
                  <dt className="text-neutral-500">{label}</dt>
                  <dd className="mt-0.5 text-lg font-semibold text-neutral-900 tabular-nums">{value}</dd>
                </div>
              ))}
            </dl>
            {p.withoutFee > 0 && (
              <Alert tone="info" title={`${p.withoutFee} registered student(s) have no fee set for their program`}>
                Set a fee for {p.programsWithoutFee.join(', ')} to bill them.
              </Alert>
            )}
          </div>
        )}
      </CardBody>
      {p && (
        <ConfirmDialog
          open={confirming}
          tone="primary"
          title="Generate charges?"
          description={`${p.toCharge} student(s) will be charged a total of ${formatMoney(p.amountToCharge, p.currency)} for ${p.semester.name}. Charged fees can no longer change.`}
          confirmLabel="Generate charges"
          loading={generate.isPending}
          onConfirm={() => generate.mutate()}
          onCancel={() => setConfirming(false)}
        />
      )}
    </Card>
  )
}

/** Tuition per program for a semester, and billing it to registered students. */
export function FeeStructuresPage() {
  const queryClient = useQueryClient()
  const toast = useToast()
  const { hasRole } = useAuth()
  const canEdit = hasRole('FINANCE')
  const semesters = useSemesters()
  const [params, setParams] = useSearchParams()
  const semesterId = Number(params.get('semesterId')) || (semesters.data && defaultSemester(semesters.data)?.id)
  const [editing, setEditing] = useState<FeeStructureRow | null>(null)
  const [deleting, setDeleting] = useState<FeeStructureRow | null>(null)

  const query = useQuery({
    queryKey: ['fee-structures', semesterId],
    queryFn: () => getFeeStructures(semesterId as number),
    enabled: semesterId !== undefined,
  })
  const remove = useMutation({
    mutationFn: (structureId: number) => deleteFeeStructure(structureId),
    onSuccess: () => {
      toast.success(`Fee for ${deleting?.program.code} removed.`)
      if (semesterId) invalidateBilling(queryClient, semesterId)
      setDeleting(null)
    },
    onError: (error) => {
      toast.error(errorMessage(error))
      setDeleting(null)
    },
  })
  const data = query.data

  return (
    <>
      <PageHeader
        title="Fee structures"
        description="Tuition per program for each semester."
        actions={
          <div className="w-64">
            <SelectField
              label="Semester"
              hideLabel
              value={semesterId ?? ''}
              onChange={(e) => setParams({ semesterId: e.target.value }, { replace: true })}
            >
              {semesters.data?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </SelectField>
          </div>
        }
      />
      {semesters.data?.length === 0 ? (
        <Alert tone="info">Create a semester first; fees are set per semester.</Alert>
      ) : (
        <div className="space-y-4">
          <Card>
            <DataTable
              caption="Fee structures"
              isLoading={!data}
              error={query.error ?? semesters.error}
              onRetry={() => query.refetch()}
              isFetching={query.isFetching}
              isEmpty={data?.rows.length === 0}
              emptyIcon={Landmark}
              emptyTitle="No active programs"
              head={
                <>
                  <Th>Program</Th>
                  <Th className="text-right">Fee</Th>
                  <Th className="hidden sm:table-cell">Description</Th>
                  <Th className="hidden text-right md:table-cell">Students charged</Th>
                  {canEdit && (
                    <Th>
                      <span className="sr-only">Actions</span>
                    </Th>
                  )}
                </>
              }
            >
              {data?.rows.map((row) => (
                <tr key={row.program.id} className="hover:bg-neutral-50/60">
                  <Td>
                    <p className="font-medium text-neutral-900">{row.program.code}</p>
                    <p className="text-xs text-neutral-500">{row.program.name}</p>
                  </Td>
                  <Td className="text-right whitespace-nowrap tabular-nums">
                    {row.structure ? formatMoney(row.structure.amount, data.currency) : <span className="text-neutral-400">Not set</span>}
                  </Td>
                  <Td className="hidden text-neutral-600 sm:table-cell">{row.structure?.description ?? '—'}</Td>
                  <Td className="hidden text-right tabular-nums md:table-cell">{row.chargedCount}</Td>
                  {canEdit && (
                    <Td className="text-right whitespace-nowrap">
                      <Button variant="ghost" size="sm" onClick={() => setEditing(row)} aria-label={`Set fee for ${row.program.code}`}>
                        <Pencil aria-hidden="true" className="size-4" />
                      </Button>
                      {row.structure && row.chargedCount === 0 && (
                        <Button variant="ghost" size="sm" onClick={() => setDeleting(row)} aria-label={`Remove fee for ${row.program.code}`}>
                          <Trash2 aria-hidden="true" className="size-4" />
                        </Button>
                      )}
                    </Td>
                  )}
                </tr>
              ))}
            </DataTable>
          </Card>
          {canEdit && semesterId !== undefined && <BillingCard semesterId={semesterId} />}
        </div>
      )}
      {editing && data && <FeeModal structures={data} row={editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={deleting !== null}
        title="Remove fee?"
        description={`The fee for ${deleting?.program.code} in ${data?.semester.name} will be removed. No student has been charged it yet.`}
        confirmLabel="Remove fee"
        loading={remove.isPending}
        onConfirm={() => deleting?.structure && remove.mutate(deleting.structure.id)}
        onCancel={() => setDeleting(null)}
      />
    </>
  )
}
