import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ShieldCheck } from 'lucide-react'
import { api, errorMessage } from '@/lib/api'

interface EvidenceItem {
  sourceTitle: string
  evidenceType: string
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
}

interface SnapshotView {
  passport: PassportView
  readinessScore: number | null
  roleTitle: string | null
  createdAt: string
}

/**
 * The view a link opens to — no login, no nav, nothing but the evidence.
 *
 * <p>Deliberately outside {@code AppShell}: this is the page an employer or
 * reviewer lands on cold, and it needs to make sense with zero context about
 * the rest of the product.
 */
export function PublicPassportPage() {
  const { token } = useParams<{ token: string }>()

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['public-passport', token],
    queryFn: async () => (await api.get<SnapshotView>(`/public/passports/${token}`)).data,
    retry: false,
  })

  if (isLoading) {
    return <div className="min-h-screen bg-bg" />
  }

  if (isError || !data) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-bg px-4">
        <div className="text-center">
          <p className="text-sm font-medium">Link not found</p>
          <p className="mt-1 text-sm text-fg-muted">
            {errorMessage(error, 'This share link may have expired.')}
          </p>
        </div>
      </div>
    )
  }

  const { passport, readinessScore, roleTitle, createdAt } = data

  return (
    <div className="min-h-screen bg-bg">
      <div className="mx-auto max-w-2xl px-4 py-12">
        <div className="mb-8 flex items-center justify-between">
          <span className="font-semibold tracking-tight">SkillSphere</span>
          <span className="legend">
            shared {new Date(createdAt).toLocaleDateString()}
          </span>
        </div>

        <header className="mb-8 border-b border-line pb-6">
          <h1 className="text-2xl font-semibold tracking-tight">Skill passport</h1>
          <p className="mt-1 text-sm text-fg-muted">
            Every number below traces to a specific diagnostic or a project
            defended live — not a self-reported claim.
          </p>
          {readinessScore !== null && (
            <div className="mt-4 inline-flex items-center gap-2 rounded-sq border border-line bg-surface px-3 py-2">
              <span className="num text-xl font-semibold">{Math.round(readinessScore)}%</span>
              <span className="text-sm text-fg-muted">ready for {roleTitle}</span>
            </div>
          )}
        </header>

        <dl className="mb-6 flex border-b border-line pb-5">
          <div className="flex-1 border-r border-line pr-4">
            <dd className="num text-2xl font-semibold text-mastered">{passport.verifiedSkillCount}</dd>
            <dt className="legend mt-1">verified skills</dt>
          </div>
          <div className="flex-1 pl-4">
            <dd className="num text-2xl font-semibold">{passport.totalEvidenceCount}</dd>
            <dt className="legend mt-1">pieces of evidence</dt>
          </div>
        </dl>

        <div className="space-y-2">
          {passport.skills.map((skill) => {
            const tone = skill.basis === 'verified' ? 'mastered' : skill.basis === 'diagnostic' ? 'available' : 'locked'
            return (
              <div key={skill.skillId} className="rounded-sq border border-line bg-surface p-4">
                <div className="flex items-center gap-3">
                  {skill.basis === 'verified' && <ShieldCheck className="size-4 shrink-0 text-mastered" />}
                  <p className="flex-1 text-sm font-medium">{skill.skillName}</p>
                  <span className="num text-lg font-semibold" style={{ color: `var(--${tone})` }}>
                    {Math.round(skill.displayScore * 100)}%
                  </span>
                </div>
                {skill.evidence.length > 0 && (
                  <div className="mt-2.5 space-y-1.5 border-t border-line pt-2.5">
                    {skill.evidence.map((e, i) => (
                      <div key={i} className="flex items-center justify-between text-xs text-fg-muted">
                        <span>{e.sourceTitle} — {e.evidenceType.toLowerCase()}</span>
                        <span className="num">{Math.round(e.score * 100)}%</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
