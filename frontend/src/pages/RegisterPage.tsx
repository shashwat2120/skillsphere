import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Check, GraduationCap, Loader2, Presentation } from 'lucide-react'
import { toast } from 'sonner'
import { useAuth, type RegisterInput } from '@/stores/auth'
import { errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

const MIN_PASSWORD = 12

const ROLES = [
  {
    value: 'LEARNER' as const,
    label: 'Learner',
    icon: GraduationCap,
    note: 'Start straight away',
  },
  {
    value: 'INSTRUCTOR' as const,
    label: 'Instructor',
    icon: Presentation,
    // Stated up front rather than discovered after signing up. Instructors
    // write the items that measure other people, so the role is granted by a
    // human — and finding that out only at the first failed login feels like a
    // bug rather than a policy.
    note: 'Needs admin approval',
  },
]

export function RegisterPage() {
  const register = useAuth((state) => state.register)
  const navigate = useNavigate()

  const [form, setForm] = useState<RegisterInput>({
    email: '',
    password: '',
    fullName: '',
    role: 'LEARNER',
  })
  const [submitting, setSubmitting] = useState(false)

  const passwordLongEnough = form.password.length >= MIN_PASSWORD

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    try {
      const result = await register(form)
      // Registration never returns a session: the address is unproven and an
      // instructor is still pending. So this confirms and sends them to sign in
      // rather than pretending they are logged in.
      toast.success(result.message)
      navigate('/login', { replace: true })
    } catch (error) {
      toast.error(errorMessage(error, 'Could not create the account.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-6">
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
        className="w-full max-w-md"
      >
        <h1 className="text-2xl font-semibold tracking-tight">Create your account</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Build a skill profile backed by evidence, not certificates.
        </p>

        <form onSubmit={onSubmit} className="mt-8 space-y-5">
          <div className="space-y-2">
            <Label htmlFor="fullName">Full name</Label>
            <Input
              id="fullName"
              required
              autoComplete="name"
              value={form.fullName}
              onChange={(e) => setForm({ ...form, fullName: e.target.value })}
              placeholder="Alice Johnson"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              required
              autoComplete="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              placeholder="you@example.com"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              required
              autoComplete="new-password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              placeholder="Twelve characters or more"
            />
            {/* Live feedback beats a server round trip telling them the same
                thing after they submit. Length is the only rule shown because
                length is the only rule that meaningfully helps — composition
                requirements mostly push people toward predictable
                substitutions. */}
            <p
              className={cn(
                'flex items-center gap-1.5 text-xs transition-colors',
                passwordLongEnough ? 'text-success' : 'text-muted-foreground',
              )}
            >
              {passwordLongEnough && <Check className="size-3.5" />}
              At least {MIN_PASSWORD} characters — a passphrase works well
            </p>
          </div>

          <div className="space-y-2">
            <Label>I am joining as</Label>
            <div className="grid grid-cols-2 gap-3">
              {ROLES.map(({ value, label, icon: Icon, note }) => {
                const selected = form.role === value
                return (
                  <button
                    key={value}
                    type="button"
                    onClick={() => setForm({ ...form, role: value })}
                    aria-pressed={selected}
                    className={cn(
                      'rounded-app border p-3 text-left transition-all',
                      selected
                        ? 'border-primary bg-primary-subtle'
                        : 'border-border hover:border-border-strong',
                    )}
                  >
                    <Icon
                      className={cn(
                        'size-4',
                        selected ? 'text-primary' : 'text-muted-foreground',
                      )}
                    />
                    <span className="mt-2 block text-sm font-medium">{label}</span>
                    <span className="mt-0.5 block text-xs text-muted-foreground">{note}</span>
                  </button>
                )
              })}
            </div>
          </div>

          <Button type="submit" className="w-full" disabled={submitting || !passwordLongEnough}>
            {submitting && <Loader2 className="size-4 animate-spin" />}
            {submitting ? 'Creating account…' : 'Create account'}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </p>
      </motion.div>
    </div>
  )
}
