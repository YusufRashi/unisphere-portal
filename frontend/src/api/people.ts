import { api } from './client'
import { cleanParams, type ListParams } from './query'
import type { Lecturer, PageResponse, Role, Student, StudentStatus, UserAccount } from '@/types/api'

interface PersonInput {
  firstName: string
  lastName: string
  email: string
  phone: string | null
}

// ---- Students ----

export interface StudentSearchParams extends ListParams {
  programId?: number
  departmentId?: number
  status?: StudentStatus
  yearOfStudy?: number
}

export interface CreateStudentInput extends PersonInput {
  programId: number
  yearOfStudy: number
  admissionDate: string
}

export interface UpdateStudentInput {
  programId: number
  yearOfStudy: number
  status: StudentStatus
}

export async function searchStudents(params: StudentSearchParams): Promise<PageResponse<Student>> {
  return (await api.get<PageResponse<Student>>('/admin/students', { params: cleanParams(params) })).data
}

export async function createStudent(input: CreateStudentInput): Promise<Student> {
  return (await api.post<Student>('/admin/students', input)).data
}

export async function updateStudent(id: number, input: UpdateStudentInput): Promise<Student> {
  return (await api.put<Student>(`/admin/students/${id}`, input)).data
}

// ---- Lecturers ----

export interface LecturerSearchParams extends ListParams {
  departmentId?: number
}

export interface LecturerProfileInput {
  departmentId: number
  title: string | null
  office: string | null
}

export async function searchLecturers(params: LecturerSearchParams): Promise<PageResponse<Lecturer>> {
  return (await api.get<PageResponse<Lecturer>>('/admin/lecturers', { params: cleanParams(params) })).data
}

export async function createLecturer(input: PersonInput & LecturerProfileInput): Promise<Lecturer> {
  return (await api.post<Lecturer>('/admin/lecturers', input)).data
}

export async function updateLecturer(id: number, input: LecturerProfileInput): Promise<Lecturer> {
  return (await api.put<Lecturer>(`/admin/lecturers/${id}`, input)).data
}

// ---- Staff & any account ----

export async function createStaff(input: PersonInput & { roles: Role[] }): Promise<UserAccount> {
  return (await api.post<UserAccount>('/admin/users/staff', input)).data
}

export async function resendActivation(userId: number): Promise<void> {
  await api.post(`/admin/users/${userId}/resend-activation`)
}
