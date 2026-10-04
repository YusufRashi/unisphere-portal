import { api } from './client'
import { cleanParams, type ListParams } from './query'
import type { AwardLevel, CourseDetail, CourseSummary, Department, PageResponse, Program } from '@/types/api'

// ---- Departments ----

export interface DepartmentInput {
  code: string
  name: string
  faculty?: string | null
}

export async function listDepartments(): Promise<Department[]> {
  return (await api.get<Department[]>('/departments')).data
}

export async function createDepartment(input: DepartmentInput): Promise<Department> {
  return (await api.post<Department>('/admin/departments', input)).data
}

export async function updateDepartment(id: number, input: DepartmentInput): Promise<Department> {
  return (await api.put<Department>(`/admin/departments/${id}`, input)).data
}

export async function deleteDepartment(id: number): Promise<void> {
  await api.delete(`/admin/departments/${id}`)
}

// ---- Programs ----

export interface ProgramInput {
  departmentId: number
  code: string
  name: string
  awardLevel: AwardLevel
  durationYears: number
  active: boolean
}

export async function listPrograms(params: { departmentId?: number; active?: boolean } = {}): Promise<Program[]> {
  return (await api.get<Program[]>('/programs', { params: cleanParams(params) })).data
}

export async function createProgram(input: ProgramInput): Promise<Program> {
  return (await api.post<Program>('/admin/programs', input)).data
}

export async function updateProgram(id: number, input: ProgramInput): Promise<Program> {
  return (await api.put<Program>(`/admin/programs/${id}`, input)).data
}

// ---- Courses ----

export interface CourseSearchParams extends ListParams {
  departmentId?: number
  level?: number
  active?: boolean
}

export interface CourseInput {
  departmentId: number
  code: string
  title: string
  description?: string | null
  credits: number
  level: number
  active: boolean
}

export async function searchCourses(params: CourseSearchParams): Promise<PageResponse<CourseSummary>> {
  return (await api.get<PageResponse<CourseSummary>>('/courses', { params: cleanParams(params) })).data
}

export async function getCourse(id: number): Promise<CourseDetail> {
  return (await api.get<CourseDetail>(`/courses/${id}`)).data
}

export async function createCourse(input: CourseInput): Promise<CourseDetail> {
  return (await api.post<CourseDetail>('/admin/courses', input)).data
}

export async function updateCourse(id: number, input: CourseInput): Promise<CourseDetail> {
  return (await api.put<CourseDetail>(`/admin/courses/${id}`, input)).data
}

export async function replacePrerequisites(id: number, prerequisiteIds: number[]): Promise<CourseDetail> {
  return (await api.put<CourseDetail>(`/admin/courses/${id}/prerequisites`, { prerequisiteIds })).data
}
