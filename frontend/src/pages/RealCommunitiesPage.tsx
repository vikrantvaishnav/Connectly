import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiErrorMessage } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { Avatar } from '../components/Avatar'

interface CommunityView {
  id: number
  name: string
  slug: string
  description: string | null
  icon: string | null
  ownerUsername: string
  memberCount: number
  isMember: boolean
  myRole: string | null
  createdAt: string
}

interface ChannelView { id: number; name: string; topic: string | null }

interface MemberView { userId: number; username: string; name: string; role: string }

interface ChannelMessageView {
  id: number
  senderId: number
  senderUsername: string
  senderName: string
  content: string
  createdAt: string
  mine: boolean
}

export function RealCommunitiesPage() {
  const queryClient = useQueryClient()
  const pushToast = useAppStore((s) => s.pushToast)
  const [selected, setSelected] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  const list = useQuery({
    queryKey: ['communities'],
    queryFn: async () => (await api.get<CommunityView[]>('/communities')).data,
  })

  const create = useMutation({
    mutationFn: () => api.post<CommunityView>('/communities', { name, description, icon: '💬' }),
    onSuccess: (res) => {
      setCreating(false)
      setName('')
      setDescription('')
      queryClient.invalidateQueries({ queryKey: ['communities'] })
      pushToast(`Community "${res.data.name}" created 🎉`, '✅')
      setSelected(res.data.id)
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const join = useMutation({
    mutationFn: (id: number) => api.post<CommunityView>(`/communities/${id}/join`),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['communities'] })
      pushToast(`Joined ${res.data.name}`, '✅')
      setSelected(res.data.id)
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const leave = useMutation({
    mutationFn: (id: number) => api.delete(`/communities/${id}/join`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['communities'] })
      setSelected(null)
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  if (selected != null) {
    return <CommunityDetail id={selected} onBack={() => setSelected(null)} onLeave={() => leave.mutate(selected)} />
  }

  return (
    <div className="mx-auto max-w-4xl space-y-5 p-4 sm:p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Communities</h1>
        <button
          onClick={() => setCreating(v => !v)}
          className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)]"
        >
          {creating ? 'Cancel' : '+ Create'}
        </button>
      </div>

      {creating && (
        <form
          onSubmit={(e) => { e.preventDefault(); if (name.trim().length >= 3) create.mutate() }}
          className="space-y-2 rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4"
        >
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Community name (3-60 chars)"
            maxLength={60}
            className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)]"
          />
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="What is it about?"
            rows={2}
            maxLength={500}
            className="w-full resize-none rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)]"
          />
          <button
            type="submit"
            disabled={name.trim().length < 3 || create.isPending}
            className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)] disabled:opacity-40"
          >
            {create.isPending ? 'Creating…' : 'Create community'}
          </button>
        </form>
      )}

      {list.isPending && <div className="h-24 animate-pulse rounded-2xl bg-[var(--surface)]" />}
      <div className="grid gap-3 sm:grid-cols-2">
        {list.data?.map((c) => (
          <div key={c.id} className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
            <button onClick={() => setSelected(c.id)} className="w-full text-left">
              <div className="flex items-start gap-3">
                <span className="text-2xl" aria-hidden="true">{c.icon ?? '💬'}</span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">{c.name}</p>
                  <p className="line-clamp-2 text-xs text-[var(--muted)]">{c.description}</p>
                </div>
              </div>
            </button>
            <div className="mt-3 flex items-center justify-between">
              <span className="text-xs text-[var(--muted)]">{c.memberCount} member{c.memberCount === 1 ? '' : 's'}</span>
              {c.isMember ? (
                <button onClick={() => setSelected(c.id)} className="rounded-lg bg-[var(--accent-soft)] px-3 py-1.5 text-xs font-medium text-indigo-400">
                  Open
                </button>
              ) : (
                <button onClick={() => join.mutate(c.id)} disabled={join.isPending} className="rounded-lg bg-[var(--accent)] px-3 py-1.5 text-xs font-medium text-white disabled:opacity-40">
                  Join
                </button>
              )}
            </div>
          </div>
        ))}
        {list.data?.length === 0 && !list.isPending && (
          <p className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center text-sm text-[var(--muted)] sm:col-span-2">
            No communities yet. Create the first one!
          </p>
        )}
      </div>
    </div>
  )
}

