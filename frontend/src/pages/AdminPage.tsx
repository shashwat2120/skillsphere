import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import {
  ChevronDown,
  Loader2,
  Pencil,
  Plus,
  Save,
  ShieldCheck,
  ShieldX,
  Trash2,
  UserCheck,
  UserX,
  X,
} from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'

interface PendingInstructor {
  id: number
  email: string
  fullName: string
  emailVerified: boolean
  appliedAt: string
}

/** The instructor_applications-backed version — carries applicationId in addition. */
interface PendingApplication {
  applicationId: number
  userId: number
  email: string
  fullName: string
  emailVerified: boolean
  appliedAt: string
}

/** PlatformSettingsDtos.SettingView — `value` is arbitrary JSON, opaque by design. */
interface PlatformSetting {
  key: string
  value: unknown
  updatedBy: number | null
  updatedAt: string
}

interface UserSummary {
  id: number
  email: string
  fullName: string
  status: 'ACTIVE' | 'PENDING' | 'SUSPENDED'
  emailVerified: boolean
  roles: string[]
  createdAt: string
  lastLoginAt: string | null
}

interface AuditEntry {
  id: number
  adminId: number
  adminEmail: string
  action: string
  targetType: string
  targetId: number | null
  beforeState: string | null
  afterState: string | null
  ipAddress: string | null
  reason: string | null
  createdAt: string
}

