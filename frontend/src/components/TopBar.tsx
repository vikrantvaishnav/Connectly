import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { SearchModal } from './SearchModal'

export function TopBar() {
  const theme = useAppStore((s) => s.theme)
  const toggleTheme = useAppStore((s) => s.toggleTheme)
  const authUser = useAuthStore((s) => s.user)
  const [searchOpen, setSearchOpen] = useState(false)

  // Real unread count for signed-in users; demo fallback otherwise.
  const realUnread = useQuery({
    queryKey: ['topbar-unread'],
    queryFn: async () => (await api.get<{ count: number }>('/notifications/unread-count')).data.count,
    enabled: Boolean(authUser),
    refetchInterval: 15_000,
  })
  const demoUnread = useAppStore((s) => s.notifications.filter((n) => !n.read).length)
  const unread = authUser ? (realUnread.data ?? 0) : demoUnread

  // Ctrl+K / ⌘K opens search
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setSearchOpen(true)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <header className="sticky top-0 z-40 flex h-14 items-center gap-3 border-b border-[var(--border)] bg-[var(--bg)]/80 px-4 backdrop-blur">
      <button
        onClick={() => setSearchOpen(true)}
        className="group flex flex-1 items-center gap-2 rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm text-[var(--muted)] transition-colors hover:border-[var(--accent)]"
      >
        <span aria-hidden="true">🔍</span>
        <span className="hidden sm:inline">Search Connectly…</span>
        <span className="sm:hidden">Search</span>
        <kbd className="ml-auto hidden rounded-md border border-[var(--border)] bg-[var(--surface)] px-1.5 py-0.5 text-[10px] text-[var(--muted)] sm:inline">
          Ctrl K
        </kbd>
      </button>

      <button
        onClick={toggleTheme}
        className="rounded-full border border-[var(--border)] bg-[var(--surface)] p-2 text-sm transition-transform hover:scale-105 active:scale-95"
        title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
        aria-label="Toggle theme"
      >
        {theme === 'dark' ? '☀️' : '🌙'}
      </button>

      <Link
        to="/notifications"
        className="relative rounded-full border border-[var(--border)] bg-[var(--surface)] p-2 text-sm transition-transform hover:scale-105 active:scale-95"
        aria-label={`Notifications (${unread} unread)`}
      >
        🔔
        {unread > 0 && (
          <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">
            {unread}
          </span>
        )}
      </Link>

      <SearchModal open={searchOpen} onClose={() => setSearchOpen(false)} />
    </header>
  )
}
