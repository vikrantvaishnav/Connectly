import { useHealth } from '../hooks/useHealth'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { Composer } from '../components/Composer'
import { PostCard } from '../components/PostCard'
import { Stories } from '../components/Stories'
import { RightRail } from '../components/RightRail'
import { RealFeed } from '../components/RealFeed'
import { Link } from 'react-router-dom'

export function HomePage() {
  const posts = useAppStore((s) => s.posts)
  const health = useHealth()
  const authUser = useAuthStore((s) => s.user)

  return (
    <div className="mx-auto flex max-w-none">
      <div className="mx-auto min-w-0 flex-1 space-y-6 p-4 sm:p-6 lg:max-w-2xl">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold">Home</h1>
          <span
            className={`flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] ${
              health.isError
                ? 'border-amber-500/30 bg-amber-500/10 text-amber-400'
                : health.isPending
                  ? 'border-[var(--border)] text-[var(--muted)]'
                  : 'border-emerald-500/30 bg-emerald-500/10 text-emerald-400'
            }`}
          >
            <span className={`h-1.5 w-1.5 rounded-full ${health.isError ? 'bg-amber-400' : health.isPending ? 'bg-slate-400' : 'bg-emerald-400'}`} />
            {health.isPending ? 'checking backend…' : health.isError ? 'demo mode · backend offline' : 'backend live'}
          </span>
        </div>

        {authUser ? (
          <>
            <p className="rounded-2xl border border-emerald-500/25 bg-emerald-500/5 p-3 text-sm text-[var(--text)]">
              👋 Signed in as <strong>{authUser.username}</strong> — this feed is <strong>real</strong>: posts,
              likes and comments go through the Spring Boot API.
            </p>
            <RealFeed />
          </>
        ) : (
          <>
            <div className="flex items-start gap-3 rounded-2xl border border-indigo-500/25 bg-[var(--accent-soft)] p-4 text-sm text-[var(--text)]">
              <span className="text-lg" aria-hidden="true">🧪</span>
              <p>
                <strong>Demo mode.</strong> Everything below is dummy data.{' '}
                <Link to="/register" className="font-medium text-indigo-400 hover:text-indigo-300">
                  Create an account
                </Link>{' '}
                to post, like and comment for real.
              </p>
            </div>
            <Stories />
            <Composer />
            <div className="space-y-4">
              {posts.map((p) => (
                <PostCard key={p.id} post={p} />
              ))}
            </div>
          </>
        )}
      </div>

      <RightRail />
    </div>
  )
}
