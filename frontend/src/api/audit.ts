import { api } from './client'
import { cleanParams } from './query'
import type { AuditEntry, AuditFilters, PageResponse } from '@/types/api'

export interface AuditSearchParams {
  actor?: string
  action?: string
  entityType?: string
  /** ISO dates, inclusive, UTC. */
  from?: string
  to?: string
  page?: number
  size?: number
}

export async function searchAuditLogs(params: AuditSearchParams): Promise<PageResponse<AuditEntry>> {
  return (await api.get<PageResponse<AuditEntry>>('/admin/audit-logs', { params: cleanParams(params) })).data
}

export async function getAuditFilters(): Promise<AuditFilters> {
  return (await api.get<AuditFilters>('/admin/audit-logs/filters')).data
}
