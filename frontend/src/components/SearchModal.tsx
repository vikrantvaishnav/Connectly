import { useEffect, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { Avatar } from './Avatar'

interface ApiUserHit { id: number; username: string; firstName: string | null; lastName: string | null; profession: string | null }
interface ApiPostHit { id: number; content: string; author: { id: number; username: string } }

/** Global search across users, posts, communities, tags. Opens with Ctrl+K / ⌘K. */
export function SearchModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const users = useAppStore((s) => s.users)
  const posts = useAppStore((s) => s.posts)
  const communities = useAppStore((s) => s.communities)
  const signedIn = !!useAuthStore((s) => s.user)
  const navigate = useNavigate()

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    if (open) window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null
  // Inner component remounts each open, starting from a fresh query
  return signedIn
    ? <ApiSearchDialog onClose={onClose} navigate={navigate} />
    : <SearchDialog users={users} posts={posts} communities={communities} onClose={onClose} navigate={navigate} />
}

/** Real API search for signed-in users (debounced). */
function ApiSearchDialog({ onClose, navigate }: { onClose: () => void; navigate: (to: string) => void }) {
  const [q, setQ] = useState('')
  const [debounced, setDebounced] = useState('')

  useEffect(() => {
    const t = setTimeout(() => setDebounced(q.trim()), 250)
    return () => clearTimeout(t)
  }, [q])

  const search = useQuery({
    queryKey: ['api-search', debounced],
    queryFn: async () => (await api.get(`/posts/search?q=${encodeURIComponent(debounced)}`)).data,
    enabled: debounced.length > 0,
  })

  const go = (to: string) => { onClose(); navigate(to) }
  const userHits: ApiUserHit[] = search.data?.users ?? []
  const postHits: ApiPostHit[] = search.data?.posts ?? []

  return (
    <div
      className="fixed inset-0 z-[70] flex items-start justify-center bg-black/60 p-4 pt-[12vh] backdrop-blur-sm"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Search"
    >
      <div
        className="animate-fade-up w-full max-w-xl overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--surface)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <input
          autoFocus
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Escape') onClose() }}
          placeholder="Search people and posts…"
          className="w-full border-b border-[var(--border)] bg-transparent px-5 py-4 text-base outline-none placeholder:text-[var(--muted)]"
        />
        <div className="max-h-[50vh] overflow-y-auto p-2">
          {!debounced && (
            <p className="px-3 py-6 text-center text-sm text-[var(--muted)]">
              Search real people and posts from the database
            </p>
          )}
          {debounced && search.isPending && (
            <p className="px-3 py-6 text-center text-sm text-[var(--muted)]">Searching…</p>
          )}
          {debounced && !search.isPending && !userHits.length && !postHits.length && (
            <p className="px-3 py-6 text-center text-sm text-[var(--muted)]">No results for “{q}”</p>
          )}

          {userHits.length > 0 && (
            <Section title="People">
              {userHits.map((u) => (
                <ResultRow key={u.id} onClick={() => go(`/profile/${u.username}`)}>
                  <Avatar name={u.firstName ? `${u.firstName} ${u.lastName ?? ''}` : u.username} id={String(u.id)} size="sm" />
                  <span className="font-medium">{u.firstName ? `${u.firstName} ${u.lastName ?? ''}` : u.username}</span>
                  <span className="text-[var(--muted)]">@{u.username}</span>
                </ResultRow>
              ))}
            </Section>
          )}

          {postHits.length > 0 && (
            <Section title="Posts">
              {postHits.map((p) => (
                <ResultRow key={p.id} onClick={() => go(`/post/${p.id}`)}>
                  <span className="line-clamp-1 flex-1 text-sm">{p.content}</span>
                  <span className="shrink-0 text-xs text-[var(--muted)]">@{p.author.username}</span>
                </ResultRow>
              ))}
            </Section>
          )}
        </div>
        <div className="border-t border-[var(--border)] px-5 py-2 text-xs text-[var(--muted)]">
          Esc to close · results from the live API
        </div>
      </div>
    </div>
  )
}

