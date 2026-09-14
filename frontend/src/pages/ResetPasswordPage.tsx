import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Check, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

const MIN_PASSWORD = 12

/**
 * Where the link in the reset email actually lands. The email itself
 * (IdentityMailListener) has pointed at `/reset-password?token=...` since
 * Phase 1 — this page catching up is what makes that link do anything.
 */
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const navigate = useNavigate()

  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const passwordLongEnough = password.length >= MIN_PASSWORD

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    try {
      const { data } = await api.post<{ message: string }>('/auth/reset-password', {
        token,
        newPassword: password,
      })
      toast.success(data.message)
      navigate('/login', { replace: true })
    } catch (error) {
      toast.error(errorMessage(error, 'Could not reset the password.'))
    } finally {
      setSubmitting(false)
    }
  }

  if (!token) {
    return (
      <div className="flex min-h-screen items-center justify-center p-6">
        <div className="w-full max-w-sm text-center">
          <h1 className="text-2xl font-semibold tracking-tight">Link not valid</h1>
          <p className="mt-2 text-sm text-fg-muted">
            This page needs a reset link from your email — it's missing its token.
          </p>
          <Link to="/forgot-password" className="mt-6 inline-block text-sm font-medium text-accent hover:underline">
            Request a new link
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-6">
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
        className="w-full max-w-sm"
      >
        <h1 className="text-2xl font-semibold tracking-tight">Choose a new password</h1>
        <p className="mt-1 text-sm text-fg-muted">
          Everything currently signed in will be signed out once you do.
        </p>

        <form onSubmit={onSubmit} className="mt-8 space-y-4">
          <div className="space-y-2">
            <Label htmlFor="password">New password</Label>
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              required
              autoFocus
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Twelve characters or more"
            />
            <p
              className={cn(
                'flex items-center gap-1.5 text-xs transition-colors',
                passwordLongEnough ? 'text-mastered' : 'text-fg-muted',
              )}
            >
              {passwordLongEnough && <Check className="size-3.5" />}
              At least {MIN_PASSWORD} characters — a passphrase works well
            </p>
          </div>

          <Button type="submit" className="w-full" disabled={submitting || !passwordLongEnough}>
            {submitting && <Loader2 className="size-4 animate-spin" />}
            {submitting ? 'Updating…' : 'Update password'}
          </Button>
        </form>
      </motion.div>
    </div>
  )
}
