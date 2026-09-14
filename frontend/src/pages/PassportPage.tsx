import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import { Award, ChevronDown, Copy, Loader2, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'

// ---------------------------------------------------------------------------
// Wire types — mirror PassportService's response records.
// ---------------------------------------------------------------------------

interface EvidenceItem {
  id: number
  skillId: number
  evidenceType: string
  sourceType: string
  sourceId: number
  sourceTitle: string
  weight: number
  score: number
  verifiedAt: string
}

interface SkillEntry {
  skillId: number
  skillName: string
  diagnosticMastery: number | null
  diagnosticResponses: number | null
  evidence: EvidenceItem[]
  displayScore: number
  basis: 'verified' | 'diagnostic' | 'none'
}

interface PassportView {
  skills: SkillEntry[]
  verifiedSkillCount: number
  totalEvidenceCount: number
  generatedAt: string
}

interface RoleSummary {
  id: number
  title: string
}

interface SnapshotView {
  snapshotId: number
  shareToken: string
  readinessScore: number | null
  roleTitle: string | null
}

/**
 * Every percentage on this page is clickable and backed by evidence — that
 * is the entire pitch of the product, so this page is where the pitch either
 * holds up or doesn't. Nothing here is a generated sentence: the drill-down
 * shows the actual project or viva that produced each number.
 */
export function PassportPage() {
  const { data: passport, isLoading } = useQuery({
    queryKey: ['passport'],
    queryFn: async () => (await api.get<PassportView>('/passport')).data,
  })

  const [expandedSkill, setExpandedSkill] = useState<number | null>(null)
  const [shareOpen, setShareOpen] = useState(false)

  return (
    <div className="mx-auto max-w-2xl">
      <header className="mb-6 flex items-start justify-between gap-4 border-b border-line pb-5">
        <div>
          <h1 className="text-[19px] font-semibold">Skill passport</h1>
          <p className="mt-1 text-[13px] text-fg-muted">
            A percentage here means something specific — click it to see what.
          </p>
        </div>
        <Button size="sm" variant="outline" onClick={() => setShareOpen(true)}>
          Share
        </Button>
      </header>

      {isLoading && <div className="skeleton h-[300px] w-full rounded-sq-lg" />}

      {!isLoading && passport && (
        <>
          <dl className="mb-6 flex border-b border-line pb-5">
            <div className="flex-1 border-r border-line pr-4">
              <dd className="num text-2xl font-semibold text-mastered">
                {passport.verifiedSkillCount}
              </dd>
              <dt className="legend mt-1">verified skills</dt>
            </div>
            <div className="flex-1 pl-4">
              <dd className="num text-2xl font-semibold">{passport.totalEvidenceCount}</dd>
              <dt className="legend mt-1">pieces of evidence</dt>
            </div>
          </dl>

          {!passport.skills.length ? (
            <div className="rounded-sq border border-dashed border-line py-12 text-center">
              <Award className="mx-auto size-5 text-fg-muted" />
              <p className="mt-2 text-sm font-medium">Nothing here yet</p>
              <p className="mx-auto mt-1 max-w-sm text-sm text-fg-muted">
                Attempt a diagnostic or defend a project to start building your passport.
              </p>
            </div>
          ) : (
            <div className="space-y-2">
              {passport.skills.map((skill) => (
                <SkillRow
                  key={skill.skillId}
                  skill={skill}
                  expanded={expandedSkill === skill.skillId}
                  onToggle={() =>
                    setExpandedSkill(expandedSkill === skill.skillId ? null : skill.skillId)
                  }
                />
              ))}
            </div>
          )}
        </>
      )}

      {shareOpen && <ShareDialog onClose={() => setShareOpen(false)} />}
    </div>
  )
}

function SkillRow({
  skill,
  expanded,
  onToggle,
}: {
  skill: SkillEntry
  expanded: boolean
  onToggle: () => void
}) {
  const tone = skill.basis === 'verified' ? 'mastered' : skill.basis === 'diagnostic' ? 'available' : 'locked'

  return (
    <div className="overflow-hidden rounded-sq border border-line bg-surface">
      <button
        onClick={onToggle}
        className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-bg-sunken"
      >
        {skill.basis === 'verified' && <ShieldCheck className="size-4 shrink-0 text-mastered" />}
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium">{skill.skillName}</p>
          <p className="legend mt-0.5">
            {skill.basis === 'verified'
              ? 'verified'
              : skill.basis === 'diagnostic'
                ? `${skill.diagnosticResponses ?? 0} diagnostic responses`
                : 'no evidence'}
          </p>
        </div>
        <span className="num text-lg font-semibold" style={{ color: `var(--${tone})` }}>
          {Math.round(skill.displayScore * 100)}%
        </span>
        <ChevronDown className={`size-4 shrink-0 text-fg-subtle transition-transform ${expanded ? 'rotate-180' : ''}`} />
      </button>

      <AnimatePresence>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.15 }}
            className="overflow-hidden border-t border-line"
          >
            <div className="px-4 py-3.5">
              {skill.diagnosticMastery != null && (
                <div className="mb-3 flex items-center justify-between text-xs">
                  <span className="text-fg-muted">Diagnostic mastery (Bayesian Knowledge Tracing)</span>
                  <span className="num font-medium">
                    {Math.round(skill.diagnosticMastery * 100)}% · {skill.diagnosticResponses} responses
                  </span>
                </div>
              )}

              {skill.evidence.length > 0 ? (
                <div className="space-y-2">
                  {skill.evidence.map((e) => (
                    <div
                      key={e.id}
                      className="flex items-center justify-between rounded-sq bg-mastered-bg px-3 py-2 text-xs"
                    >
                      <div>
                        <p className="font-medium text-fg">{e.sourceTitle}</p>
                        <p className="legend mt-0.5">
                          {e.evidenceType.toLowerCase()} · {new Date(e.verifiedAt).toLocaleDateString()}
                        </p>
                      </div>
                      <span className="num font-medium text-mastered">{Math.round(e.score * 100)}%</span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-fg-subtle">
                  No project or viva evidence yet — this number comes only from the adaptive diagnostic.
                </p>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function ShareDialog({ onClose }: { onClose: () => void }) {
  const [roleId, setRoleId] = useState<string>('')
  const [result, setResult] = useState<SnapshotView | null>(null)

  const { data: roles } = useQuery({
    queryKey: ['career', 'catalogue'],
    queryFn: async () => (await api.get<RoleSummary[]>('/careers')).data,
  })

  const share = useMutation({
    mutationFn: async () =>
      (
        await api.post<SnapshotView>(
          `/passport/share${roleId ? `?careerRoleId=${roleId}` : ''}`,
        )
      ).data,
    onSuccess: setResult,
    onError: (error) => toast.error(errorMessage(error, 'Could not create a share link.')),
  })

  const publicUrl = result ? `${window.location.origin}/p/${result.shareToken}` : ''

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 px-4">
      <motion.div
        initial={{ opacity: 0, scale: 0.97 }}
        animate={{ opacity: 1, scale: 1 }}
        className="w-full max-w-sm rounded-sq-lg border border-line bg-surface p-5"
      >
        {!result ? (
          <>
            <h3 className="text-sm font-semibold">Share your passport</h3>
            <p className="mt-1 text-xs text-fg-muted">
              Frozen at the moment you share it — an employer sees exactly this, not a
              number that could change later.
            </p>

            <label className="mt-4 block">
              <span className="legend mb-1 block">Against a role (optional)</span>
              <select
                value={roleId}
                onChange={(e) => setRoleId(e.target.value)}
                className="w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong"
              >
                <option value="">No specific role</option>
                {roles?.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.title}
                  </option>
                ))}
              </select>
            </label>

            <div className="mt-5 flex justify-end gap-2">
              <Button variant="ghost" size="sm" onClick={onClose}>
                Cancel
              </Button>
              <Button size="sm" disabled={share.isPending} onClick={() => share.mutate()}>
                {share.isPending && <Loader2 className="size-3.5 animate-spin" />}
                Create link
              </Button>
            </div>
          </>
        ) : (
          <>
            <h3 className="text-sm font-semibold">Link ready</h3>
            {result.readinessScore != null && (
              <p className="mt-1 text-xs text-fg-muted">
                {Math.round(result.readinessScore)}% ready for {result.roleTitle}
              </p>
            )}
            <div className="mt-3 flex items-center gap-2 rounded-sq border border-line bg-bg-sunken px-3 py-2">
              <code className="flex-1 truncate text-xs">{publicUrl}</code>
              <button
                onClick={() => {
                  navigator.clipboard.writeText(publicUrl)
                  toast.success('Copied.')
                }}
                className="shrink-0 text-fg-muted hover:text-fg"
              >
                <Copy className="size-3.5" />
              </button>
            </div>
            <Button className="mt-4 w-full" size="sm" variant="outline" onClick={onClose}>
              Done
            </Button>
          </>
        )}
      </motion.div>
    </div>
  )
}
