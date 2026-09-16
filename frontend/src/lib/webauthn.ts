/**
 * WebAuthn (passkey) support: base64url <-> ArrayBuffer conversion, and the
 * two ceremonies (`navigator.credentials.create` / `.get`) built from the
 * shapes PasskeyDtos.RegistrationOptionsResponse and
 * PasskeyDtos.AuthenticationOptionsResponse return.
 *
 * The backend deliberately shapes its options responses close to what the
 * browser API expects (see PasskeyDtos' class comment) — the one real gap is
 * that JSON has no binary type, so `challenge`, credential ids and the user
 * id all cross the wire as base64url strings and need converting to
 * ArrayBuffer/Uint8Array before `navigator.credentials` will accept them.
 */

// ---------------------------------------------------------------------------
// base64url <-> ArrayBuffer
// ---------------------------------------------------------------------------

export function base64urlToBuffer(base64url: string): ArrayBuffer {
  const padding = '='.repeat((4 - (base64url.length % 4)) % 4)
  const base64 = (base64url + padding).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(base64)
  const bytes = new Uint8Array(raw.length)
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i)
  return bytes.buffer
}

export function bufferToBase64url(buffer: ArrayBuffer | Uint8Array): string {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer)
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** Feature-detect WebAuthn support — used to hide/disable passkey UI on browsers without it. */
export function isWebAuthnSupported(): boolean {
  return typeof window !== 'undefined' && typeof window.PublicKeyCredential !== 'undefined'
}

/** True for the OS-level "user cancelled the prompt" case, which is not an error worth logging. */
export function isPasskeyCancellation(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'NotAllowedError'
}

// ---------------------------------------------------------------------------
// Registration — matches PasskeyDtos.RegistrationOptionsResponse
// ---------------------------------------------------------------------------

export interface PasskeyRegistrationOptions {
  challenge: string
  rpId: string
  rpName: string
  userId: string
  userName: string
  userDisplayName: string
  pubKeyCredParams: { type: string; alg: number }[]
  excludeCredentialIds: string[]
  timeoutMillis: number
}

/**
 * Drives `navigator.credentials.create()` from the server's options and
 * returns the JSON string PasskeyDtos.RegisterPasskeyRequest.credentialResponseJson
 * expects — the same shape `PublicKeyCredential.toJSON()` produces, which is
 * what webauthn4j parses directly on the backend.
 */
export async function createPasskeyCredential(options: PasskeyRegistrationOptions): Promise<string> {
  const publicKey: PublicKeyCredentialCreationOptions = {
    challenge: base64urlToBuffer(options.challenge),
    rp: { id: options.rpId, name: options.rpName },
    user: {
      id: base64urlToBuffer(options.userId),
      name: options.userName,
      displayName: options.userDisplayName,
    },
    pubKeyCredParams: options.pubKeyCredParams.map((param) => ({
      type: 'public-key',
      alg: param.alg,
    })),
    excludeCredentials: options.excludeCredentialIds.map((id) => ({
      type: 'public-key',
      id: base64urlToBuffer(id),
    })),
    timeout: options.timeoutMillis,
    authenticatorSelection: { userVerification: 'preferred' },
  }

  const credential = (await navigator.credentials.create({ publicKey })) as PublicKeyCredential | null
  if (!credential) {
    throw new Error('No credential was returned by the browser.')
  }
  return JSON.stringify(credential.toJSON())
}

// ---------------------------------------------------------------------------
// Authentication — matches PasskeyDtos.AuthenticationOptionsResponse
// ---------------------------------------------------------------------------

export interface PasskeyAuthenticationOptions {
  challenge: string
  rpId: string
  allowCredentialIds: string[]
  timeoutMillis: number
}

export interface PasskeyAssertion {
  credentialId: string
  assertionResponseJson: string
}

/**
 * Drives `navigator.credentials.get()` from the server's options. Returns
 * both the credential id (base64url, as PasskeyDtos.AuthenticatePasskeyRequest
 * wants it separately) and the JSON string of the assertion response.
 */
export async function getPasskeyAssertion(options: PasskeyAuthenticationOptions): Promise<PasskeyAssertion> {
  const publicKey: PublicKeyCredentialRequestOptions = {
    challenge: base64urlToBuffer(options.challenge),
    rpId: options.rpId,
    allowCredentials: options.allowCredentialIds.map((id) => ({
      type: 'public-key',
      id: base64urlToBuffer(id),
    })),
    timeout: options.timeoutMillis,
    userVerification: 'preferred',
  }

  const credential = (await navigator.credentials.get({ publicKey })) as PublicKeyCredential | null
  if (!credential) {
    throw new Error('No credential was returned by the browser.')
  }

  return {
    credentialId: bufferToBase64url(credential.rawId),
    assertionResponseJson: JSON.stringify(credential.toJSON()),
  }
}
