import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiErrorMessage } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { Avatar } from '../components/Avatar'

interface NotificationView {
  id: number
  type: string
  actorId: number | null
  actorUsername: string | null
  actorName: string | null
  actorProfileImage: string | null
  entityType: string | null
  entityId: number | null
  read: boolean
  createdAt: string
}

const KIND_ICON: Record<string, string> = {
  LIKE: '❤️',
  COMMENT: '💬',
  FOLLOW: '👤',
  CONNECTION_REQUEST: '🤝',
  CONNECTION_ACCEPTED: '🎉',
  MESSAGE: '✉️',
}

function kindText(t: string): string {
  switch (t) {
    case 'LIKE': return 'liked your post'
    case 'COMMENT': return 'commented on your post'
    case 'FOLLOW': return 'started following you'
    case 'CONNECTION_REQUEST': return 'sent you a connection request'
    case 'CONNECTION_ACCEPTED': return 'accepted your connection request'
    case 'MESSAGE': return 'sent you a message'
    default: return 'sent you a notification'
  }
}

function timeAgo(iso: string): string {
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 60) return 'just now'
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  return `${Math.floor(h / 24)}d ago`
}

function targetHref(n: NotificationView): string {
  if (n.entityType === 'post' && n.entityId) return `/post/${n.entityId}`
  if (n.actorUsername) return `/profile/${n.actorUsername}`
  return '/notifications'
}

export function RealNotifications() {
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()

  const list = useQuery({
    queryKey: ['real-notifications'],
    queryFn: async () => (await api.get<NotificationView[]>('/notifications?size=50')).data,
    refetchInterval: 30_000,
  })

  const unread = useQuery({
    queryKey: ['real-notifications-unread'],
    queryFn: async () => (await api.get<{ count: number }>('/notifications/unread-count')).data.count,
    refetchInterval: 20_000,
  })

  const markAll = useMutation({
    mutationFn: () => api.post('/notifications/read-all'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['real-notifications'] })
      queryClient.invalidateQueries({ queryKey: ['real-notifications-unread'] })
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const markOne = useMutation({
    mutationFn: (id: number) => api.post(`/notifications/${id}/read`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['real-notifications'] })
      queryClient.invalidateQueries({ queryKey: ['real-notifications-unread'] })
    },
  })

  const unreadCount = unread.data ?? 0

  return (
    <div className="mx-auto max-w-2xl space-y-4 p-4 sm:p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Notifications</h1>
        {unreadCount > 0 && (
          <button
            onClick={() => markAll.mutate()}
            disabled={markAll.isPending}
            className="rounded-lg bg-[var(--surface-2)] px-3 py-1.5 text-xs font-medium text-[var(--muted)] transition-colors hover:text-[var(--text)]"
          >
            Mark all read ({unreadCount})
          </button>
        )}
      </div>

      {list.isPending && <div className="h-24 animate-pulse rounded-2xl bg-[var(--surface)]" />}
      {list.isError && (
        <p className="rounded-2xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-amber-500">
          Couldn't load notifications.
        </p>
      )}

      <div className="space-y-2">
        {list.data?.map((n) => (
          <Link
            key={n.id}
            to={targetHref(n)}
            onClick={() => { if (!n.read) markOne.mutate(n.id) }}
            className={`animate-fade-up flex items-center gap-3 rounded-2xl border p-4 transition-colors hover:bg-[var(--surface-2)] ${
              n.read ? 'border-[var(--border)] bg-[var(--surface)]' : 'border-indigo-500/30 bg-[var(--accent-soft)]'
            }`}
          >
            <span className="text-xl" aria-hidden="true">{KIND_ICON[n.type] ?? '🔔'}</span>
            {n.actorUsername && (
              <Avatar name={n.actorName ?? n.actorUsername} id={String(n.actorId ?? n.id)} size="sm" src={n.actorProfileImage} />
            )}
            <p className="min-w-0 flex-1 text-sm">
              {n.actorName && <span className="font-semibold">{n.actorName}{' '}</span>}
              {kindText(n.type)}
              <span className="ml-1 text-xs text-[var(--muted)]">· {timeAgo(n.createdAt)}</span>
            </p>
            {!n.read && <span className="h-2 w-2 shrink-0 rounded-full bg-indigo-500" />}
          </Link>
        ))}
        {list.data?.length === 0 && (
          <p className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center text-sm text-[var(--muted)]">
            Nothing here yet. Interactions with your posts and profile show up here.
          </p>
        )}
      </div>
    </div>
  )
}

