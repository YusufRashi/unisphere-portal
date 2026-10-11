import { z } from 'zod'
import { PAYMENT_METHODS } from '@/types/api'
import { amountError, toAmount } from '@/lib/money'

/** A typed amount, sent as an exact 2-decimal string. */
export const amountField = z
  .string()
  .superRefine((value, ctx) => {
    const message = amountError(value)
    if (message) ctx.addIssue({ code: 'custom', message })
  })
  .transform(toAmount)

export const chargeSchema = z.object({
  description: z.string().trim().min(1, 'Describe the charge.').max(200, 'At most 200 characters.'),
  amount: amountField,
  semesterId: z.string().transform((v) => (v === '' ? null : Number(v))),
})

export const officePaymentSchema = z.object({
  amount: amountField,
  method: z.enum(PAYMENT_METHODS),
  reference: z
    .string()
    .trim()
    .min(1, 'Enter the receipt or transaction reference.')
    .max(60, 'At most 60 characters.')
    .regex(/^[A-Za-z0-9][A-Za-z0-9/_.-]*$/, 'Use only letters, digits and / _ . -'),
  /** A datetime-local value; empty means "now". */
  paidAt: z
    .string()
    .refine((v) => v === '' || new Date(v).getTime() <= Date.now(), 'The payment date cannot be in the future.')
    .transform((v) => (v === '' ? null : new Date(v).toISOString())),
  note: z
    .string()
    .trim()
    .max(200, 'At most 200 characters.')
    .transform((v) => (v === '' ? null : v)),
})

export const reasonSchema = z.object({
  reason: z.string().trim().min(1, 'Give a reason; it is kept in the account history.').max(500, 'At most 500 characters.'),
})

export const feeStructureSchema = z.object({
  amount: amountField,
  description: z.string().trim().min(1, 'Enter a description.').max(120, 'At most 120 characters.'),
})
