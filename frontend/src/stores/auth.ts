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
  login: (email: string, password: string) => Promise<void>
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
    const { data } = await api.post('/auth/login', { email, password })
    setAccessToken(data.accessToken)
    set({ user: data.user })
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
