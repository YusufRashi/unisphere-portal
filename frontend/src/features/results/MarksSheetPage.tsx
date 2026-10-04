import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Users } from 'lucide-react'
import { useEffect, useState, type KeyboardEvent } from 'react'
import { Link, useBlocker, useParams } from 'react-router'
import { errorMessage, toApiError } from '@/api/errors'
import { finalizeResults, getClassStatistics, getGradingScale, getMarksSheet, returnResults, saveMarks, submitMarks } from '@/api/results'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardHeader } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState } from '@/components/ui/EmptyState'
import { TextAreaField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { cn } from '@/lib/cn'
import { formatDateTime } from '@/lib/format'
import type { MarkEntry, MarkRow, MarksSheet } from '@/types/api'
import { ClassStatisticsCard } from './ClassStatisticsCard'
import { RESULTS_STATUS_LABELS, RESULTS_STATUS_TONES } from './labels'
import { addMarks, formatNumber, markError, parseMark, previewGrade } from './marks'

type Mode = 'lecturer' | 'registrar'
type Column = 'cat' | 'exam'
/** Only cells the user has typed in; everything else shows the saved value. */
type Edits = Record<number, Partial<Record<Column, string>>>

const saved = (row: MarkRow, column: Column) => {
  const value = column === 'cat' ? row.catMark : row.examMark
  return value === null ? '' : formatNumber(value)
}

/**
 * The marks sheet of one offering. Lecturers enter CAT and exam marks (saved as a draft, then submitted);
 * registrars open the same sheet read-only to finalize or return it. Grades shown while typing are a
 * preview — the server computes and stores the real ones.
 */
export function MarksSheetPage({ mode }: { mode: Mode }) {
  const offeringId = Number(useParams().offeringId)
  const queryClient = useQueryClient()
  const toast = useToast()
  const sheetKey = ['marks-sheet', offeringId]
  const sheet = useQuery({ queryKey: sheetKey, queryFn: () => getMarksSheet(offeringId) })
  const scale = useQuery({ queryKey: ['grading-scale'], queryFn: getGradingScale, staleTime: 5 * 60_000 })
  const stats = useQuery({ queryKey: ['class-statistics', offeringId], queryFn: () => getClassStatistics(offeringId) })
  const [edits, setEdits] = useState<Edits>({})
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})
  const [confirming, setConfirming] = useState<'submit' | 'finalize' | null>(null)
  const [returning, setReturning] = useState(false)
  const [comment, setComment] = useState('')
  const [commentError, setCommentError] = useState<string | null>(null)

  const data = sheet.data
  const rows = data?.rows ?? []
  const valueOf = (row: MarkRow, column: Column) => edits[row.registrationItemId]?.[column] ?? saved(row, column)
  const weightOf = (column: Column) => (column === 'cat' ? data?.offering.catWeight : data?.offering.examWeight) ?? 100
  const errorOf = (row: MarkRow, column: Column) =>
    markError(valueOf(row, column), weightOf(column)) ?? serverErrors[`${row.registrationItemId}.${column}`] ?? null

  const changedRows = rows.filter((row) =>
    (['cat', 'exam'] as const).some((c) => edits[row.registrationItemId]?.[c] !== undefined && valueOf(row, c) !== saved(row, c)),
  )
  const dirty = changedRows.length > 0
  const hasErrors = rows.some((row) => errorOf(row, 'cat') || errorOf(row, 'exam'))

  // Unsaved marks: confirm before leaving the page in the app, and let the browser warn on reload/close.
  const blocker = useBlocker(({ currentLocation, nextLocation }) => dirty && currentLocation.pathname !== nextLocation.pathname)
  useEffect(() => {
    if (!dirty) return
    const warn = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const afterChange = (updated: MarksSheet, message: string) => {
    queryClient.setQueryData(sheetKey, updated)
    void queryClient.invalidateQueries({ queryKey: ['class-statistics', offeringId] })
    void queryClient.invalidateQueries({ queryKey: ['lecturer', 'teaching'] })
    void queryClient.invalidateQueries({ queryKey: ['admin', 'results'] })
    toast.success(message)
  }

  const save = useMutation({
    mutationFn: (entries: MarkEntry[]) => saveMarks(offeringId, entries),
    onSuccess: (updated, entries) => {
      setEdits({})
      setServerErrors({})
      afterChange(updated, `Marks saved for ${entries.length} ${entries.length === 1 ? 'student' : 'students'}.`)
    },
    onError: (error, entries) => {
      // "entries[3].catMark" → the cell of the fourth student sent.
      const mapped: Record<string, string> = {}
      Object.entries(toApiError(error).fieldErrors).forEach(([field, message]) => {
        const match = /^entries\[(\d+)]\.(catMark|examMark)$/.exec(field)
        const entry = match ? entries[Number(match[1])] : undefined
        if (entry && match) mapped[`${entry.registrationItemId}.${match[2] === 'catMark' ? 'cat' : 'exam'}`] = message
      })
      setServerErrors(mapped)
      toast.error(Object.keys(mapped).length > 0 ? 'Some marks were not accepted. Check the highlighted cells.' : errorMessage(error))
    },
  })

  const transition = useMutation({
    mutationFn: (action: 'submit' | 'finalize' | 'return') =>
      action === 'submit' ? submitMarks(offeringId) : action === 'finalize' ? finalizeResults(offeringId) : returnResults(offeringId, comment.trim()),
    onSuccess: (updated, action) => {
      setConfirming(null)
      setReturning(false)
      const label = `${updated.offering.courseCode} (${updated.offering.section})`
      afterChange(updated, {
        submit: `Marks for ${label} submitted to the registrar.`,
        finalize: `Results for ${label} finalized and released to students.`,
        return: `Marks for ${label} returned to the lecturer.`,
      }[action])
    },
    onError: (error) => {
      setConfirming(null)
      toast.error(errorMessage(error))
    },
  })

  if (sheet.error) {
    return (
      <>
        <PageHeader title="Marks" />
        <Alert tone="error">{errorMessage(sheet.error)}</Alert>
      </>
    )
  }
  if (!data) {
    return (
      <div className="flex justify-center p-12 text-brand-700">
        <Spinner label="Loading marks…" />
      </div>
    )
  }

  const { offering, permissions } = data
  const label = `${offering.courseCode} (${offering.section})`
  const editable = mode === 'lecturer' && permissions.canEdit

  const edit = (row: MarkRow, column: Column, value: string) => {
    setEdits((prev) => ({ ...prev, [row.registrationItemId]: { ...prev[row.registrationItemId], [column]: value } }))
    setServerErrors((prev) => {
      const next = { ...prev }
      delete next[`${row.registrationItemId}.${column}`]
      return next
    })
  }

  const onSave = () => {
    if (hasErrors) {
      toast.error('Fix the highlighted marks before saving.')
      return
    }
    save.mutate(changedRows.map((row) => ({
      registrationItemId: row.registrationItemId,
      catMark: parseMark(valueOf(row, 'cat')) ?? null,
      examMark: parseMark(valueOf(row, 'exam')) ?? null,
    })))
  }

  // Enter moves down the column, like a spreadsheet.
  const onCellKeyDown = (e: KeyboardEvent<HTMLInputElement>, column: Column, index: number) => {
    if (e.key !== 'Enter') return
    e.preventDefault()
    const next = document.querySelector<HTMLInputElement>(`[data-marks-cell="${column}-${index + 1}"]`)
    next?.focus()
    next?.select()
  }

  const backTo = mode === 'lecturer' ? '/lecturer/courses' : '/admin/results'
  const submitBlockedReason = dirty
    ? 'Save your changes before submitting.'
    : !permissions.canSubmit && data.classSize > 0
      ? `${data.classSize - data.complete} of ${data.classSize} students still need both marks.`
      : null

  return (
    <>
      <Link to={backTo} className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:text-brand-900">
        <ArrowLeft aria-hidden="true" className="size-4" />
        {mode === 'lecturer' ? 'My courses' : 'Results'}
      </Link>
      <PageHeader
        title={`${offering.courseCode} · ${offering.courseTitle}`}
        description={`Section ${offering.section} · ${offering.semester.name}${offering.lecturer ? ` · ${offering.lecturer}` : ''}`}
        actions={<Badge tone={RESULTS_STATUS_TONES[offering.resultsStatus]}>{RESULTS_STATUS_LABELS[offering.resultsStatus]}</Badge>}
      />

      <div className="space-y-4">
        {offering.returnComment && offering.resultsStatus === 'DRAFT' && (
          <Alert tone="error" title="Returned by the registrar">
            {offering.returnComment}
          </Alert>
        )}
        {offering.resultsStatus === 'SUBMITTED' && (
          <Alert tone="info" title={mode === 'registrar' ? 'Ready for review' : 'Submitted'}>
            Submitted {formatDateTime(offering.submittedAt)}.{' '}
            {mode === 'registrar'
              ? 'Finalizing fixes each student’s grade and releases the results.'
              : 'Marks are locked while the registrar reviews them.'}
          </Alert>
        )}
        {offering.resultsStatus === 'FINALIZED' && (
          <Alert tone="success" title="Finalized">
            Results were released to students on {formatDateTime(offering.finalizedAt)}.
          </Alert>
        )}

        <div className="grid grid-cols-[minmax(0,1fr)] gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
          <Card>
            <CardHeader
              title="Marks"
              description={`CAT out of ${formatNumber(offering.catWeight)} · Exam out of ${formatNumber(offering.examWeight)} · ${data.complete} of ${data.classSize} complete`}
              actions={
                editable ? (
                  <div className="flex flex-wrap gap-2">
                    <Button variant="secondary" size="sm" onClick={onSave} loading={save.isPending} disabled={!dirty}>
                      Save draft
                    </Button>
                    <Button size="sm" onClick={() => setConfirming('submit')} disabled={submitBlockedReason !== null || !permissions.canSubmit}
                      title={submitBlockedReason ?? undefined}>
                      Submit to registrar
                    </Button>
                  </div>
                ) : mode === 'registrar' && offering.resultsStatus === 'SUBMITTED' ? (
                  <div className="flex flex-wrap gap-2">
                    <Button variant="secondary" size="sm" onClick={() => setReturning(true)}>
                      Return to lecturer
                    </Button>
                    <Button size="sm" onClick={() => setConfirming('finalize')}>
                      Finalize results
                    </Button>
                  </div>
                ) : undefined
              }
            />
            {editable && submitBlockedReason && (
              <p className="border-b border-slate-200 bg-slate-50 px-5 py-2 text-xs text-slate-600">{submitBlockedReason}</p>
            )}
            {rows.length === 0 ? (
              <EmptyState icon={Users} title="No students yet" description="Students appear here once their registration for this course is approved." />
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-[36rem] divide-y divide-slate-200 text-sm sm:min-w-full">
                  <caption className="sr-only">Marks for {label}</caption>
                  <thead className="bg-slate-50">
                    <tr>
                      <th scope="col" className="px-4 py-2.5 text-left text-xs font-semibold text-slate-600 uppercase">Student</th>
                      <th scope="col" className="px-2 py-2.5 text-left text-xs font-semibold text-slate-600 uppercase">CAT / {formatNumber(offering.catWeight)}</th>
                      <th scope="col" className="px-2 py-2.5 text-left text-xs font-semibold text-slate-600 uppercase">Exam / {formatNumber(offering.examWeight)}</th>
                      <th scope="col" className="px-2 py-2.5 text-right text-xs font-semibold text-slate-600 uppercase">Total</th>
                      <th scope="col" className="px-4 py-2.5 text-left text-xs font-semibold text-slate-600 uppercase">Grade</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {rows.map((row, index) => {
                      const cat = parseMark(valueOf(row, 'cat'))
                      const exam = parseMark(valueOf(row, 'exam'))
                      const total = addMarks(cat ?? null, exam ?? null)
                      const finalized = offering.resultsStatus === 'FINALIZED'
                      const band = finalized ? null : previewGrade(total, scale.data?.bands ?? [])
                      const grade = finalized ? row.grade : (band?.letter ?? (edits[row.registrationItemId] ? null : row.grade))
                      const passed = finalized ? row.passed : (band?.passing ?? row.passed)
                      return (
                        <tr key={row.registrationItemId}>
                          <td className="px-4 py-2">
                            <p className="font-medium text-slate-900">{row.name}</p>
                            <p className="text-xs text-slate-500">{row.studentNumber}</p>
                          </td>
                          {(['cat', 'exam'] as const).map((column) => {
                            const error = errorOf(row, column)
                            const name = `${column === 'cat' ? 'CAT' : 'Exam'} mark for ${row.name}`
                            return (
                              <td key={column} className="px-2 py-2 align-top">
                                {editable ? (
                                  <>
                                    <input
                                      aria-label={name}
                                      aria-invalid={error ? true : undefined}
                                      aria-describedby={error ? `err-${row.registrationItemId}-${column}` : undefined}
                                      data-marks-cell={`${column}-${index}`}
                                      inputMode="decimal"
                                      autoComplete="off"
                                      value={valueOf(row, column)}
                                      onChange={(e) => edit(row, column, e.target.value)}
                                      onKeyDown={(e) => onCellKeyDown(e, column, index)}
                                      className={cn(
                                        'h-8 w-20 rounded-md border px-2 text-right text-sm tabular-nums focus:ring-2 focus:outline-none',
                                        error ? 'border-red-500 focus:ring-red-500/20' : 'border-slate-300 focus:border-brand-600 focus:ring-brand-600/20',
                                      )}
                                    />
                                    {error && (
                                      <p id={`err-${row.registrationItemId}-${column}`} className="mt-1 text-xs text-red-700">
                                        {error}
                                      </p>
                                    )}
                                  </>
                                ) : (
                                  <span className="tabular-nums">{valueOf(row, column) || '—'}</span>
                                )}
                              </td>
                            )
                          })}
                          <td className="px-2 py-2 text-right font-medium text-slate-900 tabular-nums">{formatNumber(total)}</td>
                          <td className="px-4 py-2">
                            {grade ? (
                              <span className="inline-flex items-center gap-2">
                                <span className={cn('font-semibold', passed === false ? 'text-red-700' : 'text-slate-900')}>{grade}</span>
                                {passed === false && <Badge tone="danger">Fail</Badge>}
                              </span>
                            ) : (
                              <span className="text-slate-400">—</span>
                            )}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
          <ClassStatisticsCard stats={stats.data} />
        </div>
      </div>

      <ConfirmDialog
        open={confirming === 'submit'}
        tone="primary"
        title={`Submit marks for ${label}?`}
        description="The registrar will review and finalize them. You will not be able to change marks unless they are returned to you."
        confirmLabel="Submit marks"
        loading={transition.isPending}
        onConfirm={() => transition.mutate('submit')}
        onCancel={() => setConfirming(null)}
      />
      <ConfirmDialog
        open={confirming === 'finalize'}
        tone="primary"
        title={`Finalize results for ${label}?`}
        description={`Grades for all ${data.classSize} students will be fixed using the current grading scale and released to them. This cannot be undone.`}
        confirmLabel="Finalize results"
        loading={transition.isPending}
        onConfirm={() => transition.mutate('finalize')}
        onCancel={() => setConfirming(null)}
      />
      <Modal
        open={returning}
        title={`Return marks for ${label}`}
        description="The lecturer can edit the marks again and resubmit."
        onClose={() => setReturning(false)}
        busy={transition.isPending}
        footer={
          <>
            <Button variant="secondary" onClick={() => setReturning(false)} disabled={transition.isPending}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={transition.isPending}
              onClick={() => {
                if (!comment.trim()) {
                  setCommentError('Tell the lecturer what needs correcting.')
                  return
                }
                transition.mutate('return')
              }}
            >
              Return marks
            </Button>
          </>
        }
      >
        <TextAreaField
          label="Comment for the lecturer"
          rows={3}
          maxLength={500}
          autoFocus
          value={comment}
          error={commentError ?? undefined}
          onChange={(e) => {
            setComment(e.target.value)
            setCommentError(null)
          }}
        />
      </Modal>
      <ConfirmDialog
        open={blocker.state === 'blocked'}
        title="Leave without saving?"
        description="You have marks that have not been saved. They will be lost if you leave this page."
        confirmLabel="Discard changes"
        onConfirm={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      />
    </>
  )
}
