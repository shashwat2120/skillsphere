import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { Fingerprint, KeyRound, Loader2, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { useAuth, type MfaChallenge, type AuthSession } from '@/stores/auth'
import { api, errorMessage } from '@/lib/api'
import {
  getPasskeyAssertion,
  isPasskeyCancellation,
  isWebAuthnSupported,
  type PasskeyAuthenticationOptions,
} from '@/lib/webauthn'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export function LoginPage() {
  const login = useAuth((state) => state.login)
  const completeMfaLogin = useAuth((state) => state.completeMfaLogin)
  const setSession = useAuth((state) => state.setSession)
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const passkeysSupported = isWebAuthnSupported()
  const [passkeyPending, setPasskeyPending] = useState(false)

  // Set once POST /auth/login reports mfaRequired: true. Its presence swaps
  // the form below for the second-factor step, on the same page rather than
  // a separate route — losing the email/password state on a redirect here
  // would be a worse experience than just hiding one form and showing another.
  const [mfaChallenge, setMfaChallenge] = useState<MfaChallenge | null>(null)
  const [mfaCode, setMfaCode] = useState('')
  const [useRecoveryCode, setUseRecoveryCode] = useState(false)
  const [mfaSubmitting, setMfaSubmitting] = useState(false)

  function goToDestination() {
    // Return the user to wherever they were headed before the redirect.
    const from = (location.state as { from?: { pathname: string } })?.from?.pathname ?? '/'
    navigate(from, { replace: true })
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    try {
      const outcome = await login(email, password)
      if (outcome.mfaRequired) {
        setMfaChallenge(outcome)
        return
      }
      goToDestination()
    } catch (error) {
      // The server deliberately returns the same message for a wrong password,
      // an unknown address and a locked account — anything else would let an
      // attacker enumerate which accounts exist. The UI simply relays it rather
      // than trying to be more helpful and undoing that.
      toast.error(errorMessage(error, 'Could not sign in.'))
    } finally {
      setSubmitting(false)
    }
  }

  async function onSubmitMfa(event: React.FormEvent) {
    event.preventDefault()
    if (!mfaChallenge) return
    setMfaSubmitting(true)
    try {
      await completeMfaLogin(
        mfaChallenge.mfaToken,
        useRecoveryCode ? undefined : mfaCode,
        useRecoveryCode ? mfaCode : undefined,
      )
      goToDestination()
    } catch (error) {
      toast.error(errorMessage(error, 'That code was not accepted.'))
    } finally {
      setMfaSubmitting(false)
    }
  }

  async function onPasskeySignIn() {
    if (!email.trim()) {
      toast.error('Enter your email first, then sign in with a passkey.')
      return
    }
    setPasskeyPending(true)
    try {
      const { data: options } = await api.post<PasskeyAuthenticationOptions>('/auth/passkey/authenticate/options', {
        email,
      })
      const assertion = await getPasskeyAssertion(options)
      const { data: session } = await api.post<AuthSession>('/auth/passkey/authenticate', {
        email,
        credentialId: assertion.credentialId,
        assertionResponseJson: assertion.assertionResponseJson,
      })
      setSession(session)
      goToDestination()
    } catch (error) {
      if (isPasskeyCancellation(error)) {
        toast.info('Cancelled.')
        return
      }
      toast.error(errorMessage(error, 'Could not sign in with that passkey.'))
    } finally {
      setPasskeyPending(false)
    }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Left: the pitch. Hidden below lg — on a phone it would push the form
          below the fold, and someone who came here to sign in wants the form. */}
      <aside className="relative hidden overflow-hidden bg-bg-sunken lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div
          aria-hidden
          className="pointer-events-none absolute -right-24 -top-24 size-96 rounded-full bg-accent/10 blur-3xl"
        />
        <span className="relative text-lg font-semibold tracking-tight">SkillSphere</span>

        <div className="relative max-w-md">
          <h1 className="text-3xl font-semibold leading-tight tracking-tight">
            Every skill claim comes with proof that can be checked.
          </h1>
          <p className="mt-4 text-sm leading-relaxed text-fg-muted">
            Learning platforms tell you someone finished a course. We show you
            what they can actually do, and the evidence behind it.
          </p>
        </div>

        <p className="relative flex items-center gap-2 text-xs text-fg-muted">
          <ShieldCheck className="size-4" />
          Argon2id hashing · rotating tokens · instant revocation
        </p>
      </aside>

      <main className="flex items-center justify-center p-6">
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
          className="w-full max-w-sm"
        >
          {!mfaChallenge ? (
            <>
              <h2 className="text-2xl font-semibold tracking-tight">Welcome back</h2>
              <p className="mt-1 text-sm text-fg-muted">
                Sign in to continue building your skill profile.
              </p>

              <form onSubmit={onSubmit} className="mt-8 space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="email">Email</Label>
                  <Input
                    id="email"
                    type="email"
                    autoComplete="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="you@example.com"
                  />
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label htmlFor="password">Password</Label>
                    <Link to="/forgot-password" className="text-xs font-medium text-accent hover:underline">
                      Forgot password?
                    </Link>
                  </div>
                  <Input
                    id="password"
                    type="password"
                    // Tells a password manager this is a sign-in rather than a
                    // sign-up, so it offers the saved credential instead of
                    // proposing a new one.
                    autoComplete="current-password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••••••"
                  />
                </div>

                <Button type="submit" className="w-full" disabled={submitting}>
                  {submitting && <Loader2 className="size-4 animate-spin" />}
                  {submitting ? 'Signing in…' : 'Sign in'}
                </Button>
              </form>

              {passkeysSupported && (
                <>
                  <div className="mt-4 flex items-center gap-3 text-xs text-fg-subtle">
                    <span className="h-px flex-1 bg-line" />
                    or
                    <span className="h-px flex-1 bg-line" />
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    className="mt-4 w-full"
                    disabled={passkeyPending}
                    onClick={onPasskeySignIn}
                  >
                    {passkeyPending ? (
                      <Loader2 className="size-4 animate-spin" />
                    ) : (
                      <Fingerprint className="size-4" />
                    )}
                    {passkeyPending ? 'Waiting for your device…' : 'Sign in with a passkey'}
                  </Button>
                </>
              )}

              <p className="mt-6 text-center text-sm text-fg-muted">
                No account?{' '}
                <Link to="/register" className="font-medium text-accent hover:underline">
                  Create one
                </Link>
              </p>
            </>
          ) : (
            <>
              <h2 className="text-2xl font-semibold tracking-tight">Verify it's you</h2>
              <p className="mt-1 text-sm text-fg-muted">
                {useRecoveryCode
                  ? 'Enter one of your unused recovery codes.'
                  : 'Enter the 6-digit code from your authenticator app.'}
              </p>

              <form onSubmit={onSubmitMfa} className="mt-8 space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="mfa-code">{useRecoveryCode ? 'Recovery code' : 'Authentication code'}</Label>
                  <Input
                    id="mfa-code"
                    autoComplete="one-time-code"
                    inputMode={useRecoveryCode ? 'text' : 'numeric'}
                    maxLength={useRecoveryCode ? 9 : 6}
                    required
                    autoFocus
                    value={mfaCode}
                    onChange={(e) =>
                      setMfaCode(useRecoveryCode ? e.target.value : e.target.value.replace(/\D/g, ''))
                    }
                    placeholder={useRecoveryCode ? 'XXXX-XXXX' : '000000'}
                  />
                </div>

                <Button type="submit" className="w-full" disabled={!mfaCode || mfaSubmitting}>
                  {mfaSubmitting && <Loader2 className="size-4 animate-spin" />}
                  {mfaSubmitting ? 'Verifying…' : 'Verify and sign in'}
                </Button>
              </form>

              <button
                type="button"
                onClick={() => {
                  setUseRecoveryCode((r) => !r)
                  setMfaCode('')
                }}
                className="mt-4 flex w-full items-center justify-center gap-1.5 text-sm font-medium text-accent hover:underline"
              >
                <KeyRound className="size-3.5" />
                {useRecoveryCode ? 'Use an authenticator code instead' : 'Use a recovery code instead'}
              </button>

              <button
                type="button"
                onClick={() => {
                  setMfaChallenge(null)
                  setMfaCode('')
                  setUseRecoveryCode(false)
                }}
                className="mt-6 block w-full text-center text-sm text-fg-muted hover:underline"
              >
                Back to sign in
              </button>
            </>
          )}
        </motion.div>
      </main>
    </div>
  )
}
