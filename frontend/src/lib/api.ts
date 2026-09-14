import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'

/**
 * The HTTP client, and the token strategy that goes with it.
 *
 * ---------------------------------------------------------------------------
 * WHERE THE TOKENS LIVE — the decision that matters most on this file.
 *
 * The access token is kept in a module variable, in memory only. Not
 * localStorage, not sessionStorage, not a readable cookie. Anything a script can
 * read, an XSS flaw can exfiltrate, and localStorage is the most common place
 * tokens are stolen from. Memory dies with the tab, which is the point.
 *
 * The refresh token is never touched by this code at all. It is an httpOnly
 * SameSite=Strict cookie set by the server, so JavaScript cannot read it even in
 * principle — which is what allows a page reload to restore a session without
 * the long-lived credential ever being reachable from script.
 *
 * The cost is that a hard refresh starts with no access token. That is handled
 * by calling /refresh once on boot, and it is a far better trade than leaving a
 * thirty-day credential sitting where any injected script can pick it up.
 * ---------------------------------------------------------------------------
 */

let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

export const api = axios.create({
  baseURL: '/api',
  // Required for the refresh cookie to be sent on the refresh call.
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

/**
 * Single-flight refresh.
 *
 * When an access token expires, every in-flight request fails at roughly the
 * same moment — a dashboard might have five running. Refreshing per failure
 * would fire five concurrent /refresh calls with the same token, and since the
 * server rotates on every refresh, four of them would present an
 * already-rotated token. The server would correctly read that as theft and
 * revoke every session, logging the user out for doing nothing wrong.
 *
 * So the first failure starts the refresh and the rest wait on the same promise.
 */
let refreshPromise: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = axios
      .post('/api/auth/refresh', null, { withCredentials: true })
      .then((response) => {
        const token = response.data?.accessToken ?? null
        setAccessToken(token)
        return token
      })
      .catch(() => {
        setAccessToken(null)
        return null
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

type RetriableRequest = InternalAxiosRequestConfig & { _retried?: boolean }

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableRequest | undefined

    // Retry once, and only once. Without the flag a persistently rejected token
    // would loop forever between the interceptor and the server.
    if (error.response?.status === 401 && original && !original._retried) {
      // The auth endpoints are excluded deliberately: a failed login returns 401
      // legitimately, and trying to "refresh" out of it would hide the real
      // error from the user behind a pointless extra round trip.
      const isAuthCall = original.url?.includes('/auth/login')
        || original.url?.includes('/auth/refresh')
        || original.url?.includes('/auth/register')

      if (!isAuthCall) {
        original._retried = true
        const token = await refreshAccessToken()
        if (token) {
          original.headers.Authorization = `Bearer ${token}`
          return api(original)
        }
      }
    }

    return Promise.reject(error)
  },
)

/**
 * Pulls a human-readable message out of an RFC 9457 problem detail.
 *
 * The backend returns `detail` plus a stable machine-readable `code`. UI text
 * uses `detail`; anything that needs to branch on the failure should switch on
 * `code`, never on the message, which is prose and will change.
 */
export function errorMessage(error: unknown, fallback = 'Something went wrong.'): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { detail?: string; fieldErrors?: Record<string, string> } | undefined
    // A field error is more actionable than the generic summary above it.
    const firstFieldError = data?.fieldErrors && Object.values(data.fieldErrors)[0]
    return firstFieldError ?? data?.detail ?? fallback
  }
  return fallback
}
