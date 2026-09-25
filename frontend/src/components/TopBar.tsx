import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'

export function TopBar() {
  const theme = useAppStore((s) => s.theme)
  const toggleTheme = useAppStore((s) => s.toggleTheme)
  const authUser = useAuthStore((s) => s.user)

  // Real unread count — the only badge in the app, fed by the live API.
  const unread = useQuery({
    queryKey: ['topbar-unread'],
    queryFn: async () => (await api.get<{ count: number }>('/notifications/unread-count')).data.count,
    enabled: Boolean(authUser),
    refetchInterval: 15_000,
  })
  const unreadCount = authUser ? (unread.data ?? 0) : 0

  return (
    <header className="sticky top-0 z-40 flex h-14 items-center gap-3 border-b border-[var(--border)] bg-[var(--bg)]/80 px-4 backdrop-blur">
      <div className="flex flex-1 items-center">
        <Link to="/home" className="text-lg font-black tracking-tight text-[var(--text)]">
          Connectly
        </Link>
      </div>

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
        aria-label={`Notifications (${unreadCount} unread)`}
      >
        🔔
        {unreadCount > 0 && (
          <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">
            {unreadCount}
          </span>
        )}
      </Link>
    </header>
  )
}
