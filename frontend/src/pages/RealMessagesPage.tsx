import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { Client } from '@stomp/stompjs'
import { api, apiErrorMessage, getAccessToken, wsUrl } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { Avatar } from '../components/Avatar'

interface ConversationSummary {
  id: number
  otherUserId: number
  otherUsername: string
  otherFirstName: string | null
  otherLastName: string | null
  otherProfileImage: string | null
  lastMessage: string | null
  lastMessageAt: string | null
  unread: number
}

interface Reaction {
  emoji: string
  count: number
  mine: boolean
}

interface MessageView {
  id: number
  senderId: number
  senderUsername: string
  content: string
  createdAt: string
  mine: boolean
  reactions: Reaction[]
}

/** Quick reactions offered on hover — the Discord staple. */
const QUICK_REACTIONS = ['❤️', '🔥', '😂', '👍', '😮', '🥰']

function nameOf(c: ConversationSummary) {
  const full = [c.otherFirstName, c.otherLastName].filter(Boolean).join(' ')
  return full || c.otherUsername
}

function timeAgo(iso: string | null) {
  if (!iso) return ''
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 60) return 'now'
  if (s < 3600) return `${Math.floor(s / 60)}m`
  if (s < 86400) return `${Math.floor(s / 3600)}h`
  return `${Math.floor(s / 86400)}d`
}

