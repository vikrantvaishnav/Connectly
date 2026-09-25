import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, apiErrorMessage } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { useGeolocation } from '../hooks/useGeolocation'
import { Avatar } from './Avatar'

export type ConnectionStatus = 'NONE' | 'PENDING_INCOMING' | 'PENDING_OUTGOING' | 'ACCEPTED'

export interface NearbyHit {
  id: number
  username: string
  firstName: string | null
  lastName: string | null
  profession: string | null
  profileImage: string | null
  bio: string | null
  interests: string[]
  lookingFor: string | null
  age: number | null
  distanceKm: number
  followingMe: boolean
  online: boolean
  connectionStatus: ConnectionStatus
}

interface NearbyStatus {
  locationShared: boolean
  discoverable: boolean
}

const RADII = [1, 5, 10, 25, 50]

export function displayNameOf(h: { firstName?: string | null; lastName?: string | null; username: string }): string {
  const name = [h.firstName, h.lastName].filter(Boolean).join(' ')
  return name || h.username
}

/** Photo-or-initials avatar for a nearby/discover hit. */
export function PersonAvatar(h: NearbyHit, size: 'md' | 'lg' | 'xl' = 'lg') {
  return <Avatar name={displayNameOf(h)} id={String(h.id)} size={size} src={h.profileImage} online={h.online} />
}

/** Real nearby discovery for signed-in users: location sharing + server-side privacy. */
export function RealNearby() {
  const authUser = useAuthStore((s) => s.user)
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()
  const { geo, requestLocation } = useGeolocation()
  const [radius, setRadius] = useState(10)
  const [discoverable, setDiscoverable] = useState<boolean | null>(null)
  const navigate = useNavigate()

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

  // Relationship state now comes from the server on each card — one request, not four.
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['nearby'] })
    queryClient.invalidateQueries({ queryKey: ['connections-summary'] })
  }

  const connect = useMutation({
    mutationFn: (userId: number) => api.post<{ status: string }>(`/connections/${userId}`),
    onSuccess: (res) => {
      pushToast(res.data.status === 'ACCEPTED' ? "It's a match — you're connected! 💘" : 'Connect request sent 💘', '💘')
      refresh()
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const sayHi = useMutation({
    mutationFn: async (userId: number) =>
      (await api.post<{ id: number }>('/conversations', { userId })).data,
    onSuccess: (conv) => navigate(`/messages/${conv.id}`),
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
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
      refresh()
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const toggleDiscoverable = useMutation({
    mutationFn: () => api.put<NearbyStatus>('/users/me/discoverability', { discoverable: !currentDiscoverable }),
    onSuccess: (res) => {
      setDiscoverable(res.data.discoverable)
      pushToast(res.data.discoverable ? 'You are visible on Nearby' : 'You are hidden from Nearby', '🛡️')
      queryClient.invalidateQueries({ queryKey: ['nearby-status'] })
      refresh()
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  if (!authUser) return null

  const hits = results.data ?? []
  const onlineCount = hits.filter((h) => h.online).length

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
            <p className="flex items-center gap-2 text-sm font-medium">
              {status.data?.locationShared ? 'Location shared with Connectly' : 'Location not shared yet'}
              {onlineCount > 0 && (
                <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-[10px] font-semibold text-emerald-400">
                  ● {onlineCount} active now
                </span>
              )}
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
        <Link
          to="/discover"
          className="ml-auto rounded-full bg-gradient-to-r from-rose-500 to-pink-500 px-4 py-1.5 text-sm font-medium text-white transition-all hover:from-rose-400 hover:to-pink-400"
        >
          💘 Open Discover
        </Link>
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
            className="animate-fade-up flex flex-wrap items-start gap-4 rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4 transition-shadow hover:shadow-md"
          >
            <Link to={`/profile/${h.username}`}>
              {PersonAvatar(h)}
            </Link>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <Link to={`/profile/${h.username}`} className="font-semibold hover:underline">
                  {displayNameOf(h)}
                  {h.age != null && <span className="font-normal text-[var(--muted)]">, {h.age}</span>}
                </Link>
                {h.online && (
                  <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-[10px] font-medium text-emerald-400">
                    ● Active now
                  </span>
                )}
                {h.followingMe && (
                  <span className="rounded-full bg-[var(--accent-soft)] px-2 py-0.5 text-[10px] font-medium text-indigo-400">
                    Follows you
                  </span>
                )}
              </div>
              <p className="truncate text-sm text-[var(--muted)]">
                {h.lookingFor || h.profession || 'Connectly member'}
              </p>
              {h.bio && <p className="mt-0.5 line-clamp-2 text-xs text-[var(--muted)]">{h.bio}</p>}
              {h.interests.length > 0 && (
                <div className="mt-1.5 flex flex-wrap gap-1.5">
                  {h.interests.slice(0, 5).map((t) => (
                    <span key={t} className="rounded-full bg-[var(--surface-2)] px-2 py-0.5 text-[10px] text-[var(--muted)]">
                      {t}
                    </span>
                  ))}
                </div>
              )}
              <p className="mt-1 text-xs font-medium text-emerald-500">~{h.distanceKm.toFixed(1)} km away</p>
            </div>
            <div className="flex shrink-0 flex-col items-stretch gap-1.5">
              {h.connectionStatus === 'ACCEPTED' ? (
                <>
                  <span className="rounded-xl bg-rose-500/10 px-4 py-2 text-center text-xs font-semibold text-rose-500">✓ Connected</span>
                  <button
                    onClick={() => sayHi.mutate(h.id)}
                    disabled={sayHi.isPending}
                    className="rounded-xl bg-gradient-to-r from-rose-500 to-pink-500 px-4 py-2 text-sm font-medium text-white transition-all hover:from-rose-400 hover:to-pink-400 active:scale-95 disabled:opacity-50"
                  >
                    💬 Say hi
                  </button>
                </>
              ) : h.connectionStatus === 'PENDING_INCOMING' ? (
                <button
                  onClick={() => connect.mutate(h.id)}
                  disabled={connect.isPending}
                  className="rounded-xl bg-gradient-to-r from-rose-500 to-pink-500 px-4 py-2 text-sm font-medium text-white transition-all hover:from-rose-400 hover:to-pink-400 active:scale-95 disabled:opacity-50"
                >
                  ❤️ Accept
                </button>
              ) : h.connectionStatus === 'PENDING_OUTGOING' ? (
                <span className="rounded-xl bg-[var(--surface-2)] px-4 py-2 text-center text-xs text-[var(--muted)]">⏳ Requested</span>
              ) : (
                <button
                  onClick={() => connect.mutate(h.id)}
                  disabled={connect.isPending}
                  className="rounded-xl bg-gradient-to-r from-rose-500 to-pink-500 px-4 py-2 text-sm font-medium text-white transition-all hover:from-rose-400 hover:to-pink-400 active:scale-95 disabled:opacity-50"
                >
                  ⚡ Connect
                </button>
              )}
              <Link to={`/profile/${h.username}`} className="text-center text-xs text-[var(--muted)] hover:text-[var(--text)]">
                View profile
              </Link>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
