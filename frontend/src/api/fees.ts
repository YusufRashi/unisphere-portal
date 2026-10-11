import { api } from './client'
import { downloadFile } from './download'
import { cleanParams, type ListParams } from './query'
import type {
  ChargePreview,
  FeeAccount,
  FeeAccountRow,
  FeeStructures,
  GenerationResult,
  OnlinePaymentResult,
  PageResponse,
  PaymentMethod,
  PaymentReport,
} from '@/types/api'

// ---- Student ----

export async function getMyFees(): Promise<FeeAccount> {
  return (await api.get<FeeAccount>('/fees/me')).data
}

/** `idempotencyKey` must stay the same for retries of one payment attempt. */
export async function payOnline(input: { amount: string; method: PaymentMethod; idempotencyKey: string }): Promise<OnlinePaymentResult> {
  return (await api.post<OnlinePaymentResult>('/fees/me/payments/simulate', input)).data
}

// ---- Accounts ----

export interface FeeAccountSearchParams extends ListParams {
  programId?: number
  hasBalance?: boolean
}

export async function searchFeeAccounts(params: FeeAccountSearchParams): Promise<PageResponse<FeeAccountRow>> {
  return (await api.get<PageResponse<FeeAccountRow>>('/fees/accounts', { params: cleanParams(params) })).data
}

export async function getFeeAccount(studentId: number): Promise<FeeAccount> {
  return (await api.get<FeeAccount>(`/fees/accounts/${studentId}`)).data
}

export async function addCharge(studentId: number, input: { description: string; amount: string; semesterId: number | null }): Promise<FeeAccount> {
  return (await api.post<FeeAccount>(`/fees/accounts/${studentId}/charges`, input)).data
}

export interface OfficePaymentInput {
  amount: string
  method: PaymentMethod
  reference: string
  paidAt: string | null
  note: string | null
}

export async function recordPayment(studentId: number, input: OfficePaymentInput): Promise<FeeAccount> {
  return (await api.post<FeeAccount>(`/fees/accounts/${studentId}/payments`, input)).data
}

export async function reversePayment(paymentId: number, reason: string): Promise<FeeAccount> {
  return (await api.post<FeeAccount>(`/fees/payments/${paymentId}/reverse`, { reason })).data
}

export async function voidCharge(chargeId: number, reason: string): Promise<FeeAccount> {
  return (await api.post<FeeAccount>(`/fees/charges/${chargeId}/void`, { reason })).data
}

// ---- Fee structures and billing ----

export async function getFeeStructures(semesterId: number): Promise<FeeStructures> {
  return (await api.get<FeeStructures>('/fees/structures', { params: { semesterId } })).data
}

export async function saveFeeStructure(input: { programId: number; semesterId: number; amount: string; description: string }): Promise<FeeStructures> {
  return (await api.put<FeeStructures>('/fees/structures', input)).data
}

export async function deleteFeeStructure(structureId: number): Promise<void> {
  await api.delete(`/fees/structures/${structureId}`)
}

export async function previewCharges(semesterId: number): Promise<ChargePreview> {
  return (await api.get<ChargePreview>(`/fees/semesters/${semesterId}/charges/preview`)).data
}

export async function generateCharges(semesterId: number): Promise<GenerationResult> {
  return (await api.post<GenerationResult>(`/fees/semesters/${semesterId}/generate-charges`)).data
}

// ---- Reports ----

export async function getOutstanding(programId?: number): Promise<FeeAccountRow[]> {
  return (await api.get<FeeAccountRow[]>('/fees/reports/outstanding', { params: cleanParams({ programId }) })).data
}

export async function getPaymentReport(from: string, to: string): Promise<PaymentReport> {
  return (await api.get<PaymentReport>('/fees/reports/payments', { params: { from, to } })).data
}

export async function downloadOutstandingCsv(programId?: number): Promise<void> {
  await downloadFile('/fees/reports/outstanding', cleanParams({ programId, format: 'csv' }), 'outstanding-balances.csv')
}

export async function downloadPaymentsCsv(from: string, to: string): Promise<void> {
  await downloadFile('/fees/reports/payments', { from, to, format: 'csv' }, 'payments.csv')
}
