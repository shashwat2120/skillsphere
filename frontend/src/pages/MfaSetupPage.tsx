import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import QRCode from 'qrcode'
import { AlertTriangle, Check, Copy, Loader2, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** Matches AuthDtos.MfaEnrollResponse. */
interface MfaEnrollResponse {
  secret: string
  provisioningUri: string
  recoveryCodes: string[]
}

type Step = 'start' | 'confirm' | 'done'

/**
 * TOTP enrollment: generate a secret + recovery codes, show the QR code and
 * the raw secret, confirm with a live code, then show the recovery codes one
 * final time with a "save these now" warning — they are never retrievable
 * again after this screen (AuthDtos.MfaEnrollResponse's own comment).
 */
export function MfaSetupPage() {
  const [step, setStep] = useState<Step>('start')
  const [enrollment, setEnrollment] = useState<MfaEnrollResponse | null>(null)
  const [code, setCode] = useState('')
  const canvasRef = useRef<HTMLCanvasElement>(null)

  const enroll = useMutation({
    mutationFn: async () => (await api.post<MfaEnrollResponse>('/auth/mfa/totp/enroll')).data,
    onSuccess: (data) => {
      setEnrollment(data)
      setStep('confirm')
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not start enrollment.')),
  })

  const confirm = useMutation({
    mutationFn: async () => api.post('/auth/mfa/verify', { code }),
    onSuccess: () => {
      toast.success('Authenticator app enabled.')
      setStep('done')
    },
    onError: (error) => toast.error(errorMessage(error, 'That code was not accepted.')),
  })

  useEffect(() => {
    if (step !== 'confirm' || !enrollment || !canvasRef.current) return
    QRCode.toCanvas(canvasRef.current, enrollment.provisioningUri, { width: 200, margin: 1 }).catch(() => {
      // The raw secret below still works for manual entry if this fails.
    })
  }, [step, enrollment])

  function copySecret() {
    if (!enrollment) return
    void navigator.clipboard.writeText(enrollment.secret).then(
      () => toast.success('Secret copied.'),
      () => toast.error('Could not copy — select and copy it manually.'),
    )
  }

  return (
    <div className="mx-auto max-w-md">
      <header className="mb-6 border-b border-line pb-5">
        <h1 className="text-[19px] font-semibold">Two-factor authentication</h1>
        <p className="mt-1 text-[13px] text-fg-muted">
          Protect your account with a time-based code from an authenticator app.
        </p>
      </header>

      {step === 'start' && (
        <div className="rounded-sq border border-line bg-surface p-5">
          <p className="text-sm text-fg-muted">
            You will need an authenticator app — Google Authenticator, 1Password, Authy, or similar — on
            your phone or computer to scan a QR code.
          </p>
          <Button className="mt-4" disabled={enroll.isPending} onClick={() => enroll.mutate()}>
            {enroll.isPending && <Loader2 className="size-4 animate-spin" />}
            {enroll.isPending ? 'Starting…' : 'Set up an authenticator app'}
          </Button>
        </div>
      )}

      {step === 'confirm' && enrollment && (
        <div className="space-y-5 rounded-sq border border-line bg-surface p-5">
          <div className="flex justify-center">
            <canvas ref={canvasRef} className="rounded-sq border border-line bg-white p-2" />
          </div>

          <div>
            <Label className="mb-1.5 block">Can't scan? Enter this key manually</Label>
            <div className="flex items-center gap-1.5">
              <code className="flex-1 truncate rounded-sq border border-line bg-bg-sunken px-2.5 py-1.5 text-xs">
                {enrollment.secret}
              </code>
              <Button type="button" size="sm" variant="ghost" onClick={copySecret}>
                <Copy className="size-3.5" />
              </Button>
            </div>
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault()
              confirm.mutate()
            }}
            className="space-y-2"
          >
            <Label htmlFor="mfa-code">Enter the 6-digit code from your app</Label>
            <Input
              id="mfa-code"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              placeholder="000000"
              required
            />
            <Button type="submit" className="w-full" disabled={code.length !== 6 || confirm.isPending}>
              {confirm.isPending && <Loader2 className="size-4 animate-spin" />}
              {confirm.isPending ? 'Confirming…' : 'Confirm and enable'}
            </Button>
          </form>
        </div>
      )}

      {step === 'done' && enrollment && (
        <div className="space-y-5">
          <div className="flex items-start gap-2 rounded-sq border border-decaying bg-decaying-bg px-4 py-3 text-sm">
            <AlertTriangle className="mt-0.5 size-4 shrink-0 text-decaying" />
            <p>
              Save these recovery codes now — this is the only time they are shown. Each one can be used
              once, in place of a code from your app, if you lose access to it.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-2 rounded-sq border border-line bg-surface p-4 font-mono text-sm">
            {enrollment.recoveryCodes.map((rc) => (
              <span key={rc}>{rc}</span>
            ))}
          </div>

          <Button
            variant="outline"
            className="w-full"
            onClick={() =>
              void navigator.clipboard.writeText(enrollment.recoveryCodes.join('\n')).then(
                () => toast.success('Recovery codes copied.'),
                () => toast.error('Could not copy — select and copy them manually.'),
              )
            }
          >
            <Copy className="size-3.5" /> Copy all
          </Button>

          <div className="flex items-center gap-2 rounded-sq border border-mastered bg-mastered-bg px-4 py-3 text-sm text-mastered">
            <ShieldCheck className="size-4 shrink-0" />
            <p>Two-factor authentication is now on for your account.</p>
          </div>

          <Link to="/" className="flex items-center justify-center gap-1.5 text-sm font-medium text-accent hover:underline">
            <Check className="size-3.5" /> Done
          </Link>
        </div>
      )}
    </div>
  )
}
