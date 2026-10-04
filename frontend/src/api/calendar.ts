import { api } from './client'
import { cleanParams, type ListParams } from './query'
import type { DayOfWeek, Offering, PageResponse, Semester } from '@/types/api'

// ---- Semesters ----

export interface SemesterInput {
  code: string
  name: string
  academicYear: string
  term: number
  startDate: string
  endDate: string
  registrationOpensAt: string
  registrationClosesAt: string
  addDropDeadline: string
  requiresApproval: boolean
  minCredits: number
  maxCredits: number
}

export async function listSemesters(): Promise<Semester[]> {
  return (await api.get<Semester[]>('/semesters')).data
}

export async function createSemester(input: SemesterInput): Promise<Semester> {
  return (await api.post<Semester>('/admin/semesters', input)).data
}

export async function updateSemester(id: number, input: SemesterInput): Promise<Semester> {
  return (await api.put<Semester>(`/admin/semesters/${id}`, input)).data
}

export type SemesterAction = 'make-current' | 'registration/open' | 'registration/close'

export async function semesterAction(id: number, action: SemesterAction): Promise<Semester> {
  return (await api.post<Semester>(`/admin/semesters/${id}/${action}`)).data
}

// ---- Offerings ----

export interface OfferingSearchParams extends ListParams {
  semesterId?: number
  departmentId?: number
  level?: number
  lecturerId?: number
}

export interface OfferingInput {
  courseId: number
  semesterId: number
  section: string
  lecturerId: number | null
  capacity: number
  catWeight: number
  examWeight: number
  examStartsAt: string | null
  examVenue: string | null
  sessions: { dayOfWeek: DayOfWeek; startTime: string; endTime: string; venue: string }[]
}

export async function searchOfferings(params: OfferingSearchParams): Promise<PageResponse<Offering>> {
  return (await api.get<PageResponse<Offering>>('/offerings', { params: cleanParams(params) })).data
}

export async function getOffering(id: number): Promise<Offering> {
  return (await api.get<Offering>(`/offerings/${id}`)).data
}

export async function createOffering(input: OfferingInput): Promise<Offering> {
  return (await api.post<Offering>('/admin/offerings', input)).data
}

export async function updateOffering(id: number, input: OfferingInput): Promise<Offering> {
  return (await api.put<Offering>(`/admin/offerings/${id}`, input)).data
}

export async function deleteOffering(id: number): Promise<void> {
  await api.delete(`/admin/offerings/${id}`)
}
