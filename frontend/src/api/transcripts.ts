import { api } from './client'
import { downloadFile } from './download'
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

export async function downloadTranscriptPdf(studentId?: number): Promise<void> {
  const path = studentId === undefined ? '/transcripts/me/pdf' : `/transcripts/students/${studentId}/pdf`
  await downloadFile(path, undefined, 'transcript.pdf')
}
