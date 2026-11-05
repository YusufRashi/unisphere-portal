import { api } from './client'
import type { AdminDashboardData, FinanceDashboardData, LecturerDashboardData, StudentDashboardData } from '@/types/api'

export async function getStudentDashboard(): Promise<StudentDashboardData> {
  return (await api.get<StudentDashboardData>('/students/me/dashboard')).data
}

export async function getLecturerDashboard(): Promise<LecturerDashboardData> {
  return (await api.get<LecturerDashboardData>('/lecturer/dashboard')).data
}

export async function getAdminDashboard(): Promise<AdminDashboardData> {
  return (await api.get<AdminDashboardData>('/admin/dashboard')).data
}

export async function getFinanceDashboard(): Promise<FinanceDashboardData> {
  return (await api.get<FinanceDashboardData>('/fees/dashboard')).data
}
