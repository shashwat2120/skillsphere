import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import {
  Award,
  BarChart3,
  FolderGit2,
  GitBranch,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  ShieldCheck,
  Sun,
  Swords,
  Target,
  X,
} from 'lucide-react'
import { hasRole, useAuth } from '@/stores/auth'
import { useTheme } from '@/hooks/useTheme'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const BASE_NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/skills', label: 'Skill tree', icon: GitBranch },
  { to: '/career', label: 'Career', icon: Target },
  { to: '/projects', label: 'Projects', icon: FolderGit2 },
  { to: '/passport', label: 'Passport', icon: Award },
  { to: '/arenas', label: 'Arena', icon: Swords },
]

export function AppShell() {
  const { user, logout } = useAuth()
  const { theme, toggle } = useTheme()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  const isInstructor = hasRole(user, 'INSTRUCTOR') || hasRole(user, 'ADMIN')
  const isAdmin = hasRole(user, 'ADMIN')
  const nav = [
    ...BASE_NAV,
    ...(isInstructor ? [{ to: '/instructor/analytics', label: 'Analytics', icon: BarChart3 }] : []),
    ...(isAdmin ? [{ to: '/admin', label: 'Admin', icon: ShieldCheck }] : []),
  ]

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

          {/* Collapses below lg: eight items plus the account controls no
              longer fit one row once Analytics and Admin join the base six,
              and a wrapped or overflowing nav reads as broken rather than
              busy. The hamburger panel below carries the same links. */}
          <nav className="hidden items-center gap-1 lg:flex">
            {nav.map(({ to, label, icon: Icon }) => (
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

            <Button
              variant="ghost"
              size="icon"
              className="lg:hidden"
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((open) => !open)}
            >
              {menuOpen ? <X className="size-4" /> : <Menu className="size-4" />}
            </Button>
          </div>
        </div>

        <AnimatePresence>
          {menuOpen && (
            <motion.nav
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.18 }}
              className="overflow-hidden border-t border-line bg-bg lg:hidden"
            >
              <div className="flex flex-col gap-0.5 px-4 py-2">
                {nav.map(({ to, label, icon: Icon }) => (
                  <NavLink
                    key={to}
                    to={to}
                    end={to === '/'}
                    onClick={() => setMenuOpen(false)}
                    className={({ isActive }) =>
                      cn(
                        'flex items-center gap-2.5 rounded-sq px-3 py-2.5 text-sm transition-colors',
                        isActive ? 'bg-bg-sunken text-fg' : 'text-fg-muted hover:text-fg',
                      )
                    }
                  >
                    <Icon className="size-4" />
                    {label}
                  </NavLink>
                ))}
              </div>
            </motion.nav>
          )}
        </AnimatePresence>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
