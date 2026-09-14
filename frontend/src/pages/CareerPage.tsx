import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { ChevronRight, Sparkles, Target } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'

// ---------------------------------------------------------------------------
// Wire types — mirror CareerService's response records exactly.
// ---------------------------------------------------------------------------

interface RoleSummary {
  id: number
  title: string
  slug: string
  description: string | null
  category: string | null
  seniority: string
  icon: string | null
}

interface GapView {
  skillName: string
  currentMastery: number
  requiredMastery: number
  weight: number
  core: boolean
  met: boolean
}

interface GapAnalysisView {
  roleId: number
  roleTitle: string
  readinessScore: number
  readyForRole: boolean
  gaps: GapView[]
}

interface PathStepView {
  id: number
  skillId: number
  skillName: string
  activityType: string
  status: string
  estMinutes: number
  rationale: string
}

interface PathView {
  id: number
  version: number
  status: string
  generationReason: string
  totalSteps: number
  completedSteps: number
  steps: PathStepView[]
}

interface Rationale {
  prerequisites: { skillId: number; name: string; status: string; mastery?: number }[]
  gap: { current: number; required: number }
  roleWeight: number
  core: boolean
  selectedBecause: string
}

/**
 * Career goals, the gap to one, and the generated path toward it.
 *
 * <p>Two views live on one page rather than two routes: whichever the
 * learner needs is the whole screen, and switching between "browsing roles"
 * and "working my path" should feel like one continuous decision, not a
 * navigation event.
 */
export function CareerPage() {
  const queryClient = useQueryClient()
  const [browsingRoleId, setBrowsingRoleId] = useState<number | null>(null)

  const { data: activePath, isLoading: pathLoading } = useQuery({
    queryKey: ['career', 'path'],
    queryFn: async () => {
      try {
        return (await api.get<PathView>('/careers/path')).data
      } catch (error) {
        if ((error as { response?: { status?: number } })?.response?.status === 404) return null
        throw error
      }
    },
  })

  const { data: catalogue, isLoading: catalogueLoading } = useQuery({
    queryKey: ['career', 'catalogue'],
    queryFn: async () => (await api.get<RoleSummary[]>('/careers')).data,
  })

  const showCatalogue = browsingRoleId !== null || !activePath

  return (
    <div className="mx-auto max-w-3xl">
      <header className="mb-6">
        <h1 className="text-[19px] font-semibold">Career path</h1>
        <p className="mt-1 text-[13px] text-fg-muted">
          Pick where you're headed. The route there — and why each step is on
          it — is generated from what you've actually proven, not a fixed
          curriculum.
        </p>
      </header>

      {(pathLoading || catalogueLoading) && (
        <div className="skeleton h-[300px] w-full rounded-sq-lg" />
      )}

      {!pathLoading && !catalogueLoading && (
        <AnimatePresence mode="wait">
          {showCatalogue ? (
            <RoleCatalogue
              key="catalogue"
              roles={catalogue ?? []}
              onBack={activePath ? () => setBrowsingRoleId(null) : undefined}
              onGoalSet={() => {
                setBrowsingRoleId(null)
                queryClient.invalidateQueries({ queryKey: ['career'] })
              }}
            />
          ) : (
            <PathTimeline
              key="path"
              path={activePath!}
              onChangeGoal={() => setBrowsingRoleId(-1)}
            />
          )}
        </AnimatePresence>
      )}
    </div>
  )
}

function RoleCatalogue({
  roles,
  onBack,
  onGoalSet,
}: {
  roles: RoleSummary[]
  onBack?: () => void
  onGoalSet: () => void
}) {
  const [expandedId, setExpandedId] = useState<number | null>(null)

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
      {onBack && (
        <button
          onClick={onBack}
          className="mb-4 text-xs text-fg-muted transition-colors hover:text-fg"
        >
          ← Back to your path
        </button>
      )}

      {!roles.length && (
        <div className="rounded-sq border border-dashed border-line py-12 text-center">
          <Target className="mx-auto size-5 text-fg-muted" />
          <p className="mt-2 text-sm font-medium">No roles published yet</p>
        </div>
      )}

      <div className="space-y-3">
        {roles.map((role) => (
          <RoleCard
            key={role.id}
            role={role}
            expanded={expandedId === role.id}
            onToggle={() => setExpandedId(expandedId === role.id ? null : role.id)}
            onGoalSet={onGoalSet}
          />
        ))}
      </div>
    </motion.div>
  )
}

