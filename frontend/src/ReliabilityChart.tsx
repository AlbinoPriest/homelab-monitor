import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { MetricBucket } from './types'

export default function ReliabilityChart({ buckets }: { buckets: MetricBucket[] }) {
  const data = buckets.map((bucket) => {
    const total = bucket.availableMillis + bucket.unavailableMillis + bucket.excludedMillis
    return {
      label: new Date(bucket.start).toLocaleString(),
      available: total ? (bucket.availableMillis / total) * 100 : 0,
      unavailable: total ? (bucket.unavailableMillis / total) * 100 : 0,
      excluded: total ? (bucket.excludedMillis / total) * 100 : 100,
    }
  })
  return (
    <figure className="reliability-chart" aria-labelledby="reliability-chart-caption">
      <figcaption id="reliability-chart-caption" className="sr-only">
        Availability over time. Green is available, red is unavailable, and gray is excluded because
        no fresh observation existed.
      </figcaption>
      <ResponsiveContainer width="100%" height="100%" minWidth={0}>
        <BarChart data={data} accessibilityLayer barCategoryGap={2}>
          <XAxis dataKey="label" hide />
          <YAxis domain={[0, 100]} hide />
          <Tooltip
            formatter={(value) => `${Number(value).toFixed(1)}%`}
            labelFormatter={(label) => String(label)}
            contentStyle={{ background: '#101719', border: '1px solid #344244' }}
          />
          <Bar dataKey="available" name="Available" stackId="availability" fill="#56ce9b" />
          <Bar dataKey="unavailable" name="Unavailable" stackId="availability" fill="#e2675d" />
          <Bar dataKey="excluded" name="Excluded" stackId="availability" fill="#202b2d" />
        </BarChart>
      </ResponsiveContainer>
    </figure>
  )
}
