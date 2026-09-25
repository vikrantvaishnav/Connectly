import { useAuthStore } from '../store/authStore'
import { RealFeed } from '../components/RealFeed'
import { Link } from 'react-router-dom'

export function HomePage() {
  const authUser = useAuthStore((s) => s.user)

  return (
    <div className="mx-auto flex max-w-none">
      <div className="mx-auto min-w-0 flex-1 space-y-6 p-4 sm:p-6 lg:max-w-2xl">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold">Home</h1>
        </div>

        {authUser ? (
          <RealFeed />
        ) : (
          <div className="space-y-4">
            <div className="flex items-start gap-3 rounded-2xl border border-indigo-500/25 bg-[var(--accent-soft)] p-4 text-sm text-[var(--text)]">
              <span className="text-lg" aria-hidden="true">👋</span>
              <p>
                <strong>Welcome to Connectly.</strong> Create an account or sign in to post, discover
                people nearby, chat in real time and join communities.{' '}
                <Link to="/register" className="font-medium text-indigo-400 hover:text-indigo-300">
                  Create an account
                </Link>{' '}
                ·{' '}
                <Link to="/login" className="font-medium text-indigo-400 hover:text-indigo-300">
                  Sign in
                </Link>
              </p>
            </div>
            <EmptyFeedHint />
          </div>
        )}
      </div>
    </div>
  )
}

function EmptyFeedHint() {
  return (
    <div className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center">
      <p className="text-3xl" aria-hidden="true">🌱</p>
      <p className="mt-2 text-sm text-[var(--muted)]">
        The feed fills up with real posts once people start sharing. Be the first — sign in and say hello.
      </p>
    </div>
  )
}
