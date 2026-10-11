import { Receipt, Undo2, Wallet, XCircle } from 'lucide-react'
import type { ReactNode } from 'react'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardHeader } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { cn } from '@/lib/cn'
import { formatDate, formatDateTime } from '@/lib/format'
import { balanceStatus, formatMoney, PAYMENT_METHOD_LABELS, PAYMENT_STATUS_LABELS, PAYMENT_STATUS_TONES } from '@/lib/money'
import type { FeeAccount, FeeCharge, FeePayment, FeeSummary } from '@/types/api'

const headClass = 'px-5 py-2.5 text-left text-xs font-semibold tracking-wide text-slate-600 uppercase'

export function SummaryTiles({ summary, currency }: { summary: FeeSummary; currency: string }) {
  const status = balanceStatus(summary.balance)
  const tiles: [string, string, string?][] = [
    ['Total charged', formatMoney(summary.totalCharged, currency)],
    ['Total paid', formatMoney(summary.totalPaid, currency)],
    [status.label, formatMoney(status.amount, currency), status.tone],
  ]
  return (
    <dl className="grid gap-4 sm:grid-cols-3">
      {tiles.map(([label, value, tone]) => (
        <div key={label} className="rounded-lg border border-slate-200 bg-white px-5 py-4 shadow-xs">
          <dt className="text-sm text-slate-500">{label}</dt>
          <dd className={cn('mt-1 text-2xl font-semibold tabular-nums', tone ?? 'text-slate-900')}>{value}</dd>
        </div>
      ))}
    </dl>
  )
}

function ChargesTable({ charges, currency, onVoid }: {
  charges: FeeCharge[]
  currency: string
  onVoid?: (charge: FeeCharge) => void
}) {
  return (
    <Card>
      <CardHeader title="Charges" description="Fees billed to the account, newest first." />
      {charges.length === 0 ? (
        <EmptyState icon={Receipt} title="No charges yet" description="Tuition is charged once the student is registered for a semester." />
      ) : (
        <div className="relative overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <caption className="sr-only">Charges</caption>
            <thead className="bg-slate-50">
              <tr>
                <th scope="col" className={headClass}>Date</th>
                <th scope="col" className={headClass}>Description</th>
                <th scope="col" className={cn(headClass, 'text-right')}>Amount</th>
                <th scope="col" className={headClass}>Status</th>
                {onVoid && (
                  <th scope="col" className={headClass}>
                    <span className="sr-only">Actions</span>
                  </th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {charges.map((c) => {
                const voided = c.status === 'VOIDED'
                return (
                  <tr key={c.id}>
                    <td className="px-5 py-3 align-top whitespace-nowrap text-slate-600">{formatDate(c.createdAt)}</td>
                    <td className="px-5 py-3 align-top">
                      <p className={cn('font-medium text-slate-900', voided && 'text-slate-500 line-through')}>{c.description}</p>
                      <p className="text-xs text-slate-500">
                        {c.kind === 'TUITION' ? 'Tuition' : 'Manual charge'}
                        {c.semester && ` · ${c.semester.name}`}
                      </p>
                      {voided && (
                        <p className="mt-1 text-xs text-slate-600">
                          Voided {formatDate(c.voidedAt)}: {c.voidReason}
                        </p>
                      )}
                    </td>
                    <td className={cn('px-5 py-3 text-right align-top whitespace-nowrap tabular-nums', voided && 'text-slate-500 line-through')}>
                      {formatMoney(c.amount, currency)}
                    </td>
                    <td className="px-5 py-3 align-top">
                      <Badge tone={voided ? 'neutral' : 'brand'}>{voided ? 'Voided' : 'Active'}</Badge>
                    </td>
                    {onVoid && (
                      <td className="px-5 py-3 text-right align-top">
                        {!voided && (
                          <Button variant="ghost" size="sm" onClick={() => onVoid(c)} aria-label={`Void charge ${c.description}`}>
                            <XCircle aria-hidden="true" className="size-4" /> Void
                          </Button>
                        )}
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

function PaymentsTable({ payments, currency, onReverse }: {
  payments: FeePayment[]
  currency: string
  onReverse?: (payment: FeePayment) => void
}) {
  return (
    <Card>
      <CardHeader title="Payments" description="Money received, newest first. Declined and reversed payments do not count." />
      {payments.length === 0 ? (
        <EmptyState icon={Wallet} title="No payments yet" />
      ) : (
        <div className="relative overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <caption className="sr-only">Payments</caption>
            <thead className="bg-slate-50">
              <tr>
                <th scope="col" className={headClass}>Paid</th>
                <th scope="col" className={headClass}>Reference</th>
                <th scope="col" className={cn(headClass, 'text-right')}>Amount</th>
                <th scope="col" className={headClass}>Status</th>
                {onReverse && (
                  <th scope="col" className={headClass}>
                    <span className="sr-only">Actions</span>
                  </th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {payments.map((p) => {
                const counted = p.status === 'COMPLETED'
                return (
                  <tr key={p.id}>
                    <td className="px-5 py-3 align-top whitespace-nowrap text-slate-600">{formatDateTime(p.paidAt)}</td>
                    <td className="px-5 py-3 align-top">
                      <p className="font-mono text-xs font-medium text-slate-900">{p.reference}</p>
                      <p className="text-xs text-slate-500">
                        {PAYMENT_METHOD_LABELS[p.method]} · {p.channel === 'ONLINE' ? 'Online' : 'Finance office'}
                      </p>
                      {p.note && <p className="mt-1 text-xs text-slate-600">{p.note}</p>}
                      {p.failureReason && <p className="mt-1 text-xs text-red-700">{p.failureReason}</p>}
                      {p.status === 'REVERSED' && (
                        <p className="mt-1 text-xs text-slate-600">
                          Reversed {formatDate(p.reversedAt)}: {p.reversalReason}
                        </p>
                      )}
                    </td>
                    <td className={cn('px-5 py-3 text-right align-top whitespace-nowrap tabular-nums', !counted && 'text-slate-500 line-through')}>
                      {formatMoney(p.amount, currency)}
                    </td>
                    <td className="px-5 py-3 align-top">
                      <Badge tone={PAYMENT_STATUS_TONES[p.status]}>{PAYMENT_STATUS_LABELS[p.status]}</Badge>
                    </td>
                    {onReverse && (
                      <td className="px-5 py-3 text-right align-top">
                        {counted && (
                          <Button variant="ghost" size="sm" onClick={() => onReverse(p)} aria-label={`Reverse payment ${p.reference}`}>
                            <Undo2 aria-hidden="true" className="size-4" /> Reverse
                          </Button>
                        )}
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

/** Totals, charges and payments of one fee account. Row actions appear only when handlers are given. */
export function AccountStatement({ account, onVoidCharge, onReversePayment, children }: {
  account: FeeAccount
  onVoidCharge?: (charge: FeeCharge) => void
  onReversePayment?: (payment: FeePayment) => void
  /** Shown between the totals and the tables (e.g. a payment call to action). */
  children?: ReactNode
}) {
  return (
    <div className="space-y-4">
      <SummaryTiles summary={account.summary} currency={account.currency} />
      {children}
      <ChargesTable charges={account.charges} currency={account.currency} onVoid={onVoidCharge} />
      <PaymentsTable payments={account.payments} currency={account.currency} onReverse={onReversePayment} />
    </div>
  )
}
