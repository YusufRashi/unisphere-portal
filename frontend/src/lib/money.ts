import type { BadgeTone } from '@/components/ui/Badge'
import type { PaymentMethod, PaymentStatus } from '@/types/api'

const formatters = new Map<string, Intl.NumberFormat>()

/** "KES 85,000.00". Display only: amounts are never calculated on the client. */
export function formatMoney(amount: number | null | undefined, currency: string): string {
  if (amount === null || amount === undefined) return '—'
  let f = formatters.get(currency)
  if (!f) {
    f = new Intl.NumberFormat('en', { style: 'currency', currency, currencyDisplay: 'code', minimumFractionDigits: 2 })
    formatters.set(currency, f)
  }
  // Intl puts a non-breaking space after the code; a normal space reads the same and is easier to match.
  return f.format(amount).replace(/ /g, ' ')
}

/** A typed amount: digits with at most 2 decimals, more than zero. Returns a message or null. */
export function amountError(input: string, max?: number): string | null {
  const value = input.trim()
  if (value === '') return 'Enter an amount.'
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(value)) return 'Enter an amount such as 1500 or 1500.50.'
  const n = Number(value)
  if (n <= 0) return 'The amount must be more than zero.'
  if (max !== undefined && n > max) return `At most ${max.toFixed(2)}.`
  return null
}

/** Normalises "1500" or "1500.5" to "1500.00" so the exact decimal is sent to the server. */
export function toAmount(input: string): string {
  const [whole = '0', fraction = ''] = input.trim().split('.')
  return `${whole}.${fraction.padEnd(2, '0')}`
}

/** "Balance due", "In credit" or "Settled", with the figure to show beside it. */
export function balanceStatus(balance: number): { label: string; amount: number; tone: string } {
  if (balance > 0) return { label: 'Balance due', amount: balance, tone: 'text-red-700' }
  if (balance < 0) return { label: 'In credit', amount: -balance, tone: 'text-emerald-700' }
  return { label: 'Settled', amount: 0, tone: 'text-slate-900' }
}

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: 'Cash',
  BANK_TRANSFER: 'Bank transfer',
  MOBILE_MONEY: 'Mobile money',
  CARD: 'Card',
}

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  COMPLETED: 'Completed',
  FAILED: 'Declined',
  REVERSED: 'Reversed',
}

export const PAYMENT_STATUS_TONES: Record<PaymentStatus, BadgeTone> = {
  COMPLETED: 'success',
  FAILED: 'danger',
  REVERSED: 'neutral',
}
