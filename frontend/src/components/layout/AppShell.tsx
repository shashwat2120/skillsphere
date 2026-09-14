import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { GitBranch, LayoutDashboard, LogOut, Moon, Sun } from 'lucide-react'
import { useAuth } from '@/stores/auth'
import { useTheme } from '@/hooks/useTheme'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/skills', label: 'Skill tree', icon: GitBranch },
]

export function AppShell() {
  const { user, logout } = useAuth()
  const { theme, toggle } = useTheme()
  const navigate = useNavigate()

  const initials = (user?.fullName ?? '?')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <div className="min-h-screen bg-bg">
      {/* Sticky, translucent header. backdrop-blur keeps content visible behind
          it rather than hiding it under an opaque bar, which matters on the
          skill tree where the canvas scrolls underneath. */}
      <header className="sticky top-0 z-40 border-b border-line bg-bg/80 backdrop-blur-xl">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4">
          <span className="font-semibold tracking-tight">SkillSphere</span>

          <nav className="flex items-center gap-1">
            {NAV.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  cn(
                    'relative flex items-center gap-2 rounded-sq px-3 py-1.5 text-sm transition-colors',
                    isActive
                      ? 'text-fg'
                      : 'text-fg-muted hover:text-fg',
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon className="size-4" />
                    {label}
                    {/* layoutId lets the indicator physically slide between tabs
                        instead of disappearing and reappearing. Spatial
                        continuity is what makes navigation feel considered
                        rather than abrupt. */}
                    {isActive && (
                      <motion.span
                        layoutId="nav-active"
                        className="absolute inset-0 -z-10 rounded-sq bg-bg-sunken"
                        transition={{ type: 'spring', stiffness: 380, damping: 30 }}
                      />
                    )}
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-2">
            <Button
              variant="ghost"
              size="icon"
              onClick={toggle}
              aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
            >
              {theme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
            </Button>

            <div
              className="grid size-8 place-items-center rounded-full bg-bg-sunken text-xs font-medium text-accent"
              title={user?.email}
            >
              {initials}
            </div>

            <Button
              variant="ghost"
              size="icon"
              aria-label="Sign out"
              onClick={async () => {
                await logout()
                navigate('/login', { replace: true })
              }}
            >
              <LogOut className="size-4" />
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
