import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { Toaster } from '@/components/ui/sonner'
import { useAuth } from '@/stores/auth'
import { AppShell } from '@/components/layout/AppShell'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { SkillTreePage } from '@/pages/SkillTreePage'
import { DiagnosticPage } from '@/pages/DiagnosticPage'
import { CareerPage } from '@/pages/CareerPage'
import { ProjectsPage } from '@/pages/ProjectsPage'
import { ProjectWorkspacePage } from '@/pages/ProjectWorkspacePage'
import { PassportPage } from '@/pages/PassportPage'
import { PublicPassportPage } from '@/pages/PublicPassportPage'
import { ArenasPage } from '@/pages/ArenasPage'
import { ArenaHostPage } from '@/pages/ArenaHostPage'
import { ArenaPlayPage } from '@/pages/ArenaPlayPage'
import { Loader2 } from 'lucide-react'

/**
 * Gate for authenticated routes.
 *
 * Waits on `initialising` rather than reading `user` directly. The access token
 * lives in memory only, so on a page reload there is a moment where the session
 * is real but not yet restored — checking `user` in that window would redirect a
 * signed-in person to the login page for no reason, which is the classic bug in
 * this pattern.
 *
 * The current location is passed along so the user lands back where they were
 * after signing in, instead of being dumped on a dashboard.
 */
function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, initialising } = useAuth()
  const location = useLocation()

  if (initialising) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}

function RedirectIfAuthenticated({ children }: { children: React.ReactNode }) {
  const { user, initialising } = useAuth()
  if (initialising) return null
  return user ? <Navigate to="/" replace /> : <>{children}</>
}

export default function App() {
  const restore = useAuth((state) => state.restore)

  // One refresh attempt on boot. This is what turns the in-memory access token
  // from a limitation into a workable design: the httpOnly cookie survives the
  // reload even though the token does not.
  useEffect(() => {
    void restore()
  }, [restore])

  return (
    <>
      <Routes>
        <Route
          path="/login"
          element={
            <RedirectIfAuthenticated>
              <LoginPage />
            </RedirectIfAuthenticated>
          }
        />
        <Route
          path="/register"
          element={
            <RedirectIfAuthenticated>
              <RegisterPage />
            </RedirectIfAuthenticated>
          }
        />
        {/* Public — no auth, no app shell. A share link has to make sense to
            someone who has never seen this product before. */}
        <Route path="/p/:token" element={<PublicPassportPage />} />
        <Route path="/arena/join" element={<ArenaPlayPage />} />
        <Route path="/arena/join/:code" element={<ArenaPlayPage />} />

        <Route
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route path="/" element={<DashboardPage />} />
          <Route path="/skills" element={<SkillTreePage />} />
          <Route path="/diagnostics/:skillId" element={<DiagnosticPage />} />
          <Route path="/career" element={<CareerPage />} />
          <Route path="/projects" element={<ProjectsPage />} />
          <Route path="/projects/:projectId" element={<ProjectWorkspacePage />} />
          <Route path="/passport" element={<PassportPage />} />
          <Route path="/arenas" element={<ArenasPage />} />
          <Route path="/arenas/:arenaId" element={<ArenaHostPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>

      <Toaster position="top-right" richColors closeButton />
    </>
  )
}
