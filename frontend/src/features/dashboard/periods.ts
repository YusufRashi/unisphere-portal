// Series dates are whole UTC days ("2026-11-05"), so they are formatted in UTC: in a time zone behind UTC the
// browser's local date would show the day before.
const short = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', timeZone: 'UTC' })
const long = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })
const compact = new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 })

export function shortDay(date: string): string {
  return short.format(new Date(date))
}

export function longDay(date: string): string {
  return long.format(new Date(date))
}

/** 950 → "950", 1200 → "1.2K": axis ticks only; exact figures are in the tooltip and table. */
export function compactNumber(value: number): string {
  return compact.format(value)
}
