import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Plus, Wallet } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useParams } from 'react-router'
import type { z } from 'zod'
import { useAuth } from '@/auth/authContext'
import { applyServerErrors } from '@/api/formErrors'
import { errorMessage } from '@/api/errors'
import { addCharge, getFeeAccount, recordPayment, reversePayment, voidCharge } from '@/api/fees'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { SelectField, TextAreaField, TextField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { useSemesters } from '@/features/admin/queries'
import { formatMoney, PAYMENT_METHOD_LABELS } from '@/lib/money'
import { PAYMENT_METHODS, type FeeAccount, type FeeCharge, type FeePayment } from '@/types/api'
import { AccountStatement } from '../AccountStatement'
import { chargeSchema, officePaymentSchema, reasonSchema } from '../schemas'

/** Saves a returned account into the cache and refreshes the lists that show its balance. */
function useAccountUpdate(studentId: number) {
  const queryClient = useQueryClient()
  return (account: FeeAccount) => {
    queryClient.setQueryData(['fee-account', studentId], account)
    void queryClient.invalidateQueries({ queryKey: ['fee-accounts'] })
    void queryClient.invalidateQueries({ queryKey: ['fee-reports'] })
  }
}

function RecordPaymentModal({ account, onClose }: { account: FeeAccount; onClose: () => void }) {
  const toast = useToast()
  const onUpdated = useAccountUpdate(account.student.id)
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState } = useForm<
    z.input<typeof officePaymentSchema>,
    unknown,
    z.output<typeof officePaymentSchema>
  >({
    resolver: zodResolver(officePaymentSchema),
    defaultValues: { amount: '', method: 'BANK_TRANSFER', reference: '', paidAt: '', note: '' },
  })
  const errors = formState.errors

  const mutation = useMutation({
    mutationFn: (v: z.output<typeof officePaymentSchema>) => recordPayment(account.student.id, v),
    onSuccess: (updated, v) => {
      onUpdated(updated)
      toast.success(`Payment of ${formatMoney(Number(v.amount), account.currency)} recorded for ${account.student.name}.`)
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, setError, ['amount', 'method', 'reference', 'paidAt', 'note'])),
  })

  return (
    <Modal
      open
      title="Record payment"
      description={`Money received at the finance office from ${account.student.name}.`}
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="record-payment" loading={mutation.isPending}>
            Record payment
          </Button>
        </>
      }
    >
      <form id="record-payment" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label={`Amount (${account.currency})`} inputMode="decimal" autoComplete="off" error={errors.amount?.message} {...register('amount')} />
          <SelectField label="Method" error={errors.method?.message} {...register('method')}>
            {PAYMENT_METHODS.map((m) => (
              <option key={m} value={m}>
                {PAYMENT_METHOD_LABELS[m]}
              </option>
            ))}
          </SelectField>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Reference"
            autoComplete="off"
            hint="Receipt number or bank/mobile transaction code. Each reference can be recorded once."
            error={errors.reference?.message}
            {...register('reference')}
          />
          <TextField label="Received on" type="datetime-local" hint="Leave empty for now." error={errors.paidAt?.message} {...register('paidAt')} />
        </div>
        <TextField label="Note (optional)" error={errors.note?.message} {...register('note')} />
      </form>
    </Modal>
  )
}

function AddChargeModal({ account, onClose }: { account: FeeAccount; onClose: () => void }) {
  const toast = useToast()
  const semesters = useSemesters()
  const onUpdated = useAccountUpdate(account.student.id)
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState } = useForm<z.input<typeof chargeSchema>, unknown, z.output<typeof chargeSchema>>({
    resolver: zodResolver(chargeSchema),
    defaultValues: { description: '', amount: '', semesterId: '' },
  })
  const errors = formState.errors

  const mutation = useMutation({
    mutationFn: (v: z.output<typeof chargeSchema>) => addCharge(account.student.id, v),
    onSuccess: (updated) => {
      onUpdated(updated)
      toast.success(`Charge added to ${account.student.name}'s account.`)
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, setError, ['description', 'amount', 'semesterId'])),
  })

  return (
    <Modal
      open
      title="Add charge"
      description="A one-off fee such as a library fine or a replacement ID card. Tuition is charged from fee structures."
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="add-charge" loading={mutation.isPending}>
            Add charge
          </Button>
        </>
      }
    >
      <form id="add-charge" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextField label="Description" error={errors.description?.message} {...register('description')} />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label={`Amount (${account.currency})`} inputMode="decimal" autoComplete="off" error={errors.amount?.message} {...register('amount')} />
          <SelectField label="Semester (optional)" error={errors.semesterId?.message} {...register('semesterId')}>
            <option value="">None</option>
            {semesters.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </SelectField>
        </div>
      </form>
    </Modal>
  )
}

