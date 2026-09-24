import { Link } from 'react-router-dom'
import { useAppStore } from '../store/appStore'
import { Avatar } from './Avatar'

export function RightRail() {
  const users = useAppStore((s) => s.users)
  const online = users.filter((u) => u.online && u.id !== 'u0')
  const offline = users.filter((u) => !u.online && u.id !== 'u0').slice(0, 3)

  return (
    <aside className="hidden w-64 shrink-0 space-y-4 overflow-y-auto border-l border-[var(--border)] p-4 xl:block">
      <div className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
        <h2 className="mb-3 text-sm font-semibold">Online now</h2>
        <div className="space-y-2.5">
          {online.map((u) => (
            <Link key={u.id} to={`/messages`} className="flex items-center gap-2.5 rounded-lg p-1 hover:bg-[var(--accent-soft)]">
              <Avatar name={u.name} id={u.id} size="sm" online />
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{u.name}</p>
                <p className="truncate text-[11px] text-[var(--muted)]">{u.profession}</p>
              </div>
            </Link>
          ))}
        </div>
      </div>

      {offline.length > 0 && (
        <div className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
          <h2 className="mb-3 text-sm font-semibold">Offline</h2>
          <div className="space-y-2.5">
            {offline.map((u) => (
              <Link key={u.id} to={`/profile/${u.username}`} className="flex items-center gap-2.5 rounded-lg p-1 opacity-60 hover:opacity-100">
                <Avatar name={u.name} id={u.id} size="sm" online={false} />
                <p className="truncate text-sm font-medium">{u.name}</p>
              </Link>
            ))}
          </div>
        </div>
      )}

      <div className="rounded-2xl border border-dashed border-[var(--border)] p-4 text-xs text-[var(--muted)]">
        <p className="font-medium text-[var(--text)]">Try it out 👇</p>
        <ul className="mt-2 list-inside list-disc space-y-1">
          <li>Press <kbd className="rounded border border-[var(--border)] px-1">Ctrl K</kbd> to search</li>
          <li>Send a chat message — replies are simulated</li>
          <li>Toggle 🌙/☀️ for light/dark mode</li>
          <li>Open <Link to="/nearby" className="text-indigo-400 hover:underline">Nearby</Link> and allow location</li>
        </ul>
      </div>
    </aside>
  )
}
