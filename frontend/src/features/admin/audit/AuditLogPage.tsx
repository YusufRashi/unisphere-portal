import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { History, Search } from 'lucide-react'
import { getAuditFilters, searchAuditLogs } from '@/api/audit'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, TableToolbar, Td, Th } from '@/components/table/DataTable'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Pagination } from '@/components/ui/Pagination'
import { SelectField, TextField } from '@/components/ui/fields'
import { useListParams } from '@/hooks/useListParams'
import { formatDateTime } from '@/lib/format'

const PAGE_SIZE = 25

/** "REGISTRATION_APPROVED" → "Registration approved" */
function actionLabel(action: string): string {
  const words = action.toLowerCase().replaceAll('_', ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

function detailText(value: unknown): string {
  return typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value)
}

/** Who did what and when (ADM-4). Read-only: entries cannot be changed or removed. */
export function AuditLogPage() {
  const list = useListParams('')
  const action = list.get('action')
  const entityType = list.get('entityType')
  const from = list.get('from')
  const to = list.get('to')
  const filters = useQuery({ queryKey: ['admin', 'audit-filters'], queryFn: getAuditFilters })

  const params = { actor: list.q, action, entityType, from, to, page: list.page, size: PAGE_SIZE }
  const query = useQuery({
    queryKey: ['admin', 'audit-logs', params],
    queryFn: () => searchAuditLogs(params),
    placeholderData: keepPreviousData,
  })
  const data = query.data

  return (
    <>
      <PageHeader title="Audit log" description="Sign-ins, account changes, registration, results and payment actions. Times are in your time zone; the date filters use UTC." />
      <Card>
        <TableToolbar className="sm:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_14rem_11rem_10rem_10rem_auto]">
          <div className="relative">
            <Search aria-hidden="true" className="pointer-events-none absolute top-2.5 left-3 size-4 text-slate-400" />
            <TextField
              label="Search by name or email"
              hideLabel
              type="search"
              placeholder="Person"
              className="pl-9"
              value={list.searchInput}
              onChange={(e) => list.setSearchInput(e.target.value)}
            />
          </div>
          <SelectField label="Action" hideLabel value={action} onChange={(e) => list.update({ action: e.target.value })}>
            <option value="">All actions</option>
            {filters.data?.actions.map((a) => (
              <option key={a} value={a}>
                {actionLabel(a)}
              </option>
            ))}
          </SelectField>
          <SelectField label="Record type" hideLabel value={entityType} onChange={(e) => list.update({ entityType: e.target.value })}>
            <option value="">All records</option>
            {filters.data?.entityTypes.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </SelectField>
          <TextField label="From date" hideLabel type="date" value={from} max={to || undefined} onChange={(e) => list.update({ from: e.target.value })} />
          <TextField label="To date" hideLabel type="date" value={to} min={from || undefined} onChange={(e) => list.update({ to: e.target.value })} />
          {list.hasFilters && (
            <Button variant="ghost" onClick={list.clear}>
              Clear
            </Button>
          )}
        </TableToolbar>
        <DataTable
          caption="Audit log"
          isLoading={!data}
          error={query.error}
          onRetry={() => query.refetch()}
          isFetching={query.isFetching}
          isEmpty={data?.content.length === 0}
          emptyIcon={History}
          emptyTitle={list.hasFilters ? 'No entries match your filters' : 'No entries yet'}
          head={
            <>
              <Th>When</Th>
              <Th>Who</Th>
              <Th>Action</Th>
              <Th>Record</Th>
              <Th>Details</Th>
              <Th className="hidden xl:table-cell">IP address</Th>
            </>
          }
          footer={
            data && (
              <Pagination page={data.page} size={data.size} totalPages={data.totalPages} totalElements={data.totalElements} onPageChange={(page) => list.update({ page })} />
            )
          }
        >
          {data?.content.map((e) => (
            <tr key={e.id} className="hover:bg-slate-50/60">
              <Td className="whitespace-nowrap text-slate-600">{formatDateTime(e.createdAt)}</Td>
              <Td>
                {e.actor ? (
                  <>
                    <p className="font-medium text-slate-900">{e.actor.name}</p>
                    <p className="text-xs text-slate-500">{e.actor.email}</p>
                  </>
                ) : (
                  <span className="text-slate-500">System</span>
                )}
              </Td>
              <Td className="whitespace-nowrap text-slate-900">{actionLabel(e.action)}</Td>
              <Td className="whitespace-nowrap text-slate-600">{e.entityType ? `${e.entityType}${e.entityId ? ` #${e.entityId}` : ''}` : '—'}</Td>
              <Td className="min-w-48 text-xs break-words text-slate-600">
                {Object.entries(e.details).map(([key, value]) => (
                  <p key={key}>
                    <span className="text-slate-500">{key}:</span> {detailText(value)}
                  </p>
                ))}
              </Td>
              <Td className="hidden whitespace-nowrap text-slate-500 tabular-nums xl:table-cell">{e.ipAddress ?? '—'}</Td>
            </tr>
          ))}
        </DataTable>
      </Card>
    </>
  )
}
