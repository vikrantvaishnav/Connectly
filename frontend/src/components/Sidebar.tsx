import { NavLink } from 'react-router-dom'
import { useAppStore } from '../store/appStore'
import { useConnectionSummary } from '../hooks/useConnectionSummary'

const navItems = [
  { to: '/home', label: 'Home', icon: '🏠' },
  { to: '/explore', label: 'Explore', icon: '🧭' },
  { to: '/discover', label: 'Discover', icon: '💘' },
  { to: '/nearby', label: 'Nearby', icon: '📍' },
  { to: '/requests', label: 'Requests', icon: '❤️', badge: 'requests' as const },
  { to: '/messages', label: 'Messages', icon: '💬', badge: 'messages' as const },
  { to: '/communities', label: 'Communities', icon: '👥' },
  { to: '/voice', label: 'Voice', icon: '🎙️' },
  { to: '/notifications', label: 'Notifications', icon: '🔔', badge: 'notifications' as const },
  { to: '/profile', label: 'Profile', icon: '👤' },
  { to: '/settings', label: 'Settings', icon: '⚙️' },
]

export function Sidebar() {
  const conversations = useAppStore((s) => s.conversations)
  const notifications = useAppStore((s) => s.notifications)
  const summary = useConnectionSummary()
  const unreadMsgs = conversations.reduce((n, c) => n + c.unread, 0)
  const unreadNotifs = notifications.filter((n) => !n.read).length
  const pendingRequests = summary.data?.incoming ?? 0

  return (
    <nav className="sticky top-14 hidden h-[calc(100vh-3.5rem)] w-56 shrink-0 flex-col gap-1 overflow-y-auto border-r border-[var(--border)] p-3 md:flex">
      {navItems.map((item) => {
        const badge =
          item.badge === 'messages' ? unreadMsgs
            : item.badge === 'notifications' ? unreadNotifs
              : item.badge === 'requests' ? pendingRequests
                : 0
        return (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-[var(--accent-soft)] text-indigo-400'
                  : 'text-[var(--muted)] hover:bg-[var(--surface-2)] hover:text-[var(--text)]'
              }`
            }
          >
            <span aria-hidden="true" className="text-lg">{item.icon}</span>
            <span className="flex-1">{item.label}</span>
            {badge > 0 && (
              <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-indigo-500 px-1.5 text-[10px] font-bold text-white">
                {badge}
              </span>
            )}
          </NavLink>
        )
      })}
    </nav>
  )
}
