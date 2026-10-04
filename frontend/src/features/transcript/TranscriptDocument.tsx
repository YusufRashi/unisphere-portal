import { formatGpa, formatNumber } from '@/features/results/marks'
import { AWARD_LABELS, formatDate, formatDateTime, STUDENT_STATUS_LABELS } from '@/lib/format'
import type { Transcript } from '@/types/api'

/**
 * The transcript as a printable document. The diagonal watermark (with the generation time and
 * verification reference) becomes `position: fixed` in print, which browsers repeat on every page.
 */
export function TranscriptDocument({ transcript: t }: { transcript: Transcript }) {
  const generated = formatDateTime(t.generatedAt)
  const anyRepeated = t.semesters.some((s) => s.courses.some((c) => !c.countsTowardCgpa))
  const fields: [string, string][] = [
    ['Student name', t.student.name],
    ['Student number', t.student.studentNumber],
    ['Status', STUDENT_STATUS_LABELS[t.student.status]],
    ['Program', `${t.student.programCode} — ${t.student.programName} (${AWARD_LABELS[t.student.awardLevel]})`],
    ['Department', t.student.departmentName],
    ['Admitted', formatDate(t.student.admissionDate)],
  ]

  return (
    <article
      aria-label={`${t.title} for ${t.student.name}`}
      className="relative overflow-hidden rounded-lg border border-slate-200 bg-white px-6 py-8 shadow-xs sm:px-10 print:overflow-visible print:rounded-none print:border-0 print:p-0 print:shadow-none"
    >
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 flex items-center justify-center overflow-hidden select-none print:fixed">
        <div className="-rotate-[35deg] text-center text-slate-900/[0.06] print:text-slate-900/10">
          <p className="text-2xl font-bold tracking-wider whitespace-nowrap sm:text-3xl print:text-xl">{t.watermark}</p>
          <p className="mt-2 text-sm font-semibold whitespace-nowrap print:text-xs">
            Generated {generated} · Reference {t.reference}
          </p>
        </div>
      </div>

      <header className="relative border-b-2 border-brand-900 pb-4">
        <h2 className="text-2xl font-bold text-brand-900">{t.universityName}</h2>
        <p className="text-sm text-slate-600">{t.title}</p>
      </header>

      <dl className="relative mt-5 grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-3">
        {fields.map(([label, value]) => (
          <div key={label}>
            <dt className="text-[11px] font-medium tracking-wide text-slate-500 uppercase">{label}</dt>
            <dd className="text-sm font-semibold text-slate-900">{value}</dd>
          </div>
        ))}
      </dl>

      {t.semesters.length === 0 ? (
        <p className="relative mt-8 text-sm text-slate-600 italic">No finalized results yet.</p>
      ) : (
        t.semesters.map((s) => (
          <section key={s.semester.id} className="relative mt-7 break-inside-avoid-page">
            <h3 className="mb-2 text-sm font-semibold text-brand-900">{s.semester.name}</h3>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[30rem] text-sm">
                <caption className="sr-only">Results for {s.semester.name}</caption>
                <thead>
                  <tr className="border-b border-slate-300 text-[11px] tracking-wide text-slate-500 uppercase">
                    <th scope="col" className="py-1.5 pr-3 text-left font-semibold">Code</th>
                    <th scope="col" className="py-1.5 pr-3 text-left font-semibold">Course title</th>
                    <th scope="col" className="py-1.5 pr-3 text-right font-semibold">Credits</th>
                    <th scope="col" className="py-1.5 pr-3 text-right font-semibold">Mark</th>
                    <th scope="col" className="py-1.5 pr-3 text-right font-semibold">Grade</th>
                    <th scope="col" className="py-1.5 text-right font-semibold">Points</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {s.courses.map((c) => (
                    <tr key={c.resultId}>
                      <td className="py-1.5 pr-3 font-medium whitespace-nowrap text-slate-900">
                        {c.courseCode}
                        {!c.countsTowardCgpa && <span title="Earlier attempt, not counted in the CGPA"> *</span>}
                      </td>
                      <td className="py-1.5 pr-3 text-slate-700">{c.courseTitle}</td>
                      <td className="py-1.5 pr-3 text-right tabular-nums">{c.credits}</td>
                      <td className="py-1.5 pr-3 text-right tabular-nums">{formatNumber(c.totalMark)}</td>
                      <td className={c.passed ? 'py-1.5 pr-3 text-right font-semibold' : 'py-1.5 pr-3 text-right font-semibold text-red-700'}>
                        {c.grade}
                        {!c.passed && <span className="sr-only"> (fail)</span>}
                      </td>
                      <td className="py-1.5 text-right tabular-nums">{c.gradePoints.toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-slate-300">
                    <td colSpan={6} className="py-1.5 text-right text-sm font-semibold text-slate-900">
                      Semester GPA {formatGpa(s.gpa)}
                      <span className="ml-4 font-normal text-slate-600">
                        Credits earned {s.creditsEarned} of {s.creditsAttempted}
                      </span>
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </section>
        ))
      )}

      <dl className="relative mt-8 grid grid-cols-3 gap-4 border-t-2 border-brand-900 pt-3">
        {(
          [
            ['Cumulative GPA', formatGpa(t.cgpa)],
            ['Credits earned', String(t.creditsEarned)],
            ['Credits counted', String(t.creditsAttempted)],
          ] as const
        ).map(([label, value]) => (
          <div key={label}>
            <dt className="text-[11px] font-medium tracking-wide text-slate-500 uppercase">{label}</dt>
            <dd className="text-lg font-bold text-slate-900 tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>

      <footer className="relative mt-6 space-y-1 text-xs text-slate-500">
        <p>
          GPA = total of (credits × grade points) ÷ total credits, rounded half-up to two decimals. Only finalized results are listed.
          {anyRepeated && ' * Earlier attempt of a repeated course: shown for completeness, excluded from the CGPA.'}
        </p>
        <p className="font-semibold text-slate-700">{t.watermark}</p>
        <p>
          Generated {generated} · Verification reference <span className="font-mono font-semibold text-slate-700">{t.reference}</span>
        </p>
      </footer>
    </article>
  )
}
