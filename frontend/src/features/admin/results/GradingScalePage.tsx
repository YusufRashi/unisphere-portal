import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { errorMessage, toApiError } from '@/api/errors'
import { getGradingScale, updateGradingScale } from '@/api/results'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Card, CardHeader } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { formatNumber } from '@/features/results/marks'
import { cn } from '@/lib/cn'
import type { GradeBand, GradingScale } from '@/types/api'

interface BandDraft {
  key: number
  letter: string
  minMark: string
  gradePoints: string
  passing: boolean
}

let nextKey = 1
const toDraft = (b: GradeBand): BandDraft => ({
  key: nextKey++,
  letter: b.letter,
  minMark: formatNumber(b.minMark),
  gradePoints: b.gradePoints.toFixed(2),
  passing: b.passing,
})

/** Field-level checks for instant feedback; the server also checks the scale as a whole. */
function bandErrors(b: BandDraft): Partial<Record<'letter' | 'minMark' | 'gradePoints', string>> {
  const errors: Partial<Record<'letter' | 'minMark' | 'gradePoints', string>> = {}
  if (!/^[A-Za-z][A-Za-z+-]{0,2}$/.test(b.letter.trim())) errors.letter = 'Use a grade such as A, B+ or C-'
  const min = Number(b.minMark)
  if (b.minMark.trim() === '' || Number.isNaN(min) || min < 0 || min > 100 || !/^\d+(\.\d{1,2})?$/.test(b.minMark.trim())) {
    errors.minMark = '0–100, up to 2 decimals'
  }
  const points = Number(b.gradePoints)
  if (b.gradePoints.trim() === '' || Number.isNaN(points) || points < 0 || points > 5 || !/^\d(\.\d{1,2})?$/.test(b.gradePoints.trim())) {
    errors.gradePoints = '0–5, up to 2 decimals'
  }
  return errors
}

/** "70 – 100", "65 – below 70" for each band, computed from the thresholds as entered. */
function ranges(bands: BandDraft[]): Map<number, string> {
  const ordered = bands.filter((b) => !Number.isNaN(Number(b.minMark)) && b.minMark.trim() !== '').sort((a, b) => Number(b.minMark) - Number(a.minMark))
  return new Map(ordered.map((b, i) => [b.key, i === 0 ? `${b.minMark} – 100` : `${b.minMark} – below ${ordered[i - 1]!.minMark}`]))
}