function CommunityDetail({ id, onBack, onLeave }: { id: number; onBack: () => void; onLeave: () => void }) {
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()
  const [channelId, setChannelId] = useState<number | null>(null)
  const [channelName, setChannelName] = useState('#general')
  const [newChannel, setNewChannel] = useState('')
  const [showMembers, setShowMembers] = useState(false)

  const community = useQuery({
    queryKey: ['community', id],
    queryFn: async () => (await api.get<CommunityView>(`/communities/${id}`)).data,
  })

  const channels = useQuery({
    queryKey: ['community-channels', id],
    queryFn: async () => (await api.get<ChannelView[]>(`/communities/${id}/channels`)).data,
  })

  const members = useQuery({
    queryKey: ['community-members', id],
    queryFn: async () => (await api.get<MemberView[]>(`/communities/${id}/members`)).data,
    enabled: showMembers,
  })

  // auto-select first channel when channels load
  useEffect(() => {
    if (channels.data?.length && channelId == null) {
      setChannelId(channels.data[0].id)
      setChannelName('#' + channels.data[0].name)
    }
  }, [channels.data, channelId])

  const canManage = community.data?.myRole === 'OWNER' || community.data?.myRole === 'ADMIN'

  const addChannel = useMutation({
    mutationFn: () => api.post<ChannelView>(`/communities/${id}/channels`, { name: newChannel, topic: '' }),
    onSuccess: (res) => {
      setNewChannel('')
      queryClient.invalidateQueries({ queryKey: ['community-channels', id] })
      pushToast(`Channel #${res.data.name} created`, '✅')
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  if (community.isPending) return <div className="p-10 text-center text-sm text-[var(--muted)]">Loading…</div>
  if (community.isError || !community.data) {
    return (
      <div className="p-10 text-center">
        <p className="text-sm text-[var(--muted)]">Community not found.</p>
        <button onClick={onBack} className="mt-2 text-sm text-indigo-400 hover:underline">← Back</button>
      </div>
    )
  }

  return (
    <div className="flex h-[calc(100vh-3.5rem)] overflow-hidden">
      {/* channel sidebar */}
      <div className="flex w-full flex-col border-r border-[var(--border)] md:w-64 md:shrink-0">
        <div className="border-b border-[var(--border)] p-3">
          <button onClick={onBack} className="mb-2 text-xs text-[var(--muted)] hover:text-[var(--text)]">← All communities</button>
          <p className="truncate text-sm font-bold">{community.data.icon} {community.data.name}</p>
          <p className="text-xs text-[var(--muted)]">{community.data.memberCount} members</p>
        </div>
        <div className="flex-1 overflow-y-auto p-2">
          <p className="px-2 pb-1 text-[10px] font-bold uppercase tracking-wide text-[var(--muted)]">Channels</p>
          {channels.data?.map((ch) => (
            <button
              key={ch.id}
              onClick={() => { setChannelId(ch.id); setChannelName('#' + ch.name) }}
              className={`mb-0.5 flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm ${
                channelId === ch.id ? 'bg-[var(--accent-soft)] font-medium text-indigo-400' : 'text-[var(--muted)] hover:bg-[var(--surface-2)]'
              }`}
            >
              <span aria-hidden="true">#</span> {ch.name}
            </button>
          ))}
          {canManage && (
            <form onSubmit={(e) => { e.preventDefault(); if (newChannel.trim().length >= 2) addChannel.mutate() }} className="mt-2 px-1">
              <input
                value={newChannel}
                onChange={(e) => setNewChannel(e.target.value)}
                placeholder="new-channel"
                maxLength={40}
                className="w-full rounded-lg border border-[var(--border)] bg-[var(--surface-2)] px-2 py-1.5 text-xs outline-none focus:border-[var(--accent)]"
              />
            </form>
          )}
        </div>
        <div className="border-t border-[var(--border)] p-2">
          <button onClick={() => setShowMembers(v => !v)} className="w-full rounded-lg px-2 py-1.5 text-left text-xs text-[var(--muted)] hover:bg-[var(--surface-2)]">
            👥 Members
          </button>
          <button onClick={onLeave} className="w-full rounded-lg px-2 py-1.5 text-left text-xs text-rose-400 hover:bg-rose-500/10">
            🚪 Leave community
          </button>
        </div>
      </div>

      {/* channel chat */}
      <div className="hidden min-w-0 flex-1 flex-col md:flex">
        {channelId != null ? (
          <ChannelChat channelId={channelId} channelName={channelName} />
        ) : (
          <div className="flex flex-1 items-center justify-center text-sm text-[var(--muted)]">Pick a channel</div>
        )}
      </div>

      {/* members panel */}
      {showMembers && (
        <div className="w-64 shrink-0 overflow-y-auto border-l border-[var(--border)] p-3">
          <div className="mb-2 flex items-center justify-between">
            <p className="text-xs font-bold uppercase tracking-wide text-[var(--muted)]">Members</p>
            <button onClick={() => setShowMembers(false)} className="text-xs text-[var(--muted)]">✕</button>
          </div>
          {members.data?.map((m) => (
            <div key={m.userId} className="flex items-center gap-2 rounded-lg p-1.5 hover:bg-[var(--surface-2)]">
              <Avatar name={m.name} id={String(m.userId)} size="xs" />
              <span className="min-w-0 flex-1 truncate text-xs">{m.name}</span>
              <span className="text-[10px] text-[var(--muted)]">{m.role}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function ChannelChat({ channelId, channelName }: { channelId: number; channelName: string }) {
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState('')
  const scrollRef = useRef<HTMLDivElement>(null)

  const messages = useQuery({
    queryKey: ['channel-messages', channelId],
    queryFn: async () => (await api.get<ChannelMessageView[]>(`/communities/channels/${channelId}/messages?size=50`)).data,
    refetchInterval: 5_000,
  })

  const send = useMutation({
    mutationFn: () => api.post<ChannelMessageView>(`/communities/channels/${channelId}/messages`, { content: draft }),
    onSuccess: () => {
      setDraft('')
      queryClient.invalidateQueries({ queryKey: ['channel-messages', channelId] })
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight })
  }, [messages.data?.length])

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (draft.trim()) send.mutate()
  }

  return (
    <>
      <div className="border-b border-[var(--border)] bg-[var(--surface)] px-4 py-3">
        <p className="text-sm font-bold">{channelName}</p>
      </div>
      <div ref={scrollRef} className="flex-1 space-y-2 overflow-y-auto p-4">
        {(messages.data ?? []).slice().reverse().map((m) => (
          <div key={m.id} className={`flex gap-2 ${m.mine ? 'justify-end' : 'justify-start'}`}>
            {!m.mine && <Avatar name={m.senderName} id={String(m.senderId)} size="xs" />}
            <div className={`max-w-[70%] rounded-2xl px-3.5 py-2 text-sm ${
              m.mine ? 'bg-[var(--accent)] text-white' : 'bg-[var(--surface-2)]'
            }`}>
              {!m.mine && <p className="text-[10px] font-semibold opacity-70">{m.senderName}</p>}
              {m.content}
              <span className={`ml-2 text-[10px] ${m.mine ? 'text-white/70' : 'text-[var(--muted)]'}`}>
                {new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </span>
            </div>
          </div>
        ))}
        {messages.data?.length === 0 && (
          <p className="pt-10 text-center text-sm text-[var(--muted)]">Start the conversation 👋</p>
        )}
      </div>
      <form onSubmit={submit} className="flex gap-2 border-t border-[var(--border)] bg-[var(--surface)] p-3">
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={`Message ${channelName}…`}
          className="flex-1 rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-4 py-2.5 text-sm outline-none focus:border-[var(--accent)]"
        />
        <button
          type="submit"
          disabled={!draft.trim() || send.isPending}
          className="rounded-xl bg-[var(--accent)] px-4 py-2.5 text-sm font-medium text-white hover:bg-[var(--accent-hover)] disabled:opacity-40"
        >
          Send
        </button>
      </form>
    </>
  )
}
