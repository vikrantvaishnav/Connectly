import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAppStore, getUser } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { Avatar } from '../components/Avatar'
import { RealMessagesPage } from './RealMessagesPage'

export function MessagesPage() {
  const user = useAuthStore((s) => s.user)
  if (user) return <RealMessagesPage />
  return <DemoMessages />
}

function DemoMessages() {
  const { conversationId } = useParams()
  const conversations = useAppStore((s) => s.conversations)
  const [search, setSearch] = useState('')

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    const list = [...conversations].sort((a, b) => {
      const la = a.messages[a.messages.length - 1]?.at ?? ''
      const lb = b.messages[b.messages.length - 1]?.at ?? ''
      return +new Date(lb) - +new Date(la)
    })
    if (!term) return list
    return list.filter((c) => {
      const u = getUser(c.userId)
      return u?.name.toLowerCase().includes(term) || u?.username.toLowerCase().includes(term)
    })
  }, [conversations, search])

  return (
    <div className="flex h-[calc(100vh-3.5rem)] overflow-hidden">
      {/* Conversation list */}
      <div className={`flex w-full flex-col border-r border-[var(--border)] md:w-80 md:shrink-0 ${conversationId ? 'hidden md:flex' : 'flex'}`}>
        <div className="p-4 pb-2">
          <h1 className="mb-3 text-xl font-bold">Messages</h1>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search conversations…"
            className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)]"
          />
        </div>
        <div className="flex-1 overflow-y-auto px-2 pb-4">
          {filtered.map((c) => {
            const u = getUser(c.userId)
            const last = c.messages[c.messages.length - 1]
            if (!u) return null
            return (
              <Link
                key={c.id}
                to={`/messages/${c.id}`}
                className={`flex items-center gap-3 rounded-xl p-3 transition-colors ${
                  conversationId === c.id ? 'bg-[var(--accent-soft)]' : 'hover:bg-[var(--surface-2)]'
                }`}
              >
                <Avatar name={u.name} id={u.id} online={u.online} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2">
                    <p className="truncate text-sm font-semibold">{u.name}</p>
                    {c.unread > 0 && (
                      <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-indigo-500 px-1.5 text-[10px] font-bold text-white">
                        {c.unread}
                      </span>
                    )}
                  </div>
                  <p className="truncate text-xs text-[var(--muted)]">
                    {last?.kind === 'voice' ? '🎙️ Voice message' : last?.kind === 'file' ? `📎 ${last.fileName}` : last?.text}
                  </p>
                </div>
              </Link>
            )
          })}
          {filtered.length === 0 && (
            <p className="p-6 text-center text-sm text-[var(--muted)]">No conversations found.</p>
          )}
        </div>
      </div>

      {/* Chat pane */}
      {conversationId ? (
        <ChatPane conversationId={conversationId} />
      ) : (
        <div className="hidden flex-1 flex-col items-center justify-center gap-2 text-center md:flex">
          <span className="text-4xl" aria-hidden="true">💬</span>
          <p className="text-sm text-[var(--muted)]">Pick a conversation to start chatting.</p>
          <p className="text-xs text-[var(--muted)]">Replies are simulated in demo mode.</p>
        </div>
      )}
    </div>
  )
}

function ChatPane({ conversationId }: { conversationId: string }) {
  const conversations = useAppStore((s) => s.conversations)
  const sendMessage = useAppStore((s) => s.sendMessage)
  const markRead = useAppStore((s) => s.markConversationRead)
  const [draft, setDraft] = useState('')
  const [awaitingReply, setAwaitingReply] = useState(false)
  const scrollRef = useRef<HTMLDivElement>(null)
  const conv = conversations.find((c) => c.id === conversationId)
  const other = conv ? getUser(conv.userId) : undefined
  const lastMsg = conv?.messages[conv.messages.length - 1]
  // "Typing…" shows from the moment I send until the simulated reply lands
  const themTyping = awaitingReply && lastMsg?.from === 'me'

  useEffect(() => {
    if (conv && conv.unread > 0) markRead(conversationId)
  }, [conv, conversationId, markRead])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
  }, [conv?.messages.length, themTyping])

  if (!conv || !other) return null

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const t = draft.trim()
    if (!t) return
    sendMessage(conversationId, t)
    setDraft('')
    setAwaitingReply(true)
  }

  return (
    <div className="flex min-w-0 flex-1 flex-col">
      {/* Chat header */}
      <div className="flex items-center gap-3 border-b border-[var(--border)] bg-[var(--surface)] px-4 py-3">
        <Link to={`/profile/${other.username}`} className="md:hidden">
          ←
        </Link>
        <Avatar name={other.name} id={other.id} online={other.online} />
        <div className="min-w-0 flex-1">
          <Link to={`/profile/${other.username}`} className="block truncate text-sm font-semibold hover:underline">
            {other.name}
          </Link>
          <p className="text-xs text-[var(--muted)]">
            {themTyping ? 'typing…' : other.online ? '🟢 Online' : 'Offline'}
          </p>
        </div>
        <button
          className="rounded-full border border-[var(--border)] p-2 text-sm hover:bg-[var(--surface-2)]"
          title="Voice call — arrives in Phase 7"
          onClick={() => useAppStore.getState().pushToast('Voice calls arrive in Phase 7 (WebRTC) 🎙️')}
        >
          📞
        </button>
      </div>

      {/* Messages */}
      <div ref={scrollRef} className="flex-1 space-y-2 overflow-y-auto p-4">
        {conv.messages.map((m) => {
          const mine = m.from === 'me'
          return (
            <div key={m.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
              <div
                className={`max-w-[75%] rounded-2xl px-3.5 py-2 text-sm ${
                  mine
                    ? 'rounded-br-sm bg-[var(--accent)] text-white'
                    : 'rounded-bl-sm bg-[var(--surface-2)]'
                }`}
              >
                {m.kind === 'voice' ? (
                  <span className="flex items-center gap-2">
                    🎙️ <span className="inline-block h-4 w-24 rounded bg-current opacity-30" /> {m.duration}s
                  </span>
                ) : m.kind === 'file' ? (
                  <span className="flex items-center gap-2 underline decoration-dotted">📎 {m.fileName}</span>
                ) : (
                  m.text
                )}
                <span className={`ml-2 align-baseline text-[10px] ${mine ? 'text-white/70' : 'text-[var(--muted)]'}`}>
                  {new Date(m.at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
              </div>
            </div>
          )
        })}
        {themTyping && (
          <div className="flex justify-start">
            <div className="flex items-center gap-1 rounded-2xl rounded-bl-sm bg-[var(--surface-2)] px-4 py-3">
              <span className="typing-dot" />
              <span className="typing-dot" />
              <span className="typing-dot" />
            </div>
          </div>
        )}
      </div>

      {/* Input */}
      <form onSubmit={submit} className="flex gap-2 border-t border-[var(--border)] bg-[var(--surface)] p-3">
        <button
          type="button"
          onClick={() => useAppStore.getState().pushToast('Attachments arrive in Phase 5 📎')}
          className="rounded-xl border border-[var(--border)] px-3 text-lg hover:bg-[var(--surface-2)]"
          aria-label="Attach file"
        >
          📎
        </button>
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={`Message ${other.name.split(' ')[0]}…`}
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
    </div>
  )
}
