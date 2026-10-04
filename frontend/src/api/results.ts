import { api } from './client'
import { cleanParams, type ListParams } from './query'
import type {
  AcademicRecord,
  ClassStatistics,
  GradeBand,
  GradingScale,
  MarkEntry,
  MarksSheet,
  PageResponse,
  ResultsQueueEntry,
  ResultsStatus,
  TeachingOffering,
} from '@/types/api'

// ---- Lecturer ----

export async function getMyTeaching(semesterId?: number): Promise<TeachingOffering[]> {
  return (await api.get<TeachingOffering[]>('/lecturer/offerings', { params: cleanParams({ semesterId }) })).data
}

/** Lecturers (own offerings) and registrars (any offering). */
export async function getMarksSheet(offeringId: number): Promise<MarksSheet> {
  return (await api.get<MarksSheet>(`/lecturer/offerings/${offeringId}/results`)).data
}

export async function saveMarks(offeringId: number, entries: MarkEntry[]): Promise<MarksSheet> {
  return (await api.put<MarksSheet>(`/lecturer/offerings/${offeringId}/results`, { entries })).data
}

export async function submitMarks(offeringId: number): Promise<MarksSheet> {
  return (await api.post<MarksSheet>(`/lecturer/offerings/${offeringId}/results/submit`)).data
}

export async function getClassStatistics(offeringId: number): Promise<ClassStatistics> {
  return (await api.get<ClassStatistics>(`/lecturer/offerings/${offeringId}/statistics`)).data
}

// ---- Student ----

export async function getMyResults(): Promise<AcademicRecord> {
  return (await api.get<AcademicRecord>('/results/me')).data
}

// ---- Registrar ----

export interface ResultsQueueParams extends ListParams {
  semesterId?: number
  status?: ResultsStatus
}

export async function searchResults(params: ResultsQueueParams): Promise<PageResponse<ResultsQueueEntry>> {
  return (await api.get<PageResponse<ResultsQueueEntry>>('/admin/results', { params: cleanParams(params) })).data
}

export async function finalizeResults(offeringId: number): Promise<MarksSheet> {
  return (await api.post<MarksSheet>(`/admin/offerings/${offeringId}/results/finalize`)).data
}

export async function returnResults(offeringId: number, comment: string): Promise<MarksSheet> {
  return (await api.post<MarksSheet>(`/admin/offerings/${offeringId}/results/return`, { comment })).data
}

// ---- Grading scale ----

export async function getGradingScale(): Promise<GradingScale> {
  return (await api.get<GradingScale>('/grading-scale')).data
}

export async function updateGradingScale(bands: GradeBand[]): Promise<GradingScale> {
  return (await api.put<GradingScale>('/admin/grading-scale', { bands })).data
}
