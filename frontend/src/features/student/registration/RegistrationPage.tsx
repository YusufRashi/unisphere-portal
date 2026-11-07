import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, CalendarX, Check, Clock, MapPin, Plus, Search, User, X } from 'lucide-react'
import { useState } from 'react'
import { addCourse, getCatalogue, getMyRegistration, removeCourse, submitRegistration } from '@/api/registration'
import { errorMessage, toApiError } from '@/api/errors'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState } from '@/components/ui/EmptyState'
import { SelectField, TextField } from '@/components/ui/fields'
import { Pagination } from '@/components/ui/Pagination'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { useListParams } from '@/hooks/useListParams'
import { cn } from '@/lib/cn'
import { DAY_SHORT, shortTime } from '@/lib/datetime'
import { formatDateTime } from '@/lib/format'
import type { CatalogueEntry, OfferingSession, RegistrationItem, RegistrationView } from '@/types/api'
import { useDepartments } from '@/features/admin/queries'
import { CreditMeter } from './CreditMeter'
import { AVAILABILITY_LABELS, REGISTRATION_STATUS_LABELS, REGISTRATION_STATUS_TONES } from './labels'

const REGISTRATION_KEY = ['registration', 'me'] as const
const PAGE_SIZE = 10

function Sessions({ sessions }: { sessions: OfferingSession[] }) {
  if (sessions.length === 0) return <span>No weekly sessions</span>
  return (
    <>
      {sessions.map((s, i) => (
        <span key={i} className="inline-flex items-center gap-1 whitespace-nowrap">
          <Clock aria-hidden="true" className="size-3.5" />
          {DAY_SHORT[s.dayOfWeek]} {shortTime(s.startTime)}–{shortTime(s.endTime)}
          <MapPin aria-hidden="true" className="ml-1 size-3.5" />
          {s.venue}
        </span>
      ))}
    </>
  )
}

/** Keeps every view of the registration in sync after a change. */
function useRegistrationMutation<T>(fn: (arg: T) => Promise<RegistrationView>, success: (view: RegistrationView, arg: T) => string) {
  const queryClient = useQueryClient()
  const toast = useToast()
  return useMutation({
    mutationFn: fn,
    onSuccess: (view, arg) => {
      queryClient.setQueryData(REGISTRATION_KEY, view)
      void queryClient.invalidateQueries({ queryKey: ['catalogue'] })
      void queryClient.invalidateQueries({ queryKey: ['timetable'] })
      toast.success(success(view, arg))
    },
    // Rule violations (full, clash, prerequisites…) come back with a specific, readable message.
    onError: (error) => toast.error(errorMessage(error)),
  })
}

function CatalogueRow({ entry, canAdd, adding, onAdd }: {
  entry: CatalogueEntry
  canAdd: boolean
  adding: boolean
  onAdd: () => void
}) {
  const o = entry.offering
  const available = entry.availability === 'AVAILABLE'
  const seatsTone = o.seatsLeft === 0 ? 'text-red-700' : o.seatsLeft <= 5 ? 'text-amber-700' : 'text-neutral-600'
  return (
    <li className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-start">
      <div className="min-w-0 flex-1 space-y-1.5">
        <div className="flex flex-wrap items-center gap-2">
          <p className="font-semibold text-neutral-900">{o.course.code}</p>
          <Badge>Section {o.section}</Badge>
          <Badge tone="brand">{o.course.credits} credits</Badge>
        </div>
        <p className="text-sm text-neutral-700">{o.course.title}</p>
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-neutral-500">
          <span className="inline-flex items-center gap-1">
            <User aria-hidden="true" className="size-3.5" />
            {o.lecturer?.displayName ?? 'Lecturer to be announced'}
          </span>
          <Sessions sessions={o.sessions} />
          <span className={seatsTone}>
            {o.seatsLeft === 0 ? 'No seats left' : `${o.seatsLeft} of ${o.capacity} seats left`}
          </span>
        </div>
        {entry.prerequisites.length > 0 && (
          <ul className="flex flex-wrap items-center gap-1.5 text-xs" aria-label={`Prerequisites for ${o.course.code}`}>
            <li className="text-neutral-500">Requires</li>
            {entry.prerequisites.map((p) => (
              <li
                key={p.courseId}
                className={cn(
                  'inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-medium ring-1 ring-inset',
                  p.met ? 'bg-emerald-50 text-emerald-800 ring-emerald-200' : 'bg-red-50 text-red-800 ring-red-200',
                )}
              >
                {p.met ? <Check aria-hidden="true" className="size-3" /> : <X aria-hidden="true" className="size-3" />}
                {p.code}
                <span className="sr-only">{p.met ? '(passed)' : '(not passed)'}</span>
              </li>
            ))}
          </ul>
        )}
        {!available && entry.reason && <p className="text-xs text-amber-800">{entry.reason}</p>}
      </div>
      <div className="shrink-0">
        {entry.availability === 'AVAILABLE' ? (
          <Button size="sm" onClick={onAdd} loading={adding} disabled={!canAdd} aria-label={`Add ${o.course.code} section ${o.section}`}>
            {!adding && <Plus aria-hidden="true" className="size-4" />} Add
          </Button>
        ) : (
          <Badge tone={entry.availability === 'IN_REGISTRATION' ? 'success' : 'neutral'}>
            {AVAILABILITY_LABELS[entry.availability]}
          </Badge>
        )}
      </div>
    </li>
  )
}

