import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Search, Wallet } from 'lucide-react'
import { Link } from 'react-router'
import { searchFeeAccounts } from '@/api/fees'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, SortTh, TableToolbar, Td, Th } from '@/components/table/DataTable'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { SelectField, TextField } from '@/components/ui/fields'
import { Pagination } from '@/components/ui/Pagination'
import { usePrograms } from '@/features/admin/queries'
import { useListParams } from '@/hooks/useListParams'
import { balanceStatus, formatMoney } from '@/lib/money'

const PAGE_SIZE = 20

/** Every student with their fee balance; owing students first by default. */
export function FeeAccountsPage() {
  const list = useListParams('balance,desc')
  const programs = usePrograms()
  const programId = list.getNumber('programId')
  const balanceFilter = list.get('balance')
  const hasBalance = balanceFilter === 'owing' ? true : balanceFilter === 'clear' ? false : undefined

  const params = { q: list.q, programId, hasBalance, sort: list.sort, page: list.page, size: PAGE_SIZE }
  const query = useQuery({ queryKey: ['fee-accounts', params], queryFn: () => searchFeeAccounts(params), placeholderData: keepPreviousData })
  const data = query.data
  const onSort = (sort: string) => list.update({ sort })

  return (
    <>
      <PageHeader title="Fee accounts" description="Balances for every student. Open an account to record payments or charges." />
      <Card>
        <TableToolbar className="sm:grid-cols-[minmax(0,1fr)_16rem_12rem]">
          <div className="relative">
            <Search aria-hidden="true" className="pointer-events-none absolute top-2.5 left-3 size-4 text-neutral-400" />
            <TextField
              label="Search students"
              hideLabel
              type="search"
              placeholder="Name, email or student number"
              className="pl-9"
              value={list.searchInput}
              onChange={(e) => list.setSearchInput(e.target.value)}
            />
          </div>
          <SelectField label="Program" hideLabel value={programId ?? ''} onChange={(e) => list.update({ programId: e.target.value })}>
            <option value="">All programs</option>
            {programs.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </SelectField>
          <SelectField label="Balance" hideLabel value={balanceFilter} onChange={(e) => list.update({ balance: e.target.value })}>
            <option value="">Any balance</option>
            <option value="owing">Owing money</option>
            <option value="clear">Settled or in credit</option>
          </SelectField>
        </TableToolbar>
        <DataTable
          caption="Fee accounts"
          isLoading={!data}
          error={query.error}
          onRetry={() => query.refetch()}
          isFetching={query.isFetching}
          isEmpty={data?.content.length === 0}
          emptyIcon={list.hasFilters ? Search : Wallet}
          emptyTitle={list.hasFilters ? 'No accounts match your filters' : 'No students yet'}
          emptyAction={
            list.hasFilters ? (
              <Button variant="secondary" onClick={list.clear}>
                Clear filters
              </Button>
            ) : undefined
          }
          head={
            <>
              <SortTh field="name" label="Student" sort={list.sort} onSort={onSort} />
              <Th className="hidden md:table-cell">Program</Th>
              <Th className="hidden text-right lg:table-cell">Charged</Th>
              <Th className="hidden text-right lg:table-cell">Paid</Th>
              <SortTh field="balance" label="Balance" sort={list.sort} onSort={onSort} className="text-right" />
            </>
          }
          footer={
            data && (
              <Pagination
                page={data.page}
                size={data.size}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                onPageChange={(page) => list.update({ page })}
              />
            )
          }
        >
          {data?.content.map((a) => {
            const status = balanceStatus(a.balance)
            return (
              <tr key={a.studentId} className="hover:bg-neutral-50/60">
                <Td>
                  <Link to={`/finance/accounts/${a.studentId}`} className="font-medium text-brand-800 hover:underline">
                    {a.name}
                  </Link>
                  <p className="text-xs text-neutral-500">
                    {a.studentNumber} · {a.email}
                  </p>
                </Td>
                <Td className="hidden text-neutral-600 md:table-cell">{a.programCode}</Td>
                <Td className="hidden text-right tabular-nums lg:table-cell">{formatMoney(a.totalCharged, a.currency)}</Td>
                <Td className="hidden text-right tabular-nums lg:table-cell">{formatMoney(a.totalPaid, a.currency)}</Td>
                <Td className="text-right whitespace-nowrap">
                  <p className={`font-medium tabular-nums ${status.tone}`}>{formatMoney(status.amount, a.currency)}</p>
                  <p className="text-xs text-neutral-500">{status.label}</p>
                </Td>
              </tr>
            )
          })}
        </DataTable>
      </Card>
    </>
  )
}
