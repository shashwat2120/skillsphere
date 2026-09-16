import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { AlertTriangle, CheckCircle2, ClipboardList, Loader2, RefreshCw, Users } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'

interface CohortSummary {
  activeToday: number
  totalLearnersTracked: number
  recentAccuracy: number
  flaggedCount: number
  highRiskCount: number
}

interface RiskFactors {
  recentAccuracy: number
  inactivityDays: number
  daysSinceFirstSeen: number
  earlyWindow: boolean
  failureCluster: boolean
  failureClusterSkillId?: number
  failureClusterSkillName?: string
  failureClusterCount?: number
}

interface RiskScoreView {
  id: number
  userId: number
  score: number
  band: 'LOW' | 'MEDIUM' | 'HIGH'
  factors: string
  earlyWindow: boolean
  intervened: boolean
  computedAt: string
}

interface ItemStats {
  itemId: number
  skillId: number | null
  respondentCount: number
  pValue: number
  discrimination: number
  lowSample: boolean
}

/**
 * Where the class is struggling, and who is about to quit — with the
 * reasons stated plainly, because a flag with no explanation is not
 * something an instructor can act on.
 */
export function AnalyticsDashboardPage() {
  const queryClient = useQueryClient()

  const { data: cohort, isLoading: cohortLoading } = useQuery({
    queryKey: ['analytics', 'cohort'],
    queryFn: async () => (await api.get<CohortSummary>('/instructor/analytics/cohort')).data,
  })

  const { data: atRisk, isLoading: atRiskLoading } = useQuery({
    queryKey: ['analytics', 'at-risk'],
    queryFn: async () => (await api.get<RiskScoreView[]>('/instructor/analytics/at-risk')).data,
  })

  const { data: itemStats, isLoading: itemStatsLoading } = useQuery({
    queryKey: ['analytics', 'item-analysis'],
    queryFn: async () => (await api.get<ItemStats[]>('/instructor/analytics/item-analysis')).data,
  })

  const recompute = useMutation({
    mutationFn: async () => api.post('/instructor/analytics/recompute'),
    onSuccess: () => {
      toast.success('Risk scores recomputed.')
      queryClient.invalidateQueries({ queryKey: ['analytics'] })
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not recompute.')),
  })

  return (
    <div className="mx-auto max-w-3xl">
      <header className="mb-6 flex items-center justify-between border-b border-line pb-5">
        <div>
          <h1 className="text-[19px] font-semibold">Class analytics</h1>
          <p className="mt-1 text-[13px] text-fg-muted">
            Every flag below comes with the reasons behind it.
          </p>
        </div>
        <Button size="sm" variant="outline" disabled={recompute.isPending} onClick={() => recompute.mutate()}>
          {recompute.isPending ? <Loader2 className="size-3.5 animate-spin" /> : <RefreshCw className="size-3.5" />}
          Recompute
        </Button>
      </header>

      {cohortLoading ? (
        <div className="skeleton mb-6 h-20 w-full rounded-sq-lg" />
      ) : (
        cohort && (
          <dl className="mb-6 flex border-b border-line pb-5">
            <Stat label="active today" value={cohort.activeToday} />
            <Stat label="learners tracked" value={cohort.totalLearnersTracked} />
            <Stat label="7-day accuracy" value={`${Math.round(cohort.recentAccuracy * 100)}%`} />
            <Stat label="high risk" value={cohort.highRiskCount} tone="decaying" />
          </dl>
        )
      )}

      <h2 className="mb-3 flex items-center gap-2 text-sm font-medium">
        <AlertTriangle className="size-4 text-decaying" /> At-risk queue
      </h2>

      {atRiskLoading && <div className="skeleton h-32 w-full rounded-sq-lg" />}

      {!atRiskLoading && !atRisk?.length && (
        <div className="rounded-sq border border-dashed border-line py-10 text-center">
          <Users className="mx-auto size-5 text-fg-muted" />
          <p className="mt-2 text-sm font-medium">Nobody flagged right now</p>
          <p className="mx-auto mt-1 max-w-sm text-xs text-fg-muted">
            Learners need at least a few recent responses before there's enough evidence to score.
          </p>
        </div>
      )}

      <div className="space-y-2.5">
        {atRisk?.map((r, i) => (
          <RiskCard key={r.id} risk={r} index={i} />
        ))}
      </div>

      <h2 className="mb-3 mt-8 flex items-center gap-2 border-t border-line pt-6 text-sm font-medium">
        <ClipboardList className="size-4 text-available" /> Item analysis
      </h2>
      <p className="mb-3 text-xs text-fg-muted">
        p-value (proportion correct) and discrimination (upper-lower 27% method) per item, worst
        discrimination first — those are the items most worth revising or re-checking the key on.
      </p>

      {itemStatsLoading && <div className="skeleton h-32 w-full rounded-sq-lg" />}

      {!itemStatsLoading && !itemStats?.length && (
        <div className="rounded-sq border border-dashed border-line py-10 text-center">
          <ClipboardList className="mx-auto size-5 text-fg-muted" />
          <p className="mt-2 text-sm font-medium">No item responses yet</p>
          <p className="mx-auto mt-1 max-w-sm text-xs text-fg-muted">
            Once learners start answering items, p-value and discrimination appear here per item.
          </p>
        </div>
      )}

      {!!itemStats?.length && <ItemAnalysisTable items={itemStats} />}
    </div>
  )
}

function ItemAnalysisTable({ items }: { items: ItemStats[] }) {
  return (
    <div className="overflow-x-auto rounded-sq border border-line">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-fg-muted">
            <th className="px-3 py-2 font-medium">Item</th>
            <th className="px-3 py-2 font-medium">Skill</th>
            <th className="px-3 py-2 font-medium">Respondents</th>
            <th className="px-3 py-2 font-medium">p-value</th>
            <th className="px-3 py-2 font-medium">Discrimination</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => {
            const tone =
              item.discrimination < 0.1 ? 'decaying' : item.discrimination < 0.2 ? 'available' : 'mastered'
            return (
              <tr key={item.itemId} className="border-b border-line last:border-0">
                <td className="num px-3 py-2">{item.itemId}</td>
                <td className="num px-3 py-2 text-fg-muted">{item.skillId ?? '—'}</td>
                <td className="num px-3 py-2 text-fg-muted">
                  {item.respondentCount}
                  {item.lowSample && <span className="legend ml-1.5 text-fg-muted">low sample</span>}
                </td>
                <td className="num px-3 py-2">{Math.round(item.pValue * 100)}%</td>
                <td className="num px-3 py-2 font-medium" style={{ color: `var(--${tone})` }}>
                  {item.discrimination >= 0 ? '+' : ''}
                  {item.discrimination.toFixed(2)}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function Stat({ label, value, tone }: { label: string; value: number | string; tone?: string }) {
  return (
    <div className="flex-1 border-l border-line pl-4 first:border-l-0 first:pl-0">
      <dd
        className="num text-xl font-semibold"
        style={tone ? { color: `var(--${tone})` } : undefined}
      >
        {value}
      </dd>
      <dt className="legend mt-1">{label}</dt>
    </div>
  )
}

function RiskCard({ risk, index }: { risk: RiskScoreView; index: number }) {
  const queryClient = useQueryClient()
  const [expanded, setExpanded] = useState(false)
  const factors: RiskFactors = (() => {
    try {
      return JSON.parse(risk.factors)
    } catch {
      return {} as RiskFactors
    }
  })()

  const acknowledge = useMutation({
    mutationFn: async () => api.post(`/instructor/analytics/at-risk/${risk.id}/acknowledge`),
    onSuccess: () => {
      toast.success('Marked as followed up.')
      queryClient.invalidateQueries({ queryKey: ['analytics', 'at-risk'] })
    },
  })

  const tone = risk.band === 'HIGH' ? 'decaying' : 'available'

  const reasons: string[] = []
  if (factors.failureCluster) {
    reasons.push(
      `${factors.failureClusterCount} recent wrong answers in a row on ${factors.failureClusterSkillName}`,
    )
  }
  if (factors.recentAccuracy !== undefined) {
    reasons.push(`${Math.round(factors.recentAccuracy * 100)}% recent accuracy`)
  }
  if (factors.inactivityDays !== undefined && factors.inactivityDays > 0) {
    reasons.push(`inactive for ${factors.inactivityDays} day${factors.inactivityDays === 1 ? '' : 's'}`)
  }
  if (factors.earlyWindow) {
    reasons.push('still in their first two weeks — this window matters most')
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.18, delay: Math.min(index * 0.03, 0.15) }}
      className="overflow-hidden rounded-sq border border-line bg-surface"
    >
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-bg-sunken"
      >
        <span
          className="legend rounded-full border px-2 py-0.5"
          style={{ color: `var(--${tone})`, borderColor: `var(--${tone})`, background: `var(--${tone}-bg)` }}
        >
          {risk.band.toLowerCase()}
        </span>
        <span className="flex-1 text-sm">Learner {risk.userId}</span>
        {risk.intervened && <CheckCircle2 className="size-3.5 text-mastered" />}
        <span className="num text-sm font-semibold">{Math.round(risk.score * 100)}%</span>
      </button>

      {expanded && (
        <div className="border-t border-line px-4 py-3">
          <p className="legend mb-1.5">Why this learner was flagged</p>
          <ul className="space-y-1 text-sm text-fg-muted">
            {reasons.map((reason, i) => (
              <li key={i}>• {reason}</li>
            ))}
            {!reasons.length && <li>No single dominant factor — a combination of smaller signals.</li>}
          </ul>

          {!risk.intervened && (
            <Button
              size="sm"
              variant="outline"
              className="mt-3"
              disabled={acknowledge.isPending}
              onClick={() => acknowledge.mutate()}
            >
              {acknowledge.isPending && <Loader2 className="size-3.5 animate-spin" />}
              Mark as followed up
            </Button>
          )}
        </div>
      )}
    </motion.div>
  )
}
