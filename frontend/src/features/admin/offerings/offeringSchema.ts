import { z } from 'zod'
import { timesOverlap } from '@/lib/datetime'
import { DAYS_OF_WEEK } from '@/types/api'

const option = z.object({ value: z.number(), label: z.string(), description: z.string().optional() })

const session = z.object({
  dayOfWeek: z.enum(DAYS_OF_WEEK),
  startTime: z.string().regex(/^\d{2}:\d{2}$/, 'Enter a start time.'),
  endTime: z.string().regex(/^\d{2}:\d{2}$/, 'Enter an end time.'),
  venue: z.string().trim().min(1, 'Enter a venue.').max(80),
})

/**
 * Client-side mirror of the server's offering rules for instant feedback. Venue and lecturer clashes with
 * other offerings need the whole timetable, so only the server checks those (409 SCHEDULE_CONFLICT).
 */
export const offeringSchema = z
  .object({
    course: option.nullable().refine((v) => v !== null, 'Choose a course.'),
    semesterId: z.coerce.number<string>().int().positive('Choose a semester.'),
    section: z.string().trim().min(1, 'Enter a section.').max(5, 'At most 5 characters.').regex(/^[A-Za-z0-9]+$/, 'Letters and digits only.'),
    lecturer: option.nullable(),
    capacity: z.coerce.number<string>().int('Whole numbers only.').min(1, 'At least 1 seat.').max(1000, 'At most 1000 seats.'),
    catWeight: z.coerce.number<string>().min(0, 'Between 0 and 100.').max(100, 'Between 0 and 100.'),
    examStartsAt: z.string(),
    examVenue: z.string().trim().max(80),
    sessions: z.array(session).max(10, 'At most 10 weekly sessions.'),
  })
  .superRefine((v, ctx) => {
    v.sessions.forEach((s, i) => {
      if (s.startTime && s.endTime && s.endTime <= s.startTime) {
        ctx.addIssue({ code: 'custom', path: ['sessions', i, 'endTime'], message: 'Must be after the start time.' })
      }
      for (let j = 0; j < i; j++) {
        const other = v.sessions[j]
        if (other && other.dayOfWeek === s.dayOfWeek && timesOverlap(other.startTime, other.endTime, s.startTime, s.endTime)) {
          ctx.addIssue({ code: 'custom', path: ['sessions', i, 'startTime'], message: `Overlaps session ${j + 1}.` })
        }
      }
    })
  })

export type OfferingFormInput = z.input<typeof offeringSchema>
export type OfferingFormValues = z.output<typeof offeringSchema>
