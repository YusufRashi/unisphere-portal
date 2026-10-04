import { BarChart3 } from 'lucide-react'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import type { ClassStatistics } from '@/types/api'
import { formatNumber } from './marks'

export function ClassStatisticsCard({ stats }: { stats: ClassStatistics | undefined }) {
  const max = Math.max(1, ...(stats?.distribution.map((d) => d.count) ?? [1]))
  return (
    <Card>
      <CardHeader
        title="Class statistics"
        description={stats ? `${stats.graded} of ${stats.classSize} students graded` : undefined}
      />
      {!stats || stats.graded === 0 ? (
        <EmptyState icon={BarChart3} title="No complete marks yet" description="Statistics appear once students have both marks." />
      ) : (
        <CardBody className="space-y-5">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm sm:grid-cols-3">
            {(
              [
                ['Mean', formatNumber(stats.mean)],
                ['Median', formatNumber(stats.median)],
                ['Pass rate', `${formatNumber(stats.passRate, 1)}%`],
                ['Highest', formatNumber(stats.highest)],
                ['Lowest', formatNumber(stats.lowest)],
              ] as const
            ).map(([label, value]) => (
              <div key={label}>
                <dt className="text-xs text-slate-500">{label}</dt>
                <dd className="text-lg font-semibold text-slate-900 tabular-nums">{value}</dd>
              </div>
            ))}
          </dl>
          <div>
            <h3 className="mb-2 text-xs font-semibold tracking-wide text-slate-600 uppercase">Grade distribution</h3>
            <ul className="space-y-1.5" aria-label="Grade distribution">
              {stats.distribution.map((d) => (
                <li key={d.letter} className="flex items-center gap-3 text-sm">
                  <span className="w-8 font-medium text-slate-700">{d.letter}</span>
                  <span aria-hidden="true" className="h-2.5 flex-1 rounded-full bg-slate-100">
                    <span className="block h-full rounded-full bg-brand-600" style={{ width: `${(d.count / max) * 100}%` }} />
                  </span>
                  <span className="w-20 text-right text-slate-600 tabular-nums">
                    {d.count} {d.count === 1 ? 'student' : 'students'}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </CardBody>
      )}
    </Card>
  )
}