const LEVEL_BANDS = ['FOUNDATIONAL', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'] as const

interface SkillSummary {
  id: number
  slug: string
  name: string
  description: string | null
  categoryId: number | null
  categoryName: string | null
  levelBand: (typeof LEVEL_BANDS)[number] | null
  estMinutes: number
  decayRate: string
  active: boolean
}

interface SkillRef {
  id: number
  name: string
}

interface SkillGraphNode {
  skill: SkillSummary
  directPrerequisites: SkillRef[]
  directUnlocks: SkillRef[]
}

type View = 'users' | 'audit' | 'skills' | 'settings'

const STATUS_TONE: Record<UserSummary['status'], string> = {
  ACTIVE: 'mastered',
  PENDING: 'available',
  SUSPENDED: 'decaying',
}

const inputClass =
  'w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong'

/**
 * Account moderation, the skill graph, and the administrative audit trail,
 * in one place.
 *
 * The skill graph tab is form-based rather than the drag-and-drop editor the
 * schema comments once imagined — a list of skills with an edit panel and a
 * prerequisite picker, in the same register as the rest of this page. That
 * matches the risk: {@link AdminSkillController}'s own docs call this "the
 * highest-leverage surface in the product," since a bad edge can stop path
 * generation for every learner at once, so simple and legible beats clever.
 * Course and item authoring live on their own instructor-facing page rather
 * than here, since they are gated to INSTRUCTOR as well as ADMIN.
 */
export function AdminPage() {
  const [view, setView] = useState<View>('users')

  return (
    <div className="mx-auto max-w-3xl">
      <header className="mb-6 border-b border-line pb-5">
        <h1 className="text-[19px] font-semibold">Admin</h1>
        <p className="mt-1 text-[13px] text-fg-muted">
          Account moderation and every administrative action, with before and after state.
        </p>
        <div className="mt-4 flex gap-1.5">
          <ToggleButton active={view === 'users'} onClick={() => setView('users')}>
            Users
          </ToggleButton>
          <ToggleButton active={view === 'audit'} onClick={() => setView('audit')}>
            Audit log
          </ToggleButton>
          <ToggleButton active={view === 'skills'} onClick={() => setView('skills')}>
            Skills
          </ToggleButton>
          <ToggleButton active={view === 'settings'} onClick={() => setView('settings')}>
            Settings
          </ToggleButton>
        </div>
      </header>

      {view === 'users' && <UsersView />}
      {view === 'audit' && <AuditView />}
      {view === 'skills' && <SkillsView />}
      {view === 'settings' && <SettingsView />}
    </div>
  )
}

function ToggleButton({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      className={
        'rounded-sq px-3 py-1.5 text-sm font-medium transition-colors ' +
        (active ? 'bg-bg-sunken text-fg' : 'text-fg-muted hover:text-fg')
      }
    >
      {children}
    </button>
  )
}

// -----------------------------------------------------------------------
// Users
// -----------------------------------------------------------------------

function UsersView() {
  const queryClient = useQueryClient()

  const { data: pending, isLoading: pendingLoading } = useQuery({
    queryKey: ['admin', 'pending-instructors'],
    queryFn: async () =>
      (await api.get<PendingInstructor[]>('/admin/users/pending-instructors')).data,
  })

  // Same queue, read from instructor_applications instead of inferred from
  // account status — carries applicationId, which pending-instructors above
  // does not. Joined in below purely to surface that id next to each row.
  const { data: applications } = useQuery({
    queryKey: ['admin', 'pending-applications'],
    queryFn: async () =>
      (await api.get<PendingApplication[]>('/admin/users/pending-applications')).data,
  })
  const applicationIdByUserId = new Map(applications?.map((a) => [a.userId, a.applicationId]))

  const { data: users, isLoading: usersLoading } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: async () => (await api.get<UserSummary[]>('/admin/users')).data,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'pending-instructors'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'pending-applications'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
  }

  const approve = useMutation({
    mutationFn: async (id: number) => api.post(`/admin/users/${id}/approve-instructor`),
    onSuccess: () => {
      toast.success('Instructor approved.')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not approve.')),
  })

  const reject = useMutation({
    mutationFn: async ({ id, reason }: { id: number; reason: string }) =>
      api.post(`/admin/users/${id}/reject-instructor`, { reason }),
    onSuccess: () => {
      toast.success('Application declined.')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not decline.')),
  })

  const suspend = useMutation({
    mutationFn: async ({ id, reason }: { id: number; reason: string }) =>
      api.post(`/admin/users/${id}/suspend`, { reason }),
    onSuccess: () => {
      toast.success('Account suspended.')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not suspend.')),
  })

  const reactivate = useMutation({
    mutationFn: async (id: number) => api.post(`/admin/users/${id}/reactivate`),
    onSuccess: () => {
      toast.success('Account reactivated.')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not reactivate.')),
  })

  return (
    <div className="space-y-8">
      {!pendingLoading && !!pending?.length && (
        <section>
          <h2 className="legend mb-2">Awaiting instructor approval</h2>
          <div className="space-y-2">
            {pending.map((p) => (
              <div
                key={p.id}
                className="flex items-center gap-3 rounded-sq border border-line bg-surface px-4 py-3"
              >
                <div className="flex-1">
                  <p className="text-sm font-medium">
                    {p.fullName}
                    {applicationIdByUserId.has(p.id) && (
                      <span className="ml-1.5 font-mono text-[11px] font-normal text-fg-subtle">
                        #{applicationIdByUserId.get(p.id)}
                      </span>
                    )}
                  </p>
                  <p className="text-xs text-fg-subtle">
                    {p.email} · applied {new Date(p.appliedAt).toLocaleDateString()}
                    {!p.emailVerified && ' · email not verified'}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={!p.emailVerified || approve.isPending}
                  onClick={() => approve.mutate(p.id)}
                  title={!p.emailVerified ? 'Cannot approve until the applicant verifies their email' : undefined}
                >
                  <UserCheck className="size-3.5" /> Approve
                </Button>
                <ReasonAction
                  label="Decline"
                  icon={<UserX className="size-3.5" />}
                  pending={reject.isPending}
                  onConfirm={(reason) => reject.mutate({ id: p.id, reason })}
                />
              </div>
            ))}
          </div>
        </section>
      )}

      <section>
        <h2 className="legend mb-2">All accounts {users ? `(${users.length})` : ''}</h2>
        {usersLoading && <div className="skeleton h-40 w-full rounded-sq-lg" />}
        <div className="divide-y divide-line rounded-sq border border-line bg-surface">
          {users?.map((u) => {
            const isAdmin = u.roles.includes('ADMIN')
            const tone = STATUS_TONE[u.status]
            return (
              <div key={u.id} className="flex items-center gap-3 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{u.fullName}</p>
                  <p className="truncate text-xs text-fg-subtle">
                    {u.email} · {u.roles.join(', ').toLowerCase()} · joined{' '}
                    {new Date(u.createdAt).toLocaleDateString()}
                    {u.lastLoginAt && ` · last seen ${new Date(u.lastLoginAt).toLocaleDateString()}`}
                  </p>
                </div>
                <span
                  className="legend rounded-full border px-2 py-0.5"
                  style={{ color: `var(--${tone})`, borderColor: `var(--${tone})`, background: `var(--${tone}-bg)` }}
                >
                  {u.status.toLowerCase()}
                </span>
                {!isAdmin && u.status !== 'SUSPENDED' && (
                  <ReasonAction
                    label="Suspend"
                    icon={<ShieldX className="size-3.5" />}
                    pending={suspend.isPending}
                    onConfirm={(reason) => suspend.mutate({ id: u.id, reason })}
                  />
                )}
                {!isAdmin && u.status === 'SUSPENDED' && (
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={reactivate.isPending}
                    onClick={() => reactivate.mutate(u.id)}
                  >
                    <ShieldCheck className="size-3.5" /> Reactivate
                  </Button>
                )}
              </div>
            )
          })}
        </div>
      </section>
    </div>
  )
}

/** A destructive-ish action that only fires once a reason is typed — mirrors the backend's ReasonRequest requirement. */
function ReasonAction({
  label,
  icon,
  pending,
  onConfirm,
}: {
  label: string
  icon: React.ReactNode
  pending: boolean
  onConfirm: (reason: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')

  if (!open) {
    return (
      <Button size="sm" variant="ghost" onClick={() => setOpen(true)}>
        {icon} {label}
      </Button>
    )
  }

  return (
    <div className="flex items-center gap-1.5">
      <input
        autoFocus
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder="Reason (required)"
        className={inputClass + ' w-44'}
      />
      <Button
        size="sm"
        variant="outline"
        disabled={!reason.trim() || pending}
        onClick={() => {
          onConfirm(reason.trim())
          setOpen(false)
          setReason('')
        }}
      >
        {pending ? <Loader2 className="size-3.5 animate-spin" /> : 'Confirm'}
      </Button>
      <Button size="sm" variant="ghost" onClick={() => setOpen(false)}>
        Cancel
      </Button>
    </div>
  )
}

// -----------------------------------------------------------------------
// Audit log
// -----------------------------------------------------------------------

const TARGET_TYPES = ['ALL', 'USER', 'SKILL']

function AuditView() {
  const [targetType, setTargetType] = useState('ALL')

  const { data: entries, isLoading } = useQuery({
    queryKey: ['admin', 'audit-log', targetType],
    queryFn: async () =>
      (
        await api.get<AuditEntry[]>('/admin/audit-log', {
          params: targetType === 'ALL' ? {} : { targetType },
        })
      ).data,
  })

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="legend">Recent actions {entries ? `(${entries.length})` : ''}</h2>
        <select
          value={targetType}
          onChange={(e) => setTargetType(e.target.value)}
          className={inputClass + ' w-32'}
        >
          {TARGET_TYPES.map((t) => (
            <option key={t} value={t}>
              {t === 'ALL' ? 'All targets' : t}
            </option>
          ))}
        </select>
      </div>

      {isLoading && <div className="skeleton h-40 w-full rounded-sq-lg" />}

      {!isLoading && !entries?.length && (
        <div className="rounded-sq border border-dashed border-line py-10 text-center text-sm text-fg-muted">
          No administrative actions recorded yet.
        </div>
      )}

      <div className="space-y-2">
        {entries?.map((e, i) => (
          <AuditRow key={e.id} entry={e} index={i} />
        ))}
      </div>
    </div>
  )
}

function AuditRow({ entry, index }: { entry: AuditEntry; index: number }) {
  const [expanded, setExpanded] = useState(false)
  const hasDetail = entry.beforeState || entry.afterState || entry.reason

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.15, delay: Math.min(index * 0.02, 0.15) }}
      className="overflow-hidden rounded-sq border border-line bg-surface"
    >
      <button
        onClick={() => hasDetail && setExpanded(!expanded)}
        className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-bg-sunken"
        disabled={!hasDetail}
      >
        <span className="legend rounded-full border border-line-strong px-2 py-0.5 text-fg-muted">
          {entry.targetType}
        </span>
        <span className="flex-1 text-sm">
          <span className="font-medium">{entry.action.replaceAll('_', ' ').toLowerCase()}</span>
          {entry.targetId != null && <span className="text-fg-subtle"> · #{entry.targetId}</span>}
        </span>
        <span className="text-xs text-fg-subtle">{entry.adminEmail}</span>
        <span className="num text-xs text-fg-subtle">
          {new Date(entry.createdAt).toLocaleString()}
        </span>
        {hasDetail && (
          <ChevronDown className={`size-3.5 text-fg-subtle transition-transform ${expanded ? 'rotate-180' : ''}`} />
        )}
      </button>

      {expanded && (
        <div className="space-y-2 border-t border-line px-4 py-3 text-xs">
          {entry.reason && (
            <p>
              <span className="legend">Reason </span>
              <span className="text-fg-muted">{entry.reason}</span>
            </p>
          )}
          {(entry.beforeState || entry.afterState) && (
            <div className="grid grid-cols-2 gap-3">
              <div>
                <p className="legend mb-1">Before</p>
                <pre className="overflow-x-auto rounded-sq bg-bg-sunken p-2 text-[11px] text-fg-muted">
                  {entry.beforeState ? JSON.stringify(JSON.parse(entry.beforeState), null, 2) : '—'}
                </pre>
              </div>
              <div>
                <p className="legend mb-1">After</p>
                <pre className="overflow-x-auto rounded-sq bg-bg-sunken p-2 text-[11px] text-fg-muted">
                  {entry.afterState ? JSON.stringify(JSON.parse(entry.afterState), null, 2) : '—'}
                </pre>
              </div>
            </div>
          )}
          {entry.ipAddress && (
            <p className="text-fg-subtle">from {entry.ipAddress}</p>
          )}
        </div>
      )}
    </motion.div>
  )
}

// -----------------------------------------------------------------------
// Skill graph
// -----------------------------------------------------------------------

function SkillsView() {
  const queryClient = useQueryClient()
  const [creating, setCreating] = useState(false)

  const { data: skills, isLoading } = useQuery({
    queryKey: ['admin', 'skills'],
    queryFn: async () => (await api.get<SkillSummary[]>('/admin/skills')).data,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'skills'] })

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="legend">Skills {skills ? `(${skills.length})` : ''}</h2>
        <Button size="sm" variant="outline" onClick={() => setCreating((c) => !c)}>
          <Plus className="size-3.5" /> New skill
        </Button>
      </div>

      {creating && (
        <CreateSkillForm
          onDone={() => {
            setCreating(false)
            invalidate()
          }}
          onCancel={() => setCreating(false)}
        />
      )}

      {isLoading && <div className="skeleton h-40 w-full rounded-sq-lg" />}

      <div className="space-y-2">
        {skills?.map((s) => (
          <SkillRow key={s.id} skill={s} allSkills={skills} onChanged={invalidate} />
        ))}
      </div>
    </div>
  )
}

function CreateSkillForm({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const [slug, setSlug] = useState('')
  const [name, setName] = useState('')
  const [levelBand, setLevelBand] = useState<string>('FOUNDATIONAL')
  const [estMinutes, setEstMinutes] = useState('30')

  const create = useMutation({
    mutationFn: async () =>
      api.post('/admin/skills', {
        slug,
        name,
        levelBand,
        estMinutes: Number(estMinutes),
        decayRate: 0,
      }),
    onSuccess: () => {
      toast.success('Skill created.')
      onDone()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not create the skill.')),
  })

  return (
    <div className="mb-3 grid grid-cols-2 gap-2 rounded-sq border border-line bg-surface p-3">
      <div>
        <label className="legend mb-1 block">Name</label>
        <input value={name} onChange={(e) => setName(e.target.value)} className={inputClass} placeholder="Generics" />
      </div>
      <div>
        <label className="legend mb-1 block">Slug</label>
        <input
          value={slug}
          onChange={(e) => setSlug(e.target.value)}
          className={inputClass}
          placeholder="generics"
        />
      </div>
      <div>
        <label className="legend mb-1 block">Level</label>
        <select value={levelBand} onChange={(e) => setLevelBand(e.target.value)} className={inputClass}>
          {LEVEL_BANDS.map((b) => (
            <option key={b} value={b}>
              {b.toLowerCase()}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="legend mb-1 block">Est. minutes</label>
        <input
          type="number"
          min={1}
          value={estMinutes}
          onChange={(e) => setEstMinutes(e.target.value)}
          className={inputClass}
        />
      </div>
      <div className="col-span-2 flex justify-end gap-1.5">
        <Button size="sm" variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={!slug.trim() || !name.trim() || create.isPending}
          onClick={() => create.mutate()}
        >
          {create.isPending && <Loader2 className="size-3.5 animate-spin" />} Create
        </Button>
      </div>
    </div>
  )
}

function SkillRow({
  skill,
  allSkills,
  onChanged,
}: {
  skill: SkillSummary
  allSkills: SkillSummary[]
  onChanged: () => void
}) {
  const [expanded, setExpanded] = useState(false)
  const [name, setName] = useState(skill.name)
  const [description, setDescription] = useState(skill.description ?? '')
  const [levelBand, setLevelBand] = useState<string>(skill.levelBand ?? 'FOUNDATIONAL')
  const [estMinutes, setEstMinutes] = useState(String(skill.estMinutes))
  const [decayRate, setDecayRate] = useState(skill.decayRate)
  const [active, setActive] = useState(skill.active)

  const { data: graph, refetch } = useQuery({
    queryKey: ['admin', 'skills', skill.id, 'graph'],
    queryFn: async () => (await api.get<SkillGraphNode>(`/admin/skills/${skill.id}/graph`)).data,
    enabled: expanded,
  })

  const save = useMutation({
    mutationFn: async () =>
      api.put(`/admin/skills/${skill.id}`, {
        name,
        description: description || null,
        levelBand,
        estMinutes: Number(estMinutes),
        decayRate: Number(decayRate),
        active,
      }),
    onSuccess: () => {
      toast.success('Skill updated.')
      onChanged()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not update the skill.')),
  })

  const retire = useMutation({
    mutationFn: async () => api.delete(`/admin/skills/${skill.id}`),
    onSuccess: () => {
      toast.success('Skill retired.')
      onChanged()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not retire the skill.')),
  })

  const [addingPrereq, setAddingPrereq] = useState(false)
  const [prereqId, setPrereqId] = useState('')
  const [strength, setStrength] = useState('1')

  const addPrereq = useMutation({
    mutationFn: async () =>
      api.post(`/admin/skills/${skill.id}/prerequisites`, {
        prerequisiteSkillId: Number(prereqId),
        strength: Number(strength),
      }),
    onSuccess: () => {
      toast.success('Prerequisite added.')
      setAddingPrereq(false)
      setPrereqId('')
      refetch()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not add the prerequisite.')),
  })

  const removePrereq = useMutation({
    mutationFn: async (prerequisiteId: number) =>
      api.delete(`/admin/skills/${skill.id}/prerequisites/${prerequisiteId}`),
    onSuccess: () => {
      toast.success('Prerequisite removed.')
      refetch()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not remove the prerequisite.')),
  })

  const candidatePrereqs = allSkills.filter(
    (s) => s.id !== skill.id && !graph?.directPrerequisites.some((p) => p.id === s.id),
  )

  return (
    <div className="overflow-hidden rounded-sq border border-line bg-surface">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-bg-sunken"
      >
        <span className="flex-1 text-sm font-medium">{skill.name}</span>
        <span className="text-xs text-fg-subtle">{skill.slug}</span>
        {!skill.active && (
          <span
            className="legend rounded-full border px-2 py-0.5"
            style={{ color: 'var(--locked)', borderColor: 'var(--locked)', background: 'var(--locked-bg)' }}
          >
            retired
          </span>
        )}
        <ChevronDown className={`size-3.5 text-fg-subtle transition-transform ${expanded ? 'rotate-180' : ''}`} />
      </button>

      {expanded && (
        <div className="space-y-4 border-t border-line px-4 py-3">
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="legend mb-1 block">Name</label>
              <input value={name} onChange={(e) => setName(e.target.value)} className={inputClass} />
            </div>
            <div>
              <label className="legend mb-1 block">Level</label>
              <select value={levelBand} onChange={(e) => setLevelBand(e.target.value)} className={inputClass}>
                {LEVEL_BANDS.map((b) => (
                  <option key={b} value={b}>
                    {b.toLowerCase()}
                  </option>
                ))}
              </select>
            </div>
            <div className="col-span-2">
              <label className="legend mb-1 block">Description</label>
              <input
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className={inputClass}
              />
            </div>
            <div>
              <label className="legend mb-1 block">Est. minutes</label>
              <input
                type="number"
                min={1}
                value={estMinutes}
                onChange={(e) => setEstMinutes(e.target.value)}
                className={inputClass}
              />
            </div>
            <div>
              <label className="legend mb-1 block">Decay rate (0–1)</label>
              <input
                type="number"
                min={0}
                max={1}
                step="0.01"
                value={decayRate}
                onChange={(e) => setDecayRate(e.target.value)}
                className={inputClass}
              />
            </div>
            <label className="col-span-2 flex items-center gap-1.5 text-xs text-fg-muted">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Active (offered to learners)
            </label>
          </div>

          <div className="flex items-center gap-1.5">
            <Button size="sm" variant="outline" disabled={save.isPending} onClick={() => save.mutate()}>
              {save.isPending && <Loader2 className="size-3.5 animate-spin" />} Save
            </Button>
            {skill.active && (
              <Button size="sm" variant="ghost" disabled={retire.isPending} onClick={() => retire.mutate()}>
                Retire
              </Button>
            )}
          </div>

          <div className="border-t border-line pt-3">
            <div className="mb-2 flex items-center justify-between">
              <p className="legend">Prerequisites</p>
              <Button size="sm" variant="ghost" onClick={() => setAddingPrereq((a) => !a)}>
                <Plus className="size-3.5" /> Add
              </Button>
            </div>

            {addingPrereq && (
              <div className="mb-2 flex items-center gap-1.5">
                <select
                  value={prereqId}
                  onChange={(e) => setPrereqId(e.target.value)}
                  className={inputClass + ' flex-1'}
                >
                  <option value="">Choose a skill…</option>
                  {candidatePrereqs.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  min={0.01}
                  max={1}
                  step="0.01"
                  value={strength}
                  onChange={(e) => setStrength(e.target.value)}
                  className={inputClass + ' w-20'}
                  title="1 = hard gate, lower = advisory"
                />
                <Button
                  size="sm"
                  variant="outline"
                  disabled={!prereqId || addPrereq.isPending}
                  onClick={() => addPrereq.mutate()}
                >
                  {addPrereq.isPending ? <Loader2 className="size-3.5 animate-spin" /> : 'Add'}
                </Button>
              </div>
            )}

            {!graph?.directPrerequisites.length && (
              <p className="text-xs text-fg-subtle">No prerequisites — a root skill.</p>
            )}
            <div className="space-y-1">
              {graph?.directPrerequisites.map((p) => (
                <div
                  key={p.id}
                  className="flex items-center justify-between rounded-sq bg-bg-sunken px-2.5 py-1.5 text-sm"
                >
                  <span>{p.name}</span>
                  <button
                    onClick={() => removePrereq.mutate(p.id)}
                    className="text-fg-subtle transition-colors hover:text-decaying"
                    aria-label={`Remove ${p.name} as a prerequisite`}
                  >
                    <Trash2 className="size-3.5" />
                  </button>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// -----------------------------------------------------------------------
// Platform settings
// -----------------------------------------------------------------------

/**
 * A key/value editor over platform_settings, per PlatformSettingsController:
 * GET /admin/settings lists every row, PUT /admin/settings/{key} upserts one.
 * A value is arbitrary JSON — this is an admin utility, not a form builder,
 * so each value is edited as raw JSON text rather than reshaping every
 * possible setting shape into typed inputs.
 */
function SettingsView() {
  const queryClient = useQueryClient()
  const [creatingKey, setCreatingKey] = useState('')

  const { data: settings, isLoading } = useQuery({
    queryKey: ['admin', 'settings'],
    queryFn: async () => (await api.get<PlatformSetting[]>('/admin/settings')).data,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'settings'] })

  const upsert = useMutation({
    mutationFn: async ({ key, value }: { key: string; value: unknown }) =>
      api.put(`/admin/settings/${encodeURIComponent(key)}`, { value }),
    onSuccess: () => {
      toast.success('Setting saved.')
      invalidate()
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not save that setting.')),
  })

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <h2 className="legend">Platform settings {settings ? `(${settings.length})` : ''}</h2>
      </div>

      {isLoading && <div className="skeleton h-40 w-full rounded-sq-lg" />}

      {!isLoading && !settings?.length && !creatingKey && (
        <div className="mb-3 rounded-sq border border-dashed border-line py-10 text-center text-sm text-fg-muted">
          No settings configured yet.
        </div>
      )}

      <div className="space-y-2">
        {settings?.map((s) => (
          <SettingRow
            key={s.key}
            setting={s}
            onSave={(value) => upsert.mutate({ key: s.key, value })}
            saving={upsert.isPending}
          />
        ))}
      </div>

      <div className="mt-4">
        {creatingKey === '' ? (
          <Button size="sm" variant="outline" onClick={() => setCreatingKey(' ')}>
            <Plus className="size-3.5" /> New setting
          </Button>
        ) : (
          <NewSettingForm
            onCreate={(key, value) => {
              upsert.mutate({ key, value })
              setCreatingKey('')
            }}
            onCancel={() => setCreatingKey('')}
          />
        )}
      </div>
    </div>
  )
}

function NewSettingForm({
  onCreate,
  onCancel,
}: {
  onCreate: (key: string, value: unknown) => void
  onCancel: () => void
}) {
  const [key, setKey] = useState('')
  const [valueText, setValueText] = useState('""')
  const [jsonError, setJsonError] = useState(false)

  return (
    <div className="grid grid-cols-[1fr_2fr_auto] items-start gap-2 rounded-sq border border-line bg-surface p-3">
      <input
        value={key}
        onChange={(e) => setKey(e.target.value)}
        placeholder="setting.key"
        className={inputClass}
      />
      <input
        value={valueText}
        onChange={(e) => {
          setValueText(e.target.value)
          setJsonError(false)
        }}
        placeholder='JSON value, e.g. "on" or {"enabled":true}'
        className={inputClass + (jsonError ? ' border-danger' : '')}
      />
      <div className="flex gap-1.5">
        <Button
          size="sm"
          variant="outline"
          disabled={!key.trim()}
          onClick={() => {
            try {
              const value = JSON.parse(valueText)
              onCreate(key.trim(), value)
            } catch {
              setJsonError(true)
            }
          }}
        >
          <Save className="size-3.5" />
        </Button>
        <Button size="sm" variant="ghost" onClick={onCancel}>
          <X className="size-3.5" />
        </Button>
      </div>
    </div>
  )
}

function SettingRow({
  setting,
  onSave,
  saving,
}: {
  setting: PlatformSetting
  onSave: (value: unknown) => void
  saving: boolean
}) {
  const [editing, setEditing] = useState(false)
  const [valueText, setValueText] = useState(JSON.stringify(setting.value, null, 2))
  const [jsonError, setJsonError] = useState(false)

  return (
    <div className="rounded-sq border border-line bg-surface px-4 py-3">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="font-mono text-sm font-medium">{setting.key}</p>
          <p className="text-xs text-fg-subtle">
            updated {new Date(setting.updatedAt).toLocaleString()}
            {setting.updatedBy != null && ` by admin #${setting.updatedBy}`}
          </p>
        </div>
        {!editing && (
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setValueText(JSON.stringify(setting.value, null, 2))
              setJsonError(false)
              setEditing(true)
            }}
          >
            <Pencil className="size-3.5" /> Edit
          </Button>
        )}
      </div>

      {!editing ? (
        <pre className="mt-2 overflow-x-auto rounded-sq bg-bg-sunken p-2 text-[11px] text-fg-muted">
          {JSON.stringify(setting.value, null, 2)}
        </pre>
      ) : (
        <div className="mt-2 space-y-2">
          <textarea
            value={valueText}
            onChange={(e) => {
              setValueText(e.target.value)
              setJsonError(false)
            }}
            rows={4}
            className={
              'w-full rounded-sq border bg-bg px-2.5 py-1.5 font-mono text-xs outline-none focus:border-line-strong ' +
              (jsonError ? 'border-danger' : 'border-line')
            }
          />
          {jsonError && <p className="text-xs text-danger">That is not valid JSON.</p>}
          <div className="flex gap-1.5">
            <Button
              size="sm"
              variant="outline"
              disabled={saving}
              onClick={() => {
                try {
                  const value = JSON.parse(valueText)
                  onSave(value)
                  setEditing(false)
                } catch {
                  setJsonError(true)
                }
              }}
            >
              {saving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />} Save
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>
              Cancel
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