function ScaleEditor({ scale }: { scale: GradingScale }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [bands, setBands] = useState<BandDraft[]>(() => scale.bands.map(toDraft))
  const [problems, setProblems] = useState<string[]>([])
  const [showErrors, setShowErrors] = useState(false)
  const [confirming, setConfirming] = useState(false)

  const save = useMutation({
    mutationFn: () =>
      updateGradingScale(bands.map((b) => ({
        letter: b.letter.trim().toUpperCase(),
        minMark: Number(b.minMark),
        gradePoints: Number(b.gradePoints),
        passing: b.passing,
      }))),
    onSuccess: (updated) => {
      setConfirming(false)
      setProblems([])
      queryClient.setQueryData(['grading-scale'], updated)
      setBands(updated.bands.map(toDraft))
      toast.success('Grading scale saved.')
    },
    onError: (error) => {
      setConfirming(false)
      const messages = toApiError(error).errors.map((e) => (e.field === 'bands' ? e.message : `${e.field}: ${e.message}`))
      setProblems(messages.length > 0 ? messages : [errorMessage(error)])
    },
  })

  const update = (key: number, changes: Partial<BandDraft>) => setBands((prev) => prev.map((b) => (b.key === key ? { ...b, ...changes } : b)))
  const range = ranges(bands)
  const invalid = bands.some((b) => Object.keys(bandErrors(b)).length > 0)

  return (
    <Card>
      <CardHeader
        title={scale.name}
        description="Each grade covers marks from its minimum up to the next higher grade's minimum. The lowest grade must start at 0."
        actions={
          <div className="flex gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setBands((prev) => [...prev, { key: nextKey++, letter: '', minMark: '', gradePoints: '', passing: true }])}
              disabled={bands.length >= 15}
            >
              <Plus aria-hidden="true" className="size-4" />
              Add grade
            </Button>
            <Button
              size="sm"
              onClick={() => {
                setShowErrors(true)
                if (!invalid) setConfirming(true)
              }}
            >
              Save scale
            </Button>
          </div>
        }
      />
      {problems.length > 0 && (
        <div className="px-5 pt-4">
          <Alert tone="error" title="The scale was not saved">
            <ul className="list-disc space-y-0.5 pl-4">
              {problems.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          </Alert>
        </div>
      )}
      <div className="overflow-x-auto">
        <table className="min-w-[40rem] divide-y divide-neutral-200 text-sm sm:min-w-full">
          <caption className="sr-only">Grade bands</caption>
          <thead className="bg-neutral-50">
            <tr>
              {['Grade', 'Minimum mark', 'Covers', 'Grade points', 'Pass'].map((h) => (
                <th key={h} scope="col" className="px-4 py-2.5 text-left text-xs font-semibold text-neutral-600 uppercase">
                  {h}
                </th>
              ))}
              <th scope="col" className="px-4 py-2.5">
                <span className="sr-only">Remove</span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {bands.map((b, index) => {
              const errors = showErrors ? bandErrors(b) : {}
              const input = (field: 'letter' | 'minMark' | 'gradePoints', label: string, width: string) => (
                <div>
                  <input
                    aria-label={`${label} for row ${index + 1}`}
                    aria-invalid={errors[field] ? true : undefined}
                    value={b[field]}
                    inputMode={field === 'letter' ? 'text' : 'decimal'}
                    onChange={(e) => update(b.key, { [field]: e.target.value })}
                    className={cn(
                      'h-8 rounded-lg border px-2 text-sm focus:ring-2 focus:outline-none',
                      width,
                      errors[field] ? 'border-red-500 focus:ring-red-500/20' : 'border-neutral-300 focus:border-brand-600 focus:ring-brand-600/20',
                    )}
                  />
                  {errors[field] && <p className="mt-1 text-xs text-red-700">{errors[field]}</p>}
                </div>
              )
              return (
                <tr key={b.key}>
                  <td className="px-4 py-2 align-top">{input('letter', 'Grade', 'w-16 uppercase')}</td>
                  <td className="px-4 py-2 align-top">{input('minMark', 'Minimum mark', 'w-24 text-right tabular-nums')}</td>
                  <td className="px-4 py-2 align-top whitespace-nowrap text-neutral-600 tabular-nums">{range.get(b.key) ?? '—'}</td>
                  <td className="px-4 py-2 align-top">{input('gradePoints', 'Grade points', 'w-20 text-right tabular-nums')}</td>
                  <td className="px-4 py-2 align-top">
                    <input
                      type="checkbox"
                      aria-label={`Row ${index + 1} is a pass`}
                      checked={b.passing}
                      onChange={(e) => update(b.key, { passing: e.target.checked })}
                      className="mt-2 size-4 rounded border-neutral-300 text-brand-700 focus:ring-brand-600"
                    />
                  </td>
                  <td className="px-4 py-2 text-right align-top">
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={`Remove grade ${b.letter || `in row ${index + 1}`}`}
                      onClick={() => setBands((prev) => prev.filter((x) => x.key !== b.key))}
                      disabled={bands.length <= 2}
                    >
                      <Trash2 aria-hidden="true" className="size-4" />
                    </Button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <ConfirmDialog
        open={confirming}
        tone="primary"
        title="Save the grading scale?"
        description="New grades apply to results finalized from now on and to the previews lecturers see. Results that are already finalized keep their grades."
        confirmLabel="Save scale"
        loading={save.isPending}
        onConfirm={() => save.mutate()}
        onCancel={() => setConfirming(false)}
      />
    </Card>
  )
}

export function GradingScalePage() {
  const query = useQuery({ queryKey: ['grading-scale'], queryFn: getGradingScale })
  return (
    <>
      <PageHeader title="Grading scale" description="Letter grades, the marks they cover and their grade points." />
      {query.error ? (
        <Alert tone="error">{errorMessage(query.error)}</Alert>
      ) : !query.data ? (
        <div className="flex justify-center p-12 text-brand-700">
          <Spinner label="Loading grading scale…" />
        </div>
      ) : (
        <ScaleEditor key={query.data.id} scale={query.data} />
      )}
    </>
  )
}
