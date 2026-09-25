import { Link, useParams } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { RealProfilePage } from './RealProfilePage'

export function ProfilePage() {
  const authUser = useAuthStore((s) => s.user)
  const { username } = useParams()
  // Signed-in users always get the real page (own profile or by username).
  if (authUser) return <RealProfilePage own={!username} />

  return (
    <div className="mx-auto max-w-xl space-y-4 p-10 text-center">
      <p className="text-3xl" aria-hidden="true">👤</p>
      <p className="text-sm text-[var(--muted)]">
        {username
          ? <>Profiles live behind sign-in. Create an account or sign in to view <strong>@{username}</strong> and everyone else.</>
          : 'Sign in to see your profile, photo, interests and matches.'}
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
