import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CreditCard } from 'lucide-react'
import { useState } from 'react'
import { errorMessage, toApiError } from '@/api/errors'
import { getMyFees, payOnline } from '@/api/fees'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Card, CardBody } from '@/components/ui/Card'
import { SelectField, TextField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { AccountStatement } from '@/features/finance/AccountStatement'
import { amountError, formatMoney, PAYMENT_METHOD_LABELS, toAmount } from '@/lib/money'
import { ONLINE_METHODS, type FeeAccount, type FeePayment, type PaymentMethod } from '@/types/api'

/**
 * Online payment. One idempotency key belongs to one attempt: it is kept while the outcome is unknown (a
 * network error, so a retry cannot pay twice) and replaced once the server has answered.
 */
function PayOnlineModal({ account, onClose }: { account: FeeAccount; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const balance = account.summary.balance
  const [amount, setAmount] = useState(balance.toFixed(2))
  const [method, setMethod] = useState<PaymentMethod>('MOBILE_MONEY')
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())
  const [touched, setTouched] = useState(false)
  const [declined, setDeclined] = useState<FeePayment | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const validation = amountError(amount, balance)

  const mutation = useMutation({
    mutationFn: () => payOnline({ amount: toAmount(amount), method, idempotencyKey }),
    onSuccess: (result) => {
      setIdempotencyKey(crypto.randomUUID())
      queryClient.setQueryData(['fees', 'me'], result.account)
      if (result.payment.status === 'COMPLETED') {
        toast.success(`Payment of ${formatMoney(result.payment.amount, account.currency)} received. Reference ${result.payment.reference}.`)
        onClose()
      } else {
        setDeclined(result.payment)
      }
    },
    onError: (error) => {
      // A response means the server decided; without one the payment may or may not have gone through.
      if (toApiError(error).status !== 0) setIdempotencyKey(crypto.randomUUID())
      setFormError(errorMessage(error))
    },
  })

  function submit() {
    setTouched(true)
    setDeclined(null)
    setFormError(null)
    if (!validation) mutation.mutate()
  }

  return (
    <Modal
      open
      title="Pay online"
      description={`Outstanding balance: ${formatMoney(balance, account.currency)}. You can pay all of it or part of it.`}
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="pay-online" loading={mutation.isPending}>
            {declined ? 'Try again' : 'Pay now'}
          </Button>
        </>
      }
    >
      <form
        id="pay-online"
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
        className="space-y-4"
      >
        {declined && (
          <Alert tone="error" title="Payment declined">
            {declined.failureReason ?? 'The provider declined the payment. No money was taken.'}
          </Alert>
        )}
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label={`Amount (${account.currency})`}
            inputMode="decimal"
            autoComplete="off"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            onBlur={() => setTouched(true)}
            error={touched ? (validation ?? undefined) : undefined}
          />
          <SelectField label="Pay with" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
            {ONLINE_METHODS.map((m) => (
              <option key={m} value={m}>
                {PAYMENT_METHOD_LABELS[m]}
              </option>
            ))}
          </SelectField>
        </div>
        <p className="text-xs text-slate-500">
          This is a simulated payment provider for demonstration: no real money moves. Amounts ending in .13 are declined so the
          failure case can be tried.
        </p>
      </form>
    </Modal>
  )
}

/** The student's fee account: what was charged, what was paid and what is still owed. */
export function FeesPage() {
  const query = useQuery({ queryKey: ['fees', 'me'], queryFn: getMyFees })
  const [paying, setPaying] = useState(false)

  if (query.error) {
    return (
      <>
        <PageHeader title="Fees" />
        <Alert tone="error">{errorMessage(query.error)}</Alert>
      </>
    )
  }
  if (!query.data) {
    return (
      <div className="flex justify-center p-12 text-brand-700">
        <Spinner label="Loading fees…" />
      </div>
    )
  }

  const account = query.data
  const owing = account.summary.balance > 0
  return (
    <>
      <PageHeader
        title="Fees"
        description={`${account.student.studentNumber} · ${account.student.programName}`}
        actions={
          owing && (
            <Button onClick={() => setPaying(true)}>
              <CreditCard aria-hidden="true" className="size-4" /> Pay online
            </Button>
          )
        }
      />
      <AccountStatement account={account}>
        {!owing && account.charges.length > 0 && (
          <Card>
            <CardBody className="text-sm text-slate-600">
              {account.summary.balance < 0
                ? 'You have paid more than you were charged. The credit is applied to your next fees.'
                : 'Your account is fully paid. Thank you.'}
            </CardBody>
          </Card>
        )}
      </AccountStatement>
      {paying && <PayOnlineModal account={account} onClose={() => setPaying(false)} />}
    </>
  )
}
