import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ClipboardCheck, Search } from 'lucide-react'
import { useState } from 'react'
import { errorMessage } from '@/api/errors'
import { approveRegistration, getRegistration, rejectRegistration, searchRegistrations } from '@/api/registration'
import { DataTable, SortTh, TableToolbar, Td, Th } from '@/components/table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { SelectField, TextAreaField, TextField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { useListParams } from '@/hooks/useListParams'
import { formatDateTime } from '@/lib/format'
import { REGISTRATION_STATUSES, type RegistrationStatus, type RegistrationSummary } from '@/types/api'
import { REGISTRATION_STATUS_LABELS, REGISTRATION_STATUS_TONES } from '@/features/student/registration/labels'
import { useSemesters } from '../queries'

const PAGE_SIZE = 20

function ReviewModal({ summary, onClose }: { summary: RegistrationSummary; onClose: () => void }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | null>(null)
  const detail = useQuery({ queryKey: ['registration', summary.id], queryFn: () => getRegistration(summary.id) })

  const decide = useMutation({
    mutationFn: (action: 'approve' | 'reject') =>
      action === 'approve' ? approveRegistration(summary.id) : rejectRegistration(summary.id, reason.trim()),
    onSuccess: (_, action) => {
      toast.success(`Registration for ${summary.student.name} ${action === 'approve' ? 'approved' : 'returned to the student'}.`)
      void queryClient.invalidateQueries({ queryKey: ['admin', 'registrations'] })
      void queryClient.invalidateQueries({ queryKey: ['offerings'] })
      onClose()
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const pending = summary.status === 'SUBMITTED'
  const d = detail.data
  const outOfRange = d && (d.summary.totalCredits < d.minCredits || d.summary.totalCredits > d.maxCredits)

  return (
    <Modal
      open
      size="lg"
      title={summary.student.name}
      description={`${summary.student.studentNumber} · ${summary.student.programCode} · ${summary.semester.name}`}
      onClose={onClose}
      busy={decide.isPending}
      footer={
        pending && !rejecting ? (
          <>
            <Button variant="secondary" onClick={() => setRejecting(true)} disabled={decide.isPending}>
              Return to student
            </Button>
            <Button onClick={() => decide.mutate('approve')} loading={decide.isPending}>
              Approve
            </Button>
          </>
        ) : pending && rejecting ? (
          <>
            <Button variant="secondary" onClick={() => setRejecting(false)} disabled={decide.isPending}>
              Back
            </Button>
            <Button
              variant="danger"
              loading={decide.isPending}
              onClick={() => {
                if (!reason.trim()) {
                  setReasonError('Tell the student what to change.')
                  return
                }
                decide.mutate('reject')
              }}
            >
              Return registration
            </Button>
          </>
        ) : (
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        )
      }
    >
      {!d ? (
        <div className="flex justify-center py-8 text-brand-700">
          <Spinner label="Loading registration…" />
        </div>
      ) : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={REGISTRATION_STATUS_TONES[d.summary.status]}>{REGISTRATION_STATUS_LABELS[d.summary.status]}</Badge>
            <span className="text-sm text-neutral-600">
              {d.summary.totalCredits} credits (allowed {d.minCredits}–{d.maxCredits}) · submitted {formatDateTime(d.summary.submittedAt)}
            </span>
          </div>
          {outOfRange && <Alert tone="error">The credit load is outside the allowed range.</Alert>}
          {d.rejectionReason && <Alert tone="info" title="Previously returned">{d.rejectionReason}</Alert>}
          <table className="min-w-full divide-y divide-neutral-200 text-sm">
            <caption className="sr-only">Courses</caption>
            <thead>
              <tr>
                <th scope="col" className="py-2 text-left text-xs font-semibold text-neutral-600 uppercase">Course</th>
                <th scope="col" className="py-2 text-left text-xs font-semibold text-neutral-600 uppercase">Section</th>
                <th scope="col" className="py-2 text-right text-xs font-semibold text-neutral-600 uppercase">Credits</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {d.items.map((i) => (
                <tr key={i.id}>
                  <td className="py-2">
                    <p className="font-medium text-neutral-900">{i.courseCode}</p>
                    <p className="text-neutral-600">{i.courseTitle}</p>
                  </td>
                  <td className="py-2 text-neutral-600">{i.section}</td>
                  <td className="py-2 text-right tabular-nums">{i.credits}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {rejecting && (
            <TextAreaField
              label="Reason (shown to the student)"
              rows={3}
              maxLength={500}
              autoFocus
              value={reason}
              error={reasonError ?? undefined}
              onChange={(e) => {
                setReason(e.target.value)
                setReasonError(null)
              }}
            />
          )}
        </div>
      )}
    </Modal>
  )
}

export function RegistrationsPage() {
  const list = useListParams('submittedAt,desc')
  const semesters = useSemesters()
  const [reviewing, setReviewing] = useState<RegistrationSummary | null>(null)
  const semesterId = list.getNumber('semesterId') ?? semesters.data?.find((s) => s.current)?.id ?? semesters.data?.[0]?.id
  const status = (list.get('status') || undefined) as RegistrationStatus | undefined

  const params = { semesterId, status, q: list.q, sort: list.sort, page: list.page, size: PAGE_SIZE }
  const query = useQuery({
    queryKey: ['admin', 'registrations', params],
    queryFn: () => searchRegistrations(params),
    enabled: semesterId !== undefined,
    placeholderData: keepPreviousData,
  })
  const data = query.data
  const onSort = (sort: string) => list.update({ sort })

  return (
    <>
      <PageHeader title="Registrations" description="Review students' course registrations for a semester." />
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
              label="Search registrations"
              hideLabel
              type="search"
              placeholder="Student name, email or number"
              className="pl-9"
              value={list.searchInput}
              onChange={(e) => list.setSearchInput(e.target.value)}
            />
          </div>
          <SelectField label="Status" hideLabel value={status ?? ''} onChange={(e) => list.update({ status: e.target.value })}>
            <option value="">All statuses</option>
            {REGISTRATION_STATUSES.map((s) => (
              <option key={s} value={s}>
                {REGISTRATION_STATUS_LABELS[s]}
              </option>
            ))}
          </SelectField>
        </TableToolbar>
        <DataTable
          caption="Registrations"
          isLoading={!data}
          error={query.error ?? semesters.error}
          onRetry={() => query.refetch()}
          isFetching={query.isFetching}
          isEmpty={data?.content.length === 0}
          emptyIcon={ClipboardCheck}
          emptyTitle={list.hasFilters ? 'No registrations match your filters' : 'No registrations yet'}
          head={
            <>
              <SortTh field="studentNumber" label="Student" sort={list.sort} onSort={onSort} />
              <Th className="text-right">Credits</Th>
              <SortTh field="status" label="Status" sort={list.sort} onSort={onSort} />
              <SortTh field="submittedAt" label="Submitted" sort={list.sort} onSort={onSort} className="hidden md:table-cell" />
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
          {data?.content.map((r) => (
            <tr key={r.id} className="hover:bg-neutral-50/60">
              <Td>
                <p className="font-medium text-neutral-900">{r.student.name}</p>
                <p className="text-xs text-neutral-500">
                  {r.student.studentNumber} · {r.student.programCode}
                </p>
              </Td>
              <Td className="text-right tabular-nums">
                {r.totalCredits}
                <p className="text-xs text-neutral-500">{r.courseCount} {r.courseCount === 1 ? 'course' : 'courses'}</p>
              </Td>
              <Td>
                <Badge tone={REGISTRATION_STATUS_TONES[r.status]}>{REGISTRATION_STATUS_LABELS[r.status]}</Badge>
              </Td>
              <Td className="hidden whitespace-nowrap text-neutral-600 md:table-cell">{formatDateTime(r.submittedAt, 'Not submitted')}</Td>
              <Td className="text-right">
                <Button variant={r.status === 'SUBMITTED' ? 'primary' : 'secondary'} size="sm" onClick={() => setReviewing(r)}>
                  {r.status === 'SUBMITTED' ? 'Review' : 'View'}
                </Button>
              </Td>
            </tr>
          ))}
        </DataTable>
      </Card>
      {reviewing && <ReviewModal summary={reviewing} onClose={() => setReviewing(null)} />}
    </>
  )
}