function CataloguePanel({ canAdd }: { canAdd: boolean }) {
  const list = useListParams('code,asc')
  const departments = useDepartments()
  const departmentId = list.getNumber('departmentId')
  const level = list.getNumber('level')
  const params = { q: list.q, departmentId, level, sort: list.sort, page: list.page, size: PAGE_SIZE }
  const catalogue = useQuery({ queryKey: ['catalogue', params], queryFn: () => getCatalogue(params), placeholderData: keepPreviousData })
  const [addingId, setAddingId] = useState<number | null>(null)
  const add = useRegistrationMutation(
    (entry: CatalogueEntry) => addCourse(entry.offering.id),
    (_, entry) => `${entry.offering.course.code} added to your registration.`,
  )
  const data = catalogue.data

  return (
    <Card>
      <CardHeader title="Available courses" description="Courses offered this semester. Add the ones you want to take." />
      <div className="grid gap-3 border-b border-neutral-200 p-4 sm:grid-cols-[minmax(0,1fr)_12rem_9rem]">
        <div className="relative">
          <Search aria-hidden="true" className="pointer-events-none absolute top-2.5 left-3 size-4 text-neutral-400" />
          <TextField
            label="Search courses"
            hideLabel
            type="search"
            placeholder="Search by code or title"
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
        <SelectField label="Level" hideLabel value={level ?? ''} onChange={(e) => list.update({ level: e.target.value })}>
          <option value="">All levels</option>
          {[100, 200, 300, 400, 500].map((l) => (
            <option key={l} value={l}>
              Level {l}
            </option>
          ))}
        </SelectField>
      </div>
      {catalogue.error ? (
        <div className="p-5">
          <Alert tone="error">{errorMessage(catalogue.error)}</Alert>
        </div>
      ) : !data ? (
        <div className="flex justify-center p-12 text-brand-700">
          <Spinner label="Loading courses…" />
        </div>
      ) : data.content.length === 0 ? (
        <EmptyState
          icon={list.hasFilters ? Search : BookOpen}
          title={list.hasFilters ? 'No courses match your search' : 'No courses are offered yet'}
          action={list.hasFilters ? <Button variant="secondary" onClick={list.clear}>Clear filters</Button> : undefined}
        />
      ) : (
        <>
          <ul className="divide-y divide-neutral-100" aria-busy={catalogue.isFetching || undefined}>
            {data.content.map((entry) => (
              <CatalogueRow
                key={entry.offering.id}
                entry={entry}
                canAdd={canAdd && !add.isPending}
                adding={add.isPending && addingId === entry.offering.id}
                onAdd={() => {
                  setAddingId(entry.offering.id)
                  add.mutate(entry)
                }}
              />
            ))}
          </ul>
          <Pagination page={data.page} size={data.size} totalPages={data.totalPages} totalElements={data.totalElements} onPageChange={(page) => list.update({ page })} />
        </>
      )}
    </Card>
  )
}

