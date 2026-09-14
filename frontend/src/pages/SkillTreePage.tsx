import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { Check, Lock, Sparkles } from 'lucide-react'
import { api } from '@/lib/api'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

interface LearnerSkill {
  id: number
  slug: string
  name: string
  levelBand: 'FOUNDATIONAL' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT'
  estMinutes: number
  masteryProbability: number
  mastered: boolean
  available: boolean
  blockedBy: { id: number; name: string }[]
}

const BANDS: LearnerSkill['levelBand'][] = [
  'FOUNDATIONAL',
  'INTERMEDIATE',
  'ADVANCED',
  'EXPERT',
]

/**
 * The skill graph rendered as a game-style tree.
 *
 * This is deliberately not a table. The graph already exists for pedagogical
 * reasons — nodes with prerequisite edges, unlocking as you progress — and that
 * is structurally identical to the skill tree in any role-playing game. Drawing
 * it that way costs almost nothing extra and does three jobs at once: it
 * explains the product faster than any paragraph ("everyone's tree is
 * different"), it is the most satisfying progress visual there is, and unlike
 * most gamification it is completely honest — the tree *is* the curriculum, not
 * a layer bolted on top of it.
 *
 * Columns are level bands, so the eye reads left-to-right as difficulty rising.
 */
export function SkillTreePage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['skills', 'learner'],
    queryFn: async () => (await api.get<LearnerSkill[]>('/skills')).data,
  })

  if (isLoading) return <SkillTreeSkeleton />

  if (isError) {
    return (
      <EmptyState
        title="Could not load the skill tree"
        body="The server did not respond. Check the backend is running and try again."
      />
    )
  }

  if (!data?.length) {
    return (
      <EmptyState
        title="No skills yet"
        body="An administrator has not added any skills to the graph. Once they do, your tree appears here."
      />
    )
  }

  const mastered = data.filter((s) => s.mastered).length
  const available = data.filter((s) => s.available).length

  return (
    <div>
      <header className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight">Your skill tree</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {mastered} mastered · {available} open to start · {data.length} total.
          Locked skills show exactly what is holding them shut.
        </p>
      </header>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        {BANDS.map((band) => {
          const inBand = data.filter((skill) => skill.levelBand === band)
          if (!inBand.length) return null

          return (
            <section key={band}>
              <h2 className="mb-3 text-xs font-medium uppercase tracking-wider text-muted-foreground">
                {band.toLowerCase()}
              </h2>
              <div className="space-y-3">
                {inBand.map((skill, index) => (
                  <SkillNode key={skill.id} skill={skill} index={index} />
                ))}
              </div>
            </section>
          )
        })}
      </div>
    </div>
  )
}

function SkillNode({ skill, index }: { skill: LearnerSkill; index: number }) {
  const percent = Math.round(skill.masteryProbability * 100)

  return (
    <motion.article
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      // Staggered by position, capped so a long column does not take a visible
      // age to finish appearing.
      transition={{ duration: 0.2, delay: Math.min(index * 0.03, 0.2) }}
      className={cn(
        'relative overflow-hidden rounded-app border p-4 transition-colors',
        skill.mastered && 'border-skill-mastered/40 bg-success-subtle/40',
        !skill.mastered && skill.available && 'border-skill-available/40 bg-primary-subtle/30',
        // Locked nodes are dimmed rather than hidden. Seeing what is ahead is
        // motivating; hiding it makes the tree feel empty and gives no reason to
        // continue.
        !skill.mastered && !skill.available && 'border-border bg-surface opacity-60',
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-sm font-medium leading-snug">{skill.name}</h3>
        <StateIcon skill={skill} />
      </div>

      {/* The mastery bar. Animating width would trigger layout on every frame;
          scaleX is composited on the GPU, which is why the transform origin is
          pinned left. */}
      <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-muted">
        <motion.div
          initial={{ scaleX: 0 }}
          animate={{ scaleX: skill.masteryProbability }}
          transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1], delay: 0.1 }}
          style={{ transformOrigin: 'left' }}
          className={cn(
            'h-full w-full rounded-full',
            skill.mastered ? 'bg-skill-mastered' : 'bg-skill-available',
          )}
        />
      </div>

      <div className="mt-2 flex items-center justify-between text-xs text-muted-foreground">
        <span className="tabular">{percent}%</span>
        <span>{skill.estMinutes} min</span>
      </div>

      {/* The difference between a lock that frustrates and one that instructs. */}
      {!!skill.blockedBy.length && (
        <p className="mt-3 border-t border-border pt-2 text-xs text-muted-foreground">
          Needs{' '}
          <span className="font-medium text-foreground">
            {skill.blockedBy.map((b) => b.name).join(', ')}
          </span>
        </p>
      )}
    </motion.article>
  )
}

function StateIcon({ skill }: { skill: LearnerSkill }) {
  if (skill.mastered) {
    return <Check className="size-4 shrink-0 text-skill-mastered" aria-label="Mastered" />
  }
  if (skill.available) {
    return <Sparkles className="size-4 shrink-0 text-skill-available" aria-label="Ready to start" />
  }
  return <Lock className="size-4 shrink-0 text-skill-locked" aria-label="Locked" />
}

/**
 * Skeletons rather than a spinner.
 *
 * A spinner says "wait" and nothing else. A skeleton shows the shape of what is
 * coming, so the page does not reflow when data lands — and perceived speed is
 * governed far more by layout stability than by actual load time.
 */
function SkillTreeSkeleton() {
  return (
    <div>
      <Skeleton className="h-8 w-48" />
      <Skeleton className="mt-2 h-4 w-80" />
      <div className="mt-8 grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, column) => (
          <div key={column} className="space-y-3">
            <Skeleton className="h-3 w-24" />
            {Array.from({ length: 3 }).map((_, row) => (
              <Skeleton key={row} className="h-28 w-full rounded-app" />
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}

function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <div className="rounded-app border border-dashed border-border py-16 text-center">
      <h2 className="text-sm font-medium">{title}</h2>
      <p className="mx-auto mt-1 max-w-sm text-sm text-muted-foreground">{body}</p>
    </div>
  )
}
