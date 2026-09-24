import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAppStore } from '../store/appStore'
import { artwork, isArtworkKey } from '../lib/geo'
import type { Post } from '../types/demo'
import { Avatar } from './Avatar'

function timeAgo(iso: string): string {
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 60) return 'just now'
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h`
  return `${Math.floor(h / 24)}d`
}

export function PostCard({ post }: { post: Post }) {
  const users = useAppStore((s) => s.users)
  const liked = useAppStore((s) => s.likedPostIds.includes(post.id))
  const saved = useAppStore((s) => s.savedPostIds.includes(post.id))
  const toggleLike = useAppStore((s) => s.toggleLike)
  const addComment = useAppStore((s) => s.addComment)
  const toggleSave = useAppStore((s) => s.toggleSave)
  const pushToast = useAppStore((s) => s.pushToast)
  const [showComments, setShowComments] = useState(false)
  const [commentText, setCommentText] = useState('')
  const [burst, setBurst] = useState(false)
  const author = users.find((u) => u.id === post.userId)
  if (!author) return null

  const handleLike = () => {
    if (!liked) {
      setBurst(true)
      setTimeout(() => setBurst(false), 900)
    }
    toggleLike(post.id)
  }

  const handleShare = async () => {
    const url = `${window.location.origin}/post/${post.id}`
    try {
      await navigator.clipboard.writeText(url)
      pushToast('Link copied to clipboard', '🔗')
    } catch {
      pushToast(url, '🔗')
    }
  }

  return (
    <article className="animate-fade-up overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--surface)]">
      <div className="flex items-center gap-3 p-4 pb-3">
        <Link to={`/profile/${author.username}`}>
          <Avatar name={author.name} id={author.id} online={author.online} />
        </Link>
        <div className="min-w-0 flex-1">
          <Link to={`/profile/${author.username}`} className="font-semibold hover:underline">
            {author.name}
          </Link>
          <p className="truncate text-xs text-[var(--muted)]">
            @{author.username} · {timeAgo(post.createdAt)}
            {post.location && <> · 📍 {post.location}</>}
          </p>
        </div>
      </div>

      <p className="whitespace-pre-wrap px-4 pb-3 text-[15px] leading-relaxed">{post.text}</p>

      {post.image && isArtworkKey(post.image) && (
        <div
          className={`flex h-64 items-end bg-gradient-to-br p-4 ${artwork[post.image].bg}`}
          role="img"
          aria-label={artwork[post.image].label}
        >
          <span className="rounded-lg bg-black/30 px-2 py-1 text-xs text-white/90 backdrop-blur">
            {artwork[post.image].label}
          </span>
        </div>
      )}

      {post.tags.length > 0 && (
        <div className="flex flex-wrap gap-2 px-4 pt-3">
          {post.tags.map((t) => (
            <Link
              key={t}
              to={`/explore?tag=${t}`}
              className="rounded-full bg-[var(--accent-soft)] px-2.5 py-0.5 text-xs font-medium text-indigo-400 hover:bg-indigo-500/25"
            >
              #{t}
            </Link>
          ))}
        </div>
      )}

      <div className="flex items-center gap-1 p-2">
        <button
          onClick={handleLike}
          className={`relative flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm transition-colors hover:bg-[var(--accent-soft)] ${
            liked ? 'text-rose-500' : 'text-[var(--muted)]'
          }`}
          aria-pressed={liked}
          aria-label={liked ? 'Unlike' : 'Like'}
        >
          <span className={burst ? 'animate-heart-burst' : liked ? 'animate-pop' : ''}>{liked ? '❤️' : '🤍'}</span>
          {post.likes}
        </button>
        <button
          onClick={() => setShowComments((v) => !v)}
          className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm text-[var(--muted)] transition-colors hover:bg-[var(--accent-soft)]"
          aria-expanded={showComments}
        >
          💬 {post.comments.length}
        </button>
        <button
          onClick={handleShare}
          className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm text-[var(--muted)] transition-colors hover:bg-[var(--accent-soft)]"
        >
          🔗 Share
        </button>
        <button
          onClick={() => toggleSave(post.id)}
          className={`ml-auto flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm transition-colors hover:bg-[var(--accent-soft)] ${
            saved ? 'text-amber-400' : 'text-[var(--muted)]'
          }`}
          aria-pressed={saved}
          aria-label={saved ? 'Remove from saved' : 'Save'}
        >
          {saved ? '🔖' : '📑'} {saved ? 'Saved' : 'Save'}
        </button>
      </div>

      {showComments && (
        <div className="border-t border-[var(--border)] bg-[var(--surface-2)] p-4">
          {post.comments.length === 0 && (
            <p className="pb-3 text-sm text-[var(--muted)]">No comments yet — start the conversation!</p>
          )}
          <div className="space-y-3">
            {post.comments.map((c) => {
              const cu = users.find((u) => u.id === c.userId)
              return (
                <div key={c.id} className="flex items-start gap-2.5">
                  <Avatar name={cu?.name ?? '?'} id={c.userId} size="sm" />
                  <div className="min-w-0 flex-1 rounded-xl bg-[var(--surface)] px-3 py-2">
                    <p className="text-xs font-semibold">
                      {cu?.name ?? 'Unknown'}{' '}
                      <span className="font-normal text-[var(--muted)]">· {timeAgo(c.createdAt)}</span>
                    </p>
                    <p className="mt-0.5 text-sm">{c.text}</p>
                  </div>
                </div>
              )
            })}
          </div>
          <form
            className="mt-3 flex gap-2"
            onSubmit={(e) => {
              e.preventDefault()
              const t = commentText.trim()
              if (!t) return
              addComment(post.id, t)
              setCommentText('')
            }}
          >
            <input
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder="Add a comment…"
              className="flex-1 rounded-xl border border-[var(--border)] bg-[var(--surface)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)]"
            />
            <button
              type="submit"
              className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-hover)]"
            >
              Post
            </button>
          </form>
        </div>
      )}
    </article>
  )
}
