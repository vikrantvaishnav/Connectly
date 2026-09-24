import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { useGeolocation } from '../hooks/useGeolocation'
import { distanceKm, formatDistance } from '../lib/geo'
import { CURRENT_USER_ID } from '../data/demo'
import { Avatar } from '../components/Avatar'
import { ConnectButton } from '../components/ConnectButton'
import { RealNearby } from '../components/RealNearby'

const RADII = [1, 5, 10, 25, 50]
const ALL_INTERESTS = [
  'java', 'spring', 'react', 'typescript', 'design', 'fitness', 'running',
  'food', 'music', 'books', 'photography', 'sql', 'python', 'travel',
]

export function NearbyPage() {
  const authUser = useAuthStore((s) => s.user)
  const users = useAppStore((s) => s.users)
  const followedUserIds = useAppStore((s) => s.followedUserIds)
  const { geo, requestLocation } = useGeolocation()
  const [radius, setRadius] = useState(10)
  const [interest, setInterest] = useState<string | null>(null)
  const [onlineOnly, setOnlineOnly] = useState(false)

  const people = useMemo(() => {
    return users
      .filter((u) => u.id !== CURRENT_USER_ID)
      .map((u) => ({ user: u, km: distanceKm(geo.lat, geo.lng, u.lat, u.lng) }))
      .filter(({ km }) => km <= radius)
      .filter(({ user }) => (interest ? user.interests.includes(interest) : true))
      .filter(({ user }) => (onlineOnly ? user.online : true))
      .sort((a, b) => a.km - b.km)
  }, [users, geo.lat, geo.lng, radius, interest, onlineOnly])

  // Signed-in users get the real, privacy-enforced API experience.
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

      {/* Location panel */}
      <div className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
        <div className="flex flex-wrap items-center gap-3">
          <span
            className={`flex h-10 w-10 items-center justify-center rounded-full text-lg ${
              geo.status === 'granted' ? 'bg-emerald-500/15 text-emerald-400' : 'bg-[var(--accent-soft)]'
            } ${geo.status === 'prompt' ? 'animate-pulse-ring' : ''}`}
            aria-hidden="true"
          >
            📍
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium">
              {geo.status === 'granted' && 'Using your real location'}
              {geo.status === 'prompt' && 'Requesting location…'}
              {geo.status === 'denied' && 'Permission denied — using demo location (Powai)'}
              {geo.status === 'unavailable' && 'Location unavailable — using demo location (Powai)'}
              {geo.status === 'idle' && 'Using demo location (Powai, Mumbai)'}
            </p>
            <p className="text-xs text-[var(--muted)]">
              {geo.status === 'granted'
                ? `±${geo.accuracyKm} km accuracy · distances are approximate`
                : 'Grant location access for real distances · we only ever show approximate distance, never coordinates'}
            </p>
          </div>
          <button
            onClick={requestLocation}
            disabled={geo.status === 'prompt'}
            className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)] active:scale-95 disabled:opacity-50"
          >
            {geo.status === 'granted' ? 'Refresh location' : 'Use my location'}
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-2">
        {RADII.map((r) => (
          <button
            key={r}
            onClick={() => setRadius(r)}
            className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
              radius === r ? 'bg-[var(--accent)] text-white' : 'bg-[var(--surface-2)] text-[var(--muted)] hover:bg-[var(--accent-soft)]'
            }`}
          >
            {r >= 1000 ? `${r / 1000}k` : r} km
          </button>
        ))}
        <label className="ml-auto flex cursor-pointer items-center gap-2 text-sm text-[var(--muted)]">
          <input
            type="checkbox"
            checked={onlineOnly}
            onChange={(e) => setOnlineOnly(e.target.checked)}
            className="h-4 w-4 accent-indigo-500"
          />
          Online now
        </label>
      </div>

      <div className="flex flex-wrap gap-2">
        <button
          onClick={() => setInterest(null)}
          className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
            !interest ? 'bg-[var(--accent-soft)] text-indigo-400' : 'bg-[var(--surface-2)] text-[var(--muted)]'
          }`}
        >
          All interests
        </button>
        {ALL_INTERESTS.map((i) => (
          <button
            key={i}
            onClick={() => setInterest(interest === i ? null : i)}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              interest === i ? 'bg-[var(--accent-soft)] text-indigo-400' : 'bg-[var(--surface-2)] text-[var(--muted)] hover:bg-[var(--accent-soft)]'
            }`}
          >
            {i}
          </button>
        ))}
      </div>

      {/* Results */}
      <div className="space-y-3">
        <p className="text-sm text-[var(--muted)]">
          {people.length} {people.length === 1 ? 'person' : 'people'} within {radius} km
          {interest && <> interested in <strong>{interest}</strong></>}
        </p>

        {people.length === 0 && (
          <div className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center">
            <p className="text-3xl" aria-hidden="true">🗺️</p>
            <p className="mt-2 text-sm text-[var(--muted)]">
              Nobody here yet. Try a bigger radius or clear the interest filter.
            </p>
          </div>
        )}

        {people.map(({ user: u, km }) => (
          <div
            key={u.id}
            className="animate-fade-up flex flex-wrap items-center gap-4 rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4 transition-shadow hover:shadow-md"
          >
            <Link to={`/profile/${u.username}`}>
              <Avatar name={u.name} id={u.id} size="lg" online={u.online} />
            </Link>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <Link to={`/profile/${u.username}`} className="font-semibold hover:underline">
                  {u.name}
                </Link>
                {followedUserIds.includes(u.id) && (
                  <span className="rounded-full bg-[var(--accent-soft)] px-2 py-0.5 text-[10px] font-medium text-indigo-400">
                    Following
                  </span>
                )}
              </div>
              <p className="truncate text-sm text-[var(--muted)]">
                {u.profession} · {u.city}
              </p>
              <p className="mt-0.5 text-xs font-medium text-emerald-500">{formatDistance(km)}</p>
              <div className="mt-1.5 flex flex-wrap gap-1.5">
                {u.interests.slice(0, 4).map((t) => (
                  <span key={t} className="rounded-full bg-[var(--surface-2)] px-2 py-0.5 text-[10px] text-[var(--muted)]">
                    {t}
                  </span>
                ))}
              </div>
            </div>
            <ConnectButton userId={u.id} />
          </div>
        ))}
      </div>
    </div>
  )
}
