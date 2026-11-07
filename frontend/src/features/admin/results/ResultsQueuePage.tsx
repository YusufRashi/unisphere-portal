import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { FileCheck2, Search } from 'lucide-react'
import { Link } from 'react-router'
import { searchResults } from '@/api/results'
import { DataTable, SortTh, TableToolbar, Td, Th } from '@/components/table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { Badge } from '@/components/ui/Badge'
import { Card } from '@/components/ui/Card'
import { SelectField, TextField } from '@/components/ui/fields'
import { Pagination } from '@/components/ui/Pagination'
import { RESULTS_STATUS_LABELS, RESULTS_STATUS_TONES } from '@/features/results/labels'
import { useListParams } from '@/hooks/useListParams'
import { cn } from '@/lib/cn'
import { formatDateTime } from '@/lib/format'
import { RESULTS_STATUSES, type ResultsStatus } from '@/types/api'
import { useSemesters } from '../queries'

const PAGE_SIZE = 20

/** Every offering of a semester with its marks progress; submitted ones are reviewed and finalized from here. */
export function ResultsQueuePage() {
  const list = useListParams('code')
  const semesters = useSemesters()
  const semesterId = list.getNumber('semesterId') ?? semesters.data?.find((s) => s.current)?.id ?? semesters.data?.[0]?.id
  const status = (list.get('status') || undefined) as ResultsStatus | undefined
  const params = { semesterId, status, q: list.q, sort: list.sort, page: list.page, size: PAGE_SIZE }
  const query = useQuery({
    queryKey: ['admin', 'results', params],
    queryFn: () => searchResults(params),
    enabled: semesterId !== undefined,
    placeholderData: keepPreviousData,
  })
  const data = query.data
  const onSort = (sort: string) => list.update({ sort })

  return (
    <>
      <PageHeader title="Results" description="Review submitted marks, then finalize them or return them to the lecturer." />
      <Card>
        <TableToolbar className="sm:grid-cols-[14rem_minmax(0,1fr)_12rem]">
          <SelectField label="Semester" hideLabel value={semesterId ?? ''} onChange={(e) => list.update({ semesterId: e.target.value })}>
            {semesters.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
                {s.current ? ' (current)' : ''}
              </option>
            ))}
          </SelectField>
          <div className="relative">
            <Search aria-hidden="true" className="pointer-events-none absolute top-2.5 left-3 size-4 text-neutral-400" />
            <TextField
              label="Search courses"
              hideLabel
              type="search"
              placeholder="Course code or title"
              className="pl-9"
              value={list.searchInput}
              onChange={(e) => list.setSearchInput(e.target.value)}
            />
          </div>
          <SelectField label="Results status" hideLabel value={status ?? ''} onChange={(e) => list.update({ status: e.target.value })}>
            <option value="">All statuses</option>
            {RESULTS_STATUSES.map((s) => (
              <option key={s} value={s}>
                {RESULTS_STATUS_LABELS[s]}
              </option>
            ))}
          </SelectField>
        </TableToolbar>
        <DataTable
          caption="Results by course"
          isLoading={!data}
          error={query.error ?? semesters.error}
          onRetry={() => query.refetch()}
          isFetching={query.isFetching}
          isEmpty={data?.content.length === 0}
          emptyIcon={FileCheck2}
          emptyTitle={list.hasFilters ? 'No courses match your filters' : 'No courses offered in this semester'}
          head={
            <>
              <SortTh field="code" label="Course" sort={list.sort} onSort={onSort} />
              <Th className="hidden md:table-cell">Lecturer</Th>
              <Th className="text-right">Marked</Th>
              <SortTh field="status" label="Status" sort={list.sort} onSort={onSort} />
              <SortTh field="submittedAt" label="Submitted" sort={list.sort} onSort={onSort} className="hidden lg:table-cell" />
              <Th>
                <span className="sr-only">Actions</span>
              </Th>
            </>
          }
          footer={
            data && (
              <Pagination page={data.page} size={data.size} totalPages={data.totalPages} totalElements={data.totalElements} onPageChange={(page) => list.update({ page })} />
            )
          }
        >
          {data?.content.map((r) => {
            const pending = r.resultsStatus === 'SUBMITTED'
            return (
              <tr key={r.offeringId} className="hover:bg-neutral-50/60">
                <Td>
                  <p className="font-medium text-neutral-900">
                    {r.courseCode} · {r.section}
                  </p>
                  <p className="text-xs text-neutral-500">{r.courseTitle}</p>
                </Td>
                <Td className="hidden text-neutral-600 md:table-cell">{r.lecturer ?? 'Unassigned'}</Td>
                <Td className="text-right whitespace-nowrap tabular-nums">
                  {r.marksComplete} / {r.classSize}
                </Td>
                <Td>
                  <Badge tone={RESULTS_STATUS_TONES[r.resultsStatus]}>{RESULTS_STATUS_LABELS[r.resultsStatus]}</Badge>
                </Td>
                <Td className="hidden whitespace-nowrap text-neutral-600 lg:table-cell">{formatDateTime(r.submittedAt)}</Td>
                <Td className="text-right">
                  <Link
                    to={`/admin/results/${r.offeringId}`}
                    aria-label={`${pending ? 'Review' : 'View'} results for ${r.courseCode} section ${r.section}`}
                    className={cn(
                      'inline-flex h-8 items-center rounded-md px-3 text-xs font-medium',
                      pending ? 'bg-brand-800 text-white hover:bg-brand-900' : 'border border-neutral-300 bg-surface text-neutral-800 hover:bg-neutral-50',
                    )}
                  >
                    {pending ? 'Review' : 'View'}
                  </Link>
                </Td>
              </tr>
            )
          })}
        </DataTable>
      </Card>
    </>
  )
}