function RoleCard({
  role,
  expanded,
  onToggle,
  onGoalSet,
}: {
  role: RoleSummary
  expanded: boolean
  onToggle: () => void
  onGoalSet: () => void
}) {
  const { data: gap, isFetching } = useQuery({
    queryKey: ['career', 'gap', role.id],
    queryFn: async () => (await api.get<GapAnalysisView>(`/careers/${role.id}/gap`)).data,
    enabled: expanded,
  })

  const setGoal = useMutation({
    mutationFn: async () => (await api.post<PathView>(`/careers/${role.id}/goal`)).data,
    onSuccess: () => {
      toast.success(`Path generated toward ${role.title}.`)
      onGoalSet()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not set that goal.')),
  })

  return (
    <div className="overflow-hidden rounded-sq border border-line bg-surface">
      <button
        onClick={onToggle}
        className="flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors hover:bg-bg-sunken"
      >
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-medium">{role.title}</h3>
            <span className="legend rounded-full border border-line px-2 py-0.5">
              {role.seniority.toLowerCase()}
            </span>
          </div>
          {role.description && (
            <p className="mt-0.5 text-xs text-fg-muted">{role.description}</p>
          )}
        </div>
        <ChevronRight
          className={`size-4 shrink-0 text-fg-subtle transition-transform ${expanded ? 'rotate-90' : ''}`}
        />
      </button>

      {expanded && (
        <div className="border-t border-line px-4 py-4">
          {isFetching && <div className="skeleton h-24 w-full rounded-sq" />}

          {gap && (
            <>
              <div className="mb-4 flex items-center justify-between">
                <div>
                  <span className="num text-xl font-semibold">
                    {Math.round(gap.readinessScore)}%
                  </span>
                  <span className="ml-2 text-xs text-fg-muted">readiness</span>
                </div>
                {!gap.readyForRole && (
                  <span className="legend rounded-full bg-decaying-bg px-2 py-0.5 text-decaying">
                    core skills unmet
                  </span>
                )}
                {gap.readyForRole && (
                  <span className="legend rounded-full bg-mastered-bg px-2 py-0.5 text-mastered">
                    core skills met
                  </span>
                )}
              </div>

              <div className="space-y-2.5">
                {gap.gaps.map((g) => (
                  <GapBar key={g.skillName} gap={g} />
                ))}
              </div>

              <Button
                className="mt-5 w-full"
                size="sm"
                disabled={setGoal.isPending}
                onClick={() => setGoal.mutate()}
              >
                <Sparkles className="size-3.5" />
                Set as goal &amp; generate path
              </Button>
            </>
          )}
        </div>
      )}
    </div>
  )
}

function GapBar({ gap }: { gap: GapView }) {
  const pct = Math.min(100, Math.round((gap.currentMastery / gap.requiredMastery) * 100))
  const requiredPct = Math.min(100, Math.round(gap.requiredMastery * 100))

  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-xs">
        <span className="flex items-center gap-1.5">
          {gap.skillName}
          {gap.core && <span className="legend text-fg-subtle">core</span>}
        </span>
        <span className="num text-fg-subtle">
          {Math.round(gap.currentMastery * 100)}% / {Math.round(gap.requiredMastery * 100)}%
        </span>
      </div>
      <div className="relative h-1.5 w-full overflow-hidden rounded-full bg-bg-sunken">
        <div
          className="h-full rounded-full"
          style={{
            width: `${pct}%`,
            background: gap.met ? 'var(--mastered)' : 'var(--available)',
          }}
        />
        {/* Marker at the required threshold — the bar shows progress toward
            it, the marker shows where "enough" actually is. */}
        <div
          className="absolute top-0 h-full w-px bg-fg-subtle/40"
          style={{ left: `${requiredPct}%` }}
        />
      </div>
    </div>
  )
}

