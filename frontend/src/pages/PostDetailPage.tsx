import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useAuthStore } from '../store/authStore'
import { RealPostCard, type RealPost } from '../components/RealFeed'

export function PostDetailPage() {
  const { postId } = useParams()
  const authUser = useAuthStore((s) => s.user)

  // Public posts are readable by anyone; visibility rules live on the server
  // (FOLLOWERS/PRIVATE 404 for strangers), so guests can safely fetch too.
  const realPost = useQuery({
    queryKey: ['real-post', postId],
    queryFn: async () => (await api.get<RealPost>(`/posts/${postId}`)).data,
    enabled: Boolean(postId),
    retry: false,
  })

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
      {!authUser && (
        <p className="text-center text-xs text-[var(--muted)]">
          <Link to="/register" className="text-indigo-400 hover:underline">Create an account</Link>
          {' '}to like, comment and follow.
        </p>
      )}
    </div>
  )
}