export function RealMessagesPage() {
  const { conversationId } = useParams()
  const me = useAuthStore((s) => s.user)

  const [convs, setConvs] = useState<ConversationSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [hits, setHits] = useState<{ id: number; username: string; firstName: string | null; lastName: string | null; profileImage: string | null }[]>([])
  const [connected, setConnected] = useState(false)
  const [typingName, setTypingName] = useState<string | null>(null)
  const [typingUntil, setTypingUntil] = useState(0)
  const [tick, setTick] = useState(Date.now())
  const [reactingTo, setReactingTo] = useState<number | null>(null)
  const wsRef = useRef<Client | null>(null)
  const lastTypingPing = useRef(0)

  // Drives the "is typing…" expiry without re-rendering on every keystroke.
  useEffect(() => {
    const t = setInterval(() => setTick(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])

  const loadInbox = useCallback(async () => {
    try {
      const { data } = await api.get<ConversationSummary[]>('/conversations')
      setConvs(data)
      setError(null)
    } catch (e) {
      setError(apiErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }, [])

  // initial inbox
  useEffect(() => {
    loadInbox()
  }, [loadInbox])

  // authenticated live channel: pings tell us to refetch (REST stays the authority)
  useEffect(() => {
    if (!me?.id) return
    const client = new Client({
      brokerURL: wsUrl(),
      connectHeaders: { Authorization: `Bearer ${getAccessToken() ?? ''}` },
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/user/${me.id}`, (frame) => {
          let ping: { type?: string; conversationId?: number; username?: string } = {}
          try {
            ping = JSON.parse(frame.body) as typeof ping
          } catch {
            /* non-JSON ping — fall through to a plain refetch */
          }
          if (ping.type === 'typing') {
            if (String(ping.conversationId) === String(conversationId)) {
              setTypingName(ping.username ?? 'Someone')
              setTypingUntil(Date.now() + 4_000)
            }
            return
          }
          void loadInbox()
          if (conversationId) void loadMessagesRef.current?.()
        })
      },
      onWebSocketClose: () => setConnected(false),
    })
    client.activate()
    wsRef.current = client
    return () => { client.deactivate(); wsRef.current = null }
  }, [me?.id, loadInbox, conversationId])

  // ---- chat pane state ----
  const [messages, setMessages] = useState<MessageView[]>([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const scrollRef = useRef<HTMLDivElement>(null)
  const loadMessagesRef = useRef<(() => Promise<void>) | null>(null)

  const loadMessages = useCallback(async () => {
    if (!conversationId) return
    try {
      const { data } = await api.get<MessageView[]>(`/conversations/${conversationId}/messages?size=50`)
      setMessages([...data].reverse()) // API returns newest-first; render oldest→newest
      await api.post(`/conversations/${conversationId}/read`)
      void loadInbox()
    } catch {
      /* conversation may have been opened from search but not yet loaded */
    }
  }, [conversationId, loadInbox])

  useEffect(() => { loadMessagesRef.current = loadMessages }, [loadMessages])

  useEffect(() => {
    setMessages([])
    void loadMessages()
  }, [loadMessages])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight })
  }, [messages.length])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const text = draft.trim()
    if (!text || !conversationId) return
    // Optimistic: render my message instantly; the round-trip to Singapore
    // (database save + WebSocket broadcast) replaces it with the real record.
    const temp: MessageView = {
      id: -Date.now(),
      senderId: me?.id ?? 0,
      senderUsername: me?.username ?? 'me',
      content: text,
      createdAt: new Date().toISOString(),
      mine: true,
      reactions: [],
    }
    setMessages((m) => [...m, temp])
    setDraft('')
    setSending(true)
    try {
      const { data } = await api.post<MessageView>(`/conversations/${conversationId}/messages`, { content: text })
      setMessages((m) => m.map((x) => (x.id === temp.id ? data : x)))
      void loadInbox()
    } catch (err) {
      setMessages((m) => m.filter((x) => x.id !== temp.id))
      setDraft(text)
      useAppStore.getState().pushToast(apiErrorMessage(err))
    } finally {
      setSending(false)
    }
  }

  /** Toggle an emoji reaction and adopt the server's authoritative tallies. */
  const react = async (messageId: number, emoji: string) => {
    setReactingTo(null)
    try {
      const { data } = await api.post<Reaction[]>(`/messages/${messageId}/reactions`, { emoji })
      setMessages((m) => m.map((x) => (x.id === messageId ? { ...x, reactions: data } : x)))
    } catch (err) {
      useAppStore.getState().pushToast(apiErrorMessage(err))
    }
  }

  /** Tell the other side we're typing — throttled so a fast typist sends one ping, not 40. */
  const pingTyping = () => {
    if (!conversationId) return
    const t = Date.now()
    if (t - lastTypingPing.current < 3000) return
    lastTypingPing.current = t
    void api.post(`/conversations/${conversationId}/typing`).catch(() => { /* presence is best-effort */ })
  }

  const startChatWith = async (userId: number) => {
    try {
      const { data } = await api.post<ConversationSummary>('/conversations', { userId })
      setHits([])
      setSearch('')
      await loadInbox()
      window.location.hash = '' // keep router history clean
      history.pushState({}, '', `/messages/${data.id}`)
      window.dispatchEvent(new PopStateEvent('popstate'))
    } catch (err) {
      useAppStore.getState().pushToast(apiErrorMessage(err))
    }
  }

  // debounced user search
  useEffect(() => {
    const term = search.trim()
    if (term.length < 2) { setHits([]); return }
    const t = setTimeout(async () => {
      try {
        const { data } = await api.get<{ users: typeof hits }>(`/posts/search?q=${encodeURIComponent(term)}&userLimit=6&postLimit=0`)
        setHits(data.users)
      } catch { setHits([]) }
    }, 250)
    return () => clearTimeout(t)
  }, [search])

  const active = convs.find((c) => String(c.id) === conversationId)
  const isTyping = typingUntil > tick

  return (
    <div className="flex h-[calc(100vh-3.5rem)] overflow-hidden pb-[3.75rem] md:pb-0">
      {/* Conversation list */}
      <div className={`flex w-full flex-col border-r border-[var(--border)] md:w-80 md:shrink-0 ${conversationId ? 'hidden md:flex' : 'flex'}`}>
        <div className="p-4 pb-2">
          <h1 className="mb-3 flex items-center gap-2 text-xl font-bold">
            Messages
            <span
              className={`h-2 w-2 rounded-full ${connected ? 'bg-emerald-500' : 'bg-zinc-400'}`}
              title={connected ? 'Live connection active' : 'Connecting…'}
            />
          </h1>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search people to start a chat…"
            className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)]"
          />
          {hits.length > 0 && (
            <div className="mt-2 divide-y divide-[var(--border)] overflow-hidden rounded-xl border border-[var(--border)] bg-[var(--surface)]">
              {hits.map((u) => (
                <button
                  key={u.id}
                  onClick={() => void startChatWith(u.id)}
                  className="flex w-full items-center gap-3 p-2.5 text-left hover:bg-[var(--surface-2)]"
                >
                  <Avatar name={`${u.firstName ?? ''} ${u.lastName ?? ''}`.trim() || u.username} id={String(u.id)} src={u.profileImage} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold">{[u.firstName, u.lastName].filter(Boolean).join(' ') || u.username}</span>
                    <span className="block truncate text-xs text-[var(--muted)]">@{u.username}</span>
                  </span>
                  <span className="text-xs text-[var(--accent)]">Message</span>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="flex-1 overflow-y-auto px-2 pb-4">
          {convs.map((c) => (
            <button
              key={c.id}
              onClick={() => { history.pushState({}, '', `/messages/${c.id}`); window.dispatchEvent(new PopStateEvent('popstate')) }}
              className={`flex w-full items-center gap-3 rounded-xl p-3 text-left transition-colors ${
                conversationId === String(c.id) ? 'bg-[var(--accent-soft)]' : 'hover:bg-[var(--surface-2)]'
              }`}
            >
              <Avatar name={nameOf(c)} id={String(c.otherUserId)} src={c.otherProfileImage} />
              <span className="min-w-0 flex-1">
                <span className="flex items-center justify-between gap-2">
                  <span className="truncate text-sm font-semibold">{nameOf(c)}</span>
                  {c.unread > 0 && (
                    <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-indigo-500 px-1.5 text-[10px] font-bold text-white">
                      {c.unread}
                    </span>
                  )}
                </span>
                <span className="flex items-center gap-1 text-xs text-[var(--muted)]">
                  {c.lastMessage && <span className="truncate">{c.lastMessage}</span>}
                  {c.lastMessageAt && <span className="ml-auto shrink-0">· {timeAgo(c.lastMessageAt)}</span>}
                </span>
              </span>
            </button>
          ))}
          {!loading && convs.length === 0 && !search && (
            <p className="p-6 text-center text-sm text-[var(--muted)]">
              No conversations yet. Search for someone above to say hi 👋
            </p>
          )}
          {loading && <p className="p-6 text-center text-sm text-[var(--muted)]">Loading…</p>}
          {error && <p className="p-6 text-center text-sm text-rose-500">{error}</p>}
        </div>
      </div>

      {/* Chat pane */}
      {conversationId && active ? (
        <div className="flex min-w-0 flex-1 flex-col">
          {/* header */}
          <div className="flex items-center gap-3 border-b border-[var(--border)] bg-[var(--surface)] px-4 py-3">
            <Avatar name={nameOf(active)} id={String(active.otherUserId)} src={active.otherProfileImage} online={isTyping ? undefined : connected} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold">{nameOf(active)}</p>
              <p className="text-xs text-[var(--muted)]">
                {isTyping ? <span className="text-emerald-400">typing…</span> : `@${active.otherUsername}`}
              </p>
            </div>
          </div>

          {/* messages */}
          <div ref={scrollRef} className="flex-1 space-y-2 overflow-y-auto p-4">
            {messages.map((m) => (
              <div key={m.id} className={`flex flex-col ${m.mine ? 'items-end' : 'items-start'}`}>
                <div className={`group flex max-w-[80%] items-center gap-1 ${m.mine ? 'flex-row-reverse' : 'flex-row'}`}>
                  <button
                    type="button"
                    onClick={() => setReactingTo(reactingTo === m.id ? null : m.id)}
                    aria-label="Add reaction"
                    className="shrink-0 rounded-full px-1 text-xs opacity-0 transition-opacity hover:bg-[var(--surface-2)] group-hover:opacity-100 focus:opacity-100"
                  >
                    😊
                  </button>
                  <div className={`rounded-2xl px-3.5 py-2 text-sm ${
                    m.mine ? 'rounded-br-sm bg-[var(--accent)] text-white' : 'rounded-bl-sm bg-[var(--surface-2)]'
                  }`}>
                    {m.content}
                    <span className={`ml-2 align-baseline text-[10px] ${m.mine ? 'text-white/70' : 'text-[var(--muted)]'}`}>
                      {new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </span>
                  </div>
                </div>

                {reactingTo === m.id && (
                  <div className="mt-1 flex gap-1 rounded-full border border-[var(--border)] bg-[var(--surface)] px-2 py-1 shadow-sm">
                    {QUICK_REACTIONS.map((emoji) => (
                      <button
                        key={emoji}
                        type="button"
                        onClick={() => void react(m.id, emoji)}
                        className="rounded-full px-1 text-base transition-transform hover:scale-125"
                        aria-label={`React ${emoji}`}
                      >
                        {emoji}
                      </button>
                    ))}
                  </div>
                )}

                {m.reactions.length > 0 && (
                  <div className="mt-1 flex flex-wrap gap-1">
                    {m.reactions.map((r) => (
                      <button
                        key={r.emoji}
                        type="button"
                        onClick={() => void react(m.id, r.emoji)}
                        className={`rounded-full px-2 py-0.5 text-xs transition-colors ${
                          r.mine
                            ? 'bg-rose-500/20 text-rose-400 ring-1 ring-rose-500/40'
                            : 'bg-[var(--surface-2)] text-[var(--muted)] hover:bg-[var(--accent-soft)]'
                        }`}
                        title={r.mine ? 'Remove your reaction' : 'React'}
                      >
                        {r.emoji} {r.count}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
            {messages.length === 0 && (
              <p className="pt-10 text-center text-sm text-[var(--muted)]">Say hello 👋</p>
            )}
          </div>

          {isTyping && (
            <div className="flex items-center gap-2 px-4 pb-1 text-xs text-[var(--muted)]">
              <span className="flex gap-0.5" aria-hidden="true">
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-[var(--muted)]" />
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-[var(--muted)] [animation-delay:120ms]" />
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-[var(--muted)] [animation-delay:240ms]" />
              </span>
              {typingName} is typing…
            </div>
          )}

          {/* input */}
          <form onSubmit={submit} className="flex gap-2 border-t border-[var(--border)] bg-[var(--surface)] p-3">
            <input
              value={draft}
              onChange={(e) => { setDraft(e.target.value); pingTyping() }}
              placeholder={`Message ${nameOf(active).split(' ')[0]}…`}
              className="flex-1 rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-4 py-2.5 text-sm outline-none focus:border-[var(--accent)]"
            />
            <button
              type="submit"
              disabled={!draft.trim() || sending}
              className="rounded-xl bg-[var(--accent)] px-4 py-2.5 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)] active:scale-95 disabled:opacity-40"
            >
              Send
            </button>
          </form>
        </div>
      ) : conversationId ? (
        // conversation not in inbox yet (opened fresh from search) — minimal loader
        <div className="flex flex-1 items-center justify-center text-sm text-[var(--muted)]">Loading conversation…</div>
      ) : (
        <div className="hidden flex-1 flex-col items-center justify-center gap-2 text-center md:flex">
          <span className="text-4xl" aria-hidden="true">💬</span>
          <p className="text-sm text-[var(--muted)]">Pick a conversation, or search for someone to start chatting.</p>
          <p className="text-xs text-[var(--muted)]">Real-time delivery over WebSocket 🔴</p>
        </div>
      )}
    </div>
  )
}
