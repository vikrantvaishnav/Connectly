import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiErrorMessage } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { useGeolocation } from '../hooks/useGeolocation'
import { Avatar } from '../components/Avatar'
import { displayNameOf, type NearbyHit } from '../components/RealNearby'

interface DiscoverCard {
  person: NearbyHit
  sharedInterests: number
  /** The exact tags you both have — highlighted on the card. */
  sharedTags: string[]
  score: number
}

const RADII = [5, 10, 25, 50]

/**
 * Dating-style discovery deck. People are ranked by shared interests and
 * proximity (server-side), people you're already matched with are excluded,
 * and every action is a real API call — Connect goes through the same mutual
 * consent flow as the rest of the app.
 */
export function DiscoverPage() {
  const authUser = useAuthStore((s) => s.user)
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { geo, requestLocation } = useGeolocation()

  const [radius, setRadius] = useState(50)
  const [index, setIndex] = useState(0)

  const deck = useQuery({
    queryKey: ['discover', geo.lat, geo.lng, radius],
    queryFn: async () =>
      (await api.get<DiscoverCard[]>(
        `/discover/suggestions?lat=${geo.lat}&lng=${geo.lng}&radiusKm=${radius}`,
      )).data,
    // Only run with a real granted position — never a fallback.
    enabled: !!authUser && geo.status === 'granted' && geo.lat != null && geo.lng != null,
  })

  const cards = useMemo(() => deck.data ?? [], [deck.data])

  // A refreshed deck restarts at the top.
  useEffect(() => { setIndex(0) }, [deck.data])

  const advance = () => setIndex((i) => Math.min(i + 1, cards.length))

  const connect = useMutation({
    mutationFn: (userId: number) => api.post<{ status: string }>(`/connections/${userId}`),
    onSuccess: (res) => {
      pushToast(res.data.status === 'ACCEPTED' ? "It's a match — you're connected! 💘" : 'Connect request sent 💘', '💘')
      queryClient.invalidateQueries({ queryKey: ['connections-summary'] })
      advance()
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
    mutationFn: () => {
      if (geo.lat == null || geo.lng == null) {
        return Promise.reject(new Error('no-coordinates'))
      }
      return api.put('/users/me/location', {
        latitude: geo.lat, longitude: geo.lng, discoverable: true,
      })
    },
    onSuccess: () => {
      pushToast('Location shared — Discover just got much better', '📍')
      queryClient.invalidateQueries({ queryKey: ['nearby-status'] })
      queryClient.invalidateQueries({ queryKey: ['discover'] })
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  if (!authUser) return null

  const card = cards[index]

  return (
    <div className="mx-auto max-w-xl space-y-5 p-4 sm:p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Discover 💘</h1>
          <p className="text-sm text-[var(--muted)]">People near you, ranked by what you have in common.</p>
        </div>
        <Link to="/requests" className="rounded-xl border border-[var(--border)] px-3 py-2 text-sm font-medium hover:bg-[var(--surface-2)]">
          ❤️ Requests
        </Link>
      </div>

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
        <button
          onClick={() => { if (geo.status !== 'granted') requestLocation(); shareLocation.mutate() }}
          className="ml-auto text-xs text-[var(--muted)] underline hover:text-[var(--text)]"
        >
          Refresh my location
        </button>
      </div>

      {deck.isPending && (
        <div className="h-96 animate-pulse rounded-3xl border border-[var(--border)] bg-[var(--surface)]" />
      )}

      {!deck.isPending && cards.length === 0 && (
        <div className="rounded-3xl border border-dashed border-[var(--border)] p-12 text-center">
          <p className="text-4xl" aria-hidden="true">🌙</p>
          <p className="mt-3 text-sm text-[var(--muted)]">
            Nobody new nearby right now. Share your location, widen the radius, or check back later.
          </p>
        </div>
      )}

      {card && (
        <>
          <article className="animate-fade-up overflow-hidden rounded-3xl border border-[var(--border)] bg-[var(--surface)] shadow-sm">
            <div className="flex items-center justify-center bg-gradient-to-br from-rose-500/15 via-fuchsia-500/10 to-indigo-500/15 py-8">
              <Avatar
                name={displayNameOf(card.person)}
                id={String(card.person.id)}
                size="xl"
                src={card.person.profileImage}
                online={card.person.online}
                ring
              />
            </div>
            <div className="space-y-3 p-5">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-xl font-bold">
                  {displayNameOf(card.person)}
                  {card.person.age != null && <span className="font-normal text-[var(--muted)]">, {card.person.age}</span>}
                </h2>
                {card.person.online && (
                  <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-[10px] font-medium text-emerald-400">● Active now</span>
                )}
              </div>

              <p className="text-sm font-medium text-rose-400">
                {card.person.lookingFor || 'Here to meet someone new'}
              </p>
              <p className="text-xs text-[var(--muted)]">
                {card.person.profession ? `${card.person.profession} · ` : ''}~{card.person.distanceKm.toFixed(1)} km away
              </p>

              {card.person.bio && <p className="text-sm">{card.person.bio}</p>}

              {card.person.interests.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {card.person.interests.map((tag) => {
                    const shared = card.sharedTags.includes(tag)
                    return (
                      <span
                        key={tag}
                        className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                          shared ? 'bg-rose-500 text-white' : 'bg-[var(--surface-2)] text-[var(--muted)]'
                        }`}
                        title={shared ? 'You both like this' : undefined}
                      >
                        {shared ? '✨ ' : ''}{tag}
                      </span>
                    )
                  })}
                </div>
              )}

              {card.sharedInterests > 0 && (
                <p className="text-xs font-medium text-rose-400">
                  You both like {card.sharedInterests} thing{card.sharedInterests === 1 ? '' : 's'} in common ✨
                </p>
              )}

              <div className="flex flex-wrap items-center gap-2 pt-1">
                <button
                  onClick={advance}
                  className="rounded-xl border border-[var(--border)] px-4 py-2.5 text-sm font-medium text-[var(--muted)] transition-colors hover:bg-[var(--surface-2)]"
                >
                  ✕ Skip
                </button>
                <button
                  onClick={() => connect.mutate(card.person.id)}
                  disabled={connect.isPending}
                  className="flex-1 rounded-xl bg-gradient-to-r from-rose-500 to-pink-500 px-4 py-2.5 text-sm font-semibold text-white transition-all hover:from-rose-400 hover:to-pink-400 active:scale-95 disabled:opacity-50"
                >
                  ❤️ Connect
                </button>
                <button
                  onClick={() => sayHi.mutate(card.person.id)}
                  disabled={sayHi.isPending}
                  className="rounded-xl bg-[var(--surface-2)] px-4 py-2.5 text-sm font-medium transition-colors hover:bg-[var(--accent-soft)]"
                >
                  💬 Say hi
                </button>
              </div>

              <Link to={`/profile/${card.person.username}`} className="block text-center text-xs text-[var(--muted)] hover:text-[var(--text)]">
                View full profile
              </Link>
            </div>
          </article>

          <div className="flex items-center justify-between text-xs text-[var(--muted)]">
            <span>{index + 1} of {cards.length}</span>
            <div className="h-1.5 w-40 overflow-hidden rounded-full bg-[var(--surface-2)]">
              <div
                className="h-full rounded-full bg-gradient-to-r from-rose-500 to-pink-500 transition-all"
                style={{ width: `${((index + 1) / cards.length) * 100}%` }}
              />
            </div>
          </div>
        </>
      )}

      {!deck.isPending && cards.length > 0 && index >= cards.length && (
        <div className="rounded-3xl border border-dashed border-[var(--border)] p-10 text-center">
          <p className="text-3xl" aria-hidden="true">🎉</p>
          <p className="mt-2 text-sm text-[var(--muted)]">That's everyone nearby. New people appear as they join.</p>
          <button
            onClick={() => { setIndex(0); void deck.refetch() }}
            className="mt-3 rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)]"
          >
            Start over
          </button>
        </div>
      )}
    </div>
  )
}
