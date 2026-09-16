import { create } from 'zustand'
import { api, setAccessToken } from '@/lib/api'

export interface User {
  id: number
  email: string
  fullName: string
  status: 'ACTIVE' | 'PENDING' | 'SUSPENDED'
  emailVerified: boolean
  roles: string[]
}

/** Matches AuthDtos.AuthResponse on the backend — a normal, complete session. */
export interface AuthSession {
  accessToken: string
  tokenType: string
  expiresAt: string
  user: User
}

/**
 * Matches AuthDtos.MfaChallengeResponse. Returned by POST /auth/login instead
 * of an {@link AuthSession} when the account has MFA enabled — no tokens, no
 * refresh cookie. The caller completes the login with `mfaToken` plus a code
 * at POST /api/auth/mfa/verify (see {@link AuthState.completeMfaLogin}).
 */
export interface MfaChallenge {
  mfaRequired: true
  mfaToken: string
  expiresAt: string
}

export type LoginOutcome = { mfaRequired: false } | MfaChallenge

function isMfaChallenge(data: AuthSession | MfaChallenge): data is MfaChallenge {
  return (data as MfaChallenge).mfaRequired === true
}

interface AuthState {
  user: User | null
  /**
   * True until the initial session restore finishes.
   *
   * Without it the app renders as logged-out for a moment on every reload, then
   * flips to logged-in — a flash that looks broken and, worse, can bounce
   * someone to the login page mid-navigation. Routes wait on this rather than
   * reading `user` directly.
   */
  initialising: boolean
  login: (email: string, password: string) => Promise<LoginOutcome>
  /**
   * Completes a login POST /auth/login paused for a second factor. Exactly
   * one of `code`/`recoveryCode` should be supplied — the caller (MfaSetupPage
   * or LoginPage's second step) is responsible for that.
   */
  completeMfaLogin: (mfaToken: string, code?: string, recoveryCode?: string) => Promise<void>
  /**
   * Adopts an already-issued session — used by the passkey sign-in flow,
   * which authenticates entirely outside `login` (no password, no MFA
   * challenge) but ends up with the exact same {@link AuthSession} shape.
   */
  setSession: (session: AuthSession) => void
  register: (input: RegisterInput) => Promise<{ status: string; message: string }>
  logout: () => Promise<void>
  restore: () => Promise<void>
}

export interface RegisterInput {
  email: string
  password: string
  fullName: string
  role: 'LEARNER' | 'INSTRUCTOR'
}

export const useAuth = create<AuthState>((set) => ({
  user: null,
  initialising: true,

  login: async (email, password) => {
    const { data } = await api.post<AuthSession | MfaChallenge>('/auth/login', { email, password })
    if (isMfaChallenge(data)) {
      // No tokens, no refresh cookie — the caller (LoginPage) shows the
      // second-factor step and finishes with completeMfaLogin.
      return data
    }
    setAccessToken(data.accessToken)
    set({ user: data.user })
    return { mfaRequired: false }
  },

  completeMfaLogin: async (mfaToken, code, recoveryCode) => {
    const { data } = await api.post<{ verified: boolean; session: AuthSession }>('/auth/mfa/verify', {
      mfaToken,
      code,
      recoveryCode,
    })
    setAccessToken(data.session.accessToken)
    set({ user: data.session.user })
  },

  setSession: (session) => {
    setAccessToken(session.accessToken)
    set({ user: session.user })
  },

  register: async (input) => {
    const { data } = await api.post('/auth/register', input)
    // Registration deliberately does not sign anyone in — the address is not
    // proven yet, and an instructor still needs approval. The caller shows the
    // returned message instead of navigating to a dashboard.
    return data
  },

  logout: async () => {
    try {
      await api.post('/auth/logout')
    } finally {
      // Cleared even if the call fails. A network error must not leave the UI
      // showing a signed-in state the server has already ended.
      setAccessToken(null)
      set({ user: null })
    }
  },

  /**
   * Restores a session on page load.
   *
   * The access token lives in memory only, so a reload always starts without
   * one. The refresh cookie survives, so a single /refresh call re-establishes
   * the session. This is the cost of not storing tokens where a script can read
   * them, and it is paid once per page load.
   */
  restore: async () => {
    try {
      const { data } = await api.post('/auth/refresh')
      setAccessToken(data.accessToken)
      set({ user: data.user, initialising: false })
    } catch {
      // No valid refresh cookie — an ordinary logged-out visitor, not an error.
      setAccessToken(null)
      set({ user: null, initialising: false })
    }
  },
}))

export const hasRole = (user: User | null, role: string) =>
  !!user?.roles.includes(role)
