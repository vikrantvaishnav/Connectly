import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAppStore } from '../store/appStore'
import { Avatar } from '../components/Avatar'
import { CURRENT_USER_ID } from '../data/demo'

interface ChannelMsg {
  id: string
  userId: string
  text: string
  at: string
}

/** Demo channel messages per community (client-side only). */
const seedChannelMessages: Record<string, ChannelMsg[]> = {
  ch1: [
    { id: 'cm1', userId: 'u1', text: 'Welcome to Mumbai Tech Hub! Introduce yourself 👋', at: '2026-09-22T10:00:00Z' },
    { id: 'cm2', userId: 'u4', text: 'Hi! Frontend dev, looking for a hackathon team.', at: '2026-09-22T10:02:00Z' },
    { id: 'cm3', userId: 'u7', text: 'Data analyst here. Someone said there’s free pizza at meetups?', at: '2026-09-22T10:05:00Z' },
  ],
  ch2: [
    { id: 'cm4', userId: 'u2', text: 'Senior product designer @ fintech startup — DM me for a referral.', at: '2026-09-22T09:00:00Z' },
    { id: 'cm5', userId: 'u1', text: 'Backend roles open at my company too, Spring Boot + MySQL.', at: '2026-09-22T09:30:00Z' },
  ],
  ch3: [
    { id: 'cm6', userId: 'u1', text: 'PSA: Virtual threads are not a silver bullet for blocking JDBC calls.', at: '2026-09-23T06:00:00Z' },
    { id: 'cm7', userId: 'u7', text: 'Found this the hard way last week 😅', at: '2026-09-23T06:15:00Z' },
  ],
  ch6: [
    { id: 'cm8', userId: 'u2', text: 'New dashboard exploration — feedback welcome!', at: '2026-09-22T14:00:00Z' },
  ],
  ch7: [
    { id: 'cm9', userId: 'u8', text: 'Rule of thumb: specific praise, kind critique.', at: '2026-09-21T11:00:00Z' },
  ],
  ch9: [
    { id: 'cm10', userId: 'u3', text: 'Sunday 6am: Marine Drive loop, 15k, easy pace. Who’s in?', at: '2026-09-22T18:00:00Z' },
  ],
  ch11: [
    { id: 'cm11', userId: 'u6', text: 'New misal place near Dadar. 4.8/5. Fight me.', at: '2026-09-23T05:00:00Z' },
  ],
}

