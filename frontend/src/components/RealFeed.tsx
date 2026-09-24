import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { api, apiErrorMessage } from '../lib/api'
import { useAuthStore } from '../store/authStore'
import { useAppStore } from '../store/appStore'
import { Avatar } from './Avatar'

interface Author {
  id: number
  username: string
  firstName: string | null
  lastName: string | null
}

export interface RealPost {
  id: number
  author: Author
  content: string
  imageUrl: string | null
  visibility: 'PUBLIC' | 'FOLLOWERS' | 'PRIVATE'
  createdAt: string
  likeCount: number
  commentCount: number
  likedByMe: boolean
  canEdit: boolean
}

interface RealPostCardProps { post: RealPost; showSave?: boolean }

function timeAgo(iso: string): string {
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 60) return 'now'
  if (s < 3600) return `${Math.floor(s / 60)}m`
  if (s < 86400) return `${Math.floor(s / 3600)}h`
  return `${Math.floor(s / 86400)}d`
}

function displayName(a: Author): string {
  const name = [a.firstName, a.lastName].filter(Boolean).join(' ')
  return name || a.username
}

/** One real post from the API, with working like/comment/save. */
export function RealPostCard({ post, showSave = true }: RealPostCardProps) {
  const queryClient = useQueryClient()
  const pushToast = useAppStore((s) => s.pushToast)
  const [showComments, setShowComments] = useState(false)
  const [commentText, setCommentText] = useState('')
  const [saved, setSaved] = useState(false)

  const like = useMutation({
    mutationFn: () => api.post(`/posts/${post.id}/like`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['real-feed'] }),
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const save = useMutation({
    mutationFn: () => api.post<{ liked: boolean }>(`/posts/${post.id}/save`),
    onSuccess: (res) => {
      setSaved(res.data.liked)
      pushToast(res.data.liked ? 'Post saved 🔖' : 'Removed from saved', '🔖')
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const comment = useMutation({
    mutationFn: () => api.post(`/posts/${post.id}/comments`, { content: commentText }),
    onSuccess: () => {
      setCommentText('')
      setShowComments(true)
      queryClient.invalidateQueries({ queryKey: ['real-feed'] })
      queryClient.invalidateQueries({ queryKey: ['real-comments', post.id] })
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const comments = useQuery({
    queryKey: ['real-comments', post.id],
    queryFn: async () => (await api.get(`/posts/${post.id}/comments`)).data,
    enabled: showComments,
  })

  return (
    <article className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
      <header className="flex items-center gap-3">
        <Avatar name={displayName(post.author)} id={String(post.author.id)} size="md" />
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{displayName(post.author)}</p>
          <p className="text-xs text-[var(--muted)]">@{post.author.username} · {timeAgo(post.createdAt)}</p>
        </div>
        {post.visibility !== 'PUBLIC' && (
          <span className="ml-auto rounded-full bg-[var(--surface-2)] px-2 py-0.5 text-[10px] font-medium text-[var(--muted)]">
            {post.visibility === 'PRIVATE' ? '🔒 private' : '👥 followers'}
          </span>
        )}
      </header>

      <p className="mt-3 whitespace-pre-wrap text-[15px] leading-relaxed">{post.content}</p>

      {post.imageUrl && (
        <img
          src={post.imageUrl}
          alt="Post image"
          className="mt-3 max-h-96 w-full rounded-xl border border-[var(--border)] object-cover"
          loading="lazy"
        />
      )}

      <footer className="mt-3 flex items-center gap-5 text-sm">
        <button
          onClick={() => like.mutate()}
          disabled={like.isPending}
          className={`flex items-center gap-1.5 transition-colors ${post.likedByMe ? 'text-rose-500' : 'text-[var(--muted)] hover:text-rose-500'}`}
        >
          <span>{post.likedByMe ? '❤️' : '🤍'}</span> {post.likeCount}
        </button>
        <button
          onClick={() => setShowComments((v) => !v)}
          className="flex items-center gap-1.5 text-[var(--muted)] transition-colors hover:text-[var(--text)]"
        >
          💬 {post.commentCount}
        </button>
        {showSave && (
          <button
            onClick={() => save.mutate()}
            disabled={save.isPending}
            className={`ml-auto flex items-center gap-1.5 transition-colors ${saved ? 'text-amber-500' : 'text-[var(--muted)] hover:text-amber-500'}`}
          >
            {saved ? '🔖' : '📑'} Saved
          </button>
        )}
      </footer>

      {showComments && (
        <div className="mt-3 space-y-2 border-t border-[var(--border)] pt-3">
          {comments.isPending && <p className="text-xs text-[var(--muted)]">Loading…</p>}
          {comments.data?.length === 0 && <p className="text-xs text-[var(--muted)]">No comments yet.</p>}
          {comments.data?.map((c: { id: number; author: Author; content: string }) => (
            <div key={c.id} className="flex gap-2">
              <Avatar name={displayName(c.author)} id={String(c.author.id)} size="xs" />
              <p className="text-sm">
                <span className="font-semibold">{displayName(c.author)}</span>{' '}
                <span className="text-[var(--muted)]">{c.content}</span>
              </p>
            </div>
          ))}
          <form
            onSubmit={(e) => { e.preventDefault(); if (commentText.trim()) comment.mutate() }}
            className="flex gap-2 pt-1"
          >
            <input
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder="Write a comment…"
              className="flex-1 rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-1.5 text-sm outline-none focus:border-[var(--accent)]"
            />
            <button
              type="submit"
              disabled={!commentText.trim() || comment.isPending}
              className="rounded-xl bg-[var(--accent)] px-3 py-1.5 text-xs font-medium text-white hover:bg-[var(--accent-hover)] disabled:opacity-40"
            >
              Reply
            </button>
          </form>
        </div>
      )}
    </article>
  )
}

/** Home feed for signed-in users: real posts from the API. */
export function RealFeed() {
  const authUser = useAuthStore((s) => s.user)
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const [visibility, setVisibility] = useState<'PUBLIC' | 'FOLLOWERS' | 'PRIVATE'>('PUBLIC')

  const feed = useQuery({
    queryKey: ['real-feed'],
    queryFn: async () => (await api.get('/posts/feed?page=0&size=20')).data,
  })

  const create = useMutation({
    mutationFn: () => api.post('/posts', { content: text, visibility, imageUrl: imageUrl || undefined }),
    onSuccess: () => {
      setText('')
      setImageUrl(null)
      setImagePreview(null)
      pushToast('Posted!', '✅')
      queryClient.invalidateQueries({ queryKey: ['real-feed'] })
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const onPickImage = async (f: File | null) => {
    if (!f) return
    if (f.size > 5 * 1024 * 1024) {
      pushToast('Image too large (max 5 MB)', '⚠️')
      return
    }
    // Instant local preview — no waiting for the upload round-trip.
    const localUrl = URL.createObjectURL(f)
    setImagePreview(localUrl)
    setUploading(true)
    try {
      const fd = new FormData()
      fd.append('file', f)
      const { data } = await api.post<{ url: string }>('/media', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setImageUrl(data.url)
      setImagePreview(data.url)
      URL.revokeObjectURL(localUrl)
    } catch (e) {
      setImagePreview(null)
      URL.revokeObjectURL(localUrl)
      pushToast(apiErrorMessage(e), '⚠️')
    } finally {
      setUploading(false)
    }
  }

  if (!authUser) return null

  return (
    <div className="space-y-4">
      {/* composer */}
      <form
        onSubmit={(e) => { e.preventDefault(); if (text.trim()) create.mutate() }}
        className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4"
      >
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="What's happening?"
          rows={3}
          maxLength={2000}
          className="w-full resize-none bg-transparent text-[15px] outline-none placeholder:text-[var(--muted)]"
        />
        {imagePreview && (
          <div className="relative mt-2 overflow-hidden rounded-xl border border-[var(--border)]">
            <img src={imagePreview} alt="Upload preview" className="max-h-56 w-full object-cover" />
            <button
              type="button"
              onClick={() => { setImageUrl(null); setImagePreview(null) }}
              className="absolute right-2 top-2 rounded-full bg-black/60 px-2 py-1 text-xs text-white hover:bg-black/80"
              aria-label="Remove image"
            >
              ✕
            </button>
          </div>
        )}
        <div className="mt-2 flex items-center justify-between gap-3 border-t border-[var(--border)] pt-3">
          <div className="flex items-center gap-3">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/png,image/jpeg,image/gif,image/webp"
              className="hidden"
              onChange={(e) => { void onPickImage(e.target.files?.[0] ?? null); e.target.value = '' }}
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              className="text-lg text-[var(--muted)] transition-colors hover:text-[var(--accent)] disabled:opacity-40"
              title="Attach an image (png, jpeg, gif, webp — max 5 MB)"
              aria-label="Attach image"
            >
              {uploading ? '⏳' : '🖼️'}
            </button>
            <span className="text-xs text-[var(--muted)]">{text.length}/2000</span>
          </div>
          <div className="flex items-center gap-2">
            <select
              value={visibility}
              onChange={(e) => setVisibility(e.target.value as typeof visibility)}
              className="rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm outline-none"
            >
              <option value="PUBLIC">🌍 Public</option>
              <option value="FOLLOWERS">👥 Followers</option>
              <option value="PRIVATE">🔒 Only me</option>
            </select>
            <button
              type="submit"
              disabled={!text.trim() || create.isPending}
              className="rounded-xl bg-[var(--accent)] px-5 py-2 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)] active:scale-[0.98] disabled:opacity-40"
            >
              {create.isPending ? 'Posting…' : 'Post'}
            </button>
          </div>
        </div>
      </form>

      {/* feed */}
      {feed.isPending && (
        <div className="space-y-3">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-28 animate-pulse rounded-2xl bg-[var(--surface)]" />
          ))}
        </div>
      )}
      {feed.isError && (
        <p className="rounded-2xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-amber-500">
          Couldn't load your feed — is the backend running?
        </p>
      )}
      {feed.data?.posts?.length === 0 && (
        <p className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-6 text-center text-sm text-[var(--muted)]">
          Your feed is empty. Follow people from <strong>Nearby</strong> or post something yourself!
        </p>
      )}
      {feed.data?.posts?.map((p: RealPost) => <RealPostCard key={p.id} post={p} />)}
    </div>
  )
}

/** Explore for signed-in users: real public posts from the API, tag filter + sort. */
export function RealExplore() {
  const [params, setParams] = useSearchParams()
  const activeTag = params.get('tag')
  const [sort, setSort] = useState<'trending' | 'recent'>('trending')

  const explore = useQuery({
    queryKey: ['real-explore'],
    queryFn: async () => (await api.get('/posts/explore?page=0&size=30')).data as { posts: RealPost[] },
  })

  const posts = explore.data?.posts ?? []

  const trendingTags = Object.entries(
    posts.flatMap((p) => (p.content.match(/#(\w{2,20})/g) ?? [])).reduce<Record<string, number>>((acc, raw) => {
      const t = raw.slice(1).toLowerCase()
      acc[t] = (acc[t] ?? 0) + 1
      return acc
    }, {}),
  )
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8)

  const visible = (() => {
    let list = activeTag ? posts.filter((p) => p.content.toLowerCase().includes(`#${activeTag.toLowerCase()}`)) : [...posts]
    if (sort === 'trending') list.sort((a, b) => b.likeCount - a.likeCount)
    else list.sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))
    return list
  })()

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-4 sm:p-6">
      <h1 className="text-2xl font-bold">Explore</h1>

      <div className="flex flex-wrap gap-2">
        <button
          onClick={() => setParams({})}
          className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
            !activeTag ? 'bg-[var(--accent)] text-white' : 'bg-[var(--surface-2)] text-[var(--muted)] hover:bg-[var(--accent-soft)]'
          }`}
        >
          All
        </button>
        {trendingTags.map(([tag, count]) => (
          <button
            key={tag}
            onClick={() => setParams({ tag })}
            className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
              activeTag === tag ? 'bg-[var(--accent)] text-white' : 'bg-[var(--surface-2)] text-[var(--muted)] hover:bg-[var(--accent-soft)]'
            }`}
          >
            #{tag} <span className="opacity-60">{count}</span>
          </button>
        ))}
      </div>

      <div className="flex items-center gap-2 text-sm">
        <span className="text-[var(--muted)]">Sort:</span>
        {(['trending', 'recent'] as const).map((s) => (
          <button
            key={s}
            onClick={() => setSort(s)}
            className={`rounded-lg px-2.5 py-1 capitalize transition-colors ${
              sort === s ? 'bg-[var(--accent-soft)] font-medium text-indigo-400' : 'text-[var(--muted)] hover:text-[var(--text)]'
            }`}
          >
            {s}
          </button>
        ))}
      </div>

      {explore.isPending && (
        <div className="space-y-3">
          {[...Array(3)].map((_, i) => <div key={i} className="h-28 animate-pulse rounded-2xl bg-[var(--surface)]" />)}
        </div>
      )}
      {explore.isError && (
        <p className="rounded-2xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-amber-500">
          Couldn't load explore — is the backend running?
        </p>
      )}
      {visible.length === 0 && !explore.isPending && (
        <p className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center text-sm text-[var(--muted)]">
          {activeTag ? `No posts with #${activeTag} yet.` : 'No public posts yet.'}
        </p>
      )}
      <div className="space-y-4">
        {visible.map((p) => <RealPostCard key={p.id} post={p} />)}
      </div>
    </div>
  )
}
