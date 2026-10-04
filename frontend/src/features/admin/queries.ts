import { useQuery } from '@tanstack/react-query'
import { listDepartments, listPrograms, searchCourses } from '@/api/academic'
import { listSemesters } from '@/api/calendar'
import { searchLecturers } from '@/api/people'
import type { SelectOption } from '@/components/ui/SearchSelect'

/** Reference data used by filters and forms across admin screens (cached and shared). */
export function useDepartments() {
  return useQuery({ queryKey: ['departments'], queryFn: listDepartments, staleTime: 60_000 })
}

export function usePrograms(params: { departmentId?: number; active?: boolean } = {}) {
  return useQuery({ queryKey: ['programs', params], queryFn: () => listPrograms(params), staleTime: 60_000 })
}

export function useSemesters() {
  return useQuery({ queryKey: ['semesters'], queryFn: listSemesters, staleTime: 30_000 })
}

export async function courseOptions(q: string): Promise<SelectOption[]> {
  const page = await searchCourses({ q, active: true, size: 10 })
  return page.content.map((c) => ({ value: c.id, label: `${c.code} — ${c.title}`, description: `${c.credits} credits · Level ${c.level}` }))
}

/** Label is the course code and description the title, so a chip can show both. */
export async function prerequisiteOptions(q: string): Promise<SelectOption[]> {
  const page = await searchCourses({ q, size: 10 })
  return page.content.map((c) => ({ value: c.id, label: c.code, description: c.title }))
}

export async function lecturerOptions(q: string): Promise<SelectOption[]> {
  const page = await searchLecturers({ q, size: 10 })
  return page.content.map((l) => ({ value: l.id, label: l.displayName, description: `${l.staffNumber} · ${l.department.name}` }))
}
