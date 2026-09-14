import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { ChevronDown, Loader2, ShieldCheck, ShieldX, UserCheck, UserX } from 'lucide-react'
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

type View = 'users' | 'audit'

const STATUS_TONE: Record<UserSummary['status'], string> = {
  ACTIVE: 'mastered',
  PENDING: 'available',
  SUSPENDED: 'decaying',
}

const inputClass =
  'w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong'

/**
 * Account moderation and the administrative audit trail, in one place.
 *
 * A visual-graph editor and a course-authoring UI are deliberately not here —
 * both were scoped out earlier (Phases 2 and 3) as reviewer-facing depth the
 * project traded away, and nothing about building the admin surface changes
 * that trade. What an admin actually has to do day to day — let instructors
 * in, moderate accounts, and see who did what — is what this page covers.
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
        </div>
      </header>

      {view === 'users' ? <UsersView /> : <AuditView />}
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

  const { data: users, isLoading: usersLoading } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: async () => (await api.get<UserSummary[]>('/admin/users')).data,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'pending-instructors'] })
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
                  <p className="text-sm font-medium">{p.fullName}</p>
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