type Correction = { kind: 'void'; charge: FeeCharge } | { kind: 'reverse'; payment: FeePayment }

/** Voiding a charge or reversing a payment: both keep the entry in the history and need a reason. */
function CorrectionModal({ account, correction, onClose }: { account: FeeAccount; correction: Correction; onClose: () => void }) {
  const toast = useToast()
  const onUpdated = useAccountUpdate(account.student.id)
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState } = useForm<z.input<typeof reasonSchema>, unknown, z.output<typeof reasonSchema>>({
    resolver: zodResolver(reasonSchema),
    defaultValues: { reason: '' },
  })
  const isVoid = correction.kind === 'void'
  const amount = formatMoney(isVoid ? correction.charge.amount : correction.payment.amount, account.currency)

  const mutation = useMutation({
    mutationFn: ({ reason }: { reason: string }) =>
      correction.kind === 'void' ? voidCharge(correction.charge.id, reason) : reversePayment(correction.payment.id, reason),
    onSuccess: (updated) => {
      onUpdated(updated)
      toast.success(isVoid ? `Charge of ${amount} voided.` : `Payment of ${amount} reversed.`)
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, setError, ['reason'])),
  })

  return (
    <Modal
      open
      size="sm"
      title={isVoid ? 'Void charge' : 'Reverse payment'}
      description={
        isVoid
          ? `"${correction.charge.description}" (${amount}) will no longer count towards the balance. It stays in the history.`
          : `Payment ${correction.payment.reference} (${amount}) will no longer count towards the balance, for example after a bounced cheque. It stays in the history.`
      }
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button variant="danger" type="submit" form="correction" loading={mutation.isPending}>
            {isVoid ? 'Void charge' : 'Reverse payment'}
          </Button>
        </>
      }
    >
      <form id="correction" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <TextAreaField label="Reason" rows={3} error={formState.errors.reason?.message} {...register('reason')} />
      </form>
    </Modal>
  )
}

/** One student's fee account. Finance staff can record payments and charges and correct entries; others only view. */
export function FeeAccountPage() {
  const studentId = Number(useParams().studentId)
  const { hasRole } = useAuth()
  const canEdit = hasRole('FINANCE')
  const query = useQuery({ queryKey: ['fee-account', studentId], queryFn: () => getFeeAccount(studentId) })
  const [dialog, setDialog] = useState<'payment' | 'charge' | null>(null)
  const [correction, setCorrection] = useState<Correction | null>(null)
  const account = query.data

  return (
    <>
      <Link to="/finance/accounts" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:text-brand-900">
        <ArrowLeft aria-hidden="true" className="size-4" />
        Fee accounts
      </Link>
      {query.error ? (
        <>
          <PageHeader title="Fee account" />
          <Alert tone="error">{errorMessage(query.error)}</Alert>
        </>
      ) : !account ? (
        <div className="flex justify-center p-12 text-brand-700">
          <Spinner label="Loading account…" />
        </div>
      ) : (
        <>
          <PageHeader
            title={account.student.name}
            description={`${account.student.studentNumber} · ${account.student.programName} · ${account.student.email}`}
            actions={
              canEdit && (
                <div className="flex flex-wrap gap-2">
                  <Button variant="secondary" onClick={() => setDialog('charge')}>
                    <Plus aria-hidden="true" className="size-4" /> Add charge
                  </Button>
                  <Button onClick={() => setDialog('payment')}>
                    <Wallet aria-hidden="true" className="size-4" /> Record payment
                  </Button>
                </div>
              )
            }
          />
          <AccountStatement
            account={account}
            onVoidCharge={canEdit ? (charge) => setCorrection({ kind: 'void', charge }) : undefined}
            onReversePayment={canEdit ? (payment) => setCorrection({ kind: 'reverse', payment }) : undefined}
          />
          {dialog === 'payment' && <RecordPaymentModal account={account} onClose={() => setDialog(null)} />}
          {dialog === 'charge' && <AddChargeModal account={account} onClose={() => setDialog(null)} />}
          {correction && <CorrectionModal account={account} correction={correction} onClose={() => setCorrection(null)} />}
        </>
      )}
    </>
  )
}
