import graduatesCaps from '@/assets/photos/graduates-caps.webp'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ArrowLeft, Download, Printer } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { errorMessage } from '@/api/errors'
import { downloadTranscriptPdf, getMyTranscript, getStudentTranscript } from '@/api/transcripts'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { TranscriptDocument } from './TranscriptDocument'

/**
 * Unofficial transcript: the student's own, or (registrar) any student's from the route. Each load issues
 * the transcript on the server, so the query is not refetched in the background.
 */
export function TranscriptPage({ mode }: { mode: 'student' | 'registrar' }) {
  const studentId = Number(useParams().studentId)
  const toast = useToast()
  const query = useQuery({
    queryKey: ['transcript', mode === 'student' ? 'me' : studentId],
    queryFn: () => (mode === 'student' ? getMyTranscript() : getStudentTranscript(studentId)),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })
  const download = useMutation({
    mutationFn: () => downloadTranscriptPdf(mode === 'student' ? undefined : studentId),
    onError: (error) => toast.error(errorMessage(error)),
  })

  return (
    <>
      <div className="print:hidden">
        {mode === 'registrar' && (
          <Link to="/admin/students" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-brand-700 hover:text-brand-900">
            <ArrowLeft aria-hidden="true" className="size-4" />
            Students
          </Link>
        )}
        <PageHeader
          image={graduatesCaps}
          imageClassName="object-[center_30%]"
          title={mode === 'student' ? 'My transcript' : 'Student transcript'}
          description="Unofficial: valid only with registrar certification."
          actions={
            query.data && (
              <div className="flex flex-wrap gap-2">
                <Button variant="secondary" onClick={() => window.print()}>
                  <Printer aria-hidden="true" className="size-4" />
                  Print
                </Button>
                <Button onClick={() => download.mutate()} loading={download.isPending}>
                  <Download aria-hidden="true" className="size-4" />
                  Download PDF
                </Button>
              </div>
            )
          }
        />
      </div>
      {query.error ? (
        <Alert tone="error">{errorMessage(query.error)}</Alert>
      ) : !query.data ? (
        <div className="flex justify-center p-12 text-brand-700">
          <Spinner label="Preparing transcript…" />
        </div>
      ) : (
        <TranscriptDocument transcript={query.data} />
      )}
    </>
  )
}
