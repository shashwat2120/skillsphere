import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CheckCircle2, Loader2, XCircle } from 'lucide-react'
import { api, errorMessage } from '@/lib/api'

type Status = 'checking' | 'confirmed' | 'failed'

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
            <Link to="/login" className="mt-6 inline-block text-sm font-medium text-accent hover:underline">
              Back to sign in
            </Link>
          </>
        )}
      </div>
    </div>
  )
}
