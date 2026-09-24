import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useAuthStore } from '../store/authStore'
import { RealPostCard, type RealPost } from '../components/RealFeed'
import { PostCard } from '../components/PostCard'
import { useAppStore } from '../store/appStore'

export function PostDetailPage() {
  const { postId } = useParams()
  const authUser = useAuthStore((s) => s.user)
  const demoPosts = useAppStore((s) => s.posts)

  // Signed-in: fetch the real post (server enforces visibility).
  const realPost = useQuery({
    queryKey: ['real-post', postId],
    queryFn: async () => (await api.get<RealPost>(`/posts/${postId}`)).data,
    enabled: Boolean(authUser && postId),
    retry: false,
  })

  if (authUser) {
    if (realPost.isPending) {
      return <div className="p-10 text-center text-sm text-[var(--muted)]">Loading…</div>
    }
    if (realPost.isError || !realPost.data) {
      return (
        <div className="p-10 text-center">
          <p className="text-sm text-[var(--muted)]">Post not found (or not visible to you).</p>
          <Link to="/home" className="mt-2 inline-block text-sm text-indigo-400 hover:underline">← Back home</Link>
        </div>
      )
    }
    return (
      <div className="mx-auto max-w-2xl space-y-4 p-4 sm:p-6">
        <Link to="/home" className="inline-block text-sm text-[var(--muted)] hover:text-[var(--text)]">
          ← Back to feed
        </Link>
        <RealPostCard post={realPost.data} />
      </div>
    )
  }

  // Guests: demo fallback.
  const demoPost = demoPosts.find((p) => p.id === postId)
  if (!demoPost) {
    return (
      <div className="p-10 text-center">
        <p className="text-sm text-[var(--muted)]">Post not found (it may be from an older demo session).</p>
        <Link to="/home" className="mt-2 inline-block text-sm text-indigo-400 hover:underline">← Back home</Link>
      </div>
    )
  }
  return (
    <div className="mx-auto max-w-2xl space-y-4 p-4 sm:p-6">
      <Link to="/home" className="inline-block text-sm text-[var(--muted)] hover:text-[var(--text)]">
        ← Back to feed
      </Link>
      <PostCard post={demoPost} />
    </div>
  )
}
