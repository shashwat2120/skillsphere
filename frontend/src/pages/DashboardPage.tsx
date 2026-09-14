import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { ArrowRight, GitBranch, Sparkles, Target } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { Skeleton } from '@/components/ui/skeleton'
import { Button } from '@/components/ui/button'

interface SkillRef {
  id: number
  name: string
}

export function DashboardPage() {
  const user = useAuth((state) => state.user)

  const { data: ready, isLoading } = useQuery({
    queryKey: ['skills', 'ready'],
    queryFn: async () => (await api.get<SkillRef[]>('/skills/ready')).data,
  })

  const firstName = user?.fullName.split(' ')[0] ?? 'there'

  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">
          Welcome back, {firstName}
        </h1>
        <p className="mt-1 text-sm text-fg-muted">
          {/* The product claim, stated plainly on the first screen. */}
          Your route is worked out from what you have proven, not from a fixed
          course order.
        </p>
      </header>

      {/* Email verification is surfaced, not silently ignored — an unverified
          account will hit a wall later, and finding out then is worse. */}
      {user && !user.emailVerified && (
        <div className="rounded-sq border border-decaying/30 bg-decaying-bg px-4 py-3 text-sm">
          Confirm your email address to unlock verified evidence on your passport.
        </div>
      )}

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-sm font-medium">
            <Sparkles className="size-4 text-accent" />
            Ready to start
          </h2>
          <Button asChild variant="ghost" size="sm">
            <Link to="/skills">
              Full tree <ArrowRight className="size-3.5" />
            </Link>
          </Button>
        </div>

        {isLoading ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-20 rounded-sq" />
            ))}
          </div>
        ) : ready?.length ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {ready.map((skill, index) => (
              <motion.div
                key={skill.id}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2, delay: Math.min(index * 0.04, 0.24) }}
              >
                <Link
                  to={`/diagnostics/${skill.id}`}
                  className="block rounded-sq border border-line bg-surface p-4 transition-colors hover:border-accent/40"
                >
                  <Target className="size-4 text-accent" />
                  <h3 className="mt-2 text-sm font-medium">{skill.name}</h3>
                  <p className="mt-0.5 text-xs text-fg-muted">
                    Prerequisites met — take the diagnostic
                  </p>
                </Link>
              </motion.div>
            ))}
          </div>
        ) : (
          <div className="rounded-sq border border-dashed border-line py-12 text-center">
            <GitBranch className="mx-auto size-5 text-fg-muted" />
            <p className="mt-2 text-sm font-medium">Nothing open yet</p>
            <p className="mx-auto mt-1 max-w-sm text-sm text-fg-muted">
              Once an administrator adds skills to the graph, everything you can
              start appears here — worked out from your own mastery.
            </p>
          </div>
        )}
      </section>
    </div>
  )
}