function SearchDialog({
  users,
  posts,
  communities,
  onClose,
  navigate,
}: {
  users: ReturnType<typeof useAppStore.getState>['users']
  posts: ReturnType<typeof useAppStore.getState>['posts']
  communities: ReturnType<typeof useAppStore.getState>['communities']
  onClose: () => void
  navigate: (to: string) => void
}) {
  const [q, setQ] = useState('')

  const term = q.trim().toLowerCase()
  const match = (s: string) => s.toLowerCase().includes(term)
  const userHits = term ? users.filter((u) => match(u.name) || match(u.username) || u.interests.some(match)).slice(0, 4) : []
  const postHits = term ? posts.filter((p) => match(p.text) || p.tags.some(match)).slice(0, 4) : []
  const communityHits = term ? communities.filter((c) => match(c.name)).slice(0, 3) : []
  const tagHits = term
    ? [...new Set(posts.flatMap((p) => p.tags))].filter(match).slice(0, 5)
    : []
  const empty = !userHits.length && !postHits.length && !communityHits.length && !tagHits.length

  const go = (to: string) => {
    onClose()
    navigate(to)
  }

  return (
    <div
      className="fixed inset-0 z-[70] flex items-start justify-center bg-black/60 p-4 pt-[12vh] backdrop-blur-sm"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Search"
    >
      <div
        className="animate-fade-up w-full max-w-xl overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--surface)] shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <input
          autoFocus
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search people, posts, communities, #tags…"
          className="w-full border-b border-[var(--border)] bg-transparent px-5 py-4 text-base outline-none placeholder:text-[var(--muted)]"
        />
        <div className="max-h-[50vh] overflow-y-auto p-2">
          {!term && (
            <p className="px-3 py-6 text-center text-sm text-[var(--muted)]">
              Try “spring”, “design”, “food”, or a person’s name
            </p>
          )}
          {empty && term && (
            <p className="px-3 py-6 text-center text-sm text-[var(--muted)]">No results for “{q}”</p>
          )}

          {userHits.length > 0 && (
            <Section title="People">
              {userHits.map((u) => (
                <ResultRow key={u.id} onClick={() => go(`/profile/${u.username}`)}>
                  <Avatar name={u.name} id={u.id} size="sm" online={u.online} />
                  <span className="font-medium">{u.name}</span>
                  <span className="text-[var(--muted)]">@{u.username}</span>
                </ResultRow>
              ))}
            </Section>
          )}

          {communityHits.length > 0 && (
            <Section title="Communities">
              {communityHits.map((c) => (
                <ResultRow key={c.id} onClick={() => go(`/communities/${c.id}`)}>
                  <span className="text-lg">{c.icon}</span>
                  <span className="font-medium">{c.name}</span>
                  <span className="text-[var(--muted)]">{c.members.toLocaleString()} members</span>
                </ResultRow>
              ))}
            </Section>
          )}

          {tagHits.length > 0 && (
            <Section title="Tags">
              {tagHits.map((t) => (
                <ResultRow key={t} onClick={() => go(`/explore?tag=${t}`)}>
                  <span className="font-medium text-indigo-400">#{t}</span>
                </ResultRow>
              ))}
            </Section>
          )}

          {postHits.length > 0 && (
            <Section title="Posts">
              {postHits.map((p) => {
                const author = useAppStore.getState().users.find((u) => u.id === p.userId)
                return (
                  <ResultRow key={p.id} onClick={() => go(`/post/${p.id}`)}>
                    <span className="line-clamp-1 flex-1 text-sm">{p.text}</span>
                    <span className="shrink-0 text-xs text-[var(--muted)]">by @{author?.username}</span>
                  </ResultRow>
                )
              })}
            </Section>
          )}
        </div>
        <div className="border-t border-[var(--border)] px-5 py-2 text-xs text-[var(--muted)]">
          Enter to open · Esc to close
        </div>
      </div>
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="mb-2">
      <p className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-[var(--muted)]">{title}</p>
      <div className="space-y-0.5">{children}</div>
    </div>
  )
}

function ResultRow({ children, onClick }: { children: ReactNode; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm hover:bg-[var(--accent-soft)]"
    >
      {children}
    </button>
  )
}
