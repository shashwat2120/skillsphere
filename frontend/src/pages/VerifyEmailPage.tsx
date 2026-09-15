import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CheckCircle2, Loader2, MailCheck, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'

type Status = 'checking' | 'confirmed' | 'failed'

const inputClass =
  'w-full rounded-sq border border-line bg-bg px-2.5 py-1.5 text-sm outline-none focus:border-line-strong'

/**
 * Where the confirmation email actually lands. Registration has sent a
 * `/verify-email?token=...` link since Phase 1 (IdentityMailListener), and
 * approving a pending instructor already required a verified address — but
 * until this page existed, the link had nowhere to go: the SPA's catch-all
 * route silently swallowed the token and sent the visitor to the dashboard,
 * so nobody could actually confirm an address through the UI.
 */
export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [status, setStatus] = useState<Status>('checking')
  const [message, setMessage] = useState('')
  // StrictMode double-invokes effects in development; without this guard the
  // one-time-use token would be spent on the first call and the second would
  // always report failure.
  const attempted = useRef(false)

  useEffect(() => {
    if (!token || attempted.current) {
      if (!token) setStatus('failed')
      return
    }
    attempted.current = true

    api
      .post<{ message: string }>('/auth/verify-email', null, { params: { token } })
      .then(({ data }) => {
        setMessage(data.message)
        setStatus('confirmed')
      })
      .catch((error) => {
        setMessage(errorMessage(error, 'This verification link is not valid.'))
        setStatus('failed')
      })
  }, [token])

  return (
    <div className="flex min-h-screen items-center justify-center p-6">
      <div className="w-full max-w-sm text-center">
        {status === 'checking' && (
          <>
            <Loader2 className="mx-auto size-8 animate-spin text-fg-muted" />
            <p className="mt-4 text-sm text-fg-muted">Confirming your email address…</p>
          </>
        )}

        {status === 'confirmed' && (
          <>
            <CheckCircle2 className="mx-auto size-8 text-mastered" />
            <h1 className="mt-4 text-2xl font-semibold tracking-tight">Email confirmed</h1>
            <p className="mt-2 text-sm text-fg-muted">{message}</p>
            <Link to="/login" className="mt-6 inline-block text-sm font-medium text-accent hover:underline">
              Sign in
            </Link>
          </>
        )}

        {status === 'failed' && (
          <>
            <XCircle className="mx-auto size-8 text-decaying" />
            <h1 className="mt-4 text-2xl font-semibold tracking-tight">Link not valid</h1>
            <p className="mt-2 text-sm text-fg-muted">
              {token ? message : "This page needs a confirmation link from your email — it's missing its token."}
            </p>
            {/* A dead link with no way to get a fresh one is a dead end — this
                is the resend path for exactly that, reachable whether or not
                a session is currently active. */}
            {token && <ResendForm />}
            <Link to="/login" className="mt-6 inline-block text-sm font-medium text-accent hover:underline">
              Back to sign in
            </Link>
          </>
        )}
      </div>
    </div>
  )
}

function ResendForm() {
  const [email, setEmail] = useState('')
  const [sending, setSending] = useState(false)
  const [sent, setSent] = useState(false)

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSending(true)
    try {
      await api.post('/auth/resend-verification', { email })
      setSent(true)
    } catch (error) {
      toast.error(errorMessage(error, 'Could not send the email.'))
    } finally {
      setSending(false)
    }
  }

  if (sent) {
    return (
      <p className="mt-4 flex items-center justify-center gap-1.5 text-sm text-mastered">
        <MailCheck className="size-4" /> New link sent — check your inbox.
      </p>
    )
  }

  return (
    <form onSubmit={onSubmit} className="mt-4 flex items-center gap-1.5">
      <input
        type="email"
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="you@example.com"
        className={inputClass}
      />
      <Button type="submit" size="sm" variant="outline" disabled={sending}>
        {sending ? <Loader2 className="size-3.5 animate-spin" /> : 'Resend'}
      </Button>
    </form>
  )
}