export function CommunityDetailPage() {
  const { communityId } = useParams()
  const communities = useAppStore((s) => s.communities)
  const toggleCommunity = useAppStore((s) => s.toggleCommunity)
  const users = useAppStore((s) => s.users)
  const community = communities.find((c) => c.id === communityId)

  const textChannels = community?.channels.filter((c) => c.kind === 'text') ?? []
  const voiceChannels = community?.channels.filter((c) => c.kind === 'voice') ?? []
  const [activeChannelId, setActiveChannelId] = useState(textChannels[0]?.id ?? '')

  const [messages, setMessages] = useState<Record<string, ChannelMsg[]>>(seedChannelMessages)
  const [draft, setDraft] = useState('')

  if (!community) {
    return (
      <div className="p-10 text-center">
        <p className="text-sm text-[var(--muted)]">Community not found.</p>
        <Link to="/communities" className="mt-2 inline-block text-sm text-indigo-400 hover:underline">
          ← Back to communities
        </Link>
      </div>
    )
  }

  const activeChannel = community.channels.find((c) => c.id === activeChannelId) ?? textChannels[0]
  const channelMsgs = messages[activeChannel.id] ?? []

  const send = (e: FormEvent) => {
    e.preventDefault()
    const t = draft.trim()
    if (!t) return
    setMessages((prev) => ({
      ...prev,
      [activeChannel.id]: [
        ...(prev[activeChannel.id] ?? []),
        { id: `cm${Date.now()}`, userId: CURRENT_USER_ID, text: t, at: new Date().toISOString() },
      ],
    }))
    setDraft('')
  }

  return (
    <div className="flex h-[calc(100vh-3.5rem)] overflow-hidden">
      {/* Channel sidebar */}
      <aside className="flex w-56 shrink-0 flex-col border-r border-[var(--border)] bg-[var(--surface)]">
        <div className={`flex items-center gap-2 bg-gradient-to-br p-4 ${community.color}`}>
          <span className="text-2xl" aria-hidden="true">{community.icon}</span>
          <div className="min-w-0">
            <p className="truncate text-sm font-bold text-white">{community.name}</p>
            <p className="text-[11px] text-white/80">{community.members.toLocaleString()} members</p>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-3">
          <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-[var(--muted)]">Text channels</p>
          {textChannels.map((ch) => (
            <button
              key={ch.id}
              onClick={() => setActiveChannelId(ch.id)}
              className={`flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm transition-colors ${
                activeChannel?.id === ch.id ? 'bg-[var(--accent-soft)] font-medium text-indigo-400' : 'text-[var(--muted)] hover:bg-[var(--surface-2)]'
              }`}
            >
              <span aria-hidden="true">#</span>
              <span className="truncate">{ch.name}</span>
            </button>
          ))}

          {voiceChannels.length > 0 && (
            <>
              <p className="px-2 pb-1 pt-4 text-[11px] font-semibold uppercase tracking-wide text-[var(--muted)]">Voice channels</p>
              {voiceChannels.map((ch) => (
                <button
                  key={ch.id}
                  onClick={() => { useAppStore.getState().pushToast('Sign in to join voice rooms 🔊'); window.location.href = '/login' }}
                  className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-[var(--muted)] transition-colors hover:bg-[var(--surface-2)]"
                >
                  <span aria-hidden="true">🔊</span>
                  <span className="truncate">{ch.name}</span>
                  {ch.members != null && ch.members > 0 && (
                    <span className="ml-auto flex -space-x-1.5">
                      {users.slice(0, Math.min(ch.members, 3)).map((u) => (
                        <Avatar key={u.id} name={u.name} id={u.id} size="xs" className="ring-2 ring-[var(--surface)]" />
                      ))}
                    </span>
                  )}
                </button>
              ))}
            </>
          )}
        </div>

        <div className="border-t border-[var(--border)] p-3">
          {community.joined ? (
            <button
              onClick={() => toggleCommunity(community.id)}
              className="w-full rounded-lg bg-[var(--surface-2)] px-3 py-2 text-xs text-[var(--muted)] transition-colors hover:text-rose-400"
            >
              Leave community
            </button>
          ) : (
            <button
              onClick={() => toggleCommunity(community.id)}
              className="w-full rounded-lg bg-[var(--accent)] px-3 py-2 text-xs font-medium text-white hover:bg-[var(--accent-hover)]"
            >
              Join community
            </button>
          )}
        </div>
      </aside>

      {/* Channel content */}
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="border-b border-[var(--border)] bg-[var(--surface)] px-4 py-3">
          <p className="text-sm font-semibold">
            <span className="text-[var(--muted)]">#</span> {activeChannel.name}
          </p>
          {activeChannel.topic && <p className="text-xs text-[var(--muted)]">{activeChannel.topic}</p>}
        </div>

        <div className="flex-1 space-y-3 overflow-y-auto p-4">
          {channelMsgs.length === 0 && (
            <p className="pt-10 text-center text-sm text-[var(--muted)]">
              This is the beginning of #{activeChannel.name}. Say hi!
            </p>
          )}
          {channelMsgs.map((m) => {
            const u = users.find((x) => x.id === m.userId)
            const mine = m.userId === CURRENT_USER_ID
            return (
              <div key={m.id} className="flex items-start gap-3">
                {u ? <Avatar name={u.name} id={u.id} size="sm" online={u.online} /> : <Avatar name="You" id={CURRENT_USER_ID} size="sm" />}
                <div>
                  <p className="text-xs font-semibold">
                    {mine ? 'You' : u?.name ?? 'Unknown'}{' '}
                    <span className="font-normal text-[var(--muted)]">
                      {new Date(m.at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </span>
                  </p>
                  <p className="mt-0.5 text-sm">{m.text}</p>
                </div>
              </div>
            )
          })}
        </div>

        {community.joined ? (
          <form onSubmit={send} className="flex gap-2 border-t border-[var(--border)] bg-[var(--surface)] p-3">
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder={`Message #${activeChannel.name}`}
              className="flex-1 rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-4 py-2.5 text-sm outline-none focus:border-[var(--accent)]"
            />
            <button
              type="submit"
              disabled={!draft.trim()}
              className="rounded-xl bg-[var(--accent)] px-4 py-2.5 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)] active:scale-95 disabled:opacity-40"
            >
              Send
            </button>
          </form>
        ) : (
          <div className="border-t border-[var(--border)] bg-[var(--surface)] p-4 text-center text-sm text-[var(--muted)]">
            Join this community to participate.
          </div>
        )}
      </div>
    </div>
  )
}
