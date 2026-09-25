import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { RealNearby } from '../components/RealNearby'
import { Link } from 'react-router-dom'

export function NearbyPage() {
  const authUser = useAuthStore((s) => s.user)
  const geo = useAppStore((s) => s.geo)

  if (authUser) {
    return (
      <div className="mx-auto max-w-3xl space-y-5 p-4 sm:p-6">
        <h1 className="text-2xl font-bold">People near you</h1>
        <RealNearby />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5 p-4 sm:p-6">
      <h1 className="text-2xl font-bold">People near you</h1>

      <div className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
        <div className="flex flex-wrap items-center gap-3">
          <span
            className={`flex h-10 w-10 items-center justify-center rounded-full text-lg ${
              geo.status === 'granted' ? 'bg-emerald-500/15 text-emerald-400' : 'bg-[var(--accent-soft)]'
            }`}
            aria-hidden="true"
          >
            📍
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium">Nearby discovery, done privately</p>
            <p className="text-xs text-[var(--muted)]">
              Share your location once to see who's around — and be seen. Only approximate distance is
              ever shown, never coordinates, and you can hide again any time.
            </p>
          </div>
        </div>
      </div>

      <div className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center">
        <p className="text-3xl" aria-hidden="true">🛡️</p>
        <p className="mt-2 text-sm text-[var(--muted)]">
          Sign in to see who's nearby. Your location is stored server-side and never shown to anyone.
        </p>
        <div className="mt-3 flex items-center justify-center gap-2">
          <Link to="/register" className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)]">
            Create account
          </Link>
          <Link to="/login" className="rounded-xl border border-[var(--border)] px-4 py-2 text-sm font-medium hover:bg-[var(--surface-2)]">
            Sign in
          </Link>
        </div>
      </div>
    </div>
  )
}
