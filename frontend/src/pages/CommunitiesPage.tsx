import { Link } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { RealCommunitiesPage } from './RealCommunitiesPage'
import { useAppStore } from '../store/appStore'

export function CommunitiesPage() {
  const authUser = useAuthStore((s) => s.user)
  if (authUser) return <RealCommunitiesPage />
  return <DemoCommunities />
}

function DemoCommunities() {
  const communities = useAppStore((s) => s.communities)
  const toggleCommunity = useAppStore((s) => s.toggleCommunity)
  const pushToast = useAppStore((s) => s.pushToast)

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-4 sm:p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Communities</h1>
        <button
          onClick={() => { pushToast('Sign in to create communities ✨'); window.location.href = '/login' }}
          className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)] active:scale-95"
        >
          + Create
        </button>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        {communities.map((c) => (
          <div
            key={c.id}
            className="animate-fade-up overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--surface)] transition-shadow hover:shadow-md"
          >
            <div className={`flex h-20 items-center gap-3 bg-gradient-to-br p-4 ${c.color}`}>
              <span className="text-3xl" aria-hidden="true">{c.icon}</span>
              <div className="min-w-0">
                <h2 className="truncate font-bold text-white">{c.name}</h2>
                <p className="text-xs text-white/80">{c.members.toLocaleString()} members</p>
              </div>
            </div>
            <div className="p-4">
              <p className="text-sm text-[var(--muted)]">{c.description}</p>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {c.channels.filter((ch) => ch.kind === 'text').slice(0, 3).map((ch) => (
                  <span key={ch.id} className="rounded-full bg-[var(--surface-2)] px-2 py-0.5 text-[10px] text-[var(--muted)]">
                    #{ch.name}
                  </span>
                ))}
              </div>
              <div className="mt-4 flex items-center gap-2">
                <Link
                  to={`/communities/${c.id}`}
                  className="flex-1 rounded-xl bg-[var(--surface-2)] px-4 py-2 text-center text-sm font-medium transition-colors hover:bg-[var(--accent-soft)]"
                >
                  Open
                </Link>
                <button
                  onClick={() => toggleCommunity(c.id)}
                  className={`flex-1 rounded-xl px-4 py-2 text-sm font-medium transition-all active:scale-95 ${
                    c.joined
                      ? 'bg-[var(--surface-2)] text-[var(--muted)] hover:text-rose-400'
                      : 'bg-[var(--accent)] text-white hover:bg-[var(--accent-hover)]'
                  }`}
                >
                  {c.joined ? 'Joined ✓' : 'Join'}
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
