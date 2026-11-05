import { useState, type ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'

// Recharts draws SVG attributes, so these repeat the theme: brand-600 (checked for contrast and colour
// blindness against the white card) and slate-200 / slate-500 for the recessive grid and axis text.
const SERIES = '#2550eb'
const GRID = '#e2e8f0'
const AXIS_TEXT = '#64748b'
const HEIGHT = 240

export interface TimePoint {
  /** Short text on the axis, e.g. "5 Nov". */
  label: string
  /** Longer text for the tooltip and table, e.g. "5 Nov 2026". */
  period: string
  value: number
  /** The value as a reader should see it, e.g. "KES 1,250.00". */
  text: string
}

interface TimeChartProps {
  title: string
  description?: string
  kind: 'column' | 'line'
  data: TimePoint[]
  /** Column headings of the table view. */
  periodHeading: string
  valueHeading: string
  formatTick: (value: number) => string
  /** Fixed value axis, e.g. 0–4 for GPA. Defaults to starting at zero. */
  domain?: [number, number]
  ticks?: number[]
  /** Replaces the chart when there is nothing to plot yet. */
  empty?: ReactNode
}

function PointTooltip({ active, payload }: { active?: boolean; payload?: readonly { payload?: TimePoint }[] }) {
  const point = payload?.[0]?.payload
  if (!active || !point) return null
  return (
    <div className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs shadow-md">
      <p className="text-sm font-semibold text-slate-900">{point.text}</p>
      <p className="mt-0.5 text-slate-500">{point.period}</p>
    </div>
  )
}

/**
 * One series over time. Every chart can be switched to a table, so no value depends on hovering or on seeing
 * the chart at all; the caption tells screen readers the same choice exists.
 */
export function TimeChart({ title, description, kind, data, periodHeading, valueHeading, formatTick, domain, ticks, empty }: TimeChartProps) {
  const [asTable, setAsTable] = useState(false)
  const axes = (
    <>
      <CartesianGrid vertical={false} stroke={GRID} />
      <XAxis
        dataKey="label"
        tickLine={false}
        axisLine={{ stroke: GRID }}
        interval="preserveStartEnd"
        minTickGap={24}
        tick={{ fontSize: 12, fill: AXIS_TEXT }}
      />
      <YAxis
        width={52}
        tickLine={false}
        axisLine={false}
        allowDecimals={false}
        domain={domain ?? [0, 'auto']}
        ticks={ticks}
        tickFormatter={formatTick}
        tick={{ fontSize: 12, fill: AXIS_TEXT }}
      />
    </>
  )

  return (
    <Card>
      <CardHeader
        title={title}
        description={description}
        actions={
          !empty && (
            <Button variant="ghost" size="sm" aria-pressed={asTable} onClick={() => setAsTable((t) => !t)}>
              {asTable ? 'View as chart' : 'View as table'}
              <span className="sr-only">: {title}</span>
            </Button>
          )
        }
      />
      <CardBody>
        {empty ?? (asTable ? (
          <div className="max-h-60 overflow-y-auto">
            <table className="w-full text-sm">
              <caption className="sr-only">{title}</caption>
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                  <th scope="col" className="py-2 font-medium">{periodHeading}</th>
                  <th scope="col" className="py-2 text-right font-medium">{valueHeading}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((p) => (
                  <tr key={p.period}>
                    <td className="py-1.5 text-slate-700">{p.period}</td>
                    <td className="py-1.5 text-right text-slate-900 tabular-nums">{p.text}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <figure>
            <figcaption className="sr-only">{title}. Use the "View as table" button for the exact figures.</figcaption>
            <ResponsiveContainer width="100%" height={HEIGHT}>
              {kind === 'column' ? (
                <BarChart data={data} margin={{ top: 8, right: 4, bottom: 0, left: 0 }} barCategoryGap="20%">
                  {axes}
                  <Tooltip cursor={{ fill: '#f1f5f9' }} content={PointTooltip} />
                  <Bar dataKey="value" fill={SERIES} maxBarSize={24} radius={[4, 4, 0, 0]} isAnimationActive={false} />
                </BarChart>
              ) : (
                <LineChart data={data} margin={{ top: 16, right: 32, bottom: 0, left: 0 }}>
                  {axes}
                  <Tooltip cursor={{ stroke: GRID }} content={PointTooltip} />
                  <Line
                    dataKey="value"
                    stroke={SERIES}
                    strokeWidth={2}
                    dot={{ r: 4, fill: SERIES, stroke: '#ffffff', strokeWidth: 2 }}
                    activeDot={{ r: 5, fill: SERIES, stroke: '#ffffff', strokeWidth: 2 }}
                    isAnimationActive={false}
                    label={({ x, y, index }: { x?: number | string; y?: number | string; index?: number }) =>
                      index === data.length - 1 ? (
                        <text x={Number(x)} y={Number(y) - 12} textAnchor="middle" fontSize={12} fontWeight={600} fill="#0f172a">
                          {data[index]?.text}
                        </text>
                      ) : (
                        <g />
                      )
                    }
                  />
                </LineChart>
              )}
            </ResponsiveContainer>
          </figure>
        ))}
      </CardBody>
    </Card>
  )
}
