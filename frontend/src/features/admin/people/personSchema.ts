import { z } from 'zod'

/** Name / email / phone fields shared by the student, lecturer and staff account forms. */
export const personSchema = z.object({
  firstName: z.string().trim().min(1, 'Enter a first name.').max(100),
  lastName: z.string().trim().min(1, 'Enter a last name.').max(100),
  email: z.string().trim().min(1, 'Enter an email address.').pipe(z.email('Enter a valid email address.')),
  phone: z
    .string()
    .trim()
    .refine((v) => v === '' || /^\+?[0-9 ()-]{7,30}$/.test(v), 'Enter a valid phone number.'),
})

export const PERSON_FIELDS = ['firstName', 'lastName', 'email', 'phone'] as const
