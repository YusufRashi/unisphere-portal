import { api } from './client'
import { cleanParams, type ListParams } from './query'
import type {
  CatalogueEntry,
  PageResponse,
  RegistrationDetail,
  RegistrationStatus,
  RegistrationSummary,
  RegistrationView,
  Timetable,
} from '@/types/api'

// ---- Student ----

export async function getMyRegistration(): Promise<RegistrationView> {
  return (await api.get<RegistrationView>('/registrations/me/current')).data
}

export interface CatalogueParams extends ListParams {
  departmentId?: number
  level?: number
}

export async function getCatalogue(params: CatalogueParams): Promise<PageResponse<CatalogueEntry>> {
  return (await api.get<PageResponse<CatalogueEntry>>('/registrations/me/catalogue', { params: cleanParams(params) })).data
}

export async function addCourse(offeringId: number): Promise<RegistrationView> {
  return (await api.post<RegistrationView>('/registrations/me/current/items', { offeringId })).data
}

export async function removeCourse(itemId: number): Promise<RegistrationView> {
  return (await api.delete<RegistrationView>(`/registrations/me/current/items/${itemId}`)).data
}

export async function submitRegistration(): Promise<RegistrationView> {
  return (await api.post<RegistrationView>('/registrations/me/current/submit')).data
}

export async function getMyTimetable(): Promise<Timetable> {
  return (await api.get<Timetable>('/students/me/timetable')).data
}

// ---- Registrar ----

export interface RegistrationSearchParams extends ListParams {
  semesterId?: number
  status?: RegistrationStatus
}

export async function searchRegistrations(params: RegistrationSearchParams): Promise<PageResponse<RegistrationSummary>> {
  return (await api.get<PageResponse<RegistrationSummary>>('/admin/registrations', { params: cleanParams(params) })).data
}

export async function getRegistration(id: number): Promise<RegistrationDetail> {
  return (await api.get<RegistrationDetail>(`/admin/registrations/${id}`)).data
}

export async function approveRegistration(id: number): Promise<RegistrationDetail> {
  return (await api.post<RegistrationDetail>(`/admin/registrations/${id}/approve`)).data
}

export async function rejectRegistration(id: number, reason: string): Promise<RegistrationDetail> {
  return (await api.post<RegistrationDetail>(`/admin/registrations/${id}/reject`, { reason })).data
}
