import { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Loader2, MailCheck } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * The other half of a flow the backend has had since Phase 1 but the
 * frontend never surfaced: {@code POST /api/auth/forgot-password} always
 * existed, there was simply no page that called it, so a locked-out learner
 * had no way to reach it except a raw curl command.
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    try {
      await api.post('/auth/forgot-password', { email })
      // The backend deliberately returns the same response whether or not
      // the address exists, so there is nothing more specific to show here
      // without undoing that protection.
      setSent(true)
    } catch (error) {
      toast.error(errorMessage(error, 'Could not send the reset link.'))
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
        className="w-full max-w-sm"
      >
        {sent ? (
          <div className="text-center">
            <MailCheck className="mx-auto size-8 text-mastered" />
            <h1 className="mt-4 text-2xl font-semibold tracking-tight">Check your email</h1>
            <p className="mt-2 text-sm text-fg-muted">
              If an account exists for <span className="text-fg">{email}</span>, a reset link is
              on its way. It expires in 30 minutes and works once.
            </p>
            <Link to="/login" className="mt-6 inline-block text-sm font-medium text-accent hover:underline">
              Back to sign in
            </Link>
          </div>
        ) : (
          <>
            <h1 className="text-2xl font-semibold tracking-tight">Reset your password</h1>
            <p className="mt-1 text-sm text-fg-muted">
              Enter the email on your account and we'll send a link to choose a new password.
            </p>

            <form onSubmit={onSubmit} className="mt-8 space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  autoFocus
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                />
              </div>

              <Button type="submit" className="w-full" disabled={submitting}>
                {submitting && <Loader2 className="size-4 animate-spin" />}
                {submitting ? 'Sending…' : 'Send reset link'}
              </Button>
            </form>

            <p className="mt-6 text-center text-sm text-fg-muted">
              <Link to="/login" className="font-medium text-accent hover:underline">
                Back to sign in
              </Link>
            </p>
          </>
        )}
      </motion.div>
    </div>
  )
}
