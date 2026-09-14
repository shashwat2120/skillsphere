import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { Clock, FolderGit2 } from 'lucide-react'
import { api } from '@/lib/api'

interface ProjectSummary {
  id: number
  title: string
  slug: string
  description: string
  levelBand: string
  estMinutes: number
  requiresViva: boolean
  skillNames: string[]
}

/**
 * The project catalogue — real work, defended live.
 *
 * <p>One project exists in this slice on purpose: the viva is the feature
 * being proven out, not catalogue breadth. A wall of projects with nothing
 * behind most of them would be worse than one that actually works end to end.
 */
export function ProjectsPage() {
  const { data: projects, isLoading } = useQuery({
    queryKey: ['verification', 'projects'],
    queryFn: async () => (await api.get<ProjectSummary[]>('/verification/projects')).data,
  })

  return (
    <div className="mx-auto max-w-2xl">
      <header className="mb-6">
        <h1 className="text-[19px] font-semibold">Projects</h1>
        <p className="mt-1 text-[13px] text-fg-muted">
          Real work. Submit it, then defend it live — a viva grounded in what
          you actually built, not a quiz about the topic in general.
        </p>
      </header>

      {isLoading && <div className="skeleton h-32 w-full rounded-sq-lg" />}

      {!isLoading && !projects?.length && (
        <div className="rounded-sq border border-dashed border-line py-12 text-center">
          <FolderGit2 className="mx-auto size-5 text-fg-muted" />
          <p className="mt-2 text-sm font-medium">No projects published yet</p>
        </div>
      )}

      <div className="space-y-3">
        {projects?.map((project, index) => (
          <motion.div
            key={project.id}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.2, delay: Math.min(index * 0.04, 0.2) }}
          >
            <Link
              to={`/projects/${project.id}`}
              className="block rounded-sq border border-line bg-surface p-4 transition-colors hover:border-line-strong"
            >
              <div className="flex items-start justify-between gap-3">
                <h3 className="text-sm font-medium">{project.title}</h3>
                <span className="legend shrink-0 rounded-full border border-line px-2 py-0.5">
                  {project.levelBand.toLowerCase()}
                </span>
              </div>
              <p className="mt-1 text-xs text-fg-muted">{project.description}</p>
              <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-fg-subtle">
                <span className="flex items-center gap-1">
                  <Clock className="size-3" /> ~{project.estMinutes} min
                </span>
                {project.skillNames.map((name) => (
                  <span key={name} className="legend rounded-full bg-bg-sunken px-2 py-0.5">
                    {name}
                  </span>
                ))}
                {project.requiresViva && (
                  <span className="legend ml-auto text-available">viva required</span>
                )}
              </div>
            </Link>
          </motion.div>
        ))}
      </div>
    </div>
  )
}
