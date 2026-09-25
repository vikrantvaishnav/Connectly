import { Link, useParams } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { RealCommunitiesPage } from './RealCommunitiesPage'

export function CommunityDetailPage() {
  const authUser = useAuthStore((s) => s.user)
  const { communityId } = useParams()
  if (authUser) return <RealCommunitiesPage />

  return (
    <div className="mx-auto max-w-xl space-y-4 p-10 text-center">
      <p className="text-3xl" aria-hidden="true">🔒</p>
      <p className="text-sm text-[var(--muted)]">
        {communityId
          ? 'Community channels are member-only. Sign in (or join after creating an account) to see this community.'
          : 'Sign in to browse communities, channels and voice rooms.'}
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
