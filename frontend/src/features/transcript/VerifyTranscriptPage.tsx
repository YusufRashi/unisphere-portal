import { useMutation } from '@tanstack/react-query'
import { ShieldCheck, ShieldX } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { errorMessage, toApiError } from '@/api/errors'
import { verifyTranscript } from '@/api/transcripts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { TextField } from '@/components/ui/fields'
import { formatGpa } from '@/features/results/marks'
import { formatDateTime } from '@/lib/format'

const REFERENCE = /^[0-9A-F]{4}(-[0-9A-F]{4}){3}$/

/** Checks the verification reference printed on a transcript against what the system issued. */
export function VerifyTranscriptPage() {
  const [input, setInput] = useState('')
  const [formatError, setFormatError] = useState<string | null>(null)
  const check = useMutation({ mutationFn: (reference: string) => verifyTranscript(reference) })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    const reference = input.trim().toUpperCase()
    if (!REFERENCE.test(reference)) {
      setFormatError('Enter the reference as printed, e.g. 1A2B-3C4D-5E6F-7A8B.')
      return
    }
    check.mutate(reference)
  }

  const v = check.data
  const notFound = check.error && toApiError(check.error).status === 404

  return (
    <>
      <PageHeader title="Verify a transcript" description="Compare a printed transcript with the record the system issued." />
      <div className="grid gap-4 lg:grid-cols-[minmax(0,26rem)_minmax(0,1fr)]">
        <Card>
          <form onSubmit={onSubmit} className="space-y-4 px-5 py-4" noValidate>
            <TextField
              label="Verification reference"
              placeholder="XXXX-XXXX-XXXX-XXXX"
              autoComplete="off"
              spellCheck={false}
              className="font-mono uppercase"
              value={input}
              error={formatError ?? undefined}
              onChange={(e) => {
                setInput(e.target.value)
                setFormatError(null)
              }}
            />
            <Button type="submit" loading={check.isPending}>
              Check reference
            </Button>
          </form>
        </Card>

        {notFound ? (
          <Alert tone="error" title="Not issued by this system">
            No transcript with this reference was ever issued. Treat the document as not genuine.
          </Alert>
        ) : check.error ? (
          <Alert tone="error">{errorMessage(check.error)}</Alert>
        ) : v ? (
          <Card>
            <CardHeader
              title={`${v.studentName} · ${v.studentNumber}`}
              description={`Reference ${v.reference}`}
              actions={
                v.current ? (
                  <Badge tone="success">Matches current record</Badge>
                ) : (
                  <Badge tone="warning">Superseded</Badge>
                )
              }
            />
            <CardBody className="space-y-4">
              <div className="flex items-start gap-3 text-sm text-neutral-700">
                {v.current ? (
                  <ShieldCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-emerald-600" />
                ) : (
                  <ShieldX aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-amber-600" />
                )}
                <p>
                  {v.current
                    ? 'Genuine, and the student’s record has not changed since. Check that the CGPA and credits below match the paper copy.'
                    : 'Genuine when issued, but the student’s results have changed since. The paper copy is out of date.'}
                </p>
              </div>
              <dl className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3">
                {(
                  [
                    ['CGPA on transcript', formatGpa(v.cgpa)],
                    ['Credits earned', String(v.creditsEarned)],
                    ['Courses listed', String(v.resultsCount)],
                    ['First issued', formatDateTime(v.firstIssuedAt)],
                    ['Last issued', formatDateTime(v.lastIssuedAt)],
                    ['Times issued', String(v.issueCount)],
                  ] as const
                ).map(([label, value]) => (
                  <div key={label}>
                    <dt className="text-xs text-neutral-500">{label}</dt>
                    <dd className="font-semibold text-neutral-900 tabular-nums">{value}</dd>
                  </div>
                ))}
              </dl>
            </CardBody>
          </Card>
        ) : null}
      </div>
    </>
  )
}
