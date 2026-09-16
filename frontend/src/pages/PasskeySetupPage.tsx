import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Fingerprint, Loader2, ShieldAlert, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import {
  createPasskeyCredential,
  isPasskeyCancellation,
  isWebAuthnSupported,
  type PasskeyRegistrationOptions,
} from '@/lib/webauthn'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * Registers a new passkey for the signed-in account: fetch options from
 * POST /auth/passkey/register/options, drive navigator.credentials.create()
 * with them, then send the resulting response to POST /auth/passkey/register.
 */
export function PasskeySetupPage() {
  const supported = isWebAuthnSupported()
  const [deviceLabel, setDeviceLabel] = useState('')
  const [registered, setRegistered] = useState<string[]>([])

  const register = useMutation({
    mutationFn: async () => {
      const { data: options } = await api.post<PasskeyRegistrationOptions>('/auth/passkey/register/options')
      const credentialResponseJson = await createPasskeyCredential(options)
      await api.post('/auth/passkey/register', {
        credentialResponseJson,
        deviceLabel: deviceLabel.trim() || null,
      })
    },
    onSuccess: () => {
      toast.success('Passkey registered.')
      setRegistered((prev) => [...prev, deviceLabel.trim() || `Passkey ${prev.length + 1}`])
      setDeviceLabel('')
    },
    onError: (error) => {
      if (isPasskeyCancellation(error)) {
        toast.info('Cancelled.')
        return
      }
      toast.error(errorMessage(error, 'Could not register that passkey.'))
    },
  })

  return (
    <div className="mx-auto max-w-md">
      <header className="mb-6 border-b border-line pb-5">
        <h1 className="text-[19px] font-semibold">Passkeys</h1>
        <p className="mt-1 text-[13px] text-fg-muted">
          Sign in with your device's fingerprint, face, or screen lock instead of a password.
        </p>
      </header>

      {!supported && (
        <div className="flex items-start gap-2 rounded-sq border border-decaying bg-decaying-bg px-4 py-3 text-sm">
          <ShieldAlert className="mt-0.5 size-4 shrink-0 text-decaying" />
          <p>This browser does not support passkeys. Try a recent version of Chrome, Safari, or Edge.</p>
        </div>
      )}

      {supported && (
        <div className="space-y-5">
          <form
            onSubmit={(e) => {
              e.preventDefault()
              register.mutate()
            }}
            className="space-y-3 rounded-sq border border-line bg-surface p-5"
          >
            <div className="space-y-2">
              <Label htmlFor="device-label">Name this device (optional)</Label>
              <Input
                id="device-label"
                value={deviceLabel}
                onChange={(e) => setDeviceLabel(e.target.value)}
                placeholder="e.g. Work laptop"
              />
            </div>
            <Button type="submit" className="w-full" disabled={register.isPending}>
              {register.isPending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Fingerprint className="size-4" />
              )}
              {register.isPending ? 'Waiting for your device…' : 'Add a passkey'}
            </Button>
          </form>

          {registered.length > 0 && (
            <ul className="space-y-2">
              {registered.map((label, i) => (
                <li
                  key={`${label}-${i}`}
                  className="flex items-center gap-2 rounded-sq border border-mastered bg-mastered-bg px-4 py-3 text-sm text-mastered"
                >
                  <ShieldCheck className="size-4 shrink-0" />
                  {label} registered
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
