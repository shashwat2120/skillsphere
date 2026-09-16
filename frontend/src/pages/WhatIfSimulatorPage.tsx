import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { CalendarRange, Loader2, Sparkles } from 'lucide-react'
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api } from '@/lib/api'

interface MonthlyProjection {
  month: number
  projectedReadiness: number
}

const MIN_HOURS = 0
const MAX_HOURS = 20
const DEBOUNCE_MS = 250

/**
 * "If I study N hours a week, where am I in six months?" — a slider driving
 * a deterministic projection from analytics-service's WhatIfSimulatorService
 * (see its class comment for the formula). The slider updates instantly;
 * the number it drives is debounced before it hits the query, so dragging
 * doesn't fire a request per pixel.
 */
export function WhatIfSimulatorPage() {
  const [hoursPerWeek, setHoursPerWeek] = useState(5)
  const [debouncedHours, setDebouncedHours] = useState(hoursPerWeek)

  useEffect(() => {
    const timeout = setTimeout(() => setDebouncedHours(hoursPerWeek), DEBOUNCE_MS)
    return () => clearTimeout(timeout)
  }, [hoursPerWeek])

  const { data: projection, isFetching, isPending } = useQuery({
    queryKey: ['what-if', debouncedHours],
    queryFn: async () =>
      (
        await api.get<MonthlyProjection[]>('/me/what-if', {
          params: { hoursPerWeek: debouncedHours },
        })
      ).data,
    placeholderData: (previous) => previous,
  })

  const chartData = (projection ?? []).map((p) => ({
    month: `M${p.month}`,
    readiness: Math.round(p.projectedReadiness * 1000) / 10,
  }))

  const sixMonthReadiness = projection?.at(-1)?.projectedReadiness

  return (
    <div className="mx-auto max-w-2xl">
      <header className="mb-6 border-b border-line pb-5">
        <h1 className="flex items-center gap-2 text-[19px] font-semibold">
          <Sparkles className="size-4 text-accent" />
          What-if study simulator
        </h1>
        <p className="mt-1 text-[13px] text-fg-muted">
          Drag your weekly study hours and see how your projected readiness moves over the next six
          months, based on your own pace so far.
        </p>
      </header>

      <div className="mb-6 rounded-sq border border-line bg-surface p-4">
        <div className="mb-2 flex items-center justify-between">
          <label htmlFor="hours-per-week" className="text-sm font-medium">
            Study hours per week
          </label>
          <span className="num text-lg font-semibold text-accent">{hoursPerWeek}h</span>
        </div>
        <input
          id="hours-per-week"
          type="range"
          min={MIN_HOURS}
          max={MAX_HOURS}
          step={1}
          value={hoursPerWeek}
          onChange={(e) => setHoursPerWeek(Number(e.target.value))}
          className="w-full accent-[var(--accent)]"
        />
        <div className="mt-1 flex justify-between text-xs text-fg-muted">
          <span>{MIN_HOURS}h</span>
          <span>{MAX_HOURS}h</span>
        </div>
      </div>

      <div className="mb-3 flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-sm font-medium">
          <CalendarRange className="size-4 text-available" />
          Six-month projection
        </h2>
        {isFetching && <Loader2 className="size-3.5 animate-spin text-fg-muted" />}
      </div>

      {isPending ? (
        <div className="skeleton h-64 w-full rounded-sq-lg" />
      ) : (
        <motion.div
          key={debouncedHours}
          initial={{ opacity: 0.4 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.2 }}
          className="h-64 rounded-sq border border-line bg-surface p-4"
        >
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 8, right: 12, left: -12, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--line)" vertical={false} />
              <XAxis dataKey="month" tick={{ fontSize: 12, fill: 'var(--fg-muted)' }} tickLine={false} axisLine={{ stroke: 'var(--line)' }} />
              <YAxis
                domain={[0, 100]}
                tickFormatter={(v) => `${v}%`}
                tick={{ fontSize: 12, fill: 'var(--fg-muted)' }}
                tickLine={false}
                axisLine={false}
                width={40}
              />
              <Tooltip
                formatter={(value) => [`${value}%`, 'Projected readiness']}
                contentStyle={{
                  background: 'var(--surface)',
                  border: '1px solid var(--line)',
                  borderRadius: 8,
                  fontSize: 12,
                }}
              />
              <Line
                type="monotone"
                dataKey="readiness"
                stroke="var(--accent)"
                strokeWidth={2}
                dot={{ r: 3, fill: 'var(--accent)' }}
                activeDot={{ r: 5 }}
                isAnimationActive
              />
            </LineChart>
          </ResponsiveContainer>
        </motion.div>
      )}

      {sixMonthReadiness !== undefined && (
        <p className="mt-3 text-center text-xs text-fg-muted">
          At {hoursPerWeek}h/week, projected readiness reaches{' '}
          <span className="num font-medium text-fg">{Math.round(sixMonthReadiness * 100)}%</span> by month
          six.
        </p>
      )}
    </div>
  )
}
