import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, apiErrorMessage } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { useGeolocation } from '../hooks/useGeolocation'
import { Avatar } from './Avatar'

interface NearbyHit {
  id: number
  username: string
  firstName: string | null
  lastName: string | null
  profession: string | null
  distanceKm: number
  followingMe: boolean
}

interface NearbyStatus {
  locationShared: boolean
  discoverable: boolean
}

const RADII = [1, 5, 10, 25, 50]

function displayName(h: NearbyHit): string {
  const name = [h.firstName, h.lastName].filter(Boolean).join(' ')
  return name || h.username
}

/** Real nearby discovery for signed-in users: location sharing + server-side privacy. */
export function RealNearby() {
  const authUser = useAuthStore((s) => s.user)
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()
  const { geo, requestLocation } = useGeolocation()
  const [radius, setRadius] = useState(10)
  const [discoverable, setDiscoverable] = useState<boolean | null>(null)

  const status = useQuery({
    queryKey: ['nearby-status'],
    queryFn: async () => (await api.get<NearbyStatus>('/users/me/location-status')).data,
  })

  const currentDiscoverable = discoverable ?? status.data?.discoverable ?? false

  const results = useQuery({
    queryKey: ['nearby', geo.lat, geo.lng, radius],
    queryFn: async () =>
      (await api.get<NearbyHit[]>(`/nearby?lat=${geo.lat}&lng=${geo.lng}&radiusKm=${radius}`)).data,
    enabled: !!authUser,
  })

  const shareLocation = useMutation({
    mutationFn: () =>
      api.put<NearbyStatus>('/users/me/location', {
        latitude: geo.lat,
        longitude: geo.lng,
        discoverable: true,
      }),
    onSuccess: (res) => {
      setDiscoverable(res.data.discoverable)
      pushToast('Location shared — you are now discoverable', '📍')
      queryClient.invalidateQueries({ queryKey: ['nearby-status'] })
      queryClient.invalidateQueries({ queryKey: ['nearby'] })
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const toggleDiscoverable = useMutation({
    mutationFn: () => api.put<NearbyStatus>('/users/me/discoverability', { discoverable: !currentDiscoverable }),
    onSuccess: (res) => {
      setDiscoverable(res.data.discoverable)
      pushToast(res.data.discoverable ? 'You are visible on Nearby' : 'You are hidden from Nearby', '🛡️')
      queryClient.invalidateQueries({ queryKey: ['nearby-status'] })
      queryClient.invalidateQueries({ queryKey: ['nearby'] })
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  if (!authUser) return null

  const hits = results.data ?? []

  return (
    <div className="space-y-5">
      {/* Location + discoverability panel */}
      <div className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
        <div className="flex flex-wrap items-center gap-3">
          <span
            className={`flex h-10 w-10 items-center justify-center rounded-full text-lg ${
              status.data?.locationShared ? 'bg-emerald-500/15 text-emerald-400' : 'bg-[var(--accent-soft)]'
            }`}
            aria-hidden="true"
          >
            📍
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium">
              {status.data?.locationShared ? 'Location shared with Connectly' : 'Location not shared yet'}
            </p>
            <p className="text-xs text-[var(--muted)]">
              {geo.status === 'granted'
                ? 'Using your real position — the server only ever stores it, never shows it'
                : 'Grant browser location, then share it to appear in Nearby'}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {!status.data?.locationShared ? (
              <button
                onClick={() => { if (geo.status !== 'granted') requestLocation(); shareLocation.mutate() }}
                disabled={shareLocation.isPending}
                className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)] disabled:opacity-50"
              >
                {shareLocation.isPending ? 'Sharing…' : 'Share my location'}
              </button>
            ) : (
              <>
                <button
                  onClick={() => { if (geo.status !== 'granted') requestLocation(); shareLocation.mutate() }}
                  disabled={shareLocation.isPending}
                  className="rounded-xl border border-[var(--border)] px-4 py-2 text-sm font-medium hover:bg-[var(--surface-2)]"
                >
                  Refresh
                </button>
                <button
                  onClick={() => toggleDiscoverable.mutate()}
                  disabled={toggleDiscoverable.isPending}
                  className={`rounded-xl px-4 py-2 text-sm font-medium text-white transition-colors ${
                    currentDiscoverable ? 'bg-emerald-600 hover:bg-emerald-500' : 'bg-[var(--surface-2)] text-[var(--muted)]'
                  }`}
                  aria-pressed={currentDiscoverable}
                >
                  {currentDiscoverable ? '🟢 Discoverable' : '⚪ Hidden'}
                </button>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Radius filter */}
      <div className="flex flex-wrap items-center gap-2">
        {RADII.map((r) => (
          <button
            key={r}
            onClick={() => setRadius(r)}
            className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
              radius === r ? 'bg-[var(--accent)] text-white' : 'bg-[var(--surface-2)] text-[var(--muted)] hover:bg-[var(--accent-soft)]'
            }`}
          >
            {r} km
          </button>
        ))}
      </div>

      {/* Results */}
      <p className="text-sm text-[var(--muted)]">
        {results.isPending ? 'Searching…' : `${hits.length} ${hits.length === 1 ? 'person' : 'people'} within ${radius} km`}
      </p>

      {!status.data?.locationShared && !results.isPending && (
        <div className="rounded-2xl border border-dashed border-[var(--border)] p-8 text-center">
          <p className="text-3xl" aria-hidden="true">📡</p>
          <p className="mt-2 text-sm text-[var(--muted)]">
            Share your location once to see who's around you. You can hide again any time.
          </p>
        </div>
      )}

      {status.data?.locationShared && hits.length === 0 && !results.isPending && (
        <div className="rounded-2xl border border-dashed border-[var(--border)] p-8 text-center">
          <p className="text-3xl" aria-hidden="true">🗺️</p>
          <p className="mt-2 text-sm text-[var(--muted)]">
            Nobody discoverable within {radius} km yet. Try a bigger radius.
          </p>
        </div>
      )}

      <div className="space-y-3">
        {hits.map((h) => (
          <div
            key={h.id}
            className="animate-fade-up flex flex-wrap items-center gap-4 rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4 transition-shadow hover:shadow-md"
          >
            <Link to={`/profile/${h.username}`}>
              <Avatar name={displayName(h)} id={String(h.id)} size="lg" />
            </Link>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <Link to={`/profile/${h.username}`} className="font-semibold hover:underline">
                  {displayName(h)}
                </Link>
                {h.followingMe && (
                  <span className="rounded-full bg-[var(--accent-soft)] px-2 py-0.5 text-[10px] font-medium text-indigo-400">
                    Follows you
                  </span>
                )}
              </div>
              <p className="truncate text-sm text-[var(--muted)]">{h.profession ?? 'Connectly member'}</p>
              <p className="mt-0.5 text-xs font-medium text-emerald-500">
                ~{h.distanceKm.toFixed(1)} km away
              </p>
            </div>
            <Link
              to={`/profile/${h.username}`}
              className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)]"
            >
              View profile
            </Link>
          </div>
        ))}
      </div>
    </div>
  )
}
