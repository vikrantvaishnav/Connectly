import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAppStore } from '../store/appStore'
import { PostCard } from '../components/PostCard'
import { Avatar } from '../components/Avatar'
import { RealExplore } from '../components/RealFeed'
import { useAuthStore } from '../store/authStore'

export function ExplorePage() {
  const authUser = useAuthStore((s) => s.user)
  if (authUser) return <RealExplore />
  return <DemoExplore />
}

function DemoExplore() {
  const [params, setParams] = useSearchParams()
  const activeTag = params.get('tag')
  const posts = useAppStore((s) => s.posts)
  const communities = useAppStore((s) => s.communities)
  const users = useAppStore((s) => s.users)
  const [sort, setSort] = useState<'trending' | 'recent'>('trending')

  const trendingTags = useMemo(
    () =>
      Object.entries(
        posts.flatMap((p) => p.tags).reduce<Record<string, number>>((acc, t) => {
          acc[t] = (acc[t] ?? 0) + 1
          return acc
        }, {}),
      )
        .sort((a, b) => b[1] - a[1])
        .slice(0, 8),
    [posts],
  )

  const visible = useMemo(() => {
    let list = activeTag ? posts.filter((p) => p.tags.includes(activeTag)) : [...posts]
    if (sort === 'trending') list = list.sort((a, b) => b.likes - a.likes)
    else list = list.sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))
    return list
  }, [posts, activeTag, sort])

  const popularUsers = [...users]
    .filter((u) => u.id !== 'u0')
    .sort((a, b) => b.followers - a.followers)
    .slice(0, 4)

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-4 sm:p-6">
      <h1 className="text-2xl font-bold">Explore</h1>

      {/* Trending tags */}
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

      <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
        <div className="space-y-4">
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

          {visible.length === 0 && (
            <p className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center text-sm text-[var(--muted)]">
              No posts with #{activeTag} yet.
            </p>
          )}
          <div className="space-y-4">
            {visible.map((p) => (
              <PostCard key={p.id} post={p} />
            ))}
          </div>
        </div>

        <aside className="space-y-4">
          <div className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
            <h2 className="mb-3 text-sm font-semibold">People to follow</h2>
            <div className="space-y-3">
              {popularUsers.map((u) => (
                <Link key={u.id} to={`/profile/${u.username}`} className="flex items-center gap-3 rounded-lg p-1 hover:bg-[var(--accent-soft)]">
                  <Avatar name={u.name} id={u.id} size="sm" online={u.online} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{u.name}</p>
                    <p className="truncate text-xs text-[var(--muted)]">{u.followers.toLocaleString()} followers</p>
                  </div>
                </Link>
              ))}
            </div>
          </div>

          <div className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4">
            <h2 className="mb-3 text-sm font-semibold">Communities for you</h2>
            <div className="space-y-3">
              {communities.slice(0, 3).map((c) => (
                <Link key={c.id} to={`/communities/${c.id}`} className="flex items-center gap-3 rounded-lg p-1 hover:bg-[var(--accent-soft)]">
                  <span className="text-xl">{c.icon}</span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{c.name}</p>
                    <p className="truncate text-xs text-[var(--muted)]">{c.members.toLocaleString()} members</p>
                  </div>
                </Link>
              ))}
            </div>
          </div>
        </aside>
      </div>
    </div>
  )
}
