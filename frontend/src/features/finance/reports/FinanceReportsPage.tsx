import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { Download, Receipt, Wallet } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { errorMessage } from '@/api/errors'
import { downloadOutstandingCsv, downloadPaymentsCsv, getOutstanding, getPaymentReport } from '@/api/fees'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, TableToolbar, Td, Th } from '@/components/table/DataTable'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardHeader } from '@/components/ui/Card'
import { SelectField, TextField } from '@/components/ui/fields'
import { useToast } from '@/components/ui/toastContext'
import { usePrograms } from '@/features/admin/queries'
import { formatDateTime } from '@/lib/format'
import { formatMoney, PAYMENT_METHOD_LABELS, PAYMENT_STATUS_LABELS, PAYMENT_STATUS_TONES } from '@/lib/money'

/** yyyy-mm-dd in the browser's time zone. */
function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function DownloadButton({ onDownload, disabled }: { onDownload: () => Promise<void>; disabled?: boolean }) {
  const toast = useToast()
  const download = useMutation({ mutationFn: onDownload, onError: (error) => toast.error(errorMessage(error)) })
  return (
    <Button variant="secondary" size="sm" onClick={() => download.mutate()} loading={download.isPending} disabled={disabled}>
      <Download aria-hidden="true" className="size-4" /> CSV
    </Button>
  )
}

function OutstandingCard() {
  const programs = usePrograms()
  const [programId, setProgramId] = useState<number | undefined>()
  const query = useQuery({
    queryKey: ['fee-reports', 'outstanding', programId],
    queryFn: () => getOutstanding(programId),
    placeholderData: keepPreviousData,
  })
  const rows = query.data

  return (
    <Card>
      <CardHeader
        title="Outstanding balances"
        description={rows ? `${rows.length} student(s) owe money, largest balance first.` : 'Students who owe money.'}
        actions={<DownloadButton onDownload={() => downloadOutstandingCsv(programId)} disabled={rows?.length === 0} />}
      />
      <TableToolbar className="sm:grid-cols-[16rem]">
        <SelectField
          label="Program"
          hideLabel
          value={programId ?? ''}
          onChange={(e) => setProgramId(e.target.value === '' ? undefined : Number(e.target.value))}
        >
          <option value="">All programs</option>
          {programs.data?.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </SelectField>
      </TableToolbar>
      <DataTable
        caption="Outstanding balances"
        isLoading={!rows}
        error={query.error}
        onRetry={() => query.refetch()}
        isFetching={query.isFetching}
        isEmpty={rows?.length === 0}
        emptyIcon={Wallet}
        emptyTitle="Nobody owes money"
        head={
          <>
            <Th>Student</Th>
            <Th className="hidden sm:table-cell">Program</Th>
            <Th className="hidden text-right md:table-cell">Charged</Th>
            <Th className="hidden text-right md:table-cell">Paid</Th>
            <Th className="text-right">Balance</Th>
          </>
        }
      >
        {rows?.map((r) => (
          <tr key={r.studentId} className="hover:bg-slate-50/60">
            <Td>
              <Link to={`/finance/accounts/${r.studentId}`} className="font-medium text-brand-800 hover:underline">
                {r.name}
              </Link>
              <p className="text-xs text-slate-500">{r.studentNumber}</p>
            </Td>
            <Td className="hidden text-slate-600 sm:table-cell">{r.programCode}</Td>
            <Td className="hidden text-right tabular-nums md:table-cell">{formatMoney(r.totalCharged, r.currency)}</Td>
            <Td className="hidden text-right tabular-nums md:table-cell">{formatMoney(r.totalPaid, r.currency)}</Td>
            <Td className="text-right font-medium whitespace-nowrap text-red-700 tabular-nums">{formatMoney(r.balance, r.currency)}</Td>
          </tr>
        ))}
      </DataTable>
    </Card>
  )
}

function PaymentsCard() {
  const [from, setFrom] = useState(() => {
    const now = new Date()
    return isoDate(new Date(now.getFullYear(), now.getMonth(), 1))
  })
  const [to, setTo] = useState(() => isoDate(new Date()))
  const rangeError = !from || !to ? 'Choose both dates.' : from > to ? 'The start date must be on or before the end date.' : null
  const query = useQuery({
    queryKey: ['fee-reports', 'payments', from, to],
    queryFn: () => getPaymentReport(from, to),
    enabled: rangeError === null,
    placeholderData: keepPreviousData,
  })
  const report = query.data

  return (
    <Card>
      <CardHeader
        title="Payments received"
        description={
          report
            ? `Completed in this period: ${formatMoney(report.totalCompleted, report.currency)}. Declined and reversed payments are listed but not counted.`
            : 'Payments in a date range (UTC).'
        }
        actions={<DownloadButton onDownload={() => downloadPaymentsCsv(from, to)} disabled={rangeError !== null || report?.rows.length === 0} />}
      />
      <TableToolbar className="sm:grid-cols-[12rem_12rem]">
        <TextField label="From" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} error={rangeError ?? undefined} />
        <TextField label="To" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
      </TableToolbar>
      <DataTable
        caption="Payments"
        isLoading={rangeError === null && !report}
        error={query.error}
        onRetry={() => query.refetch()}
        isFetching={query.isFetching}
        isEmpty={rangeError !== null || report?.rows.length === 0}
        emptyIcon={Receipt}
        emptyTitle={rangeError ? 'Choose a valid date range' : 'No payments in this period'}
        head={
          <>
            <Th>Paid</Th>
            <Th>Student</Th>
            <Th className="hidden md:table-cell">Reference</Th>
            <Th className="text-right">Amount</Th>
            <Th>Status</Th>
          </>
        }
      >
        {report?.rows.map((r) => (
          <tr key={r.paymentId} className="hover:bg-slate-50/60">
            <Td className="whitespace-nowrap text-slate-600">{formatDateTime(r.paidAt)}</Td>
            <Td>
              <p className="font-medium text-slate-900">{r.name}</p>
              <p className="text-xs text-slate-500">{r.studentNumber}</p>
            </Td>
            <Td className="hidden md:table-cell">
              <p className="font-mono text-xs text-slate-900">{r.reference}</p>
              <p className="text-xs text-slate-500">
                {PAYMENT_METHOD_LABELS[r.method]} · {r.channel === 'ONLINE' ? 'Online' : 'Office'}
              </p>
            </Td>
            <Td className="text-right whitespace-nowrap tabular-nums">{formatMoney(r.amount, report.currency)}</Td>
            <Td>
              <Badge tone={PAYMENT_STATUS_TONES[r.status]}>{PAYMENT_STATUS_LABELS[r.status]}</Badge>
            </Td>
          </tr>
        ))}
      </DataTable>
    </Card>
  )
}

/** Outstanding balances and payments received, each downloadable as CSV for a spreadsheet. */
export function FinanceReportsPage() {
  return (
    <>
      <PageHeader title="Finance reports" description="Who owes money and what has been received." />
      <div className="space-y-4">
        <OutstandingCard />
        <PaymentsCard />
      </div>
    </>
  )
}
