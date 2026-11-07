import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarRange, Pencil, Plus, Search, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { deleteOffering, searchOfferings } from '@/api/calendar'
import { errorMessage } from '@/api/errors'
import { DataTable, SortTh, TableToolbar, Td, Th } from '@/components/table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState } from '@/components/ui/EmptyState'
import { SelectField, TextField } from '@/components/ui/fields'
import { Pagination } from '@/components/ui/Pagination'
import { useToast } from '@/components/ui/toastContext'
import { useListParams } from '@/hooks/useListParams'
import { cn } from '@/lib/cn'
import { DAY_SHORT, shortTime } from '@/lib/datetime'
import type { Offering } from '@/types/api'
import { useDepartments, useSemesters } from '../queries'

const PAGE_SIZE = 20

function SeatsMeter({ enrolled, capacity }: { enrolled: number; capacity: number }) {
  const ratio = capacity === 0 ? 0 : enrolled / capacity
  return (
    <div className="min-w-24">
      <p className="text-sm tabular-nums text-neutral-700">
        {enrolled} / {capacity}
      </p>
      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-neutral-100" aria-hidden="true">
        <div
          className={cn('h-full rounded-full', ratio >= 1 ? 'bg-red-500' : ratio >= 0.85 ? 'bg-amber-500' : 'bg-emerald-500')}
          style={{ width: `${Math.min(ratio, 1) * 100}%` }}
        />
      </div>
    </div>
  )
}

export function OfferingsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const list = useListParams('code,asc')
  const semesters = useSemesters()
  const departments = useDepartments()
  const [deleting, setDeleting] = useState<Offering | null>(null)

  // Default to the current semester, falling back to the most recent one.
  const semesterId =
    list.getNumber('semesterId') ?? semesters.data?.find((s) => s.current)?.id ?? semesters.data?.[0]?.id
  const departmentId = list.getNumber('departmentId')

  const params = { semesterId, q: list.q, departmentId, sort: list.sort, page: list.page, size: PAGE_SIZE }
  const query = useQuery({
    queryKey: ['offerings', params],
    queryFn: () => searchOfferings(params),
    enabled: semesterId !== undefined,
    placeholderData: keepPreviousData,
  })

  const remove = useMutation({
    mutationFn: (o: Offering) => deleteOffering(o.id),
    onSuccess: (_, o) => {
      toast.success(`${o.course.code} section ${o.section} deleted.`)
      void queryClient.invalidateQueries({ queryKey: ['offerings'] })
      void queryClient.invalidateQueries({ queryKey: ['semesters'] })
    },
    onError: (error) => toast.error(errorMessage(error)),
    onSettled: () => setDeleting(null),
  })

  const data = query.data
  const onSort = (sort: string) => list.update({ sort })
  const noSemesters = semesters.isSuccess && semesters.data.length === 0

  return (
    <>
      <PageHeader
        title="Course offerings"
        description="Courses scheduled in a semester, with lecturer, capacity and weekly timetable."
        actions={
          <Button disabled={!semesterId} onClick={() => navigate(`/admin/offerings/new?semesterId=${semesterId}`)}>
            <Plus aria-hidden="true" className="size-4" /> New offering
          </Button>
        }
      />
      <Card>
        {noSemesters ? (
          <EmptyState
            icon={CalendarRange}
            title="Create a semester first"
            description="Offerings belong to a semester."
            action={
              <Button variant="secondary" onClick={() => navigate('/admin/semesters')}>
                Go to semesters
              </Button>
            }
          />
        ) : (
          <>
            <TableToolbar className="sm:grid-cols-[14rem_minmax(0,1fr)_14rem]">
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
                  label="Search offerings"
                  hideLabel
                  type="search"
                  placeholder="Search by course code or title"
                  className="pl-9"
                  value={list.searchInput}
                  onChange={(e) => list.setSearchInput(e.target.value)}
                />
              </div>
              <SelectField label="Department" hideLabel value={departmentId ?? ''} onChange={(e) => list.update({ departmentId: e.target.value })}>
                <option value="">All departments</option>
                {departments.data?.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </SelectField>
            </TableToolbar>
            <DataTable
              caption="Course offerings"
              isLoading={!data}
              error={query.error ?? semesters.error}
              onRetry={() => query.refetch()}
              isFetching={query.isFetching}
              isEmpty={data?.content.length === 0}
              emptyIcon={CalendarRange}
              emptyTitle={list.q || departmentId ? 'No offerings match your filters' : 'No offerings in this semester yet'}
              head={
                <>
                  <SortTh field="code" label="Course" sort={list.sort} onSort={onSort} className="min-w-48" />
                  <Th className="hidden md:table-cell">Lecturer</Th>
                  <Th className="hidden lg:table-cell">Weekly schedule</Th>
                  <SortTh field="enrolled" label="Seats" sort={list.sort} onSort={onSort} />
                  <Th>
                    <span className="sr-only">Actions</span>
                  </Th>
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
              {data?.content.map((o) => (
                <tr key={o.id} className="hover:bg-neutral-50/60">
                  <Td>
                    <Link to={`/admin/offerings/${o.id}`} className="font-medium text-brand-800 hover:underline">
                      {o.course.code} · Section {o.section}
                    </Link>
                    <p className="text-sm text-neutral-600">{o.course.title}</p>
                    <p className="text-xs text-neutral-500">
                      {o.course.credits} credits · {o.course.departmentCode}
                    </p>
                  </Td>
                  <Td className="hidden text-neutral-600 md:table-cell">
                    {o.lecturer?.displayName ?? <span className="text-amber-700">Not assigned</span>}
                  </Td>
                  <Td className="hidden text-neutral-600 lg:table-cell">
                    {o.sessions.length === 0 ? (
                      '—'
                    ) : (
                      <ul className="space-y-0.5">
                        {o.sessions.map((s, i) => (
                          <li key={i} className="whitespace-nowrap">
                            {DAY_SHORT[s.dayOfWeek]} {shortTime(s.startTime)}–{shortTime(s.endTime)} · {s.venue}
                          </li>
                        ))}
                      </ul>
                    )}
                  </Td>
                  <Td>
                    <SeatsMeter enrolled={o.enrolledCount} capacity={o.capacity} />
                  </Td>
                  <Td className="text-right whitespace-nowrap">
                    <Button variant="ghost" size="sm" onClick={() => navigate(`/admin/offerings/${o.id}`)} aria-label={`Edit ${o.course.code} section ${o.section}`}>
                      <Pencil aria-hidden="true" className="size-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-red-700 hover:bg-red-50"
                      disabled={o.enrolledCount > 0}
                      title={o.enrolledCount > 0 ? 'Offerings with enrolled students cannot be deleted' : undefined}
                      onClick={() => setDeleting(o)}
                      aria-label={`Delete ${o.course.code} section ${o.section}`}
                    >
                      <Trash2 aria-hidden="true" className="size-4" />
                    </Button>
                  </Td>
                </tr>
              ))}
            </DataTable>
          </>
        )}
      </Card>

      <ConfirmDialog
        open={deleting !== null}
        title="Delete offering?"
        description={
          <>
            <strong>
              {deleting?.course.code} section {deleting?.section}
            </strong>{' '}
            and its timetable will be removed from {deleting?.semester.name}.
          </>
        }
        confirmLabel="Delete offering"
        loading={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting)}
        onCancel={() => setDeleting(null)}
      />
    </>
  )
}