function PathTimeline({ path, onChangeGoal }: { path: PathView; onChangeGoal: () => void }) {
  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
      <div className="mb-5 flex items-center justify-between border-b border-line pb-4">
        <div>
          <p className="legend">Active path · v{path.version}</p>
          <p className="mt-1 text-sm">
            <span className="num font-medium">{path.completedSteps}</span>
            <span className="text-fg-muted"> of </span>
            <span className="num font-medium">{path.totalSteps}</span>
            <span className="text-fg-muted"> steps</span>
          </p>
        </div>
        <Button variant="ghost" size="sm" onClick={onChangeGoal}>
          Change goal
        </Button>
      </div>

      {!path.steps.length ? (
        <div className="rounded-sq border border-mastered/30 bg-mastered-bg px-4 py-6 text-center">
          <p className="text-sm font-medium text-fg">Every requirement is already met.</p>
          <p className="mt-1 text-xs text-fg-muted">Nothing left to generate a step for.</p>
        </div>
      ) : (
        <ol className="space-y-2">
          {path.steps.map((step, index) => (
            <PathStepCard key={step.id} step={step} index={index} />
          ))}
        </ol>
      )}
    </motion.div>
  )
}

function PathStepCard({ step, index }: { step: PathStepView; index: number }) {
  const [showWhy, setShowWhy] = useState(false)
  const rationale: Rationale | null = (() => {
    try {
      return JSON.parse(step.rationale)
    } catch {
      return null
    }
  })()

  const locked = step.status === 'LOCKED'
  const completed = step.status === 'COMPLETED'

  return (
    <li className="overflow-hidden rounded-sq border border-line bg-surface">
      <div className={`flex items-center gap-3 px-4 py-3 ${locked ? 'opacity-50' : ''}`}>
        <span className="num flex size-6 shrink-0 items-center justify-center rounded-full border border-line-strong text-[11px]">
          {index + 1}
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium">{step.skillName}</p>
          <p className="legend mt-0.5">
            {step.activityType.toLowerCase()} · ~{step.estMinutes} min
          </p>
        </div>
        {completed && (
          <span className="legend rounded-full bg-mastered-bg px-2 py-0.5 text-mastered">done</span>
        )}
        {!locked && !completed && (
          <Link
            to={`/diagnostics/${step.skillId}`}
            className="legend rounded-full border border-line px-2.5 py-1 transition-colors hover:border-line-strong hover:text-fg"
          >
            Start
          </Link>
        )}
        <button
          onClick={() => setShowWhy(!showWhy)}
          className="legend shrink-0 underline decoration-dotted underline-offset-2"
        >
          why?
        </button>
      </div>

      {showWhy && rationale && (
        <div className="border-t border-line bg-bg-sunken px-4 py-3 text-xs">
          <p className="text-fg-muted">{rationale.selectedBecause}</p>
          {rationale.prerequisites.length > 0 && (
            <div className="mt-2">
              <p className="legend mb-1">Prerequisites</p>
              <ul className="space-y-0.5">
                {rationale.prerequisites.map((p) => (
                  <li key={p.skillId} className="flex items-center justify-between">
                    <span>{p.name}</span>
                    <span
                      className="legend"
                      style={{
                        color:
                          p.status === 'satisfied'
                            ? 'var(--mastered)'
                            : p.status === 'earlier_in_path'
                              ? 'var(--available)'
                              : 'var(--fg-subtle)',
                      }}
                    >
                      {p.status.replaceAll('_', ' ')}
                      {p.mastery !== undefined ? ` · ${Math.round(p.mastery * 100)}%` : ''}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </li>
  )
}
