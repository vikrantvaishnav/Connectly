import { Link } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { RealCommunitiesPage } from './RealCommunitiesPage'

export function CommunitiesPage() {
  const authUser = useAuthStore((s) => s.user)
  if (authUser) return <RealCommunitiesPage />

  return (
    <div className="mx-auto max-w-xl space-y-4 p-10 text-center">
      <p className="text-3xl" aria-hidden="true">👥</p>
      <p className="text-sm text-[var(--muted)]">
        Communities are live — servers, text channels and voice rooms. Sign in to join one or start your own.
      </p>
      <div className="flex items-center justify-center gap-2">
        <Link to="/register" className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)]">
          Create account
        </Link>
        <Link to="/login" className="rounded-xl border border-[var(--border)] px-4 py-2 text-sm font-medium hover:bg-[var(--surface-2)]">
          Sign in
        </Link>
      </div>
    </div>
  )
}
