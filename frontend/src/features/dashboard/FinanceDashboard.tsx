import { useQuery } from '@tanstack/react-query'
import { BarChart3, Receipt, Wallet } from 'lucide-react'
import { Link } from 'react-router'
import { getFinanceDashboard } from '@/api/dashboards'
import { errorMessage } from '@/api/errors'
import { Alert } from '@/components/ui/Alert'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { formatMoney, PAYMENT_METHOD_LABELS } from '@/lib/money'
import { BarList, DashboardLoading, Meter, StatRow, StatTile } from './figures'
import { compactNumber, longDay, shortDay } from './periods'
import { TimeChart } from './TimeChart'

export function FinanceDashboard() {
  const query = useQuery({ queryKey: ['dashboard', 'finance'], queryFn: getFinanceDashboard })

  if (query.error) return <Alert tone="error">{errorMessage(query.error)}</Alert>
  if (!query.data) return <DashboardLoading label="Loading the dashboard…" />
  const d = query.data
  const weeks = d.weeklyCollections.map((w) => ({
    label: shortDay(w.weekStart),
    period: `Week of ${longDay(w.weekStart)}`,
    value: w.amount,
    text: formatMoney(w.amount, d.currency),
  }))

  return (
    <div className="space-y-6">
      <StatRow label="Collections summary">
        <StatTile
          label="Outstanding"
          value={formatMoney(d.outstanding, d.currency)}
          hint={`${d.studentsOwing.toLocaleString()} ${d.studentsOwing === 1 ? 'student owes' : 'students owe'}`}
          link={{ to: '/finance/reports', label: 'Outstanding report' }}
        />
        <StatTile label="Collected, last 30 days" value={formatMoney(d.collectedLast30Days, d.currency)} />
        <StatTile label="Collected, all time" value={formatMoney(d.totalCollected, d.currency)} hint={`Of ${formatMoney(d.totalCharged, d.currency)} charged`} />
        <StatTile
          label="Collection rate"
          value={d.collectionRate === null ? '—' : `${d.collectionRate.toFixed(1)}%`}
          hint={
            d.collectionRate === null ? (
              'Appears once fees are charged'
            ) : (
              <Meter label="Collection rate" value={d.collectionRate} max={100} text="Collected as a share of charged" />
            )
          }
        />
      </StatRow>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <TimeChart
            title="Collections by week"
            description={`Completed payments, last 12 weeks, in ${d.currency}.`}
            kind="column"
            data={weeks}
            periodHeading="Week (UTC)"
            valueHeading="Collected"
            formatTick={compactNumber}
            empty={
              weeks.every((w) => w.value === 0) ? (
                <EmptyState icon={BarChart3} title="No payments in the last 12 weeks" description="Completed payments appear here by week." />
              ) : undefined
            }
          />
        </div>
        <Card>
          <CardHeader title="Payment methods" description="Completed payments, last 30 days." />
          {d.methodsLast30Days.length === 0 ? (
            <EmptyState icon={Receipt} title="No payments yet" />
          ) : (
            <CardBody>
              <BarList
                label="Payment methods"
                rows={d.methodsLast30Days.map((m) => ({
                  key: m.method,
                  label: PAYMENT_METHOD_LABELS[m.method],
                  value: m.amount,
                  text: formatMoney(m.amount, d.currency),
                }))}
              />
            </CardBody>
          )}
        </Card>
      </div>

      <Card>
        <CardHeader
          title="Largest balances"
          actions={
            <Link to="/finance/accounts" className="text-xs font-medium text-brand-700 hover:text-brand-900">
              All fee accounts →
            </Link>
          }
        />
        {d.largestBalances.length === 0 ? (
          <EmptyState icon={Wallet} title="Nobody owes anything" />
        ) : (
          <ul aria-label="Largest balances" className="divide-y divide-slate-100">
            {d.largestBalances.map((a) => (
              <li key={a.studentId} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5 px-5 py-3 text-sm">
                <Link to={`/finance/accounts/${a.studentId}`} className="min-w-0 font-medium text-slate-900 hover:text-brand-800">
                  {a.name} <span className="font-normal text-slate-500">{a.studentNumber} · {a.programCode}</span>
                </Link>
                <span className="font-medium text-slate-900 tabular-nums">{formatMoney(a.balance, a.currency)}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
