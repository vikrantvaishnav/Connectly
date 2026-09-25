import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiErrorMessage } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { Avatar } from '../components/Avatar'

interface ConnUser {
  id: number
  username: string
  firstName: string | null
  lastName: string | null
  profileImage: string | null
}

interface ConnDto {
  id: number
  user: ConnUser
  direction: 'incoming' | 'outgoing'
  status: 'PENDING' | 'ACCEPTED'
  createdAt: string
}

type Tab = 'requests' | 'sent' | 'matches'

function nameOf(u: ConnUser) {
  return [u.firstName, u.lastName].filter(Boolean).join(' ') || u.username
}

/**
 * Requests & Matches — the dating-app heart. Incoming connect requests you can
 * accept or decline, requests you've sent, and your matches (accepted) with a
 * one-tap "Say hi" into a real DM thread.
 */
export function RequestsPage() {
  const me = useAuthStore((s) => s.user)
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('requests')

  const filter = tab === 'requests' ? 'incoming' : tab === 'sent' ? 'outgoing' : 'all'

  const list = useQuery({
    queryKey: ['connections', filter],
    queryFn: async () => (await api.get<ConnDto[]>(`/connections?filter=${filter}`)).data,
    enabled: !!me,
  })

  const summary = useQuery({
    queryKey: ['connections-summary'],
    queryFn: async () =>
      (await api.get<{ incoming: number; outgoing: number; matches: number }>('/connections/summary')).data,
    enabled: !!me,
  })

  // Follow requests on my private account (Instagram-style).
  const followReqs = useQuery({
    queryKey: ['follow-requests'],
    queryFn: async () =>
      (await api.get<{ id: number; userId: number; username: string; firstName: string | null; lastName: string | null; image: string | null }[]>('/follow-requests')).data,
    enabled: !!me,
  })

  const acceptFollow = useMutation({
    mutationFn: (id: number) => api.post(`/follow-requests/${id}/accept`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['follow-requests'] })
      pushToast('Follower approved ✓', '✅')
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })
  const declineFollow = useMutation({
    mutationFn: (id: number) => api.delete(`/follow-requests/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['follow-requests'] }),
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['connections'] })
    queryClient.invalidateQueries({ queryKey: ['connections-summary'] })
    queryClient.invalidateQueries({ queryKey: ['nearby'] })
    queryClient.invalidateQueries({ queryKey: ['discover'] })
  }

  const accept = useMutation({
    mutationFn: (id: number) => api.post(`/connections/requests/${id}/accept`),
    onSuccess: () => { pushToast("It's a match! 💘", '💘'); invalidateAll() },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const decline = useMutation({
    mutationFn: (id: number) => api.delete(`/connections/requests/${id}`),
    onSuccess: () => { pushToast('Dismissed', '🗑️'); invalidateAll() },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const sayHi = useMutation({
    mutationFn: async (userId: number) =>
      (await api.post<{ id: number }>('/conversations', { userId })).data,
    onSuccess: (conv) => navigate(`/messages/${conv.id}`),
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  if (!me) {
    return (
      <div className="mx-auto max-w-xl p-6 text-center text-sm text-[var(--muted)]">
        <Link to="/login" className="text-indigo-400 hover:underline">Sign in</Link> to see your matches.
      </div>
    )
  }

  const rows = list.data ?? []
  const tabs: { key: Tab; label: string; count?: number }[] = [
    { key: 'requests', label: 'Requests', count: summary.data?.incoming },
    { key: 'sent', label: 'Sent', count: summary.data?.outgoing },
    { key: 'matches', label: 'Matches', count: summary.data?.matches },
  ]

  return (
    <div className="mx-auto max-w-xl space-y-5 p-4 sm:p-6">
      <div>
        <h1 className="text-2xl font-bold">Requests & Matches 💘</h1>
        <p className="text-sm text-[var(--muted)]">Mutual connections only — nobody gets a chat they didn't accept.</p>
      </div>

      {followReqs.data && followReqs.data.length > 0 && (
        <section className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-[var(--muted)]">
            Follow requests · {followReqs.data.length}
          </h2>
          <div className="space-y-3">
            {followReqs.data.map((r) => (
              <div key={r.id} className="flex items-center gap-3">
                <Link to={`/profile/${r.username}`}>
                  <Avatar name={[r.firstName, r.lastName].filter(Boolean).join(' ') || r.username} id={String(r.userId)} size="md" src={r.image} />
                </Link>
                <Link to={`/profile/${r.username}`} className="min-w-0 flex-1 truncate font-medium hover:underline">
                  {[r.firstName, r.lastName].filter(Boolean).join(' ') || r.username}
                </Link>
                <button
                  onClick={() => acceptFollow.mutate(r.id)}
                  disabled={acceptFollow.isPending}
                  className="rounded-xl bg-[var(--accent)] px-3 py-1.5 text-xs font-medium text-white hover:bg-[var(--accent-hover)]"
                >
                  Confirm
                </button>
                <button
                  onClick={() => declineFollow.mutate(r.id)}
                  disabled={declineFollow.isPending}
                  className="rounded-xl bg-[var(--surface-2)] px-3 py-1.5 text-xs font-medium text-[var(--muted)] hover:bg-rose-500/10"
                >
                  Delete
                </button>
              </div>
            ))}
          </div>
        </section>
      )}

      <div className="flex gap-2">
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`flex-1 rounded-xl px-3 py-2 text-sm font-medium transition-colors ${
              tab === t.key ? 'bg-[var(--accent)] text-white' : 'bg-[var(--surface-2)] text-[var(--muted)] hover:bg-[var(--accent-soft)]'
            }`}
          >
            {t.label}
            {t.count ? <span className="ml-1.5 rounded-full bg-black/15 px-1.5 py-0.5 text-[10px]">{t.count}</span> : null}
          </button>
        ))}
      </div>

      {list.isPending && <div className="h-24 animate-pulse rounded-2xl bg-[var(--surface)]" />}

      {list.isError && (
        <p className="rounded-2xl border border-rose-500/40 p-4 text-sm text-rose-400">{apiErrorMessage(list.error)}</p>
      )}

      {!list.isPending && rows.length === 0 && (
        <div className="rounded-2xl border border-dashed border-[var(--border)] p-12 text-center">
          <p className="text-4xl" aria-hidden="true">{tab === 'matches' ? '💞' : '📭'}</p>
          <p className="mt-3 text-sm text-[var(--muted)]">
            {tab === 'requests' && 'No pending requests. Go say hi to someone on Discover.'}
            {tab === 'sent' && "You haven't sent any requests yet."}
            {tab === 'matches' && 'No matches yet — connect with someone and they become a match.'}
          </p>
          <Link to="/discover" className="mt-3 inline-block rounded-xl bg-gradient-to-r from-rose-500 to-pink-500 px-4 py-2 text-sm font-medium text-white">
            💘 Open Discover
          </Link>
        </div>
      )}

      <div className="space-y-3">
        {rows.map((c) => (
          <div key={c.id} className="flex flex-wrap items-center gap-3 rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
            <Link to={`/profile/${c.user.username}`}>
              <Avatar name={nameOf(c.user)} id={String(c.user.id)} size="lg" src={c.user.profileImage} />
            </Link>
            <div className="min-w-0 flex-1">
              <Link to={`/profile/${c.user.username}`} className="font-semibold hover:underline">
                {nameOf(c.user)}
              </Link>
              <p className="text-xs text-[var(--muted)]">@{c.user.username}</p>
              <p className="mt-0.5 text-[11px] text-[var(--muted)]">
                {c.status === 'ACCEPTED'
                  ? "You're connected ✓"
                  : c.direction === 'incoming'
                    ? 'Wants to connect with you'
                    : 'Waiting for them to accept'}
              </p>
            </div>
            <div className="flex shrink-0 flex-wrap items-center gap-2">
              {c.status === 'ACCEPTED' ? (
                <button
                  onClick={() => sayHi.mutate(c.user.id)}
                  disabled={sayHi.isPending}
                  className="rounded-xl bg-gradient-to-r from-rose-500 to-pink-500 px-4 py-2 text-sm font-medium text-white transition-all hover:from-rose-400 hover:to-pink-400 active:scale-95 disabled:opacity-50"
                >
                  💬 Say hi
                </button>
              ) : c.direction === 'incoming' ? (
                <>
                  <button
                    onClick={() => accept.mutate(c.id)}
                    disabled={accept.isPending}
                    className="rounded-xl bg-gradient-to-r from-rose-500 to-pink-500 px-4 py-2 text-sm font-medium text-white transition-all hover:from-rose-400 hover:to-pink-400 active:scale-95 disabled:opacity-50"
                  >
                    ❤️ Accept
                  </button>
                  <button
                    onClick={() => decline.mutate(c.id)}
                    disabled={decline.isPending}
                    className="rounded-xl border border-[var(--border)] px-3 py-2 text-sm text-[var(--muted)] hover:bg-[var(--surface-2)]"
                  >
                    Decline
                  </button>
                </>
              ) : (
                <button
                  onClick={() => decline.mutate(c.id)}
                  disabled={decline.isPending}
                  className="rounded-xl border border-[var(--border)] px-3 py-2 text-sm text-[var(--muted)] hover:bg-[var(--surface-2)]"
                >
                  Cancel
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