function MyRegistrationPanel({ view }: { view: RegistrationView }) {
  const [confirmSubmit, setConfirmSubmit] = useState(false)
  const [dropping, setDropping] = useState<RegistrationItem | null>(null)
  const { permissions, semester } = view
  const isDraft = view.status === null || view.status === 'DRAFT' || view.status === 'REJECTED'

  const remove = useRegistrationMutation(
    (item: RegistrationItem) => removeCourse(item.id),
    (_, item) => `${item.courseCode} ${permissions.removeMeansDrop ? 'dropped' : 'removed'}.`,
  )
  const submit = useRegistrationMutation(
    () => submitRegistration(),
    (v) => (v.status === 'SUBMITTED' ? 'Registration submitted for approval.' : 'You are registered for this semester.'),
  )

  return (
    <Card>
      <CardHeader
        title="My registration"
        actions={view.status ? (
          <Badge tone={REGISTRATION_STATUS_TONES[view.status]}>{REGISTRATION_STATUS_LABELS[view.status]}</Badge>
        ) : (
          <Badge>Not started</Badge>
        )}
      />
      <CardBody className="space-y-5">
        <CreditMeter total={view.totalCredits} min={semester.minCredits} max={semester.maxCredits} />

        {view.status === 'REJECTED' && view.rejectionReason && (
          <Alert tone="error" title="Returned by the registrar">
            {view.rejectionReason} Make the requested changes and submit again.
          </Alert>
        )}
        {view.status === 'APPROVED' && (
          <Alert tone="success" title="You are registered">
            {semester.addDropOpen
              ? `You can still add or drop courses until ${formatDateTime(semester.addDropDeadline)}.`
              : 'The add/drop period has ended.'}
          </Alert>
        )}
        {permissions.lockedReason && view.status !== 'APPROVED' && <Alert tone="info">{permissions.lockedReason}</Alert>}

        {view.items.length === 0 ? (
          <p className="text-sm text-neutral-500">No courses yet. Add courses from the list.</p>
        ) : (
          <ul className="divide-y divide-neutral-100 rounded-lg border border-neutral-200" aria-label="Courses in my registration">
            {view.items.map((item) => (
              <li key={item.id} className="flex items-start gap-3 p-3">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-neutral-900">
                    {item.courseCode} <span className="font-normal text-neutral-500">· {item.section}</span>
                  </p>
                  <p className="truncate text-sm text-neutral-600">{item.courseTitle}</p>
                  <p className="mt-1 flex flex-wrap gap-x-3 text-xs text-neutral-500">
                    <Sessions sessions={item.sessions} />
                  </p>
                </div>
                <span className="text-sm font-medium text-neutral-700 tabular-nums">{item.credits} cr</span>
                {permissions.canRemove && (
                  <button
                    type="button"
                    onClick={() => (permissions.removeMeansDrop ? setDropping(item) : remove.mutate(item))}
                    disabled={remove.isPending}
                    className="rounded p-1 text-neutral-400 hover:bg-red-50 hover:text-red-700"
                    aria-label={`${permissions.removeMeansDrop ? 'Drop' : 'Remove'} ${item.courseCode}`}
                  >
                    <X aria-hidden="true" className="size-4" />
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}

        {view.droppedItems.length > 0 && (
          <div>
            <p className="text-xs font-semibold tracking-wide text-neutral-500 uppercase">Dropped</p>
            <ul className="mt-1 space-y-0.5 text-sm text-neutral-500">
              {view.droppedItems.map((d) => (
                <li key={d.id}>
                  <span className="line-through">{d.courseCode}</span> · {formatDateTime(d.droppedAt)}
                </li>
              ))}
            </ul>
          </div>
        )}

        {isDraft && (
          <Button className="w-full" disabled={!permissions.canSubmit} loading={submit.isPending} onClick={() => setConfirmSubmit(true)}>
            Submit registration
          </Button>
        )}

        <dl className="space-y-1 border-t border-neutral-100 pt-4 text-xs text-neutral-500">
          <div className="flex justify-between gap-2">
            <dt>Registration closes</dt>
            <dd className="text-right text-neutral-700">{formatDateTime(semester.registrationClosesAt)}</dd>
          </div>
          <div className="flex justify-between gap-2">
            <dt>Add/drop deadline</dt>
            <dd className="text-right text-neutral-700">{formatDateTime(semester.addDropDeadline)}</dd>
          </div>
        </dl>
      </CardBody>

      <ConfirmDialog
        open={confirmSubmit}
        tone="primary"
        title="Submit your registration?"
        description={
          <>
            You are registering for <strong>{view.items.length} course{view.items.length === 1 ? '' : 's'}</strong> (
            {view.totalCredits} credits). A seat will be reserved in each.
            {semester.requiresApproval && ' The registrar will review your registration before it is confirmed.'}
          </>
        }
        confirmLabel="Submit registration"
        loading={submit.isPending}
        onConfirm={() => submit.mutate(undefined, { onSettled: () => setConfirmSubmit(false) })}
        onCancel={() => setConfirmSubmit(false)}
      />
      <ConfirmDialog
        open={dropping !== null}
        title={`Drop ${dropping?.courseCode}?`}
        description={`Your seat in ${dropping?.courseCode} section ${dropping?.section} will be released and may be taken by another student.`}
        confirmLabel="Drop course"
        loading={remove.isPending}
        onConfirm={() => dropping && remove.mutate(dropping, { onSettled: () => setDropping(null) })}
        onCancel={() => setDropping(null)}
      />
    </Card>
  )
}

export function RegistrationPage() {
  const registration = useQuery({ queryKey: REGISTRATION_KEY, queryFn: getMyRegistration })

  if (registration.error) {
    const error = toApiError(registration.error)
    return (
      <>
        <PageHeader title="Course registration" />
        <Card>
          {error.status === 404 ? (
            <EmptyState icon={CalendarX} title="Registration is not open" description={error.message} />
          ) : (
            <div className="p-5">
              <Alert tone="error">{error.message}</Alert>
            </div>
          )}
        </Card>
      </>
    )
  }
  if (!registration.data) {
    return (
      <div className="flex justify-center p-12 text-brand-700">
        <Spinner label="Loading your registration…" />
      </div>
    )
  }
  const view = registration.data
  return (
    <>
      <PageHeader title="Course registration" description={view.semester.name} />
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <div className="order-2 lg:order-1">
          <CataloguePanel canAdd={view.permissions.canAdd} />
        </div>
        <aside className="order-1 lg:sticky lg:top-22 lg:order-2" aria-label="My registration">
          <MyRegistrationPanel view={view} />
        </aside>
      </div>
    </>
  )
}
