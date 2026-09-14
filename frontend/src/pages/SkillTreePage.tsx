import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { SkillGraph, type GraphSkill } from '@/components/SkillGraph'

interface GraphResponse {
  skills: GraphSkill[]
  edges: { from: number; to: number; hard: boolean }[]
}

export function SkillTreePage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['skills', 'graph'],
    queryFn: async () => (await api.get<GraphResponse>('/skills/graph')).data,
  })

  const skills = data?.skills ?? []
  const mastered = skills.filter((s) => s.mastered).length
  const open = skills.filter((s) => s.available).length
  const locked = skills.length - mastered - open

  return (
    <div>
      {/* Stats as a rule-separated strip rather than a row of cards. Four
          bordered boxes for four numbers is the template reflex; a single ruled
          band reads as an instrument readout and takes a third of the space. */}
      <header className="mb-6 flex flex-wrap items-end justify-between gap-4 border-b border-line pb-5">
        <div>
          <h1 className="text-[19px] font-semibold">Skill graph</h1>
          <p className="mt-1 text-[13px] text-fg-muted">
            Derived from your mastery, not a fixed course order. Solid edges gate
            progress; dashed ones only advise.
          </p>
        </div>

        <dl className="flex items-center gap-6">
          <Stat label="Mastered" value={mastered} tone="mastered" />
          <Stat label="Open" value={open} tone="available" />
          <Stat label="Locked" value={locked} tone="locked" />
        </dl>
      </header>

      {isLoading && <div className="skeleton h-[420px] w-full rounded-sq-lg" />}

      {isError && (
        <Notice title="Could not load the graph">
          The server did not respond. Check the backend is running.
        </Notice>
      )}

      {!isLoading && !isError && !skills.length && (
        <Notice title="No skills yet">
          An administrator has not added anything to the graph. Once they do,
          your nodes and their dependencies appear here.
        </Notice>
      )}

      {!!skills.length && <SkillGraph skills={skills} edges={data!.edges} />}

      {!!skills.length && (
        <div className="mt-4 flex flex-wrap items-center gap-5">
          <LegendKey tone="mastered" label="Mastered" />
          <LegendKey tone="available" label="Prerequisites met" />
          <LegendKey tone="locked" label="Blocked" />
          <span className="legend ml-auto">
            Hover a node to isolate its dependencies
          </span>
        </div>
      )}
    </div>
  )
}

function Stat({ label, value, tone }: { label: string; value: number; tone: string }) {
  return (
    <div className="text-right">
      <dd className="num text-[22px] leading-none" style={{ color: `var(--${tone})` }}>
        {String(value).padStart(2, '0')}
      </dd>
      <dt className="legend mt-1.5">{label}</dt>
    </div>
  )
}

function LegendKey({ tone, label }: { tone: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span
        className="size-2.5 rounded-[2px] border"
        style={{ borderColor: `var(--${tone})`, background: `var(--${tone}-bg)` }}
      />
      <span className="text-[11px] text-fg-muted">{label}</span>
    </span>
  )
}

function Notice({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-sq-lg border border-dashed border-line px-6 py-14 text-center">
      <p className="text-[13px] font-medium">{title}</p>
      <p className="mx-auto mt-1 max-w-sm text-[13px] text-fg-muted">{children}</p>
    </div>
  )
}
