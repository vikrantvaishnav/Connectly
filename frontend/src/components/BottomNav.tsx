import { NavLink } from 'react-router-dom'

// Mobile navigation per spec: Home, Explore, Nearby, Messages, Profile
const navItems = [
  { to: '/home', label: 'Home', icon: '🏠' },
  { to: '/explore', label: 'Explore', icon: '🧭' },
  { to: '/nearby', label: 'Nearby', icon: '📍' },
  { to: '/messages', label: 'Messages', icon: '💬' },
  { to: '/profile', label: 'Profile', icon: '👤' },
]

export function BottomNav() {
  return (
    <nav className="fixed inset-x-0 bottom-0 z-10 flex border-t border-[var(--border)] bg-[var(--surface)]/95 backdrop-blur md:hidden">
      {navItems.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          className={({ isActive }) =>
            `flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-medium ${
              isActive ? 'text-indigo-400' : 'text-[var(--muted)]'
            }`
          }
        >
          <span aria-hidden="true" className="text-lg">
            {item.icon}
          </span>
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
