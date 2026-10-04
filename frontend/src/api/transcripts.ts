import { api } from './client'
import type { Transcript, TranscriptVerification } from '@/types/api'

export async function getMyTranscript(): Promise<Transcript> {
  return (await api.get<Transcript>('/transcripts/me')).data
}

export async function getStudentTranscript(studentId: number): Promise<Transcript> {
  return (await api.get<Transcript>(`/transcripts/students/${studentId}`)).data
}

export async function verifyTranscript(reference: string): Promise<TranscriptVerification> {
  return (await api.get<TranscriptVerification>(`/transcripts/verify/${encodeURIComponent(reference)}`)).data
}

/** Downloads the PDF through the authenticated client and hands it to the browser as a file. */
export async function downloadTranscriptPdf(studentId?: number): Promise<void> {
  const path = studentId === undefined ? '/transcripts/me/pdf' : `/transcripts/students/${studentId}/pdf`
  const response = await api.get<Blob>(path, { responseType: 'blob' })
  const disposition = String(response.headers['content-disposition'] ?? '')
  const filename = /filename="([^"]+)"/.exec(disposition)?.[1] ?? 'transcript.pdf'
  const url = URL.createObjectURL(response.data)
  try {
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.append(link)
    link.click()
    link.remove()
  } finally {
    URL.revokeObjectURL(url)
  }
}
